package org.fuin.porelo.release;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Deletes old snapshot packages from the Debian and the RPM repository, keeping the newest versions
 * of each package name however old they are, and never touching the {@code releases} channel.
 */
public final class PruneSnapshots {

    /** The Debian repository. */
    static final String DEB = "porelo-dist-deb";

    /** The RPM repository. */
    static final String RPM = "porelo-dist-rpm";

    /** The channel pruned; the only one. */
    static final String SNAPSHOTS = "snapshots";

    /** The channel never touched. */
    static final String RELEASES = "releases";

    /** Where the Debian packages of both channels lie, one folder per package name. */
    static final String POOL = "pool/main/p";

    /** Where the probe goes in each repository: outside the pool and every channel's folder. */
    static final String PROBE_FOLDER = "prune-probe";

    /** How many versions of a name stay, however old. */
    static final int KEEP = 3;

    /** Older than this is old. */
    static final Duration AGE = Duration.ofDays(28);

    /** How long an index is given to follow a deletion, before and after it is asked to. */
    static final Duration INDEX_WAIT = Duration.ofMinutes(2);

    /** How often an index is read while waiting. */
    static final Duration INDEX_POLL = Duration.ofSeconds(10);

    /**
     * {@code name-version-release.arch.rpm}: a name may hold dashes, a version and a release do
     * not.
     */
    private static final Pattern RPM_FILE = Pattern.compile("(.+)-([^-]+)-([^-]+)\\.([^.]+)\\.rpm");

    private static final Pattern FILENAME = Pattern.compile("(?m)^Filename: (\\S+)$");

    /** Waits, so a test need not. */
    @FunctionalInterface
    interface Sleeper {
        /**
         * Waits.
         *
         * @param duration how long
         * @throws InterruptedException when interrupted
         */
        void sleep(Duration duration) throws InterruptedException;
    }

    /**
     * One package file, as read.
     *
     * @param repository the repository's key
     * @param path its path in the repository
     * @param name the package's name
     * @param version its version
     * @param created when Artifactory stored it
     */
    record Packaged(String repository, String path, String name, String version, Instant created) {

        /** The file's name, without its folder. */
        String file() {
            return path.substring(path.lastIndexOf('/') + 1);
        }
    }

    /**
     * What the rule makes of the files read.
     *
     * @param deleted what goes, oldest version first
     * @param kept the versions that stay, per repository and name, newest first
     */
    record Selection(List<Packaged> deleted, Map<String, List<String>> kept) {}

    private final Artifactory artifactory;

    private final PrintStream out;

    private final PrintStream err;

    private final Sleeper sleeper;

    /**
     * Prunes through an Artifactory.
     *
     * @param artifactory where
     * @param out what is listed and done
     * @param err what failed
     * @param sleeper how a wait for an index waits
     */
    PruneSnapshots(Artifactory artifactory, PrintStream out, PrintStream err, Sleeper sleeper) {
        this.artifactory = artifactory;
        this.out = out;
        this.err = err;
        this.sleeper = sleeper;
    }

    /**
     * The rule: per repository and package name, the {@link #KEEP} newest versions stay; of the
     * others, every file stored more than {@link #AGE} before {@code now} goes.
     *
     * @param files the snapshot files read, none of another channel
     * @param now the run's time
     * @return what goes and what stays
     */
    static Selection select(List<Packaged> files, Instant now) {
        final Map<String, List<Packaged>> byName = new TreeMap<>();
        for (Packaged file : files) {
            byName.computeIfAbsent(file.repository() + " " + file.name(), key -> new ArrayList<>())
                    .add(file);
        }
        final Instant cut = now.minus(AGE);
        final List<Packaged> deleted = new ArrayList<>();
        final Map<String, List<String>> kept = new TreeMap<>();
        for (Map.Entry<String, List<Packaged>> name : byName.entrySet()) {
            final List<String> versions =
                    name.getValue().stream()
                            .map(Packaged::version)
                            .distinct()
                            .sorted(DebianVersion.ORDER.reversed())
                            .toList();
            // By version and by time: a naming that sorts above a later one must not cost the
            // builds made last.
            final Set<String> newest =
                    new HashSet<>(versions.subList(0, Math.min(KEEP, versions.size())));
            name.getValue().stream()
                    .collect(
                            java.util.stream.Collectors.toMap(
                                    Packaged::version,
                                    Packaged::created,
                                    (a, b) -> a.isAfter(b) ? a : b))
                    .entrySet()
                    .stream()
                    .sorted(Map.Entry.<String, Instant>comparingByValue().reversed())
                    .limit(KEEP)
                    .forEach(version -> newest.add(version.getKey()));
            final List<String> stays = new ArrayList<>();
            for (String version : versions) {
                final List<Packaged> ofVersion =
                        name.getValue().stream().filter(f -> f.version().equals(version)).toList();
                if (!newest.contains(version)
                        && ofVersion.stream().allMatch(f -> f.created().isBefore(cut))) {
                    deleted.addAll(ofVersion);
                } else {
                    stays.add(version);
                }
            }
            kept.put(name.getKey(), List.copyOf(stays));
        }
        deleted.sort(
                Comparator.comparing(Packaged::repository)
                        .thenComparing(Packaged::name)
                        .thenComparing(Packaged::version, DebianVersion.ORDER)
                        .thenComparing(Packaged::path));
        return new Selection(List.copyOf(deleted), kept);
    }

    /**
     * Probes, reads, selects, and deletes when asked to.
     *
     * @param delete whether to delete; otherwise only the list is said
     * @param now the run's time
     * @param probeName a name for the probe file no other run uses, such as the run's id
     * @return 0 when done, {@link Stop#REFUSED} when a deletion or an index failed, {@link
     *     Stop#UNANSWERED} when something could not be read
     */
    int run(boolean delete, Instant now, String probeName) {
        try {
            probe(probeName);
            final List<Packaged> files = new ArrayList<>(debianSnapshots());
            files.addAll(rpmSnapshots());
            final Selection selection = select(files, now);
            say(selection, files.size());
            if (!delete) {
                out.println("nothing was deleted - run again with --delete");
                return 0;
            }
            return deleteAndFollow(selection.deleted());
        } catch (Stop stop) {
            err.println("prune-snapshots: " + stop.getMessage());
            return stop.code();
        } catch (IOException ex) {
            err.println("prune-snapshots: " + Web.reason(ex));
            return Stop.UNANSWERED;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            err.println("prune-snapshots: interrupted");
            return Stop.UNANSWERED;
        }
    }

    /** Puts and deletes one file in each repository, so a token that cannot delete fails first. */
    private void probe(String probeName) throws Stop {
        for (String repository : List.of(DEB, RPM)) {
            final String path = PROBE_FOLDER + "/" + probeName + ".txt";
            try {
                artifactory.put(
                        repository,
                        path,
                        "porelo-buildtools probe\n".getBytes(StandardCharsets.UTF_8));
            } catch (Artifactory.Unanswered ex) {
                throw Stop.unanswered(
                        "Artifactory did not answer the probe "
                                + repository
                                + "/"
                                + path
                                + ": "
                                + Web.reason(ex),
                        ex);
            } catch (IOException ex) {
                throw Stop.refused(
                        "the token cannot put the probe "
                                + repository
                                + "/"
                                + path
                                + ": "
                                + Web.reason(ex));
            }
            try {
                artifactory.delete(repository, path);
                final boolean gone =
                        artifactory
                                .children(repository, PROBE_FOLDER)
                                .map(
                                        list ->
                                                list.stream()
                                                        .noneMatch(
                                                                c ->
                                                                        c.name()
                                                                                .equals(
                                                                                        probeName
                                                                                                + ".txt")))
                                .orElse(true);
                if (!gone) {
                    throw new IOException("still listed after its delete was answered");
                }
            } catch (Artifactory.Unanswered ex) {
                throw Stop.unanswered(
                        "Artifactory did not answer; the probe "
                                + repository
                                + "/"
                                + path
                                + " may be left: "
                                + Web.reason(ex),
                        ex);
            } catch (IOException ex) {
                throw Stop.refused(
                        "the token cannot delete; the probe "
                                + repository
                                + "/"
                                + path
                                + " is left: "
                                + Web.reason(ex));
            }
            out.println("probe  " + repository + "/" + path + " put and deleted");
        }
    }

    /**
     * The Debian files whose only channel is {@code snapshots} and that the releases index does not
     * list.
     */
    private List<Packaged> debianSnapshots() throws IOException, Stop {
        // A release lies in the same folders as the snapshots: its index is a second guard beside
        // its property, and
        // a run that cannot read it deletes nothing.
        final Set<String> released =
                filenames(
                        artifactory
                                .debianPackages(DEB, RELEASES)
                                .orElseThrow(
                                        () ->
                                                Stop.unanswered(
                                                        DEB + " has no " + RELEASES + " index")));
        final List<Packaged> result = new ArrayList<>();
        final List<String> releases = new ArrayList<>();
        final List<Artifactory.Child> names =
                artifactory
                        .children(DEB, POOL)
                        .orElseThrow(() -> Stop.unanswered(DEB + "/" + POOL + " is not there"));
        for (Artifactory.Child name : names) {
            if (!name.folder()) {
                continue;
            }
            final String folder = POOL + "/" + name.name();
            final List<Artifactory.Child> files =
                    artifactory
                            .children(DEB, folder)
                            .orElseThrow(
                                    () -> Stop.unanswered(DEB + "/" + folder + " is not there"));
            for (Artifactory.Child child : files) {
                if (child.folder() || !child.name().endsWith(".deb")) {
                    continue;
                }
                final String path = folder + "/" + child.name();
                final Map<String, List<String>> properties = artifactory.properties(DEB, path);
                final List<String> channels =
                        properties.getOrDefault("deb.distribution", List.of());
                if (channels.isEmpty()) {
                    throw Stop.unanswered(DEB + "/" + path + " has no deb.distribution");
                }
                if (!properties.getOrDefault("deb.architecture", List.of()).equals(List.of("amd64"))
                        && !properties
                                .getOrDefault("deb.architecture", List.of())
                                .equals(List.of("all"))) {
                    // Only the amd64 indexes are read, which list amd64 and all; a release of
                    // another would go
                    // unrecognised.
                    out.println("refuse " + DEB + "/" + path + ": neither amd64 nor all");
                    continue;
                }
                if (channels.equals(List.of(RELEASES))) {
                    releases.add("pool/main/p/" + name.name() + "/" + child.name());
                }
                if (released.contains("pool/main/p/" + name.name() + "/" + child.name())) {
                    if (channels.contains(SNAPSHOTS)) {
                        out.println("refuse " + DEB + "/" + path + ": the releases index lists it");
                    }
                    continue;
                }
                if (!channels.equals(List.of(SNAPSHOTS))) {
                    if (!channels.equals(List.of(RELEASES))) {
                        out.println(
                                "refuse " + DEB + "/" + path + ": its channels are " + channels);
                    }
                    continue;
                }
                final String packageName = single(properties, "deb.name", path);
                final String version = single(properties, "deb.version", path);
                result.add(
                        new Packaged(
                                DEB, path, packageName, version, artifactory.created(DEB, path)));
            }
        }
        // An index that does not list every release its pool holds cannot guard the others.
        for (String release : releases) {
            if (!released.contains(release)) {
                throw Stop.unanswered(
                        "the "
                                + RELEASES
                                + " index does not list "
                                + DEB
                                + "/"
                                + release
                                + ", a release");
            }
        }
        return result;
    }

    /** The RPM files under {@code snapshots/}. */
    private List<Packaged> rpmSnapshots() throws IOException, Stop {
        final List<Packaged> result = new ArrayList<>();
        final List<Artifactory.Child> children =
                artifactory
                        .children(RPM, SNAPSHOTS)
                        .orElseThrow(
                                () -> Stop.unanswered(RPM + "/" + SNAPSHOTS + " is not there"));
        for (Artifactory.Child child : children) {
            if (child.folder() || !child.name().endsWith(".rpm")) {
                continue;
            }
            final Matcher file = RPM_FILE.matcher(child.name());
            if (!file.matches()) {
                throw Stop.unanswered(
                        RPM
                                + "/"
                                + SNAPSHOTS
                                + "/"
                                + child.name()
                                + " is not named name-version-release.arch.rpm");
            }
            final String path = SNAPSHOTS + "/" + child.name();
            result.add(
                    new Packaged(
                            RPM,
                            path,
                            file.group(1),
                            file.group(2) + "-" + file.group(3),
                            artifactory.created(RPM, path)));
        }
        return result;
    }

    private static String single(Map<String, List<String>> properties, String key, String path)
            throws Stop {
        final List<String> values = properties.getOrDefault(key, List.of());
        if (values.size() != 1 || values.getFirst().isBlank()) {
            throw Stop.unanswered(
                    DEB + "/" + path + " has " + values + " as " + key + ", not one value");
        }
        return values.getFirst();
    }

    private static Set<String> filenames(String packages) {
        final Set<String> result = new HashSet<>();
        final Matcher matcher = FILENAME.matcher(packages);
        while (matcher.find()) {
            result.add(matcher.group(1));
        }
        return result;
    }

    private void say(Selection selection, int read) {
        for (Packaged file : selection.deleted()) {
            out.println("delete " + file.repository() + "/" + file.path() + "  " + file.created());
        }
        for (Map.Entry<String, List<String>> name : selection.kept().entrySet()) {
            out.println("keep   " + name.getKey() + "  " + String.join(" ", name.getValue()));
        }
        out.println(
                selection.deleted().size() + " of " + read + " snapshot package file(s) to delete");
    }

    private int deleteAndFollow(List<Packaged> deleted) throws IOException, InterruptedException {
        final List<Packaged> gone = new ArrayList<>();
        int failed = 0;
        for (Packaged file : deleted) {
            try {
                artifactory.delete(file.repository(), file.path());
                gone.add(file);
                out.println("gone   " + file.repository() + "/" + file.path());
            } catch (IOException ex) {
                failed++;
                err.println(
                        "could not delete "
                                + file.repository()
                                + "/"
                                + file.path()
                                + ": "
                                + Web.reason(ex));
            }
        }
        final Set<String> stillListed = new LinkedHashSet<>();
        stillListed.addAll(follow(DEB, gone, true));
        stillListed.addAll(follow(RPM, gone, false));
        for (String file : stillListed) {
            err.println("still listed by its index after it was deleted: " + file);
        }
        out.println("deleted " + gone.size() + ", could not delete " + failed);
        return failed == 0 && stillListed.isEmpty() ? 0 : Stop.REFUSED;
    }

    /**
     * Waits for an index to drop the deleted files, asking for a recalculation once; returns what
     * it still lists.
     */
    private Set<String> follow(String repository, List<Packaged> gone, boolean debian)
            throws IOException, InterruptedException {
        final List<Packaged> mine =
                gone.stream().filter(f -> f.repository().equals(repository)).toList();
        if (mine.isEmpty()) {
            return Set.of();
        }
        Set<String> listed = await(repository, mine, debian);
        if (listed.isEmpty()) {
            out.println("index  " + repository + " followed by itself");
            return listed;
        }
        out.println(
                "index  "
                        + repository
                        + " still lists "
                        + listed.size()
                        + " file(s); asking it to recalculate");
        if (debian) {
            artifactory.recalculateDebian(repository);
        } else {
            artifactory.recalculateRpm(repository, SNAPSHOTS);
        }
        listed = await(repository, mine, debian);
        if (listed.isEmpty()) {
            out.println("index  " + repository + " followed once asked");
        }
        return listed;
    }

    private Set<String> await(String repository, List<Packaged> mine, boolean debian)
            throws IOException, InterruptedException {
        Duration waited = Duration.ZERO;
        while (true) {
            final Set<String> listed = listed(repository, mine, debian);
            if (listed.isEmpty() || waited.compareTo(INDEX_WAIT) >= 0) {
                return listed;
            }
            sleeper.sleep(INDEX_POLL);
            waited = waited.plus(INDEX_POLL);
        }
    }

    private Set<String> listed(String repository, List<Packaged> mine, boolean debian)
            throws IOException {
        final Set<String> result = new LinkedHashSet<>();
        if (debian) {
            final Optional<String> index = artifactory.debianPackages(repository, SNAPSHOTS);
            final Set<String> names = index.map(PruneSnapshots::filenames).orElse(Set.of());
            for (Packaged file : mine) {
                // At least three versions of each name stay, so an index that is gone has not
                // followed.
                if (index.isEmpty() || names.contains(file.path())) {
                    result.add(repository + "/" + file.path());
                }
            }
        } else {
            final Optional<String> primary = artifactory.rpmPrimary(repository, SNAPSHOTS);
            for (Packaged file : mine) {
                if (primary.isEmpty() || primary.get().contains("href=\"" + file.file() + "\"")) {
                    result.add(repository + "/" + file.path());
                }
            }
        }
        return result;
    }
}
