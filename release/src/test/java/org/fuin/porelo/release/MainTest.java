package org.fuin.porelo.release;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MainTest {

    @TempDir Path directory;

    private final ByteArrayOutputStream out = new ByteArrayOutputStream();

    private final ByteArrayOutputStream err = new ByteArrayOutputStream();

    private final List<URI> asked = new ArrayList<>();

    private final Web nothingYet =
            (address, headers) -> {
                asked.add(address);
                return Optional.empty();
            };

    @Test
    void anUnknownCommandIsNamedWithTheOnesThisBuildKnows() {
        assertThat(run("check-pins")).isEqualTo(2);
        assertThat(stderr())
                .contains("unknown command 'check-pins'")
                .contains("compare-bills")
                .contains("older than the caller");
    }

    @Test
    void theRemovedPlanCommandIsAnUnknownOne() {
        assertThat(run("plan", "check", "plan/0.6/plan.yml")).isEqualTo(2);
        assertThat(stderr())
                .contains("unknown command 'plan'")
                .doesNotContain(", plan,")
                .contains("stories");
    }

    // Prevents a workflow step that still checks the Markdown issues from failing after the
    // cut-over, and from reading or writing anything there.
    @Test
    void theIssueCommandsPassOnPurposeOnceTheProjectNamesTheTracker() throws IOException {
        Files.writeString(
                directory.resolve("project.yml"),
                "project:\n  name: sample\ntracker:\n  kind: github\n",
                StandardCharsets.UTF_8);
        final Path issues = Files.createDirectories(directory.resolve("issues"));
        Files.writeString(issues.resolve("HO12-Broken.md"), "no head at all\n"); // not-a-citation
        final Path stories = Files.createDirectories(directory.resolve("stories"));

        assertThat(run("issues", "check", issues.toString())).as(stderr()).isZero();
        assertThat(run("stories", "check", stories.toString())).as(stderr()).isZero();
        assertThat(stdout())
                .as("one line each, naming the file that says so")
                .contains("OK    issues check: the issues are kept in GitHub Issues")
                .contains("OK    stories check: the issues are kept in GitHub Issues")
                .contains(directory.resolve("project.yml").toString());
        assertThat(asked).as("the project's own file, nothing over the network").isEmpty();
    }

    // Prevents a stage record from being written, or a missing issue refused, after the cut-over.
    @Test
    void stageWritesNothingOnceTheProjectsFileOnGitHubNamesTheTracker() {
        final Web tracked =
                (address, headers) -> {
                    asked.add(address);
                    return Optional.of(
                            "project: {}\ntracker:\n  kind: github\n"
                                    .getBytes(StandardCharsets.UTF_8));
                };

        final int code =
                Main.run(
                        new String[] {"stage", "begin", "EX12", "implement"}, // not-a-citation
                        new PrintStream(out, true, StandardCharsets.UTF_8),
                        new PrintStream(err, true, StandardCharsets.UTF_8),
                        tracked,
                        at -> Optional.of("https://github.com/porelo-ai/porelo-buildtools.git"),
                        java.util.Map.of(),
                        (tree, manifest, lockfile, work) -> lockfile);

        assertThat(code).as(stderr()).isZero();
        assertThat(stdout())
                .as("one line, naming why nothing is written")
                .contains("OK    stage: the issues are kept in GitHub Issues");
        assertThat(asked).as("porelo-project's file").containsExactly(Tracker.PROJECT);
        assertThat(Path.of("stats")).as("no record written where it ran").doesNotExist();
    }

    // Prevents a test's fixture from being checked against the project's live tracker.
    @Test
    void issuesCheckReadsAFixtureOutsideTheProjectAsMarkdownIssues() throws IOException {
        final Path fixture = Files.createDirectories(directory.resolve("fixture/issues"));
        final Web tracked =
                (address, headers) -> {
                    asked.add(address);
                    return Optional.of(
                            "project: {}\ntracker:\n  kind: github\n"
                                    .getBytes(StandardCharsets.UTF_8));
                };

        Main.run(
                new String[] {"issues", "check", fixture.toString()},
                new PrintStream(out, true, StandardCharsets.UTF_8),
                new PrintStream(err, true, StandardCharsets.UTF_8),
                tracked,
                at -> Optional.empty(),
                java.util.Map.of(),
                (tree, manifest, lockfile, work) -> lockfile);

        assertThat(stdout() + stderr())
                .as("the fixture's own Markdown issues checked")
                .doesNotContain("GitHub Issues");
        assertThat(asked).as("nothing read over the network").isEmpty();
    }

    @Test
    void aFaultInsideACommandIsUnansweredNeverAStop() throws IOException {
        final Web broken =
                (address, headers) -> {
                    throw new IllegalStateException("a bug, not an answer");
                };

        final int code =
                Main.run(
                        new String[] {"compare-bills", built(), "https://repo.example/bom.json"},
                        new PrintStream(out, true, StandardCharsets.UTF_8),
                        new PrintStream(err, true, StandardCharsets.UTF_8),
                        broken,
                        java.util.Map.of(),
                        (tree, manifest, lockfile, work) -> lockfile);

        assertThat(code).as("exit 1 would read as 'a person must look'").isEqualTo(2);
        assertThat(stderr()).contains("internal error").contains("a bug, not an answer");
    }

    @Test
    void noCommandIsAUsageErrorAndNotAStop() {
        assertThat(run()).isEqualTo(2);
    }

    @Test
    void checkCommitsWithoutOneRangeIsAUsageErrorAndNotAPass() {
        // A workflow that lost its range must not read as a range with nothing wrong in it.
        assertThat(run("check-commits")).isEqualTo(2);
        assertThat(run("check-commits", "a..b", "c..d")).isEqualTo(2);
        assertThat(run("check-commits", "a..b", "--outsider")).isEqualTo(2);
        assertThat(stderr()).contains("usage: check-commits RANGE [--outside]");
    }

    @Test
    void checkCitationsTakesNoDirectoryOfOtherRepositories() {
        // A directory of other checkouts would make one repository's result depend on another's.
        assertThat(run("check-citations", "--checkouts", directory.toString(), "."))
                .as("the option is a usage error, not read: %s", stderr())
                .isEqualTo(2);
        assertThat(run("check-citations", "--checkouts=" + directory))
                .as("an option alone is no root to check: %s", stderr())
                .isEqualTo(2);
        assertThat(stderr()).contains("usage: check-citations [REPOSITORY ROOT, default .]");
    }

    @Test
    void compareBillsWithAMissingArgumentIsUnansweredNotAStop() {
        assertThat(run("compare-bills", "built.json")).isEqualTo(CompareBills.UNANSWERED);
        assertThat(asked).as("nothing may be fetched for a call that is not understood").isEmpty();
    }

    @Test
    void anUnknownOptionIsUnansweredRatherThanIgnored() throws IOException {
        assertThat(
                        run(
                                "compare-bills",
                                built(),
                                "http://example.invalid/b.json",
                                "--expect-move",
                                "x"))
                .isEqualTo(CompareBills.UNANSWERED);
        assertThat(stderr()).contains("'--expect-move'");
    }

    @Test
    void readsThePublishedBillFromTheAddressGiven() throws IOException {
        assertThat(run("compare-bills", built(), "https://repo.example/agent/1.0/bom.json"))
                .isEqualTo(0);
        assertThat(asked).containsExactly(URI.create("https://repo.example/agent/1.0/bom.json"));
    }

    @Test
    void takesExpectedMovesInBothSpellings() throws IOException {
        final String built = built();

        assertThat(
                        run(
                                "compare-bills",
                                "--expect-moved",
                                "a",
                                built,
                                "http://x.invalid/b",
                                "--expect-moved=b"))
                .isEqualTo(0);
        assertThat(asked).hasSize(1);
    }

    @Test
    void passesThePinAndTheAgeThrough() throws IOException {
        final java.util.Map<String, String> properties = new java.util.LinkedHashMap<>();
        properties.put("pin.fd.version", "10.5.0");
        properties.put("porelo.release.pin.fd.property", "pin.fd.version");
        properties.put(
                "porelo.release.pin.fd.upstream",
                "github-latest https://api.github.com/repos/fd/releases/latest");
        final String pom =
                Files.writeString(
                                directory.resolve("pom.xml"),
                                AgentRepository.pom(
                                        "porelo-agent-pi", "1.0.0-SNAPSHOT", "0.85.0", properties))
                        .toString();

        // The age is read, and an unreadable one stops the question before upstream is asked
        // anything.
        assertThat(run("upstream-version", "--pom", pom, "--pin", "fd", "--min-age", "soon"))
                .isEqualTo(2);
        assertThat(stderr()).contains("3d or 72h");
        assertThat(asked).isEmpty();
        // Asked of the pin's own upstream.
        assertThat(run("upstream-version", "--pom", pom, "--pin", "fd")).isEqualTo(2);
        assertThat(asked)
                .containsExactly(URI.create("https://api.github.com/repos/fd/releases/latest"));
        assertThat(run("update", "11.0.0", "--pin=fd", "--dry-run", "--pom", pom)).isEqualTo(0);
        assertThat(out.toString(StandardCharsets.UTF_8))
                .contains("fd            10.5.0 -> 11.0.0")
                .contains("--dry-run: nothing written");
    }

    @Test
    void addFetchedCliRecordsTheArtifactTheNamedPinsDigestNames() throws IOException {
        final String helper = "e".repeat(64);
        final AgentRepository codex =
                AgentRepository.codex(directory.resolve("codex"), "1.2.3", "d".repeat(64), helper);
        final String definition = codex.filteredTwo("1.2.3", "d".repeat(64), helper).toString();
        final String bill = built();

        assertThat(
                        run(
                                "add-fetched-cli",
                                bill,
                                definition,
                                "codex-code-mode-host",
                                "--pin",
                                "helper",
                                "--pom",
                                codex.pom().toString()))
                .as(stderr())
                .isEqualTo(0);
        assertThat(Files.readString(Path.of(bill)))
                .as("the helper's artifact, chosen by agent.helper.sha256")
                .contains("helper-x86_64.tar.gz")
                .contains(helper)
                .doesNotContain("codex-x86_64.tar.gz");
        assertThat(
                        run(
                                "add-fetched-cli",
                                bill,
                                definition,
                                "codex-code-mode-host",
                                "--pin",
                                "nope",
                                "--pom",
                                codex.pom().toString()))
                .as("a pin the pom does not declare is refused")
                .isEqualTo(1);
        assertThat(stderr()).contains("porelo.release.pin.nope.property");
    }

    @Test
    void addFetchedCliRefusesAPinItWasNotToldAboutAndChangesNothing() throws IOException {
        final AgentRepository codex =
                AgentRepository.codex(
                        directory.resolve("codex"), "1.2.3", "d".repeat(64), "e".repeat(64));
        final String definition =
                codex.filteredTwo("1.2.3", "d".repeat(64), "e".repeat(64)).toString();
        final String bill = built();
        final String before = Files.readString(Path.of(bill));

        assertThat(
                        run(
                                "add-fetched-cli",
                                bill,
                                definition,
                                "codex",
                                "--pin",
                                "nope",
                                "--pom",
                                codex.pom().toString()))
                .isEqualTo(1);
        assertThat(Files.readString(Path.of(bill)))
                .as("a refused pin writes nothing")
                .isEqualTo(before);
    }

    @Test
    void addFetchedCliRefusesAPomWithoutAPinRatherThanIgnoringIt() throws IOException {
        final String bill = built();

        assertThat(run("add-fetched-cli", bill, "agent.yaml", "codex", "--pom", "pom.xml"))
                .as("--pom alone would be read as if it chose an artifact")
                .isEqualTo(2);
        assertThat(stderr()).contains("--pom").contains("--pin");
    }

    private String built() throws IOException {
        return Files.writeString(directory.resolve("built.json"), CompareBillsTest.bill())
                .toString();
    }

    private int run(String... args) {
        return Main.run(
                args,
                new PrintStream(out, true, StandardCharsets.UTF_8),
                new PrintStream(err, true, StandardCharsets.UTF_8),
                nothingYet,
                java.util.Map.of(),
                (tree, manifest, lockfile, work) -> lockfile);
    }

    private String stderr() {
        return err.toString(StandardCharsets.UTF_8);
    }

    private String stdout() {
        return out.toString(StandardCharsets.UTF_8);
    }
}
