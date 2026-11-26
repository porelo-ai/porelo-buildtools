package org.fuin.porelo.machines;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The musl installer as porelo-machines runs it, in bash with a stand-in curl: its downloads follow
 * the rule for pinned downloads, so a source that does not serve them gives way to the mirror.
 */
@Tag("integration")
class MuslInstallerTest {

    private static final String MUSL = "https://musl.cc/x86_64-linux-musl-native.tgz";

    private static final String MIRROR = "https://mirror.example/files";

    @TempDir Path dir;

    // Prevents a build box or snapshot that cannot be set up while musl.cc's certificate is
    // expired, as it was once, although the mirror holds the toolchain by its digest.
    @Test
    void takesTheToolchainFromTheMirrorWhenMuslCcRefusesItsCertificate()
            throws IOException, InterruptedException {
        final Path toolchain = toolchain();
        final String sha256 = sha256(Files.readAllBytes(toolchain));
        final String mirrored = MIRROR + "/pinned/" + sha256 + "/x86_64-linux-musl-native.tgz";

        final Ran ran =
                install(
                        pinnedTo(sha256),
                        "case $last in "
                                + MIRROR
                                + "/*) cp "
                                + toolchain
                                + " \"$out\"; printf 200 ;; *) printf 000; exit 60 ;; esac");

        assertThat(ran.exit()).as(ran.out()).isZero();
        assertThat(Files.readAllLines(dir.resolve("calls")))
                .as("musl.cc once, since a refused certificate is not asked again, then the mirror")
                .containsExactly(MUSL, mirrored);
        assertThat(ran.out())
                .as("the source that served it is named")
                .contains("taken from " + mirrored + ", checked against " + sha256);
        assertThat(dir.resolve("opt/x86_64-linux-musl-native/bin/x86_64-linux-musl-gcc"))
                .as("the mirror's toolchain, unpacked")
                .isExecutable();
    }

    // Prevents a mirror that serves something else from being unpacked as the toolchain.
    @Test
    void refusesAToolchainFromTheMirrorThatDoesNotMatchItsPin()
            throws IOException, InterruptedException {
        final Path toolchain = toolchain();

        final Ran ran =
                install(
                        pinnedTo(sha256(Files.readAllBytes(toolchain))),
                        "case $last in "
                                + MIRROR
                                + "/*) printf planted > \"$out\"; printf 200 ;;"
                                + " *) printf 000; exit 60 ;; esac");

        assertThat(ran.exit()).as(ran.out()).isNotZero();
        assertThat(ran.out()).contains("has the digest of something else");
        assertThat(dir.resolve("opt/x86_64-linux-musl-native")).doesNotExist();
    }

    // Prevents an installer run alone that fetches without the rule, or not at all, and says
    // nothing of why.
    @Test
    void runAloneNeedsTheRuleBesideIt() throws IOException, InterruptedException {
        final Path alone = dir.resolve("install-musl.sh");
        try (var in = Snapshots.class.getResourceAsStream(Snapshots.MUSL_INSTALLER)) {
            assertThat(in).as("the installer on the classpath").isNotNull();
            Files.write(alone, in.readAllBytes());
        }

        final Process bash =
                new ProcessBuilder("bash", alone.toString()).redirectErrorStream(true).start();
        final String out = new String(bash.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertThat(bash.waitFor(60, TimeUnit.SECONDS)).isTrue();

        assertThat(bash.exitValue()).isEqualTo(1);
        assertThat(out).contains("needs pinned.sh beside it");
    }

    /** What a run of bash printed, standard error included, and how it ended. */
    private record Ran(String out, int exit) {}

    /** The installer as porelo-machines runs it, with its musl pin moved to the given digest. */
    private static String pinnedTo(String sha256) {
        final String installer = Snapshots.muslInstaller();
        assertThat(installer).containsPattern("(?m)^MUSL_SHA256=[0-9a-f]{64}$");
        return installer.replaceFirst("(?m)^MUSL_SHA256=[0-9a-f]{64}$", "MUSL_SHA256=" + sha256);
    }

    /** A stand-in toolchain whose zlib is already built, so nothing is compiled. */
    private Path toolchain() throws IOException, InterruptedException {
        final Path tree = dir.resolve("tree/x86_64-linux-musl-native");
        Files.createDirectories(tree.resolve("bin"));
        Files.createDirectories(tree.resolve("lib"));
        final Path gcc = tree.resolve("bin/x86_64-linux-musl-gcc");
        Files.writeString(gcc, "#!/bin/sh\necho 'x86_64-linux-musl-gcc (stand-in) 11.2.1'\n");
        assertThat(gcc.toFile().setExecutable(true)).isTrue();
        Files.writeString(tree.resolve("lib/libz.a"), "stand-in");
        final Path tgz = dir.resolve("toolchain.tgz");
        final Process tar =
                new ProcessBuilder(
                                "tar",
                                "-czf",
                                tgz.toString(),
                                "-C",
                                dir.resolve("tree").toString(),
                                "x86_64-linux-musl-native")
                        .redirectErrorStream(true)
                        .start();
        assertThat(tar.waitFor(60, TimeUnit.SECONDS)).isTrue();
        assertThat(tar.exitValue()).as("tar").isZero();
        return tgz;
    }

    /**
     * Runs the installer on bash's standard input, as porelo-machines does, with a stand-in curl
     * that writes each address to {@code calls} and a sleep that only says it slept.
     */
    private Ran install(String installer, String curl) throws IOException, InterruptedException {
        final Path bin = Files.createDirectories(dir.resolve("bin"));
        Files.writeString(
                bin.resolve("curl"),
                "#!/bin/sh\nfor a; do last=$a; done\necho \"$last\" >> "
                        + dir.resolve("calls")
                        + "\nwhile [ $# -gt 0 ]; do [ \"$1\" = -o ] && out=$2; shift; done\n"
                        + curl
                        + "\n");
        Files.writeString(bin.resolve("sleep"), "#!/bin/sh\necho \"slept $1\" >&2\n");
        for (final String tool : List.of("curl", "sleep")) {
            assertThat(bin.resolve(tool).toFile().setExecutable(true)).isTrue();
        }
        final ProcessBuilder builder = new ProcessBuilder("bash", "-s").redirectErrorStream(true);
        builder.environment().put("PATH", bin + ":" + System.getenv("PATH"));
        builder.environment().remove("GITHUB_SERVER_URL");
        builder.environment().remove("PORELO_PINNED_GITHUB_TOKEN");
        builder.environment()
                .putAll(
                        Map.of(
                                "PORELO_PINNED_MIRROR",
                                MIRROR,
                                "MUSL_PREFIX",
                                dir.resolve("opt").toString()));
        final Process bash = builder.start();
        try (var in = bash.getOutputStream()) {
            in.write(installer.getBytes(StandardCharsets.UTF_8));
        }
        final String out = new String(bash.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertThat(bash.waitFor(60, TimeUnit.SECONDS)).isTrue();
        return new Ran(out, bash.exitValue());
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
