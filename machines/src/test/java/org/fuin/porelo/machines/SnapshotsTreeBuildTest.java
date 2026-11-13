package org.fuin.porelo.machines;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SnapshotsTreeBuildTest {

    @TempDir Path dir;

    /** The one native image the proof builds, after everything on the JVM. */
    private static final String ONE_IMAGE =
            "mvnw -B -s settings.xml -Pnative -DskipTests -Dspotless.check.skip=true package"
                    + " -pl agents/stub -am";

    // Prevents a proof that builds every native image: seven took 11 minutes each on the
    // snapshot's machine, and the step's 60 minutes ended both snapshots of 7b37e81.
    @Test
    void aTreeWithItsOwnBuildScriptIsBuiltByItOnTheJvmThenOneImage() throws Exception {
        final Path tree = tree();
        Files.createDirectories(tree.resolve("ci"));
        Files.writeString(
                tree.resolve("ci/leg-build.sh"), "echo the own build of the tree $MAVEN_ARGS\n");

        assertThat(sh(tree, Snapshots.TREE_BUILD).lines())
                .containsExactly("the own build of the tree -P!native", ONE_IMAGE);
    }

    @Test
    void aTreeFromBeforeTheScriptGetsTheModuleListItHadWithTheAppWhereItKeepsIt() throws Exception {
        // The grouping moved app under apps/; a list naming app stopped every leg at "Could not
        // find the selected project in the reactor: app", on rented machines only.
        final Path grouped = tree();
        Files.createDirectories(grouped.resolve("apps/app"));
        Files.createDirectories(grouped.resolve("builds/stub"));
        assertThat(sh(grouped, Snapshots.TREE_BUILD).lines())
                .containsExactly(
                        "mvnw -B -s settings.xml -DskipTests package"
                                + " -pl apps/app,daemon,hooks,agents/stub,builds/stub -am",
                        ONE_IMAGE);

        final Path older = Files.createDirectories(dir.resolve("older"));
        mvnw(older);
        assertThat(sh(older, Snapshots.TREE_BUILD).lines())
                .containsExactly(
                        "mvnw -B -s settings.xml -DskipTests package"
                                + " -pl app,daemon,hooks,agents/stub -am",
                        ONE_IMAGE);
    }

    @Test
    void aFailedJvmBuildBuildsNoImage() throws Exception {
        final Path tree = tree();
        Files.createDirectories(tree.resolve("ci"));
        Files.writeString(tree.resolve("ci/leg-build.sh"), "echo broken; exit 3\n");

        final Process process =
                new ProcessBuilder("sh", "-c", Snapshots.TREE_BUILD)
                        .directory(tree.toFile())
                        .redirectErrorStream(true)
                        .start();
        final String out =
                new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).strip();
        assertThat(process.waitFor()).as(out).isEqualTo(3);
        assertThat(out).isEqualTo("broken");
    }

    // Prevents the native image running out of memory on the small machine a snapshot needs.
    @Test
    void theImageAsksForMoreHeapThanTheSmallMachineWouldGiveIt() throws Exception {
        final Path tree = Files.createDirectories(dir.resolve("heap"));
        final Path mvnw = tree.resolve("mvnw");
        Files.writeString(mvnw, "#!/bin/sh\necho \"$NATIVE_IMAGE_OPTIONS\"\n");
        Files.setPosixFilePermissions(mvnw, PosixFilePermissions.fromString("rwx------"));

        assertThat(sh(tree, Snapshots.TREE_BUILD).lines()).containsExactly("-J-Xmx5g", "-J-Xmx5g");
    }

    // Prevents the snapshot carrying a swap file, or a machine without it running out of memory.
    @Test
    void theSwapIsMadeOnlyForTheBuildAndGoneAfterIt() {
        assertThat(Snapshots.SWAP_ON)
                .contains("/swap.prepare", "dd if=/dev/zero", "chmod 600", "mkswap", "swapon");
        assertThat(Snapshots.SWAP_ON.indexOf("chmod 600"))
                .isLessThan(Snapshots.SWAP_ON.indexOf("mkswap"));
        assertThat(Snapshots.SWAP_OFF).isEqualTo("swapoff /swap.prepare && rm -f /swap.prepare");
    }

    @Test
    void theBuildCarriesNoSingleQuoteSinceItIsPassedInsideOne() {
        assertThat(Snapshots.TREE_BUILD).doesNotContain("'");
    }

    private Path tree() throws IOException {
        final Path tree = Files.createDirectories(dir.resolve("tree"));
        mvnw(tree);
        return tree;
    }

    private static void mvnw(final Path tree) throws IOException {
        final Path mvnw = tree.resolve("mvnw");
        Files.writeString(mvnw, "#!/bin/sh\necho mvnw \"$@\"\n");
        Files.setPosixFilePermissions(mvnw, PosixFilePermissions.fromString("rwx------"));
    }

    private static String sh(final Path tree, final String command)
            throws IOException, InterruptedException {
        final Process process =
                new ProcessBuilder("sh", "-c", command)
                        .directory(tree.toFile())
                        .redirectErrorStream(true)
                        .start();
        final String out =
                new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).strip();
        assertThat(process.waitFor()).as(out).isZero();
        return out;
    }
}
