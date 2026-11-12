package org.fuin.porelo.release;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CheckPackageVersionTest {

    @Test
    void aSnapshotSortsBelowItsReleaseAndAboveTheRunBefore() {
        assertThat(CheckPackageVersion.packageVersion("0.4.1-SNAPSHOT", "7"))
                .isEqualTo("0.4.1~snapshot.7");
        assertThat(CheckPackageVersion.packageVersion("0.4.1", "7")).isEqualTo("0.4.1");
    }

    @Test
    void acceptsPackagesThatCarryTheVersionAndTheRelease() {
        assertThat(
                        CheckPackageVersion.problems(
                                "porelo-build-github",
                                "0.4.2~snapshot.7",
                                "porelo-build-github",
                                "0.4.2~snapshot.7",
                                "porelo-build-github 0.4.2~snapshot.7 1"))
                .isEmpty();
    }

    @Test
    void refusesADebWhoseVersionCameFromAnotherProperty() {
        // A plugin taking its version from a property that means something else: the raw Maven
        // version sorts above the release, and apt then refuses the upgrade to it.
        assertThat(
                        CheckPackageVersion.problems(
                                "porelo-build-github",
                                "0.4.2~snapshot.7",
                                "porelo-build-github",
                                "0.4.2-SNAPSHOT",
                                "porelo-build-github 0.4.2~snapshot.7 1"))
                .containsExactly("the deb is version 0.4.2-SNAPSHOT, not 0.4.2~snapshot.7");
    }

    @Test
    void refusesAnRpmWithATimestampRelease() {
        // What the rpm plugin does to a snapshot unless told not to: the rpm and the deb then
        // disagree.
        assertThat(
                        CheckPackageVersion.problems(
                                "porelo-build-github",
                                "0.4.2~snapshot.7",
                                "porelo-build-github",
                                "0.4.2~snapshot.7",
                                "porelo-build-github 0.4.2~snapshot.7 0.20261009120000"))
                .hasSize(1)
                .first()
                .asString()
                .startsWith("the rpm is");
    }

    private static final String NAME = "porelo-build-github";

    /** Packages as their fields say, by file name, and what the command printed. */
    private static final class Run implements CheckPackageVersion.PackageMetadata {
        final Map<String, String> fields = new HashMap<>();
        final List<String> asked = new ArrayList<>();
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        final ByteArrayOutputStream err = new ByteArrayOutputStream();

        @Override
        public String debField(Path deb, String field) throws IOException {
            return field(deb.getFileName() + " " + field);
        }

        @Override
        public String rpmFields(Path rpm) throws IOException {
            return field(rpm.getFileName().toString());
        }

        private String field(String key) throws IOException {
            asked.add(key);
            final String value = fields.get(key);
            if (value == null) {
                throw new IOException("cannot read " + key);
            }
            // As the tools print them, with a line end the check strips.
            return value + "\n";
        }

        Run deb(String file, String name, String version) {
            fields.put(file + " Package", name);
            fields.put(file + " Version", version);
            return this;
        }

        Run rpm(String file, String fields) {
            this.fields.put(file, fields);
            return this;
        }

        int main(String... arguments) {
            return Main.checkPackageVersion(
                    List.of(arguments), new PrintStream(out), new PrintStream(err), this);
        }
    }

    private static Path file(Path directory, String name, long modified) throws IOException {
        final Path file = Files.createFile(directory.resolve(name));
        Files.setLastModifiedTime(file, FileTime.fromMillis(modified));
        return file;
    }

    // Prevents the command from passing packages whose fields it never read.
    @Test
    void theCommandPassesPackagesThatCarryTheSnapshotVersion(@TempDir Path dist)
            throws IOException {
        file(dist, NAME + "_0.4.2~snapshot.7_amd64.deb", 1000);
        file(dist, NAME + "-0.4.2~snapshot.7-1.x86_64.rpm", 1000);
        final Run run =
                new Run()
                        .deb(NAME + "_0.4.2~snapshot.7_amd64.deb", NAME, "0.4.2~snapshot.7")
                        .rpm(NAME + "-0.4.2~snapshot.7-1.x86_64.rpm", NAME + " 0.4.2~snapshot.7 1");

        final int code = run.main(NAME, "0.4.2-SNAPSHOT", "7", dist.toString());

        assertThat(code).as(run.err.toString()).isZero();
        assertThat(run.asked).hasSize(3);
        assertThat(run.out.toString()).contains("carry 0.4.2~snapshot.7");
    }

    // Prevents an older package left in the directory from being the one that is read.
    @Test
    void theCommandReadsTheNewestPackages(@TempDir Path dist) throws IOException {
        file(dist, NAME + "_0.4.2~snapshot.6_amd64.deb", 1000);
        file(dist, NAME + "_0.4.2~snapshot.7_amd64.deb", 2000);
        file(dist, NAME + "-0.4.2~snapshot.7-1.x86_64.rpm", 2000);
        file(dist, NAME + "-0.4.2~snapshot.6-1.x86_64.rpm", 1000);
        final Run run =
                new Run()
                        .deb(NAME + "_0.4.2~snapshot.6_amd64.deb", NAME, "0.4.2~snapshot.6")
                        .deb(NAME + "_0.4.2~snapshot.7_amd64.deb", NAME, "0.4.2~snapshot.7")
                        .rpm(NAME + "-0.4.2~snapshot.6-1.x86_64.rpm", NAME + " 0.4.2~snapshot.6 1")
                        .rpm(NAME + "-0.4.2~snapshot.7-1.x86_64.rpm", NAME + " 0.4.2~snapshot.7 1");

        final int code = run.main(NAME, "0.4.2-SNAPSHOT", "7", dist.toString());

        assertThat(code).as(run.err.toString()).isZero();
        assertThat(run.asked).allMatch(key -> key.contains("snapshot.7"));
    }

    // Prevents a deb that carries the raw Maven version from passing the command.
    @Test
    void theCommandRefusesADebWithTheWrongVersion(@TempDir Path dist) throws IOException {
        file(dist, NAME + "_0.4.2_amd64.deb", 1000);
        file(dist, NAME + "-0.4.2-1.x86_64.rpm", 1000);
        final Run run =
                new Run()
                        .deb(NAME + "_0.4.2_amd64.deb", NAME, "0.4.2-SNAPSHOT")
                        .rpm(NAME + "-0.4.2-1.x86_64.rpm", NAME + " 0.4.2~snapshot.7 1");

        final int code = run.main(NAME, "0.4.2-SNAPSHOT", "7", dist.toString());

        assertThat(code).isEqualTo(Stop.REFUSED);
        assertThat(run.err.toString())
                .contains("FAIL  the deb is version 0.4.2-SNAPSHOT, not 0.4.2~snapshot.7");
        assertThat(run.out.toString()).isEmpty();
    }

    // Prevents a package that cannot be read from being taken as a right one.
    @Test
    void theCommandStopsUnansweredWhenAPackageCannotBeRead(@TempDir Path dist) throws IOException {
        file(dist, NAME + "_0.4.2_amd64.deb", 1000);
        file(dist, NAME + "-0.4.2-1.x86_64.rpm", 1000);
        final Run run = new Run().deb(NAME + "_0.4.2_amd64.deb", NAME, "0.4.2");

        final int code = run.main(NAME, "0.4.2", "7", dist.toString());

        assertThat(code).isEqualTo(Stop.UNANSWERED);
        assertThat(run.err.toString()).contains("could not read the packages: cannot read");
    }

    // Prevents a build without its rpm from passing on the deb alone.
    @Test
    void theCommandStopsUnansweredWhenAPackageIsMissing(@TempDir Path dist) throws IOException {
        file(dist, NAME + "_0.4.2_amd64.deb", 1000);
        final Run run = new Run().deb(NAME + "_0.4.2_amd64.deb", NAME, "0.4.2");

        final int code = run.main(NAME, "0.4.2", "7", dist.toString());

        assertThat(code).isEqualTo(Stop.UNANSWERED);
        assertThat(run.asked).isEmpty();
        assertThat(run.err.toString()).contains("built by the dist profile?");
    }

    // Prevents a short command line from reading anything.
    @Test
    void theCommandNamesItsUsageWhenAnArgumentIsMissing() {
        final Run run = new Run();

        assertThat(run.main(NAME, "0.4.2", "7")).isNotZero();
        assertThat(run.asked).isEmpty();
        assertThat(run.err.toString()).contains("check-package-version NAME PROJECT-VERSION");
    }
}
