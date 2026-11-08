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
import java.util.Map;
import java.util.Set;
import org.fuin.porelo.json.Json;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class CheckCommitsTest {

    private static final String AGENT = "Assisted-by: Claude Code, claude-opus-5-5";

    private static final String HUMAN = "Jane Doe";

    private static final String HUMAN_MAIL = "jane@example.org";

    @TempDir Path directory;

    private final ByteArrayOutputStream out = new ByteArrayOutputStream();

    private final ByteArrayOutputStream err = new ByteArrayOutputStream();

    private final List<String> askedRanges = new ArrayList<>();

    private final StringBuilder log = new StringBuilder();

    private Set<String> prefixes = Set.of("BT", "SL", "PJ");

    // Each refused subject below was on a main branch, as measured over a day's commits.

    @Test
    void refusesASubjectOverSeventyTwoCharactersCountingSkipCi() {
        commit(
                "a000001",
                "Keep platform threads, as measured, and set the cost issue in review [skip ci]");

        assertThat(check()).as(stderr()).isEqualTo(Stop.REFUSED);
        assertThat(stderr()).contains("a000001  subject is 78 characters, at most 72");
    }

    @Test
    void passesASubjectOfExactlySeventyTwoCharacters() {
        final String subject = "Take " + "x".repeat(57) + " [skip ci]";
        assertThat(subject).as("the boundary itself").hasSize(72);
        commit("a000002", subject);

        assertThat(check()).as(stderr()).isEqualTo(0);
    }

    @Test
    void refusesARequirementNumberOfAKnownPrefix() {
        commit("a000003", "Set SL27 and SL25 in review, published as 0.5.1~snapshot.51 [skip ci]");

        assertThat(check()).as(stderr()).isEqualTo(Stop.REFUSED);
        assertThat(stderr())
                .contains("a000003  names a requirement number: SL27")
                .contains("a000003  names a requirement number: SL25");
    }

    @Test
    void passesAWordOfANumbersShapeWhosePrefixNoIndexHas() {
        // A word list of capitals and digits would refuse these; only the indexes' prefixes are
        // numbers.
        commit("a000004", "Take IPv4 only, and build for x86_64 with UTF8 and SHA256 names");

        assertThat(check()).as(stderr()).isEqualTo(0);
    }

    @Test
    void refusesTheOperatorAndTheLengthEachOnItsOwnLine() {
        commit(
                "a000005",
                "Keep action moves the operator's hand merge, and size the light check [skip ci]");

        assertThat(check()).as(stderr()).isEqualTo(Stop.REFUSED);
        assertThat(stderr())
                .contains("a000005  subject is 79 characters, at most 72")
                .contains("a000005  says who: operator");
    }

    @Test
    void refusesAProseBodyBelowTheSubject() {
        commit(
                "a000006",
                "Read the pins once\n\nThe second read was slow, so it is cached now.\n\n" + AGENT);

        assertThat(check()).as(stderr()).isEqualTo(Stop.REFUSED);
        assertThat(stderr())
                .contains(
                        "a000006  below the subject, a line that is not a trailer:"
                                + " The second read was slow, so it is cached now.");
    }

    @Test
    void refusesASecondLineWithoutTheBlankLineBeforeIt() {
        commit("a000007", "Read the pins once\n" + AGENT);

        assertThat(check()).as(stderr()).isEqualTo(Stop.REFUSED);
        assertThat(stderr()).contains("a000007  the subject is not followed by a blank line");
    }

    @Test
    void refusesATrailerOfAnotherKey() {
        commit(
                "a000008",
                "Read the pins once\n\n" + AGENT + "\nReviewed-by: Somebody <s@example.org>");

        assertThat(check()).as(stderr()).isEqualTo(Stop.REFUSED);
        assertThat(stderr())
                .contains("a000008  below the subject, a line that is not a trailer: Reviewed-by:");
    }

    @Test
    void passesCleanSubjectsWithAndWithoutAnAgentsTrailer() {
        commit("a000009", "Accept only the host key made for a rented machine");
        commit(
                "a000010",
                "Take the shared rules on the sweep and an agent's trailer [skip ci]\n\n" + AGENT);
        commit(
                "a000011",
                "Give every rented machine a host key made per run\n\n"
                        + "Co-authored-by: Jane Doe <jane@example.org>\n"
                        + AGENT);

        assertThat(check()).as(stderr()).isEqualTo(0);
        assertThat(stdout()).contains("OK    3 commit(s) in RANGE, 0 exempt");
    }

    @Test
    void readsATrailersKeyInAnyCaseAsGitDoes() {
        // Claude Code writes "Co-Authored-By", and git takes trailer keys in any case.
        commit(
                "a000090",
                "Give every rented machine a host key made per run\n\n"
                        + "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>\n"
                        + "assisted-by: Claude Code, claude-opus-5-5");

        assertThat(check()).as(stderr()).isEqualTo(0);
    }

    @Test
    void takesTheTrailersPoreloWritesOnItsOwnCommits() {
        // As porelo's organization tree writes them (TreeRepository): its change id on each, and
        // the
        // file a commit signs anew on one that changes no file.
        commit("a000092", "Set the project's members\n\nPorelo-Change-Id: members-porelo-3\n");
        commit(
                "a000093",
                "Sign the repository file anew: porelo.yml\n\n"
                        + "Porelo-Change-Id: sign-pi-1\n"
                        + "Porelo-Signs-Anew: porelo.yml\n");
        commit(
                "a000094",
                "Sign the repository file anew: .github/porelo.yml\n\n"
                        + "porelo-change-id: sign-pi-2\n"
                        + "PORELO-SIGNS-ANEW: .github/porelo.yml");

        assertThat(check()).as(stderr()).isEqualTo(0);
        assertThat(stdout()).contains("OK    3 commit(s) in RANGE, 0 exempt");
    }

    @Test
    void refusesPorelosTrailersOutsideTheirForm() {
        commit("a000095", "Set the project's members\n\nPorelo-Change-Id: two words");
        commit(
                "a000096",
                "Sign the file anew\n\nPorelo-Change-Id: a\nPorelo-Signs-Anew: ../outside.yml");
        commit(
                "a000097",
                "Sign the file anew\n\nPorelo-Change-Id: a\nPorelo-Signs-Anew: /etc/porelo.yml");
        commit(
                "a000098",
                "Sign the file anew\n\nPorelo-Change-Id: a\nPorelo-Signs-Anew: a file.yml");
        commit("a000099", "Set the project's members\n\nPorelo-Reviewed: yes");

        assertThat(check()).as(stderr()).isEqualTo(Stop.REFUSED);
        assertThat(stderr())
                .contains(
                        "a000095  below the subject, a line that is not a trailer: Porelo-Change-Id: two words")
                .contains(
                        "a000096  below the subject, a line that is not a trailer: Porelo-Signs-Anew: ../outside.yml")
                .contains(
                        "a000097  below the subject, a line that is not a trailer: Porelo-Signs-Anew: /etc/porelo.yml")
                .contains(
                        "a000098  below the subject, a line that is not a trailer: Porelo-Signs-Anew: a file.yml")
                .contains(
                        "a000099  below the subject, a line that is not a trailer: Porelo-Reviewed: yes");
    }

    @Test
    void refusesASignedAnewPathThatLeavesTheRepositoryByAnyRoute() {
        final List<String> paths =
                List.of("a/../../b", "..", "a/..", "..\\outside.yml", "a\\b.yml", "\\x");
        for (int i = 0; i < paths.size(); i++) {
            commit(
                    "b0000" + (10 + i),
                    "Sign the file anew\n\nPorelo-Change-Id: a\nPorelo-Signs-Anew: "
                            + paths.get(i));
        }

        assertThat(check()).as(stderr()).isEqualTo(Stop.REFUSED);
        for (int i = 0; i < paths.size(); i++) {
            assertThat(stderr())
                    .contains(
                            "b0000"
                                    + (10 + i)
                                    + "  below the subject, a line that is not a trailer: Porelo-Signs-Anew: "
                                    + paths.get(i));
        }
    }

    @Test
    void takesASignOffInAnyCaseFromOutside() {
        commit(
                "a000091",
                HUMAN,
                HUMAN_MAIL,
                HUMAN,
                HUMAN_MAIL,
                "Read the pins once\n\nSigned-Off-By: Jane Doe <jane@example.org>");

        assertThat(checkOutside()).as(stderr()).isEqualTo(0);
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "Pin the actions at Coordinator's word",
                "Move the sweep as Smith chose",
                "Let Agent Core set the pins",
                "Hand the relay to Agent Matrix",
                "Thank @octocat for the lease fix",
                "Fix the lease leak found by the nightly run",
                "Drop the retry asked by the frontend",
                "Keep the old pins, decided by the call",
                "Fix the leg reported by a user",
                "Take the sweep accepted by the owner",
                "Move the pins per Smith",
                "Rework the lease after the owner's review",
                "Rework the lease after the owner's test",
                "Merge what the Operator held back",
                "Name the coordinator in the pushes",
                "Take the host key at the lead's word",
                "Keep the pins as the lead chose",
            })
    void refusesASubjectThatSaysWho(String subject) {
        commit("a000012", subject);

        assertThat(check()).as(subject).isEqualTo(Stop.REFUSED);
        assertThat(stderr()).as(subject).contains("a000012  says who: ");
    }

    @Test
    void refusesTheCommitsOwnAuthorNamedInIt() {
        commit(
                "a000013",
                HUMAN,
                HUMAN_MAIL,
                HUMAN,
                HUMAN_MAIL,
                "Move the pins jane doe wanted moved");

        assertThat(check()).as(stderr()).isEqualTo(Stop.REFUSED);
        assertThat(stderr()).contains("a000013  says who: jane doe");
    }

    @Test
    void refusesTheCommitsOwnCommitterNamedInIt() {
        commit(
                "a000014",
                HUMAN,
                HUMAN_MAIL,
                "Max Mustermann",
                "max@example.org",
                "Take the pins Max Mustermann moved");

        assertThat(check()).as(stderr()).isEqualTo(Stop.REFUSED);
        assertThat(stderr()).contains("a000014  says who: Max Mustermann");
    }

    @Test
    void exemptsWhatDependabotAuthors() {
        commit(
                "a000015",
                "dependabot[bot]",
                "49699333+dependabot[bot]@users.noreply.github.com",
                "GitHub",
                "noreply@github.com",
                "Bump the github-actions group across 1 directory with 3 updates\n\n"
                        + "Bumps the github-actions group with 3 updates in the / directory:"
                        + " actions/checkout.\n\n"
                        + "Signed-off-by: dependabot[bot] <support@github.com>");
        commit(
                "a000016",
                "dependabot[bot]",
                "49699333+dependabot[bot]@users.noreply.github.com",
                "dependabot[bot]",
                "49699333+dependabot[bot]@users.noreply.github.com",
                "Bump actions/checkout from 6.0.1 to 6.0.2 in the github-actions group across 1"
                        + " directory");

        assertThat(check()).as(stderr()).isEqualTo(0);
        assertThat(stdout()).contains("OK    2 commit(s) in RANGE, 2 exempt");
    }

    @Test
    void exemptsAMergeGithubCommitsInItsWebInterface() {
        commit(
                "a000017",
                HUMAN,
                HUMAN_MAIL,
                "GitHub",
                "noreply@github.com",
                "Merge pull request #41 from"
                        + " porelo-ai/dependabot/github_actions/github-actions-6f1c2d\n\n"
                        + "Bump the github-actions group across 1 directory with 3 updates");

        assertThat(check()).as(stderr()).isEqualTo(0);
    }

    @Test
    void neverTakesTheMessageAsTheSignOfAnExemptCommit() {
        commit(
                "a000018",
                HUMAN,
                HUMAN_MAIL,
                HUMAN,
                HUMAN_MAIL,
                "Merge pull request #41 from"
                        + " porelo-ai/dependabot/github_actions/github-actions-6f1c2d");

        assertThat(check()).as(stderr()).isEqualTo(Stop.REFUSED);
        assertThat(stderr()).contains("a000018  subject is");
    }

    @Test
    void refusesACommitFromOutsideWithoutItsAuthorsSignOff() {
        commit("a000019", HUMAN, HUMAN_MAIL, HUMAN, HUMAN_MAIL, "Read the pins once");

        assertThat(checkOutside()).as(stderr()).isEqualTo(Stop.REFUSED);
        assertThat(stderr())
                .contains("a000019  no Signed-off-by: naming its author <jane@example.org>");
    }

    @Test
    void refusesACommitFromOutsideSignedOffBySomebodyElse() {
        commit(
                "a000020",
                HUMAN,
                HUMAN_MAIL,
                HUMAN,
                HUMAN_MAIL,
                "Read the pins once\n\nSigned-off-by: Max Mustermann <max@example.org>");

        assertThat(checkOutside()).as(stderr()).isEqualTo(Stop.REFUSED);
        assertThat(stderr())
                .contains("a000020  no Signed-off-by: naming its author <jane@example.org>");
    }

    @Test
    void passesACommitFromOutsideWithItsAuthorsSignOff() {
        commit(
                "a000021",
                HUMAN,
                HUMAN_MAIL,
                HUMAN,
                HUMAN_MAIL,
                "Read the pins once\n\nSigned-off-by: Jane Doe <Jane@Example.org>");

        assertThat(checkOutside()).as(stderr()).isEqualTo(0);
    }

    @Test
    void exemptsDependabotFromTheSignOffOfAPullRequestFromOutside() {
        commit(
                "a000022",
                "dependabot[bot]",
                "49699333+dependabot[bot]@users.noreply.github.com",
                "dependabot[bot]",
                "49699333+dependabot[bot]@users.noreply.github.com",
                "Bump actions/checkout");

        assertThat(checkOutside()).as(stderr()).isEqualTo(0);
    }

    @Test
    void neitherAsksNorRefusesASignOffOnAPush() {
        commit(
                "a000023",
                HUMAN,
                HUMAN_MAIL,
                HUMAN,
                HUMAN_MAIL,
                "Read the pins once\n\nSigned-off-by: Jane Doe <jane@example.org>");
        commit("a000024", "Read the pins twice\n\n" + AGENT);

        assertThat(check()).as(stderr()).isEqualTo(0);
    }

    @Test
    void passesARangeWithNothingInIt() {
        assertThat(check()).as(stderr()).isEqualTo(0);
        assertThat(stdout()).contains("OK    0 commit(s) in RANGE");
    }

    @Test
    void aGitThatFailsIsUnansweredNeverAPass() {
        final CheckCommits check =
                new CheckCommits(
                        stream(out),
                        stream(err),
                        range -> {
                            throw new java.io.IOException("fatal: bad revision 'RANGE'");
                        },
                        () -> prefixes);

        assertThat(check.check("RANGE", false)).isEqualTo(Stop.UNANSWERED);
        assertThat(stderr()).contains("fatal: bad revision 'RANGE'");
    }

    @Test
    void noPrefixFoundIsUnansweredNeverAPass() {
        // Without a prefix the number rule would hold nothing and say so nowhere.
        prefixes = Set.of();
        commit("a000025", "Read the pins once");

        assertThat(check()).isEqualTo(Stop.UNANSWERED);
        assertThat(stderr()).contains("no issue prefix");
    }

    @Test
    void aRangeThatGitWouldReadAsAnOptionIsNotRun() {
        // 'git log --output=FILE' writes a file: a range from a workflow must not become an option.
        assertThat(
                        new CheckCommits(stream(out), stream(err), this::fakeLog, () -> prefixes)
                                .check("--output=/tmp/x", false))
                .isEqualTo(Stop.UNANSWERED);
        assertThat(askedRanges).as("git was not run").isEmpty();
    }

    @Test
    void readsWhatGitItselfWrites() throws IOException, InterruptedException {
        git("init", "-q", "-b", "main");
        git("commit", "-q", "--allow-empty", "-m", "Read the pins once", "-m", AGENT);
        git(
                "commit",
                "-q",
                "--allow-empty",
                "-m",
                "Read the pins twice",
                "-m",
                "Because it was slow.");
        final String bad = git("log", "-1", "--format=%h").strip();
        final String good = git("log", "-1", "--skip=1", "--format=%h").strip();

        final int code =
                new CheckCommits(
                                stream(out),
                                stream(err),
                                CheckCommits.Git.in(directory, isolated()),
                                () -> prefixes)
                        .check("HEAD", false);

        assertThat(code).as(stderr()).isEqualTo(Stop.REFUSED);
        assertThat(stderr())
                .contains(
                        bad
                                + "  below the subject, a line that is not a trailer: Because it"
                                + " was slow.")
                .doesNotContain(good);
        assertThat(stderr())
                .as("one fault, so the fields were split where git put them")
                .contains("1 fault(s)");
    }

    @Test
    void takesOnlyThePrefixesOfItsOwnIssuesAndTheirRows() throws IOException {
        // Without a prefix a requirement number of that repository goes through as a word; another
        // repository's index is not read, so its state cannot decide this repository's check.
        write(
                "issues/README.md",
                "| <a id=\"bt17\"></a>[BT17](BT17-A.md) | x |\n"
                        + "| <a id=\"xy05\"></a>XY05 | done |\n");
        write(
                "issues/BT17-A.md",
                "As [PJ27](https://github.com/porelo-ai/porelo-project/blob/main/issues/README.md#pj27)"
                        + " says.\n");
        // A test's fixture deeper down holds issues of whatever prefix it was written with.
        write(
                "src/test/resources/sample/issues/README.md",
                "| <a id=\"f05\"></a>[F05](F05-A-Fixture.md) | a fixture |\n");
        write("src/test/resources/sample/issues/F05-A-Fixture.md", "# F05 - A Fixture\n");

        assertThat(CheckCitations.issuePrefixes(directory))
                .as("its own file's and a finished row's, never the linked index's or a fixture's")
                .containsExactlyInAnyOrder("BT", "XY");
    }

    @Test
    void refusesAnOpenStageInAnyCommitTreeEvenAfterItWasClosed() throws Exception {
        git("init", "-q", "-b", "main");
        final var open = StageFixtures.record("EX12", "implement", "agent", 4, "Review", "M");
        for (final String key : List.of("end", "status_after", "wall_min", "vm_min", "result")) {
            open.put(key, null);
        }
        write("stats/EX12.jsonl", Json.write(open) + "\n");
        git("add", "stats/EX12.jsonl");
        git("commit", "-q", "-m", "Record work");
        final String bad = git("rev-parse", "--short", "HEAD").strip();
        write("stats/EX12.jsonl", StageFixtures.closed("EX12"));
        git("add", "stats/EX12.jsonl");
        git("commit", "-q", "-m", "Close work");

        assertThat(checkRepository())
                .as("closing later cannot hide an open stage in the range")
                .isEqualTo(Stop.REFUSED);
        assertThat(stderr())
                .as("the fault names the offending commit and file")
                .contains(bad, "stats/EX12.jsonl", "end");
    }

    @Test
    void refusesAnIssueChangeWithoutItsStatsInThatCommitTree() throws Exception {
        git("init", "-q", "-b", "main");
        write("stats/seed.csv", "size,minutes\nM,53\n");
        write(
                "issues/EX12-Sample.md",
                "# Sample\n**Status:** Implementing.\n**Size:** M - sample.\n");
        git("add", "stats/seed.csv", "issues/EX12-Sample.md");
        git("commit", "-q", "-m", "Describe work");
        final String bad = git("rev-parse", "--short", "HEAD").strip();

        assertThat(checkRepository())
                .as("issue work cannot bypass stage recording")
                .isEqualTo(Stop.REFUSED);
        assertThat(stderr())
                .as("missing stats are attributed to their commit")
                .contains(bad, "stats/EX12.jsonl");
    }

    @Test
    void allowsAnIndexOnlyChangeAndAnIssueWithExistingClosedStats() throws Exception {
        git("init", "-q", "-b", "main");
        write("stats/seed.csv", "size,minutes\nM,53\n");
        write("issues/README.md", "# Issues\n");
        write("stats/EX12.jsonl", StageFixtures.closed("EX12"));
        git("add", "stats/seed.csv", "stats/EX12.jsonl", "issues/README.md");
        git("commit", "-q", "-m", "Index work");
        write(
                "issues/EX12-Sample.md",
                "# Sample\n**Status:** Implementing.\n**Size:** M - sample.\n");
        git("add", "issues/EX12-Sample.md");
        git("commit", "-q", "-m", "Describe work");

        assertThat(checkRepository())
                .as("stats need to exist in the tree, not change in every commit")
                .isZero();
    }

    @Test
    void reportsTheIssueStatsRuleAsNotRolledOutWithoutASeed() throws Exception {
        git("init", "-q", "-b", "main");
        write(
                "issues/EX12-Sample.md",
                "# Sample\n**Status:** Implementing.\n**Size:** M - sample.\n");
        git("add", "issues/EX12-Sample.md");
        git("commit", "-q", "-m", "Describe work");

        assertThat(checkRepository()).as("repositories opt in through their seed").isZero();
        assertThat(stdout())
                .as("the unchecked rule is explicit")
                .contains("NOTE", "stats/seed.csv");
    }

    // These regressions prevent commits bypassing stage checks or acquiring unrelated work.
    @ParameterizedTest
    @ValueSource(
            strings = {
                "empty",
                "missing-field",
                "unknown-field",
                "issue",
                "start",
                "end",
                "wall_min",
                "n",
                "actor",
                "size",
                "model",
                "status_before",
                "status_after",
                "result",
                "wait_reason",
                "note",
                "estimate_min",
                "claude_input_tokens"
            })
    void refusesInvalidStageRecordsInTheCommitTree(String defect) throws Exception {
        git("init", "-q", "-b", "main");
        final var record = StageFixtures.record("EX12", "implement", "agent", 4, "Review", "M");
        switch (defect) {
            case "missing-field" -> record.remove("actor");
            case "unknown-field" -> record.put("unexpected", true);
            case "issue" -> record.put("issue", "EX13");
            case "start", "end" -> record.put(defect, "not-an-instant");
            case "wall_min", "claude_input_tokens" -> record.put(defect, -1);
            case "n" -> record.put("n", 1.5);
            case "size" -> record.put("size", "XXL");
            case "model" -> record.put("model", "/private/model");
            case "note" -> record.put("note", "private/path");
            case "actor", "status_before", "status_after", "result", "wait_reason" ->
                    record.put(defect, "unknown");
            case "estimate_min" -> record.put("estimate_min", 42);
            default -> {}
        }
        write("stats/EX12.jsonl", defect.equals("empty") ? "" : Json.write(record) + "\n");
        git("add", "stats/EX12.jsonl");
        git("commit", "-q", "-m", "Record work");
        assertThat(checkRepository())
                .as("invalid %s must fail closed: %s", defect, stderr())
                .isEqualTo(Stop.REFUSED);
        assertThat(stderr())
                .as("the bad record's location is visible")
                .contains("stats/EX12.jsonl");
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "Idea",
                "Accepted",
                "Analyzed",
                "Estimated",
                "Backlog",
                "Rework",
                "Rejected"
            })
    void allowsIssueChangesBeforeImplementationWithoutStats(String status) throws Exception {
        git("init", "-q", "-b", "main");
        write("stats/seed.csv", "size,minutes\nM,53\n");
        write("issues/EX12-Sample.md", "**Status:** " + status + ".\n");
        git("add", "issues", "stats");
        git("commit", "-q", "-m", "Describe work");
        assertThat(checkRepository()).as(stderr()).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {"Implementing", "Review", "Finished"})
    void requiresStatsForSubgroupIssuesInActiveStates(String status) throws Exception {
        git("init", "-q", "-b", "main");
        write("stats/seed.csv", "size,minutes\nM,53\n");
        write("issues/core/EX12-Sample.md", "**Status:** " + status + ".\n**Size:** M - sample.\n");
        git("add", "issues", "stats");
        git("commit", "-q", "-m", "Describe work");
        assertThat(checkRepository())
                .as("nested issue work also needs stats")
                .isEqualTo(Stop.REFUSED);
        assertThat(stderr())
                .as("the missing stats file is identified")
                .contains("stats/EX12.jsonl");
    }

    @Test
    void skipsDeletedIssues() throws Exception {
        git("init", "-q", "-b", "main");
        write("issues/EX12-Sample.md", "**Status:** Review.\n");
        git("add", "issues");
        git("commit", "-q", "-m", "Describe work");
        git("rm", "issues/EX12-Sample.md");
        write("stats/seed.csv", "size,minutes\nM,53\n");
        git("add", "stats");
        git("commit", "-q", "-m", "Finish work");
        assertThat(checkRepository()).as(stderr()).isZero();
    }

    @Test
    void comparesMergesOnlyWithTheirFirstParentAndRootsWithTheEmptyTree() throws Exception {
        git("init", "-q", "-b", "main");
        write("initial.txt", "root\n");
        git("add", "initial.txt");
        git("commit", "-q", "-m", "Start work");
        final CheckCommits.Git reader = CheckCommits.Git.in(directory, isolated());
        assertThat(reader.changed("HEAD"))
                .as("root changes are measured against the empty tree")
                .containsExactly("initial.txt");
        git("branch", "side");
        write("issues/EX12-Sample.md", "**Status:** Review.\n");
        git("add", "issues");
        git("commit", "-q", "-m", "Describe work");
        git("checkout", "-q", "side");
        write("stats/seed.csv", "size,minutes\nM,53\n");
        git("add", "stats");
        git("commit", "-q", "-m", "Seed estimates");
        git("checkout", "-q", "main");
        git("merge", "-q", "--no-ff", "side", "-m", "Merge estimates");
        assertThat(reader.changed("HEAD"))
                .as("main's earlier issue edit is not attributed to the merge")
                .containsExactly("stats/seed.csv");
        assertThat(
                        new CheckCommits(stream(out), stream(err), reader, () -> prefixes)
                                .check("HEAD^1..HEAD", false))
                .as(stderr())
                .isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {"dependabot[bot]", "web"})
    void exemptsBotAndWebCommitsFromTheStatsRule(String author) throws Exception {
        git("init", "-q", "-b", "main");
        // An issue in Review with no stats at all: only the commit's exemption lets it through.
        write("stats/seed.csv", "size,minutes\nM,53\n");
        write("issues/EX12-Sample.md", "**Status:** Review.\n");
        git("add", "stats", "issues");
        final ProcessBuilder builder =
                new ProcessBuilder(
                        "git", "-C", directory.toString(), "commit", "-q", "-m", "Record work");
        builder.environment().putAll(isolated());
        builder.environment().put("GIT_AUTHOR_NAME", author.equals("web") ? HUMAN : author);
        builder.environment().put("GIT_AUTHOR_EMAIL", "example@example.org");
        builder.environment().put("GIT_COMMITTER_NAME", author.equals("web") ? "GitHub" : HUMAN);
        builder.environment().put("GIT_COMMITTER_EMAIL", "example@example.org");
        final Process process = builder.redirectErrorStream(true).start();
        final String output =
                new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertThat(process.waitFor()).as(output).isZero();
        assertThat(checkRepository()).as(stderr()).isZero();
    }

    private int checkRepository() {
        return new CheckCommits(
                        stream(out),
                        stream(err),
                        CheckCommits.Git.in(directory, isolated()),
                        () -> prefixes)
                .check("HEAD", false);
    }

    private void write(String name, String text) throws IOException {
        final Path file = directory.resolve(name);
        Files.createDirectories(file.getParent());
        Files.writeString(file, text, StandardCharsets.UTF_8);
    }

    private int check() {
        return new CheckCommits(stream(out), stream(err), this::fakeLog, () -> prefixes)
                .check("RANGE", false);
    }

    private int checkOutside() {
        return new CheckCommits(stream(out), stream(err), this::fakeLog, () -> prefixes)
                .check("RANGE", true);
    }

    private String fakeLog(String range) {
        askedRanges.add(range);
        return log.toString();
    }

    private void commit(String hash, String message) {
        commit(
                hash,
                "Alice Example",
                "alice@example.org",
                "Alice Example",
                "alice@example.org",
                message);
    }

    /**
     * One record as {@link CheckCommits.Git#FORMAT} asks git for it, with the newline git ends it
     * with.
     */
    private void commit(
            String hash,
            String author,
            String authorMail,
            String committer,
            String committerMail,
            String message) {
        log.append('\u001e')
                .append(
                        String.join(
                                "\u001f",
                                hash,
                                author,
                                authorMail,
                                committer,
                                committerMail,
                                message))
                .append('\n');
    }

    private String git(String... arguments) throws IOException, InterruptedException {
        final List<String> command = new ArrayList<>(List.of("git", "-C", directory.toString()));
        command.addAll(List.of(arguments));
        final ProcessBuilder builder = new ProcessBuilder(command).redirectErrorStream(true);
        builder.environment().putAll(isolated());
        builder.environment().put("GIT_AUTHOR_NAME", HUMAN);
        builder.environment().put("GIT_AUTHOR_EMAIL", HUMAN_MAIL);
        builder.environment().put("GIT_COMMITTER_NAME", HUMAN);
        builder.environment().put("GIT_COMMITTER_EMAIL", HUMAN_MAIL);
        final Process git = builder.start();
        final String said = new String(git.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertThat(git.waitFor()).as(said).isEqualTo(0);
        return said;
    }

    private static Map<String, String> isolated() {
        return Map.of("GIT_CONFIG_GLOBAL", "/dev/null", "GIT_CONFIG_NOSYSTEM", "1");
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
