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

class CheckCitationsTest {

    private static final String FRONTEND =
            "https://github.com/porelo-ai/porelo-frontend/blob/main/issues/README.md";

    /** Holds the repository and, beside it, the checkouts, so that nothing lands outside it. */
    @TempDir Path folder;

    private Path root;

    private final ByteArrayOutputStream out = new ByteArrayOutputStream();

    private final ByteArrayOutputStream err = new ByteArrayOutputStream();

    @BeforeEach
    void aRepositoryWithTwoIssues() throws IOException {
        root = folder.resolve("porelo-sample");
        write("README.md", "# A repository\n");
        write(
                "issues/workflow.yml",
                Files.readString(Path.of("../stories/src/test/resources/workflow.yml")));
        write(
                "issues/README.md",
                "## Idea\n| <a id=\"b10\"></a>[B10](B10-A-Thing.md) | a thing |\n"
                        + "| <a id=\"b11\"></a>[B11](B11-Another.md) | another |\n");
        write("issues/B10-A-Thing.md", "# B10 - A Thing\n");
        write("issues/B11-Another.md", "# B11 - Another\n");
    }

    // Own-index validation must not permit a missing contract or an invented state.
    // Prose examples must not invent durable issue rows or demand row anchors.
    // Prefix consumers such as commit checks must receive the checked read failure they handle.
    @Test
    void anInvalidIndexKeepsThePrefixReadersIoContract() throws IOException {
        write("issues/README.md", "## Alien\n| # | Size | Title |\n");
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> CheckCitations.issuePrefixes(root))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("Alien")
                .hasMessageContaining("issues/README.md:1");
    }

    @Test
    void proseExamplesSupplyNeitherRowsNorIssueAnchors() throws Exception {
        write(
                "issues/README.md",
                Files.readString(root.resolve("issues/README.md"))
                        + "\n## Examples\n| B99 | An unanchored prose example |\n| <a id=\"b98\"></a>B98 | Another prose example |\n");
        final IssueLinks links = IssueLinks.ownOnly();
        links.read(root, List.of(root.resolve("issues/README.md")), java.util.Set.of("B"));
        assertThat(links.rows).containsExactlyInAnyOrder("B10", "B11");
        assertThat(links.unanchored).isEmpty();
        write("issues/B10-A-Thing.md", "After [B98](README.md#b98).\n");
        assertThat(check()).isEqualTo(Stop.REFUSED);
        assertThat(stderr())
                .contains("README.md#b98")
                .doesNotContain("an index row that does not open");
    }

    @Test
    void unknownStateTablesAreCitationFaultsRatherThanReadFailures() throws IOException {
        write("issues/README.md", "# Issues\n\n## Alien\n| # | Size | Title |\n|---|---|---|\n");
        assertThat(check()).isEqualTo(Stop.REFUSED);
        assertThat(stderr())
                .contains("issues/README.md:3", "Alien", "workflow.yml")
                .doesNotContain("could not read");
    }

    @Test
    void ownIndexRequiresItsWorkflowAndKnownStates() throws IOException {
        write(
                "issues/README.md",
                "## Idea\n| <a id=\"b10\"></a>[B10](B10-A-Thing.md) | S | - | Thing |\n");
        final Path workflow = root.resolve("issues/workflow.yml");
        Files.deleteIfExists(workflow);
        assertThat(check()).isNotZero();
        assertThat(stderr()).contains(workflow.toString());
        write("issues/workflow.yml", "states:\n  - {name: Idea, closed: false}\nphases: []\n");
        write(
                "issues/README.md",
                "## Alien\n| # | Size | Blocked by | What it covers |\n| <a id=\"b10\"></a>[B10](B10-A-Thing.md) | S | - | Thing |\n");
        assertThat(check()).isNotZero();
        assertThat(stderr()).contains("Alien", "issues/README.md:1", "workflow.yml");
    }

    @Test
    void skipsGeneratedStageRecordsButStillChecksHandwrittenStatsNotes() throws IOException {
        write("stats/B10.jsonl", StageFixtures.closed("B10"));
        assertThat(check()).as(stderr()).isZero();
        assertThat(stdout())
                .as("generated records are counted")
                .contains("1 generated file(s) skipped");
        write("stats/notes.md", "B99\n");
        assertThat(check()).as("stats prose still obeys citation rules").isEqualTo(Stop.REFUSED);
        assertThat(stderr())
                .as("the handwritten citation is identified")
                .contains("stats/notes.md:1 -> B99");
    }

    @Test
    void skipsGeneratedPlanPagesAndCountsThem() throws IOException {
        write("plan/next/plan.yml", "release: next\nsprints: []\n");
        write(
                "plan/next/README.md",
                "<!-- Generated by porelo-release plan render; do not edit. -->\n"
                        + "B99 [gone](../../issues/B99-Gone.md) B98 (issues/README.md)\n");
        assertThat(check()).as("generated issue metadata is exempt: %s", stderr()).isZero();
        assertThat(stdout())
                .as("the exemption is visible in the summary")
                .contains("1 generated file(s) skipped");
    }

    @Test
    void skipsTheStoriesAsGeneratedAndTakesAStorysNumberWhereTheStoryIs() throws IOException {
        write(
                "stories/ST12-A-Story.md",
                "# ST12 — A Story\n\n**Issues:**\n\n- [porelo#B99](https://github.com/porelo-ai/porelo/"
                        + "blob/main/issues/README.md#b99) (Review)\n\n## Data\n\n```json\n"
                        + "{\"id\": \"ST12\", \"issues\": [\"porelo#B99\"]}\n```\n");
        write(
                "stories/README.md",
                "# Stories\n\nWritten by `porelo-release stories index` from each story's head.\n\n"
                        + "| [ST12](ST12-A-Story.md) | B:1 | B98 |\n");
        write("doc/next.md", "The next step is ST12.\n");

        assertThat(check()).as("a story names issues by design: %s", stderr()).isZero();
        assertThat(stdout()).contains("2 generated file(s) skipped");

        write("doc/next.md", "The next step is ST13.\n");
        assertThat(check()).as("no story ST13").isEqualTo(Stop.REFUSED);
        assertThat(stderr()).contains("doc/next.md:1 -> ST13");
    }

    @Test
    void checksAFileInStoriesThatIsNoStory() throws IOException {
        write("stories/notes.md", "B99\n");
        write("stories/ST12-Without-Head.md", "# ST12 — Without Head\n\nB98\n");

        assertThat(check()).isEqualTo(Stop.REFUSED);
        assertThat(stderr())
                .contains("stories/notes.md:1 -> B99", "stories/ST12-Without-Head.md:3 -> B98");
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(
            strings = {
                "",
                "\n<!-- Generated by porelo-release plan render; do not edit. -->\n",
                "<!-- Generated by porelo-release plan render; do not edit. --> extra\n"
            })
    void checksPagesWithoutTheExactFirstLineMarker(String prefix) throws IOException {
        write("plan/next/plan.yml", "release: next\nsprints: []\n");
        write("plan/next/README.md", prefix + "B99 [gone](../../issues/B99-Gone.md)\n");
        assertThat(check())
                .as("only the exact first line grants exemption")
                .isEqualTo(Stop.REFUSED);
        assertThat(stderr())
                .as("both citation checks still inspect the page")
                .contains("-> B99", "-> ../../issues/B99-Gone.md");
    }

    @Test
    void aMarkerWithoutABesidePlanCannotHideHandwrittenCitations() throws IOException {
        write(
                "doc/README.md",
                PlanRender.GENERATED_MARKER + "\nB99 [gone](../issues/B99-Gone.md)\n");
        assertThat(check())
                .as("a marker alone cannot bypass citation checks")
                .isEqualTo(Stop.REFUSED);
        assertThat(stderr())
                .as("both citation checks inspect handwritten notes")
                .contains("-> B99", "-> ../issues/B99-Gone.md");
    }

    @Test
    void aBesideFileThatIsNoPlanCannotHideHandwrittenCitations() throws IOException {
        write("doc/plan.yml", "");
        write(
                "doc/README.md",
                PlanRender.GENERATED_MARKER + "\nB99 [gone](../issues/B99-Gone.md)\n");
        assertThat(check())
                .as("an empty YAML file beside it is no plan, so the page is checked")
                .isEqualTo(Stop.REFUSED);
        assertThat(stderr()).contains("-> B99", "-> ../issues/B99-Gone.md");
    }

    @Test
    void acceptsIssuesCitingEachOtherByALinkToTheRow() throws IOException {
        write("issues/B11-Another.md", "Needs **[B10](README.md#b10)**.\n");

        assertThat(check()).as(stderr()).isEqualTo(0);
        assertThat(stdout()).contains("citations in").contains("2 issue(s)");
    }

    @Test
    void acceptsTheIndexNamingIssueFilesAndAnIssueItsDesign() throws IOException {
        // The index names files by being one; a design is deleted together with its issue.
        write(
                "issues/B10-A-Thing.md",
                "Needs [B11](README.md#b11) and [its design](../issues/B11_design.md).\n");

        assertThat(check()).as(stderr()).isEqualTo(0);
    }

    @Test
    void refusesALinkToAnIssueFileFromAPage() throws IOException {
        write("doc/guide.md", "Line one\nSee [the thing](../issues/B10-A-Thing.md#why).\n");

        assertThat(check()).isEqualTo(Stop.REFUSED);
        assertThat(stderr()).contains("doc/guide.md:2 -> ../issues/B10-A-Thing.md#why");
    }

    @Test
    void refusesALinkToADesignDocument() throws IOException {
        write("README.md", "How: [design](issues/B10-A-Thing_design.md).\n");

        assertThat(check()).isEqualTo(Stop.REFUSED);
        assertThat(stderr()).contains("README.md:1 -> issues/B10-A-Thing_design.md");
    }

    @Test
    void acceptsALinkToAStandingDocumentUnderIssues() throws IOException {
        write("doc/guide.md", "Compared in [this](../issues/Credential-Types-Compared.md).\n");

        assertThat(check()).as(stderr()).isEqualTo(0);
    }

    @Test
    void refusesAPointerToAnIssueThatIsGoneEvenWhenWrappedAndBold() throws IOException {
        write("issues/B11-Another.md", "Waits on **XY12**\n  ([index](README.md)).\n");

        assertThat(check()).isEqualTo(Stop.REFUSED);
        assertThat(stderr()).contains("issues/B11-Another.md:1 -> XY12");
    }

    @Test
    void leavesAPointerIntoAnotherRepositorysIndexAlone() throws IOException {
        // Another repository's index is not read here, so a pointer into it is not judged.
        write(
                "issues/B11-Another.md",
                "Blocked by F31"
                        + " ([index](https://github.com/x/frontend/blob/main/issues/README.md)).\n");

        assertThat(check()).as(stderr()).isEqualTo(0);
    }

    @Test
    void refusesANumberOfThisRepositorysOwnAmongTheIssuesThatNamesNoIssue() throws IOException {
        // The index row or the "blocked by" its deletion missed.
        write(
                "issues/README.md",
                "## Idea\n| <a id=\"b10\"></a>[B10](B10-A-Thing.md) | B12 | waits on the interface's consent"
                        + " |\n");

        assertThat(check()).isEqualTo(Stop.REFUSED);
        assertThat(stderr()).contains("issues/README.md:2 -> B12");
    }

    @Test
    void refusesAnIndexRowWithoutItsAnchor() throws IOException {
        write(
                "issues/README.md",
                "## Idea\n| <a id=\"b10\"></a>[B10](B10-A-Thing.md) | a thing |\n"
                        + "| [B11](B11-Another.md) | another |\n"
                        + "| <a id=\"b10\"></a>B12 | finished, named wrong |\n");

        assertThat(check()).isEqualTo(Stop.REFUSED);
        assertThat(stderr())
                .contains("an index row that does not open with the anchor of its number")
                .contains("issues/README.md:3 -> B11")
                .contains("issues/README.md:4 -> B12");
    }

    @Test
    void refusesANumberAmongTheIssuesThatIsNotALinkToItsRow() throws IOException {
        write(
                "issues/B11-Another.md",
                "Follows B10.\nAnd [B10](B10-A-Thing.md#why), and [B10](README.md).\n");

        assertThat(check()).isEqualTo(Stop.REFUSED);
        assertThat(stderr())
                .contains("an issue number under issues/ that is not a link to its row")
                .contains("issues/B11-Another.md:1 -> B10")
                .contains("issues/B11-Another.md:2 -> B10-A-Thing.md#why")
                .contains("issues/B11-Another.md:2 -> README.md");
    }

    @Test
    void refusesALinkToARowThatIsNotThere() throws IOException {
        write(
                "issues/B11-Another.md",
                "See [B10](README.md#b11) and [the other](README.md#b12).\n");

        assertThat(check()).isEqualTo(Stop.REFUSED);
        assertThat(stderr())
                .contains("a link to an index row that is not there")
                .contains("issues/B11-Another.md:1 -> B10 linked to README.md#b11")
                .contains("issues/B11-Another.md:1 -> README.md#b12");
    }

    @Test
    void leavesHeadingsCodeAndAMarkedLineAlone() throws IOException {
        write(
                "issues/B11-Another.md",
                "# B11 - Another\n\nThe form is `[B10](README.md#b10)`.\n\n```\nB10\n```\n"
                        + "B10 as the parser reads it - not-a-citation\n");

        assertThat(check()).as(stderr()).isEqualTo(0);
    }

    @Test
    void acceptsALinkToARowOnTheSamePage() throws IOException {
        write(
                "issues/README.md",
                "## Idea\n| <a id=\"b10\"></a>[B10](B10-A-Thing.md) | a thing |\n"
                        + "| <a id=\"b11\"></a>[B11](B11-Another.md) | after [B10](#b10), not"
                        + " [B12](#b12) |\n");

        assertThat(check()).isEqualTo(Stop.REFUSED);
        assertThat(stderr()).contains("issues/README.md:3 -> #b12").doesNotContain("-> #b10");
    }

    @Test
    void acceptsAFinishedRowWhoseFileIsGone() throws IOException {
        write(
                "issues/README.md",
                "## Idea\n| <a id=\"b10\"></a>[B10](B10-A-Thing.md) | a thing |\n"
                        + "| <a id=\"b11\"></a>[B11](B11-Another.md) | another |\n\n## Finished\n\n"
                        + "| <a id=\"b09\"></a>B09 | Built: the first thing. |\n");
        write("issues/B11-Another.md", "After [B09](README.md#b09).\n");

        assertThat(check()).as(stderr()).isEqualTo(0);
    }

    @Test
    void readsNothingOutsideItsOwnRepository() throws IOException {
        // Another repository's state must not decide this one's check, nor need its checkout.
        theProjectIn(folder);
        Files.createDirectories(folder.resolve("porelo-frontend/issues"));
        Files.writeString(
                folder.resolve("porelo-frontend/issues/README.md"),
                "| <a id=\"f31\"></a>F31 | x |\n");
        write(
                "issues/B11-Another.md",
                "Its interface is [F31]("
                        + FRONTEND
                        + "#f31), not [F32]("
                        + FRONTEND
                        + "#f32).\nIt waits for F31 and PJ12, after [B10](README.md#b10).\n");

        assertThat(check())
                .as("the project and the index beside it are not opened: %s", stderr())
                .isZero();
        assertThat(stdout())
                .as("what is left to the check across repositories is said once")
                .containsOnlyOnce("NOTE  not checked here:")
                .contains("2 link(s) into another repository's index");
    }

    @Test
    void aFixturesIssueNumberIsNoneOfThisRepositorysOwn() throws IOException {
        // A stale mention must not pass because a test's fixture happens to hold that number.
        write(
                "src/test/resources/sample/issues/README.md",
                "| <a id=\"b12\"></a>[B12](B12-A-Fixture.md) | a fixture |\n");
        write("src/test/resources/sample/issues/B12-A-Fixture.md", "# B12 - A Fixture\n");
        write("issues/B11-Another.md", "It follows B12 ([index](README.md)).\nAnd B12 alone.\n");

        assertThat(check()).as(stderr()).isEqualTo(Stop.REFUSED);
        assertThat(stderr())
                .as("the pointer and the bare number name no issue of this repository")
                .contains("a pointer to an issue that no longer exists")
                .contains("the mention its deletion missed")
                .contains("issues/B11-Another.md:2 -> B12")
                .doesNotContain("not a link to its row");
    }

    @Test
    void refusesANumberOfAPrefixOnlyFinishedRowsCarryThatHasNoRow() throws IOException {
        // A number no row names cannot be linked, and would send a reader nowhere.
        write(
                "issues/README.md",
                "## Idea\n| <a id=\"b10\"></a>[B10](B10-A-Thing.md) | a thing |\n"
                        + "| <a id=\"b11\"></a>[B11](B11-Another.md) | another |\n\n## Finished\n\n"
                        + "| <a id=\"xy05\"></a>XY05 | done |\n");
        write("issues/B11-Another.md", "After [XY05](README.md#xy05), and XY06.\n");

        assertThat(check()).as(stderr()).isEqualTo(Stop.REFUSED);
        assertThat(stderr())
                .contains("a number no index has a row for")
                .contains("issues/B11-Another.md:1 -> XY06")
                .doesNotContain("-> XY05");
    }

    @Test
    void stillRefusesABrokenLinkWithinItsOwnIndexes() throws IOException {
        // The check within the repository stays whole when the crossing is switched off.
        write("issues/B11-Another.md", "After [B10](README.md#b12) and B10.\n");

        assertThat(check()).as(stderr()).isEqualTo(Stop.REFUSED);
        assertThat(stderr())
                .contains("issues/B11-Another.md:1 -> B10 linked to README.md#b12")
                .contains("issues/B11-Another.md:1 -> B10");
    }

    @Test
    void aFixturesIssueDoesNotMakeAnotherRepositorysPrefixItsOwn() throws IOException {
        write(
                "src/test/resources/sample/issues/README.md",
                "| <a id=\"f05\"></a>[F05](F05-A-Fixture.md) | a fixture |\n");
        write("src/test/resources/sample/issues/F05-A-Fixture.md", "# F05 - A Fixture\n");
        write("issues/B11-Another.md", "It waits for F31.\n");

        assertThat(check())
                .as("another repository's number is no deletion this one missed: %s", stderr())
                .isZero();
    }

    @Test
    void acceptsALinkNamedLikeAnIssueThatIsNone() throws IOException {
        write("issues/B11-Another.md", "Its digest is [S256](https://example.org/digest).\n");

        assertThat(check()).as(stderr()).isZero();
    }

    @Test
    void refusesAnIssueNumberInASource() throws IOException {
        write("src/main/java/A.java", "class A {\n    // Done for B10.\n}\n");
        write("lib/a.dart", "// see PJ19\n");

        assertThat(check()).isEqualTo(Stop.REFUSED);
        assertThat(stderr())
                .contains("src/main/java/A.java:2 -> B10")
                .contains("lib/a.dart:1 -> PJ19");
    }

    @Test
    void refusesAnIssueNumberInEveryTextFileOutsideTheIssues() throws IOException {
        write("README.md", "Built for B10.\n");
        write("CHANGELOG.md", "- B11 done\n");
        write("src/test/resources/fixture.json", "{\"note\": \"from PJ18\"}\n");
        write("notes.txt", "see SL04\n");
        write("dist/control", "Description: B10\n");
        write("Dockerfile", "# CC23\nFROM scratch\n");

        assertThat(check()).isEqualTo(Stop.REFUSED);
        assertThat(stderr())
                .contains(
                        "README.md:1 -> B10",
                        "CHANGELOG.md:1 -> B11",
                        "src/test/resources/fixture.json:1 -> PJ18",
                        "notes.txt:1 -> SL04",
                        "dist/control:1 -> B10",
                        "Dockerfile:1 -> CC23");
    }

    @Test
    void skipsABinaryFileAndCountsIt() throws IOException {
        // Valid UTF-8 but for the NUL, so only the NUL makes it binary.
        Files.write(root.resolve("image.png"), new byte[] {'P', 'N', 'G', 0, 'B', '1', '0'});
        Files.write(root.resolve("latin1.txt"), new byte[] {'B', '1', '0', ' ', (byte) 0xE9});

        assertThat(check()).as(stderr()).isEqualTo(0);
        assertThat(stdout()).contains("2 binary and 0 generated file(s) skipped");
    }

    @Test
    void skipsAnImagesPathDataAndALockFilesHashes() throws IOException {
        write("doc/images/logo.svg", "<path d=\"M20 10 C12 4 B10 8\"/>\n");
        write("src/main/npm/package-lock.json", "{\"integrity\": \"sha512-ab+X768/F77==\"}\n");
        write("pubspec.lock", "sha256: S03\n");

        assertThat(check()).as(stderr()).isEqualTo(0);
        assertThat(stdout()).contains("3 generated file(s) skipped");
    }

    @Test
    void refusesAnIssueNumberInAPublishedPageButNotInAnIssue() throws IOException {
        write("doc/guide.md", "This came from B10.\n");
        write("issues/B11-Another.md", "Follows [B10](README.md#b10).\n");

        assertThat(check()).isEqualTo(Stop.REFUSED);
        assertThat(stderr())
                .contains("doc/guide.md:1 -> B10")
                .doesNotContain("issues/B11-Another.md");
    }

    @Test
    void acceptsTheNamesOfAnIssueNumbersShapeThatAreSomethingElse() throws IOException {
        write("src/main/java/A.java", "class A { String method = \"S256\"; }\n");

        assertThat(check()).as(stderr()).isEqualTo(0);
    }

    @Test
    void acceptsALintersCodesButNotANumberAfterThem() throws IOException {
        write("tests/test_a.py", "import a  # noqa: E402, W503\n# B10 for later\n");

        assertThat(check()).isEqualTo(Stop.REFUSED);
        assertThat(stderr())
                .contains("tests/test_a.py:2 -> B10")
                .doesNotContain("E402")
                .doesNotContain("W503");
    }

    @Test
    void acceptsALineThatSaysItHoldsTheShapeOnPurpose() throws IOException {
        write(
                "src/test/java/T.java",
                "String prose = \"see B10 and PJ18\"; // not-a-citation: what the filter reads\n"
                        + "// B11 is cited here\n");

        assertThat(check()).isEqualTo(Stop.REFUSED);
        assertThat(stderr()).contains("src/test/java/T.java:2 -> B11").doesNotContain("PJ18");
    }

    @Test
    void acceptsAWholeFileTheExemptListNames() throws IOException {
        write(
                "src/test/resources/corpus.txt",
                "Run 283 had 2 red in B76 and B85.\nsee MX12 and F80\n");
        write(
                CheckCitations.EXEMPT,
                "# measured prose the filter reads\nsrc/test/resources/corpus.txt\n");

        assertThat(check()).as(stderr()).isEqualTo(0);
        assertThat(stdout()).contains("1 exempt");
    }

    @Test
    void refusesAnExemptionForAFileThatIsNotThere() throws IOException {
        write(CheckCitations.EXEMPT, "src/test/resources/gone.txt  # it was deleted\n");

        assertThat(check()).isEqualTo(Stop.REFUSED);
        assertThat(stderr()).contains(CheckCitations.EXEMPT + " names src/test/resources/gone.txt");
    }

    @Test
    void readsOnlyWhatGitTracksInACheckout() throws Exception {
        write("doc/guide.md", "Fine.\n");
        git("init", "-q");
        git("add", "README.md", "issues", "doc");
        write("suite.log", "a log a tool left: B99\n");

        assertThat(check()).as(stderr()).isEqualTo(0);

        git("add", "suite.log");
        assertThat(check()).isEqualTo(Stop.REFUSED);
        assertThat(stderr()).contains("suite.log:1 -> B99");
    }

    @Test
    void ignoresBuildOutput() throws IOException {
        write("target/classes/A.java", "// B99\n");
        write("build/out.md", "[x](issues/B99-Gone.md)\n");

        assertThat(check()).as(stderr()).isEqualTo(0);
    }

    @Test
    void refusesARootWithNoPageAtAll(@TempDir Path empty) {
        assertThat(new CheckCitations(stream(out), stream(err)).check(empty))
                .isEqualTo(Stop.REFUSED);
        assertThat(stderr()).contains("no Markdown page");
    }

    @Test
    @Tag("documents")
    void thisRepositoryCitesNoIssueThatGoesStale() {
        // check-citations is this repository's own, so this test is the check.
        assertThat(new CheckCitations(stream(out), stream(err)).check(Path.of("..")))
                .as(stderr())
                .isEqualTo(0);
    }

    @Test
    void cannotAnswerForARootThatDoesNotExist() {
        assertThat(new CheckCitations(stream(out), stream(err)).check(root.resolve("missing")))
                .isEqualTo(Stop.UNANSWERED);
    }

    @Test
    void acceptsBriefsBesideTheirIssue() throws IOException {
        write("issues/B10.sub/builder.md", "Builds [B10](../README.md#b10).\n");
        write("issues/B10.sub/builder-2.md", "Also builds [B10](../README.md#b10).\n");
        write("issues/B10.sub/tester.md", "Tests it.\n");
        write("issues/B10.sub/reader-12.md", "Reads it.\n");

        assertThat(check()).as(stderr()).isEqualTo(0);
        assertThat(stdout()).contains("2 issue(s)");
    }

    @Test
    void aBriefWithoutItsIssueIsRefused() throws IOException {
        // B13 is finished: its row stays, its file is gone, and its briefs must have gone with it.
        write(
                "issues/README.md",
                Files.readString(root.resolve("issues/README.md"))
                        + "| <a id=\"b13\"></a>B13 | a finished thing |\n");
        write("issues/B13.sub/tester.md", "Tests it.\n");

        assertThat(check()).isEqualTo(Stop.REFUSED);
        assertThat(stderr()).contains("issues/B13.sub/tester.md");
    }

    @Test
    void aSubDirectoryBesideNoIssueIsRefused() throws IOException {
        write("issues/B12.sub/builder.md", "Builds it.\n");

        assertThat(check()).isEqualTo(Stop.REFUSED);
        assertThat(stderr()).contains("issues/B12.sub/builder.md");
    }

    @Test
    void aLastingPageMayNotLinkIntoABrief() throws IOException {
        write("issues/B10.sub/builder.md", "Builds it.\n");
        write("doc/guide.md", "See [the brief](../issues/B10.sub/builder.md).\n");

        assertThat(check()).isEqualTo(Stop.REFUSED);
        assertThat(stderr()).contains("doc/guide.md:1 -> ../issues/B10.sub/builder.md");
    }

    @Test
    void aBriefOfAnotherNameIsRefused() throws IOException {
        write("issues/B10.sub/planner.md", "Plans it.\n");
        write("issues/B10.sub/builder-1.md", "Builds it.\n");
        write("issues/B10.sub/more/builder.md", "Builds more.\n");
        write("issues/B10.sub/builder.txt", "Builds it.\n");

        assertThat(check()).isEqualTo(Stop.REFUSED);
        assertThat(stderr())
                .contains("issues/B10.sub/planner.md")
                .contains("issues/B10.sub/builder-1.md")
                .contains("issues/B10.sub/more/builder.md")
                .contains("issues/B10.sub/builder.txt");
    }

    @Test
    void aBriefsOwnIssueNumberMustBeALink() throws IOException {
        write("issues/B10.sub/builder.md", "Build B10 first.\n");

        assertThat(check()).isEqualTo(Stop.REFUSED);
        assertThat(stderr()).contains("issues/B10.sub/builder.md:1 -> B10");
    }

    private int check() {
        out.reset();
        err.reset();
        return new CheckCitations(stream(out), stream(err)).check(root);
    }

    /**
     * The project's repository in a directory of checkouts: its repositories' prefixes and index.
     */
    private Path theProjectIn(Path checkouts) throws IOException {
        Files.createDirectories(checkouts.resolve("porelo-project/issues"));
        Files.writeString(
                checkouts.resolve("porelo-project/project.yml"),
                "project:\n"
                        + "  upstream: \"git@github.com:porelo-ai/porelo-project.git\"\n"
                        + "repositories:\n"
                        + "  core:\n"
                        + "    upstream: \"git@github.com:porelo-ai/porelo.git\"\n"
                        + "    issues: [\"B\"]\n"
                        + "  frontend:\n"
                        + "    upstream: \"git@github.com:porelo-ai/porelo-frontend.git\"\n"
                        + "    issues: [\"F\"]\n"
                        + "  intellij:\n"
                        + "    upstream: \"git@github.com:porelo-ai/porelo-intellij.git\"\n"
                        + "    issues: [\"IJ\"]\n"
                        + "  sums:\n"
                        + "    upstream: \"git@github.com:porelo-ai/porelo-sums.git\"\n"
                        + "    issues: [\"S\"]\n");
        Files.writeString(
                checkouts.resolve("porelo-project/issues/README.md"),
                "| <a id=\"pj12\"></a>PJ12 | a requirement |\n");
        return checkouts;
    }

    private void git(String... args) throws Exception {
        final List<String> command = new ArrayList<>(List.of("git", "-C", root.toString()));
        command.addAll(List.of(args));
        final ProcessBuilder builder = new ProcessBuilder(command).redirectErrorStream(true);
        builder.environment().put("GIT_CONFIG_GLOBAL", "/dev/null");
        builder.environment().put("GIT_CONFIG_NOSYSTEM", "1");
        final Process git = builder.start();
        final String said = new String(git.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertThat(git.waitFor()).as("git %s: %s", String.join(" ", args), said).isEqualTo(0);
    }

    private void write(String name, String text) throws IOException {
        final Path file = root.resolve(name);
        Files.createDirectories(file.getParent());
        Files.writeString(file, text, StandardCharsets.UTF_8);
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
