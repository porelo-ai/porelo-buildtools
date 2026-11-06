package org.fuin.porelo.release;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;

class PruneSnapshotsTest {

    private static final Instant NOW = Instant.parse("2026-11-06T06:00:00Z");

    private static final Instant OLD = NOW.minus(Duration.ofDays(40));

    private static final Instant YOUNG = NOW.minus(Duration.ofDays(3));

    private final StandIn artifactory = new StandIn();

    private final ByteArrayOutputStream out = new ByteArrayOutputStream();

    private final ByteArrayOutputStream err = new ByteArrayOutputStream();

    @Test
    void deletesOldSnapshotsButKeepsTheNewestThreeOfANameHoweverOld() {
        for (int build = 1; build <= 6; build++) {
            artifactory.deb(
                    "porelo", "0.5.0~snapshot." + build, "snapshots", OLD.plusSeconds(build));
            artifactory.rpm("porelo", "0.5.0~snapshot." + build, OLD.plusSeconds(build));
        }

        assertThat(prune(true)).as(stderr()).isEqualTo(0);

        assertThat(artifactory.files())
                .as("the three newest of each name stay, though all are old")
                .containsExactlyInAnyOrder(
                        deb("porelo", "0.5.0~snapshot.4"),
                        deb("porelo", "0.5.0~snapshot.5"),
                        deb("porelo", "0.5.0~snapshot.6"),
                        rpm("porelo", "0.5.0~snapshot.4"),
                        rpm("porelo", "0.5.0~snapshot.5"),
                        rpm("porelo", "0.5.0~snapshot.6"));
    }

    @Test
    void keepsWhatIsYoungerThanFourWeeks() {
        for (int build = 1; build <= 5; build++) {
            artifactory.deb(
                    "porelo-frontend",
                    "0.5.0~snapshot." + build,
                    "snapshots",
                    build == 1 ? OLD : YOUNG);
        }

        assertThat(prune(true)).as(stderr()).isEqualTo(0);

        assertThat(artifactory.files())
                .as("only the old one beyond the newest three goes")
                .doesNotContain(deb("porelo-frontend", "0.5.0~snapshot.1"))
                .contains(deb("porelo-frontend", "0.5.0~snapshot.2"));
    }

    @Test
    void neverDeletesAReleaseThoughItLiesInTheSameFolderAsOldSnapshots() {
        for (int build = 1; build <= 6; build++) {
            artifactory.deb("porelo", "0.4." + build, "releases", OLD);
            artifactory.deb(
                    "porelo",
                    "0.4." + build + "~snapshot." + build,
                    "snapshots",
                    OLD.plusSeconds(build));
        }
        // Marked a snapshot, but the releases index lists it: the index wins.
        artifactory.deb("porelo", "0.3.9", "snapshots", OLD);
        artifactory.releasesIndex.add(
                deb("porelo", "0.3.9").substring(PruneSnapshots.DEB.length() + 1));
        // In both channels at once.
        artifactory.deb("porelo", "0.3.8", "snapshots,releases", OLD);

        assertThat(prune(true)).as(stderr()).isEqualTo(0);

        final List<String> files = artifactory.files();
        for (int build = 1; build <= 6; build++) {
            assertThat(files)
                    .as("release 0.4.%d stays", build)
                    .contains(deb("porelo", "0.4." + build));
        }
        assertThat(files)
                .as("a file the releases index lists, and one of both channels, stay")
                .contains(deb("porelo", "0.3.9"), deb("porelo", "0.3.8"));
        assertThat(files)
                .as("the old snapshots beyond the newest three do go")
                .doesNotContain(deb("porelo", "0.4.1~snapshot.1"));
    }

    @Test
    void ordersVersionsAsDpkgDoesSoTheNewestAreNotTakenForTheOldest() {
        int built = 0;
        // As text, snapshot.10 sorts before snapshot.9, and 0.5.1~ before 0.5.0 nowhere.
        for (String version :
                List.of(
                        "0.5.0~snapshot.9",
                        "0.5.0~snapshot.10",
                        "0.5.0~snapshot.290",
                        "0.5.0",
                        "0.5.1~snapshot.2")) {
            // Built in this order, so the newest by time are the newest by version too.
            artifactory.deb("porelo-agent-pi", version, "snapshots", OLD.plusSeconds(++built));
        }

        assertThat(prune(true)).as(stderr()).isEqualTo(0);

        assertThat(artifactory.files())
                .as("0.5.0~snapshot.290 < 0.5.0 < 0.5.1~snapshot.2 are the three newest")
                .containsExactlyInAnyOrder(
                        deb("porelo-agent-pi", "0.5.0~snapshot.290"),
                        deb("porelo-agent-pi", "0.5.0"),
                        deb("porelo-agent-pi", "0.5.1~snapshot.2"));
    }

    @Test
    void aDryRunDeletesNothingAndListsWhatWouldGo() {
        for (int build = 1; build <= 4; build++) {
            artifactory.deb(
                    "porelo", "0.5.0~snapshot." + build, "snapshots", OLD.plusSeconds(build));
        }
        final List<String> before = artifactory.files();

        assertThat(prune(false)).as(stderr()).isEqualTo(0);

        assertThat(artifactory.files()).as("nothing is deleted").isEqualTo(before);
        assertThat(stdout())
                .as("what would go is listed")
                .contains("delete " + deb("porelo", "0.5.0~snapshot.1"))
                .contains("nothing was deleted");
    }

    @Test
    void aTokenThatCannotDeleteDeletesNothingAndNamesTheProbeLeft() {
        for (int build = 1; build <= 4; build++) {
            artifactory.deb(
                    "porelo", "0.5.0~snapshot." + build, "snapshots", OLD.plusSeconds(build));
        }
        artifactory.deleteRefused = true;
        final List<String> before = artifactory.files();

        assertThat(prune(true)).as(stdout()).isEqualTo(Stop.REFUSED);

        assertThat(artifactory.files())
                .as("no package goes, the probe is left")
                .containsAll(before)
                .contains(PruneSnapshots.DEB + "/prune-probe/run-1.txt");
        assertThat(stderr())
                .as("the probe left is named")
                .contains("porelo-dist-deb/prune-probe/run-1.txt is left");
    }

    @Test
    void aReleasesIndexThatCannotBeReadDeletesNothing() {
        for (int build = 1; build <= 4; build++) {
            artifactory.deb(
                    "porelo", "0.5.0~snapshot." + build, "snapshots", OLD.plusSeconds(build));
        }
        artifactory.releasesIndexMissing = true;
        final List<String> before = artifactory.files();

        assertThat(prune(true)).as(stdout()).isEqualTo(Stop.UNANSWERED);

        assertThat(artifactory.files()).as("nothing is deleted").isEqualTo(before);
    }

    @Test
    void anIndexThatKeepsListingADeletedFileIsAskedOnceAndThenFailsTheRun() {
        for (int build = 1; build <= 4; build++) {
            artifactory.deb(
                    "porelo", "0.5.0~snapshot." + build, "snapshots", OLD.plusSeconds(build));
            artifactory.rpm("porelo", "0.5.0~snapshot." + build, OLD.plusSeconds(build));
        }
        artifactory.indexesStale = true;

        assertThat(prune(true)).as(stdout()).isEqualTo(Stop.REFUSED);

        assertThat(artifactory.recalculated)
                .as("each index is asked once")
                .containsExactly("deb", "rpm snapshots");
        assertThat(stderr())
                .as("what is still listed is named")
                .contains(
                        "still listed by its index after it was deleted: "
                                + deb("porelo", "0.5.0~snapshot.1"))
                .contains(
                        "still listed by its index after it was deleted: "
                                + rpm("porelo", "0.5.0~snapshot.1"));
    }

    @Test
    void anIndexThatFollowsWhenAskedPassesTheRun() {
        for (int build = 1; build <= 4; build++) {
            artifactory.rpm("porelo", "0.5.0~snapshot." + build, OLD.plusSeconds(build));
        }
        artifactory.indexesStale = true;
        artifactory.recalculationHelps = true;

        assertThat(prune(true)).as(stderr()).isEqualTo(0);

        assertThat(artifactory.recalculated).as("asked once").containsExactly("rpm snapshots");
    }

    @Test
    void aReleasesIndexThatListsNoReleaseOfThePoolDeletesNothing() {
        for (int build = 1; build <= 4; build++) {
            artifactory.deb(
                    "porelo", "0.5.0~snapshot." + build, "snapshots", OLD.plusSeconds(build));
        }
        artifactory.deb("porelo", "0.4.0", "releases", OLD);
        // Answered, but empty, as an index being rewritten might be.
        artifactory.releasesIndexEmpty = true;
        final List<String> before = artifactory.files();

        assertThat(prune(true)).as(stdout()).isEqualTo(Stop.UNANSWERED);

        assertThat(artifactory.files()).as("nothing is deleted").isEqualTo(before);
        assertThat(stderr())
                .as("the release it does not list is named")
                .contains("porelo_0.4.0_amd64.deb");
    }

    @Test
    void theNewestBuildsStayThoughAnOlderNamingSortsAboveThem() {
        // 0.5.1-snapshot.N sorts above 0.5.1~snapshot.M for dpkg and rpm alike, though built before
        // it.
        for (int build = 90; build <= 92; build++) {
            artifactory.deb(
                    "porelo-matrix-homeserver",
                    "0.5.1-snapshot." + build,
                    "snapshots",
                    OLD.minus(Duration.ofDays(10)).plusSeconds(build));
        }
        for (int build = 100; build <= 102; build++) {
            artifactory.deb(
                    "porelo-matrix-homeserver",
                    "0.5.1~snapshot." + build,
                    "snapshots",
                    OLD.plusSeconds(build));
        }

        assertThat(prune(true)).as(stderr()).isEqualTo(0);

        assertThat(artifactory.files())
                .as("the three built last stay beside the three that sort highest")
                .contains(
                        deb("porelo-matrix-homeserver", "0.5.1~snapshot.100"),
                        deb("porelo-matrix-homeserver", "0.5.1~snapshot.101"),
                        deb("porelo-matrix-homeserver", "0.5.1~snapshot.102"),
                        deb("porelo-matrix-homeserver", "0.5.1-snapshot.92"));
    }

    @Test
    void anIndexGoneAfterADeletionFailsTheRun() {
        for (int build = 1; build <= 4; build++) {
            artifactory.deb(
                    "porelo", "0.5.0~snapshot." + build, "snapshots", OLD.plusSeconds(build));
            artifactory.rpm("porelo", "0.5.0~snapshot." + build, OLD.plusSeconds(build));
        }
        artifactory.indexesGoneAfterDelete = true;

        assertThat(prune(true)).as(stdout()).isEqualTo(Stop.REFUSED);

        assertThat(stderr())
                .as("a missing index is not one that followed")
                .contains(
                        "still listed by its index after it was deleted: "
                                + deb("porelo", "0.5.0~snapshot.1"))
                .contains(
                        "still listed by its index after it was deleted: "
                                + rpm("porelo", "0.5.0~snapshot.1"));
    }

    @Test
    void aDebianFileWithNoChannelStopsTheRunBeforeAnyDeletion() {
        for (int build = 1; build <= 4; build++) {
            artifactory.deb(
                    "porelo", "0.5.0~snapshot." + build, "snapshots", OLD.plusSeconds(build));
        }
        artifactory.file(deb("porelo", "0.3.0"), OLD, Map.of());
        final List<String> before = artifactory.files();

        assertThat(prune(true)).as(stdout()).isEqualTo(Stop.UNANSWERED);

        assertThat(artifactory.files()).as("nothing is deleted").isEqualTo(before);
        assertThat(stderr()).as("the file is named").contains("porelo_0.3.0_amd64.deb");
    }

    @Test
    void aDebianFileOfAnotherArchitectureIsNeverDeleted() {
        for (int build = 1; build <= 4; build++) {
            artifactory.deb(
                    "porelo", "0.5.0~snapshot." + build, "snapshots", OLD.plusSeconds(build));
        }
        final String arm = PruneSnapshots.DEB + "/pool/main/p/porelo/porelo_0.4.0_arm64.deb";
        // Only the amd64 index is read, so its release would never be recognised.
        artifactory.file(
                arm,
                OLD,
                Map.of(
                        "deb.distribution", List.of("snapshots"),
                        "deb.architecture", List.of("arm64"),
                        "deb.name", List.of("porelo"),
                        "deb.version", List.of("0.4.0")));

        assertThat(prune(true)).as(stderr()).isEqualTo(0);

        assertThat(artifactory.files()).as("the arm64 file stays").contains(arm);
    }

    @Test
    void aPackageForEveryArchitectureIsPrunedLikeTheOthers() {
        // As porelo-matrix-homeserver's: the amd64 index lists it.
        for (int build = 1; build <= 4; build++) {
            artifactory.file(
                    PruneSnapshots.DEB
                            + "/pool/main/p/porelo-matrix-homeserver/porelo-matrix-homeserver_0.5.0~snapshot."
                            + build
                            + "_all.deb",
                    OLD.plusSeconds(build),
                    Map.of(
                            "deb.distribution", List.of("snapshots"),
                            "deb.architecture", List.of("all"),
                            "deb.name", List.of("porelo-matrix-homeserver"),
                            "deb.version", List.of("0.5.0~snapshot." + build)));
        }

        assertThat(prune(true)).as(stderr()).isEqualTo(0);

        assertThat(artifactory.files())
                .as("the oldest beyond the newest three goes")
                .doesNotContain(
                        PruneSnapshots.DEB
                                + "/pool/main/p/porelo-matrix-homeserver/porelo-matrix-homeserver_0.5.0~snapshot.1_all.deb");
    }

    @Test
    void aTokenThatCannotDeleteInTheRpmRepositoryDeletesNothing() {
        for (int build = 1; build <= 4; build++) {
            artifactory.deb(
                    "porelo", "0.5.0~snapshot." + build, "snapshots", OLD.plusSeconds(build));
        }
        artifactory.deleteRefusedIn = PruneSnapshots.RPM;
        final List<String> before = artifactory.files();

        assertThat(prune(true)).as(stdout()).isEqualTo(Stop.REFUSED);

        assertThat(artifactory.files()).as("no Debian package goes either").containsAll(before);
        assertThat(stderr())
                .as("the RPM probe is named")
                .contains("porelo-dist-rpm/prune-probe/run-1.txt is left");
    }

    @Test
    void aDeleteAnsweredButNotDoneStopsTheRun() {
        for (int build = 1; build <= 4; build++) {
            artifactory.deb(
                    "porelo", "0.5.0~snapshot." + build, "snapshots", OLD.plusSeconds(build));
        }
        artifactory.probeDeleteIgnored = true;

        assertThat(prune(true)).as(stdout()).isEqualTo(Stop.REFUSED);

        assertThat(artifactory.files())
                .as("no package goes")
                .contains(deb("porelo", "0.5.0~snapshot.1"));
    }

    @Test
    void touchesNoFileButThePackagesThemselves() {
        for (int build = 1; build <= 4; build++) {
            artifactory.deb(
                    "porelo", "0.5.0~snapshot." + build, "snapshots", OLD.plusSeconds(build));
            artifactory.rpm("porelo", "0.5.0~snapshot." + build, OLD.plusSeconds(build));
        }
        final String debBill = PruneSnapshots.DEB + "/pool/main/p/porelo/porelo.cdx.json";
        final String rpmBill = PruneSnapshots.RPM + "/snapshots/porelo.cdx.json";
        artifactory.file(debBill, OLD, Map.of());
        artifactory.file(rpmBill, OLD, Map.of());

        assertThat(prune(true)).as(stderr()).isEqualTo(0);

        assertThat(artifactory.files()).as("the bills stay").contains(debBill, rpmBill);
    }

    @Test
    void aVersionWithOneYoungFileStaysWhole() {
        for (int build = 1; build <= 4; build++) {
            artifactory.deb(
                    "porelo", "0.5.0~snapshot." + build, "snapshots", OLD.plusSeconds(build));
        }
        // Three newer versions, built after the young file, so it is not among the newest by time
        // either.
        for (int build = 5; build <= 7; build++) {
            artifactory.deb(
                    "porelo", "0.5.0~snapshot." + build, "snapshots", YOUNG.plusSeconds(build));
        }
        final String young =
                PruneSnapshots.DEB + "/pool/main/p/porelo/porelo_0.5.0~snapshot.1_all.deb";
        artifactory.file(
                young,
                YOUNG,
                Map.of(
                        "deb.distribution", List.of("snapshots"),
                        "deb.architecture", List.of("amd64"),
                        "deb.name", List.of("porelo"),
                        "deb.version", List.of("0.5.0~snapshot.1")));

        assertThat(prune(true)).as(stderr()).isEqualTo(0);

        assertThat(artifactory.files())
                .as("both files of the version stay")
                .contains(young, deb("porelo", "0.5.0~snapshot.1"));
    }

    private int prune(boolean delete) {
        return new PruneSnapshots(
                        artifactory,
                        new PrintStream(out, true, StandardCharsets.UTF_8),
                        new PrintStream(err, true, StandardCharsets.UTF_8),
                        duration -> {})
                .run(delete, NOW, "run-1");
    }

    private static String deb(String name, String version) {
        return PruneSnapshots.DEB
                + "/pool/main/p/"
                + name
                + "/"
                + name
                + "_"
                + version
                + "_amd64.deb";
    }

    private static String rpm(String name, String version) {
        return PruneSnapshots.RPM + "/snapshots/" + name + "-" + version + "-1.x86_64.rpm";
    }

    private String stdout() {
        return out.toString(StandardCharsets.UTF_8);
    }

    private String stderr() {
        return err.toString(StandardCharsets.UTF_8) + stdout();
    }

    /**
     * Artifactory as the pruning sees it: files with a time and properties, two indexes per
     * repository.
     */
    private static final class StandIn implements Artifactory {

        private record Stored(Instant created, Map<String, List<String>> properties) {}

        /** By "repository/path". */
        private final Map<String, Stored> stored = new LinkedHashMap<>();

        private final Set<String> releasesIndex = new TreeSet<>();

        /** What each index lists, as it was before any deletion while stale. */
        private final Set<String> staleListing = new TreeSet<>();

        private final List<String> recalculated = new ArrayList<>();

        private boolean deleteRefused;

        private boolean releasesIndexMissing;

        private boolean indexesStale;

        private boolean recalculationHelps;

        private boolean releasesIndexEmpty;

        /** A repository whose deletes are refused; the other's are not. */
        private String deleteRefusedIn = "";

        /** A delete answered as done that leaves the probe where it was. */
        private boolean probeDeleteIgnored;

        /** The snapshots' indexes answer 404 once anything was deleted. */
        private boolean indexesGoneAfterDelete;

        private boolean deletedAny;

        void file(String key, Instant created, Map<String, List<String>> properties) {
            stored.put(key, new Stored(created, properties));
        }

        void deb(String name, String version, String channels, Instant created) {
            stored.put(
                    PruneSnapshotsTest.deb(name, version),
                    new Stored(
                            created,
                            Map.of(
                                    "deb.distribution", List.of(channels.split(",")),
                                    "deb.architecture", List.of("amd64"),
                                    "deb.name", List.of(name),
                                    "deb.version", List.of(version))));
            if (channels.equals("releases")) {
                releasesIndex.add(
                        PruneSnapshotsTest.deb(name, version)
                                .substring(PruneSnapshots.DEB.length() + 1));
            }
        }

        void rpm(String name, String version, Instant created) {
            stored.put(PruneSnapshotsTest.rpm(name, version), new Stored(created, Map.of()));
        }

        List<String> files() {
            return new ArrayList<>(stored.keySet());
        }

        @Override
        public Optional<List<Child>> children(String repository, String folder) {
            final String prefix = repository + "/" + folder + "/";
            final Set<String> names = new TreeSet<>();
            final Set<String> folders = new TreeSet<>();
            for (String key : stored.keySet()) {
                if (key.startsWith(prefix)) {
                    final String rest = key.substring(prefix.length());
                    if (rest.contains("/")) {
                        folders.add(rest.substring(0, rest.indexOf('/')));
                    } else {
                        names.add(rest);
                    }
                }
            }
            // The pool and the RPM channel's folder are there in the real repositories, empty or
            // not.
            final boolean always =
                    (repository + "/" + folder)
                                    .equals(PruneSnapshots.DEB + "/" + PruneSnapshots.POOL)
                            || (repository + "/" + folder)
                                    .equals(PruneSnapshots.RPM + "/snapshots");
            if (names.isEmpty() && folders.isEmpty() && !always) {
                return Optional.empty();
            }
            final List<Child> result = new ArrayList<>();
            folders.forEach(name -> result.add(new Child(name, true)));
            names.forEach(name -> result.add(new Child(name, false)));
            return Optional.of(result);
        }

        @Override
        public Instant created(String repository, String path) throws IOException {
            return file(repository, path).created();
        }

        @Override
        public Map<String, List<String>> properties(String repository, String path)
                throws IOException {
            return file(repository, path).properties();
        }

        private Stored file(String repository, String path) throws IOException {
            final Stored file = stored.get(repository + "/" + path);
            if (file == null) {
                throw new IOException(repository + "/" + path + ": HTTP 404");
            }
            return file;
        }

        @Override
        public void put(String repository, String path, byte[] body) {
            stored.put(repository + "/" + path, new Stored(NOW, Map.of()));
        }

        @Override
        public void delete(String repository, String path) throws IOException {
            if (deleteRefused || repository.equals(deleteRefusedIn)) {
                throw new IOException("DELETE " + repository + "/" + path + ": HTTP 403");
            }
            if (indexesStale && staleListing.isEmpty()) {
                staleListing.addAll(stored.keySet());
            }
            if (probeDeleteIgnored && path.startsWith(PruneSnapshots.PROBE_FOLDER + "/")) {
                return;
            }
            if (!path.startsWith(PruneSnapshots.PROBE_FOLDER + "/")) {
                deletedAny = true;
            }
            if (stored.remove(repository + "/" + path) == null) {
                throw new IOException("DELETE " + repository + "/" + path + ": HTTP 404");
            }
        }

        private Set<String> listing() {
            return indexesStale && !staleListing.isEmpty() ? staleListing : stored.keySet();
        }

        @Override
        public Optional<String> debianPackages(String repository, String distribution) {
            final StringBuilder index = new StringBuilder();
            if (!distribution.equals("releases") && indexesGoneAfterDelete && deletedAny) {
                return Optional.empty();
            }
            if (distribution.equals("releases")) {
                if (releasesIndexMissing) {
                    return Optional.empty();
                }
                if (releasesIndexEmpty) {
                    return Optional.of("");
                }
                releasesIndex.forEach(
                        file -> index.append("Package: x\nFilename: ").append(file).append("\n\n"));
                return Optional.of(index.toString());
            }
            for (String key : listing()) {
                if (key.startsWith(repository + "/pool/")) {
                    index.append("Package: x\nFilename: ")
                            .append(key.substring(repository.length() + 1))
                            .append("\n\n");
                }
            }
            return Optional.of(index.toString());
        }

        @Override
        public Optional<String> rpmPrimary(String repository, String folder) {
            if (indexesGoneAfterDelete && deletedAny) {
                return Optional.empty();
            }
            final StringBuilder primary = new StringBuilder("<metadata>\n");
            final String prefix = repository + "/" + folder + "/";
            for (String key : listing()) {
                if (key.startsWith(prefix) && key.endsWith(".rpm")) {
                    primary.append("<package><location href=\"")
                            .append(key.substring(prefix.length()))
                            .append("\"/></package>\n");
                }
            }
            return Optional.of(primary.append("</metadata>\n").toString());
        }

        @Override
        public void recalculateDebian(String repository) {
            recalculated.add("deb");
            recalculate(repository);
        }

        @Override
        public void recalculateRpm(String repository, String folder) {
            recalculated.add("rpm " + folder);
            recalculate(repository);
        }

        private void recalculate(String repository) {
            if (recalculationHelps) {
                staleListing.removeIf(
                        key -> key.startsWith(repository + "/") && !stored.containsKey(key));
            }
        }
    }
}
