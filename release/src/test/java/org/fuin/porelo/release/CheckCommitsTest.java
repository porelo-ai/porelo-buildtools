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
                        + "Bumps the github-actions group with 3 updates in the / directory: actions/checkout.\n\n"
                        + "Signed-off-by: dependabot[bot] <support@github.com>");
        commit(
                "a000016",
                "dependabot[bot]",
                "49699333+dependabot[bot]@users.noreply.github.com",
                "dependabot[bot]",
                "49699333+dependabot[bot]@users.noreply.github.com",
                "Bump actions/checkout from 6.0.1 to 6.0.2 in the github-actions group across 1 directory");

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
                "Merge pull request #41 from porelo-ai/dependabot/github_actions/github-actions-6f1c2d\n\n"
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
                "Merge pull request #41 from porelo-ai/dependabot/github_actions/github-actions-6f1c2d");

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
                                + "  below the subject, a line that is not a trailer: Because it was slow.")
                .doesNotContain(good);
        assertThat(stderr())
                .as("one fault, so the fields were split where git put them")
                .contains("1 fault(s)");
    }

    @Test
    void takesThePrefixesOfTheIssuesTheirRowsAndTheIndexesTheyLink() throws IOException {
        // Without a prefix a requirement number of that repository goes through as a word.
        write(
                "issues/README.md",
                "| <a id=\"bt17\"></a>[BT17](BT17-A.md) | x |\n| <a id=\"xy05\"></a>XY05 | done |\n");
        write(
                "issues/BT17-A.md",
                "As [PJ27](https://github.com/porelo-ai/porelo-project/blob/main/issues/README.md#pj27)"
                        + " says.\n");
        final FakeWeb web =
                new FakeWeb()
                        .serve(
                                "https://raw.githubusercontent.com/porelo-ai/porelo-project/main/issues/README.md",
                                "| <a id=\"pj27\"></a>[PJ27](PJ27-A.md) | x |\n");

        assertThat(CheckCitations.issuePrefixes(directory, web, null, stream(out)))
                .as("its own file's, a finished row's, and the linked index's")
                .containsExactlyInAnyOrder("BT", "XY", "PJ");
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
                "Michael Schnell",
                "michael@example.org",
                "Michael Schnell",
                "michael@example.org",
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
