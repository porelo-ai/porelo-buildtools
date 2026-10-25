package org.fuin.porelo.release;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Guards against provider references escaping the implementation or bypassing the check. */
class CheckProvidersTest {

    @TempDir Path root;

    private final ByteArrayOutputStream out = new ByteArrayOutputStream();

    private final ByteArrayOutputStream err = new ByteArrayOutputStream();

    @BeforeEach
    void createsAnIsolatedGitRepository() throws Exception {
        git("init", "-q");
    }

    @Test
    void namesEveryProviderReferenceWithItsPathAndLine() throws Exception {
        write(".github/workflows/build.yml", "env:\n  TOKEN: HETZNER_API\n");
        write("src/main/java/Spec.java", "class Spec {\n  String type = \"cpx12\";\n}\n");
        write(
                "pom.xml",
                "<project>\n<profiles>\n<profile><id>hetzner</id></profile>\n</profiles>\n</project>\n");
        git("add", ".");

        assertThat(check())
                .as("provider references must refuse the repository")
                .isEqualTo(Stop.REFUSED);
        assertThat(stderr())
                .as("each finding identifies exactly where the reference occurs")
                .contains(
                        ".github/workflows/build.yml:2: HETZNER_API",
                        "src/main/java/Spec.java:2: cpx12",
                        "pom.xml:3: hetzner");
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "hetzner",
                "HeTzNeR",
                "hcloud",
                "HCLOUD",
                "HETZNER_API",
                "hetzner_ssh",
                "Hetzner_Key",
                "cpx12",
                "CPX99",
                "ccx13",
                "CCX33",
                "cax11",
                "CAX99",
                "cx20",
                "CX59" // not-a-citation: uppercase server types must match too.
            })
    void refusesEachProviderNameAndServerTypeRegardlessOfCase(String reference) throws Exception {
        write("input.txt", "neutral\n" + reference + " " + reference + "\n");
        git("add", ".");

        assertThat(check())
                .as("a forbidden reference cannot bypass the check: %s", reference)
                .isEqualTo(Stop.REFUSED);
        assertThat(
                        stderr().lines()
                                .filter(line -> line.equals("input.txt:2: " + reference))
                                .toList())
                .as("every occurrence is reported, including two on the same line")
                .hasSize(2);
    }

    @Test
    void acceptsCpuFlagsAnchorsAndWordsThatOnlyContainTheName() throws Exception {
        write("cpu.txt", "cx16 cx19 cx60 acpx12 cpx123 cpx12suffix hetzners myhcloud\n");
        write(
                "README.md",
                "[one](#cx01) [two](#cx22) [three](#cpx12) [four](#CCX13) [five](#cax11)\n");
        git("add", ".");

        assertThat(check()).as("CPU flags and link anchors must remain usable").isZero();
        assertThat(stdout())
                .as("success counts the text files actually read")
                .contains("2 text file(s)");
        assertThat(stderr()).as("accepted text has no findings").isEmpty();
    }

    @Test
    void skipsOnlyTheFixedImplementationPathsAndHistoricalDocuments() throws Exception {
        for (final String file :
                List.of(
                        "machines/src/main/java/org/fuin/porelo/machines/Hetzner.java",
                        "machines/src/main/java/org/fuin/porelo/machines/CentralSetup.java",
                        "machines/src/main/java/org/fuin/porelo/machines/Api.java",
                        "machines/src/main/java/org/fuin/porelo/machines/Fingerprint.java",
                        "machines/src/test/java/org/fuin/porelo/machines/HetznerTest.java",
                        "machines/src/test/java/org/fuin/porelo/machines/StubApi.java",
                        "machines/src/test/java/org/fuin/porelo/machines/FingerprintTest.java",
                        "machines/src/test/java/org/fuin/porelo/machines/HostKeyPairTest.java",
                        "release/src/main/java/org/fuin/porelo/release/CheckProviders.java",
                        "release/src/test/java/org/fuin/porelo/release/CheckProvidersTest.java",
                        "issues/Open.md",
                        "CHANGELOG.md",
                        "changelog/unreleased/change.yml")) {
            write(file, "hetzner cpx12\n");
        }
        write("src/Api.java", "hetzner\n");
        write("doc/CHANGELOG.md", "hcloud\n");
        write("issues.txt", "cpx12\n");
        write("changelog.txt", "ccx13\n");
        git("add", ".");

        assertThat(check())
                .as("exemptions do not cover namesakes elsewhere")
                .isEqualTo(Stop.REFUSED);
        assertThat(stderr().lines().filter(line -> line.matches(".*:[0-9]+: .*")).toList())
                .as("only exact paths and the two root directories are exempt")
                .containsExactlyInAnyOrder(
                        "src/Api.java:1: hetzner",
                        "doc/CHANGELOG.md:1: hcloud",
                        "issues.txt:1: cpx12",
                        "changelog.txt:1: ccx13");
    }

    @Test
    void ignoresUntrackedFilesButReadsTrackedContentAsItIsNow() throws Exception {
        write("tracked.txt", "neutral\n");
        git("add", ".");
        write("untracked.txt", "hetzner\n");

        assertThat(check()).as("files outside git's index are not repository content").isZero();

        write("tracked.txt", "hcloud\n");
        assertThat(check())
                .as("working tree edits to tracked files must be checked")
                .isEqualTo(Stop.REFUSED);
        assertThat(stderr())
                .as("the current tracked content is checked")
                .contains("tracked.txt:1: hcloud")
                .doesNotContain("untracked.txt");
    }

    @Test
    void skipsBinaryFilesWithNulBytesOrInvalidUtf8() throws Exception {
        write("nul.bin", "\0hetzner\n");
        write("invalid.bin", "hetzner\n");
        Files.write(
                root.resolve("invalid.bin"),
                new byte[] {(byte) 0xff},
                java.nio.file.StandardOpenOption.APPEND);
        write("text.txt", "neutral\n");
        git("add", ".");

        assertThat(check()).as("binary content is not scanned as text").isZero();
        assertThat(stdout()).as("only the text file was read").contains("1 text file(s)");
        assertThat(stderr()).as("binary matches are not findings").isEmpty();
    }

    @Test
    void doesNotHonorAnExemptionFile() throws Exception {
        write("providers-exempt.txt", "input.txt\n");
        write("input.txt", "hetzner\n");
        git("add", ".");

        assertThat(check())
                .as("repository content cannot add its own exemptions")
                .isEqualTo(Stop.REFUSED);
        assertThat(stderr())
                .as("an attempted exemption cannot hide the finding")
                .contains("input.txt:1: hetzner");
    }

    @Test
    void cannotPassWhenGitCannotListTheRepository(@TempDir Path outside) throws IOException {
        Files.writeString(outside.resolve(".git"), "gitdir: missing\n", StandardCharsets.UTF_8);

        assertThat(check(outside))
                .as("an unavailable index cannot mean a clean repository")
                .isEqualTo(Stop.UNANSWERED);
        assertThat(stderr())
                .as("the failed operation and its answer are reported")
                .contains("git", "ls-files", "128");
    }

    @Test
    void cannotPassWhenTheRepositoryCannotBeRead() {
        assertThat(check(root.resolve("missing")))
                .as("an unreadable root cannot silently bypass the check")
                .isEqualTo(Stop.UNANSWERED);
        assertThat(stderr())
                .as("the unreadable path is named")
                .contains(root.resolve("missing").toString());
    }

    @Test
    void theCommandChecksTheRootItIsGiven() throws Exception {
        write("input.txt", "HETZNER_API\n");
        git("add", ".");

        assertThat(
                        Main.run(
                                new String[] {"check-providers", root.toString()},
                                stream(out),
                                stream(err),
                                FakeWeb.untouchable(),
                                java.util.Map.of(),
                                (tree, manifest, lockfile, work) -> lockfile))
                .as("the public command must execute the check")
                .isEqualTo(Stop.REFUSED);
        assertThat(stderr())
                .as("the command reports the actual match")
                .contains("input.txt:1: HETZNER_API");
    }

    @Test
    @Tag("documents")
    void thisRepositoryNamesNoProviderOutsideItsImplementation() {
        assertThat(check(Path.of(".."))).as(stderr()).isZero();
    }

    private int check() {
        return check(root);
    }

    private int check(Path directory) {
        out.reset();
        err.reset();
        return new CheckProviders(stream(out), stream(err)).check(directory);
    }

    private void write(String name, String text) throws IOException {
        final Path file = root.resolve(name);
        Files.createDirectories(file.getParent());
        Files.writeString(file, text, StandardCharsets.UTF_8);
    }

    private void git(String... arguments) throws Exception {
        final List<String> command = new ArrayList<>(List.of("git", "-C", root.toString()));
        command.addAll(List.of(arguments));
        final ProcessBuilder builder = new ProcessBuilder(command).redirectErrorStream(true);
        builder.environment().put("GIT_CONFIG_GLOBAL", "/dev/null");
        builder.environment().put("GIT_CONFIG_NOSYSTEM", "1");
        final Process process = builder.start();
        final String answer =
                new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertThat(process.waitFor())
                .as("git %s: %s", String.join(" ", arguments), answer)
                .isZero();
    }

    private static PrintStream stream(ByteArrayOutputStream bytes) {
        return new PrintStream(bytes, true, StandardCharsets.UTF_8);
    }

    private String stdout() {
        return out.toString(StandardCharsets.UTF_8);
    }

    private String stderr() {
        return err.toString(StandardCharsets.UTF_8);
    }
}
