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
import org.junit.jupiter.api.Tag;
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

    // Prevents an issue number from reaching main once the issues are kept in GitHub Issues.
    @Test
    void refusesAnOldIdAndAGitHubReference() {
        commit("a000041", "Close HO12 at last");
        commit("a000042", "Fix the pin for #12");
        commit("a000043", "Follow the planner\n\nAssisted-by: porelo-ai/porelo-host#7");
        commit("a000044", "Follow https://github.com/Porelo-AI/porelo/issues/9 and GH-3");
        commit("a000049", "Close \"#13\", '#14' and --#15");
        commit("a000050", "Follow www.github.com/porelo-ai/porelo/issues/16 and -GH-17");

        assertThat(check()).as(stderr()).isEqualTo(Stop.REFUSED);
        assertThat(stderr())
                .as("each number by its shape, no prefix read")
                .contains("a000041  names a requirement number: HO12")
                .contains("a000042  names a requirement number: #12")
                .contains("a000043  names a requirement number: porelo-ai/porelo-host#7")
                .contains(
                        "a000044  names a requirement number:"
                                + " https://github.com/Porelo-AI/porelo/issues/9")
                .contains("a000044  names a requirement number: GH-3")
                .contains("a000049  names a requirement number: #13")
                .contains("a000049  names a requirement number: #14")
                .contains("a000049  names a requirement number: #15")
                .contains(
                        "a000050  names a requirement number:"
                                + " www.github.com/porelo-ai/porelo/issues/16")
                .contains("a000050  names a requirement number: GH-17");
    }

    // Prevents the shape rule from refusing words a subject needs, which would block every push.
    @Test
    void passesWordsOfNoNumbersShape() {
        commit("a000045", "Take IPv4 only, and build for x86_64 with UTF8 and SHA256 names");
        commit("a000046", "Keep the S256 challenge and the colour #fff\n\n" + AGENT);

        assertThat(check()).as(stderr()).isEqualTo(0);
    }

    // Prevents the tracker from switching off the rules that are not about issues.
    @Test
    void keepsEveryOtherRule() {
        commit("a000047", "Take " + "x".repeat(70) + "\n\nBecause it was slow.");
        commit("a000048", "Read the pins once\n\nCo-Authored-By: A <a@example.org>");

        assertThat(check()).as(stderr()).isEqualTo(Stop.REFUSED);
        assertThat(stderr())
                .as("the length, the body and the co-author, as without the tracker")
                .contains("a000047  subject is 75 characters, at most 72")
                .contains("a000047  below the subject, a line that is not a trailer")
                .contains("a000048  below the subject, a line that is not a trailer");
    }

    // Prevents a commit from losing its link to the issue it was made for once the tracker keeps
    // the issues, which the trailer alone gives.
    @Test
    void passesOnePoreloIssueTrailerPerIssue() {
        commit(
                "a000060",
                "Take the issue trailer\n\n"
                        + AGENT
                        + "\nPorelo-Issue: porelo-ai/porelo#123\nporelo-issue: Porelo-AI/porelo-host#7");

        assertThat(check()).as(stderr()).isEqualTo(0);
    }

    // Prevents a trailer the tracker cannot link from passing as if it named its issue.
    @ParameterizedTest
    @ValueSource(
            strings = {
                "Porelo-Issue: #123",
                "Porelo-Issue: porelo-ai/porelo",
                "Porelo-Issue: porelo-ai/porelo#123 too",
                "Porelo-Issue: porelo-ai/porelo#0",
                "Porelo-Issue: https://github.com/porelo-ai/porelo/issues/123",
                "Porelo-Issue:porelo-ai/porelo#123",
                "Porelo-Issue: porelo-ai/..#1",
                "Porelo-Issue: porelo-ai/.#1"
            })
    void refusesAPoreloIssueTrailerOfAnotherForm(String trailer) {
        commit("a000062", "Take the issue trailer\n\n" + AGENT + "\n" + trailer);

        assertThat(check()).as(stderr()).isEqualTo(Stop.REFUSED);
        assertThat(stderr())
                .contains(
                        "a000062  a Porelo-Issue trailer not of the form"
                                + " 'Porelo-Issue: <owner>/<repository>#<number>': "
                                + trailer);
    }

    // Prevents a push from closing an issue, whose Finished only the person sets.
    @ParameterizedTest
    @ValueSource(
            strings = {
                "Fixes #12",
                "closes porelo-ai/porelo#12",
                "Resolved: https://github.com/o/r/issues/12",
                "FIX octo/cat#3",
                "Closes: #3",
                "RESOLVES http://www.github.com/o/r/issues/4",
                "fixes\n#5",
                "Fixes GH-12"
            })
    void refusesAClosingKeywordBeforeAnIssueReference(String closing) {
        commit("a000063", "Take the pin\n\n" + AGENT + "\nPorelo-Issue: porelo-ai/porelo#12");
        commit("a000064", "Take the pin, " + closing.replace('\n', ' ') + "\n\n" + AGENT);
        // In a trailer's value, the one place no other rule refuses it.
        commit("a000065", "Take the pin\n\n" + AGENT + ", " + closing);

        assertThat(check()).as(stderr()).isEqualTo(Stop.REFUSED);
        assertThat(stderr())
                .doesNotContain("a000063  ")
                .contains("a000064  closes an issue on push: " + closing.replace('\n', ' '))
                .contains("a000065  closes an issue on push: " + closing);
    }

    // Prevents the closing rule from refusing the words a subject needs, which would block pushes.
    @Test
    void passesAClosingWordWithoutAnIssueReference() {
        commit("a000066", "Fix the pin, close the gap and resolve the prefix #fff\n\n" + AGENT);

        assertThat(check()).as(stderr()).isEqualTo(0);
    }

    // Prevents the trailer from carrying a number into the subject past the number rule.
    @Test
    void refusesANumberInTheSubjectBesideAPoreloIssueTrailer() {
        commit(
                "a000067",
                "Take porelo-ai/porelo#123 and #12\n\n"
                        + AGENT
                        + "\nPorelo-Issue: porelo-ai/porelo#123");

        assertThat(check()).as(stderr()).isEqualTo(Stop.REFUSED);
        assertThat(stderr())
                .contains("a000067  names a requirement number: porelo-ai/porelo#123")
                .contains("a000067  names a requirement number: #12")
                .contains("2 fault(s) in 1 of 1 commit(s)");
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
                        + "Signed-off-by: Jane Doe <jane@example.org>\n"
                        + AGENT);

        assertThat(check()).as(stderr()).isEqualTo(0);
        assertThat(stdout()).contains("OK    3 commit(s) in RANGE, 0 exempt");
    }

    @Test
    void readsATrailersKeyInAnyCaseAsGitDoes() {
        // Git takes trailer keys in any case.
        commit(
                "a000090",
                "Give every rented machine a host key made per run\n\n"
                        + "SIGNED-OFF-BY: Jane Doe <jane@example.org>\n"
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

    // Prevents anyone from writing a bot's name into a commit to skip every rule from outside.
    @Test
    void holdsABotsRecordedNameFromOutsideToEveryRule() {
        commit(
                "a000022",
                "dependabot[bot]",
                "49699333+dependabot[bot]@users.noreply.github.com",
                "dependabot[bot]",
                "49699333+dependabot[bot]@users.noreply.github.com",
                "Set BT12 done");

        assertThat(checkOutside()).as(stderr()).isEqualTo(Stop.REFUSED);
        assertThat(stderr())
                .contains("a000022  names a requirement number: BT12")
                .contains("a000022  no Signed-off-by: naming its author");
    }

    // Prevents anyone from naming GitHub as committer to skip every rule from outside.
    @Test
    void holdsTheWebCommittersNameFromOutsideToEveryRule() {
        commit("a000025", HUMAN, HUMAN_MAIL, "GitHub", "noreply@github.com", "Set BT12 done");

        assertThat(checkOutside()).as(stderr()).isEqualTo(Stop.REFUSED);
        assertThat(stderr())
                .contains("a000025  names a requirement number: BT12")
                .contains("a000025  no Signed-off-by: naming its author <jane@example.org>");
    }

    // Prevents a requirement number or a person hidden in a trailer, below the words the rules
    // read.
    @Test
    void readsEveryTrailerForNumbersAndAllButTheSignOffForWho() {
        commit("a000026", "Read the pins once\n\nAssisted-by: BT12 helper");
        commit("a000027", "Read the pins twice\n\nAssisted-by: the operator");
        commit(
                "a000028",
                "Read the pins thrice\n\nSigned-off-by: The Operator <operator@example.org>");

        assertThat(check()).as(stderr()).isEqualTo(Stop.REFUSED);
        assertThat(stderr())
                .contains("a000026  names a requirement number: BT12")
                .contains("a000027  says who: operator")
                .doesNotContain("a000028");
    }

    // Prevents a trailer the shared rules forbid an agent to write.
    @Test
    void refusesCoAuthoredBy() {
        commit("a000029", "Read the pins once\n\nCo-authored-by: Max <max@example.org>");

        assertThat(check()).as(stderr()).isEqualTo(Stop.REFUSED);
        assertThat(stderr())
                .contains(
                        "a000029  below the subject, a line that is not a trailer: Co-authored-by");
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
                        });

        assertThat(check.check("RANGE", false)).isEqualTo(Stop.UNANSWERED);
        assertThat(stderr()).contains("fatal: bad revision 'RANGE'");
    }

    @Test
    void aRangeThatGitWouldReadAsAnOptionIsNotRun() {
        // 'git log --output=FILE' writes a file: a range from a workflow must not become an option.
        assertThat(
                        new CheckCommits(stream(out), stream(err), this::fakeLog)
                                .check("--output=/tmp/x", false))
                .isEqualTo(Stop.UNANSWERED);
        assertThat(askedRanges).as("git was not run").isEmpty();
    }

    @Tag("integration")
    @Test
    void readsWhatGitItselfWrites() throws IOException, InterruptedException {
        git("init", "-q", "-b", "main");
        write("README.md", "x\n");
        git("add", "README.md");
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
                                CheckCommits.Git.in(directory, isolated()))
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
        final CheckCommits.Git git = CheckCommits.Git.in(directory, isolated());
        assertThat(git.count("HEAD")).as("git's own count of the range").hasValue(2);
        assertThat(git.holds(git("rev-parse", "HEAD").strip()))
                .as("a commit the checkout holds, asked of git itself")
                .isTrue();
        assertThat(git.holds("0".repeat(40))).as("a commit it does not hold").isFalse();
    }

    // Prevents a workflow's push from being checked over another range than the event names.
    @Test
    void checksThePushTheEventNames() throws IOException {
        final String start = "1".repeat(40);
        final String head = "2".repeat(40);
        commit("a000071", "Read the pins once\n\n" + AGENT);
        commit("a000072", "Read the pins twice\n\nBecause.");
        final FakeGit git = new FakeGit();
        git.held.add(start);
        final Path event =
                Files.writeString(
                        directory.resolve("event.json"), "{\"before\": \"" + start + "\"}");
        final Map<String, String> environment =
                Map.of(
                        "GITHUB_EVENT_PATH", event.toString(),
                        "GITHUB_EVENT_NAME", "push",
                        "GITHUB_REF", "refs/heads/main",
                        "GITHUB_REF_TYPE", "branch",
                        "GITHUB_SHA", head,
                        "GITHUB_REPOSITORY", "porelo-ai/x",
                        "GITHUB_SERVER_URL", "https://github.com");

        final int code = new CheckCommits(stream(out), stream(err), git).checkEvent(environment);

        assertThat(code).as(stderr()).isEqualTo(Stop.REFUSED);
        assertThat(askedRanges)
                .as("the range the event names")
                .containsExactly(start + ".." + head);
        assertThat(out.toString(StandardCharsets.UTF_8))
                .contains("NOTE  the event's commits: " + start + ".." + head + ", from inside");
        assertThat(stderr()).contains("1 fault(s) in 1 of 2 commit(s)");
    }

    // Prevents a number of another repository's prefix from passing where no project.yml stands
    // and no issues/ is read, and the check from asking anything outside the repository.
    @Tag("integration")
    @Test
    void refusesAnotherRepositorysNumberWithNoProjectFileAndNothingReached() throws Exception {
        git("init", "-q", "-b", "main");
        write("README.md", "x\n");
        git("add", "README.md");
        git("commit", "-q", "-m", "Keep a page", "-m", AGENT);
        // What the tracker's lookup once followed: a porelo-ai origin and a named project file.
        git("remote", "add", "origin", "https://github.com/porelo-ai/porelo-buildtools.git");
        final String clean = git("rev-parse", "--short", "HEAD").strip();
        git("commit", "-q", "--allow-empty", "-m", "Close HO12 at last", "-m", AGENT);
        final String numbered = git("rev-parse", "--short", "HEAD").strip();

        final Process process = checkCommitsProcess("HEAD").redirectErrorStream(true).start();
        final String output =
                new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);

        assertThat(process.waitFor()).as(output).isEqualTo(Stop.REFUSED);
        assertThat(output)
                .as("the old id refused by its shape alone, the clean commit passed")
                .contains(numbered + "  names a requirement number: HO12")
                .contains("1 fault(s) in 1 of 2 commit(s)")
                .doesNotContain(clean + "  ");
    }

    /** check-commits as the program runs it, in the test's repository, with no outside config. */
    private ProcessBuilder checkCommitsProcess(String range) {
        final var builder =
                new ProcessBuilder(
                                Path.of(System.getProperty("java.home"), "bin/java").toString(),
                                "-XX:-UsePerfData",
                                // Every address outside goes to a port nothing answers on.
                                "-Dhttp.proxyHost=127.0.0.1",
                                "-Dhttp.proxyPort=9",
                                "-Dhttps.proxyHost=127.0.0.1",
                                "-Dhttps.proxyPort=9",
                                "-cp",
                                System.getProperty("java.class.path"),
                                "org.fuin.porelo.release.Main",
                                "check-commits",
                                range)
                        .directory(directory.toFile());
        builder.environment().putAll(isolated());
        builder.environment().put("PORELO_PROJECT_FILE", "https://127.0.0.1:9/project.yml");
        return builder;
    }

    // Prevents a message holding git's separators from forging a second commit or hiding its body.
    @Test
    void givesNoAnswerWhenTheLogSplitsIntoMoreCommitsThanGitCounts() {
        commit(
                "a000073",
                "Read the pins once\n\u001eabc\u001fA\u001fa@example.org\u001fA\u001fa@example.org\u001fClean");
        final FakeGit git = new FakeGit();
        git.counted = 1;

        assertThat(new CheckCommits(stream(out), stream(err), git).check("HEAD", false))
                .as(stderr())
                .isEqualTo(Stop.UNANSWERED);
        assertThat(stderr()).contains("splits into 2 commit(s) where git counts 1");
    }

    /** A git that answers from the commits the test wrote, the count and the commits it holds. */
    private final class FakeGit implements CheckCommits.Git {

        private final Set<String> held = new java.util.HashSet<>();

        private long counted = -1;

        @Override
        public String log(String range) {
            return fakeLog(range);
        }

        @Override
        public java.util.OptionalLong count(String range) {
            return counted < 0
                    ? java.util.OptionalLong.empty()
                    : java.util.OptionalLong.of(counted);
        }

        @Override
        public boolean holds(String hash) {
            return held.contains(hash);
        }
    }

    private void write(String name, String text) throws IOException {
        final Path file = directory.resolve(name);
        Files.createDirectories(file.getParent());
        Files.writeString(file, text, StandardCharsets.UTF_8);
    }

    private int check() {
        return new CheckCommits(stream(out), stream(err), this::fakeLog).check("RANGE", false);
    }

    private int checkOutside() {
        return new CheckCommits(stream(out), stream(err), this::fakeLog).check("RANGE", true);
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
