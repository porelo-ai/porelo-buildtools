package org.fuin.porelo.release;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Guards plans against losing references, accepting untested work, or publishing stale pages. */
class PlanCommandsTest {
    @TempDir Path directory;
    private Path root;
    private Path file;
    private final ByteArrayOutputStream errors = new ByteArrayOutputStream();

    @BeforeEach
    void aProjectWithACheckoutBesideIt() throws IOException {
        root = Files.createDirectories(directory.resolve("porelo-project"));
        file = root.resolve("plan/next.yml");
        copyTree(Path.of("src/test/resources/plans/minimal"), directory);
        write(
                root.resolve("project.yml"),
                """
                project:
                  upstream: git@github.com:porelo-ai/porelo-project.git
                repositories:
                  sample:
                    upstream: git@github.com:porelo-ai/porelo-sample.git
                    issues: [SA]
                """);
        write(
                root.resolve("issues/README.md"),
                "# Issues\n\n## Idea\n\n| <a id=\"pj01\"></a>PJ01 | - | - | Release |\n");
    }

    @Test
    void imageReferencesFollowTheWrittenContentWhenThePlanChanges() throws Exception {
        plan(function("One", "issues: [porelo-sample#SA01]"));
        String previous = "";
        for (final String title : java.util.List.of("One", "Changed function")) {
            plan(function(title, "issues: [porelo-sample#SA01]"));
            assertThat(run("render")).as("the changed plan renders: %s", errors).isZero();
            final String markdown = Files.readString(file.resolveSibling("next.md"));
            final var reference =
                    java.util.regex.Pattern.compile("!\\[[^\\n]*]\\(([^)]+)\\)").matcher(markdown);
            assertThat(reference.find()).as("the page links its progress image").isTrue();
            final String name = reference.group(1);
            final byte[] bytes = Files.readAllBytes(file.resolveSibling(name));
            final String hash =
                    java.util.HexFormat.of()
                            .formatHex(
                                    java.security.MessageDigest.getInstance("SHA-256")
                                            .digest(bytes))
                            .substring(0, 8);
            assertThat(name)
                    .as("the reference prevents a cached image from hiding changed progress")
                    .isEqualTo("next/progress-" + hash + ".svg")
                    .isNotEqualTo(previous);
            previous = name;
        }
    }

    @Test
    void imageAltCountsFollowTheLegendAndOmitEmptyStates() throws IOException {
        plan(
                function(
                        "One",
                        "issues: [porelo-sample#SA01, porelo-sample#SA02, porelo-sample#SA03, porelo-sample#SA04, porelo-sample#SA05]"));
        write(
                directory.resolve("porelo-sample/issues/README.md"),
                """
                ## Backlog
                | <a id="sa01"></a>SA01 | S | - | One |
                ## Finished
                | <a id="sa02"></a>SA02 | S | - | Two |
                ## Implementing
                | <a id="sa03"></a>SA03 | S | - | Three |
                ## Review
                | <a id="sa04"></a>SA04 | S | - | Four |
                ## Accepted
                | <a id="sa05"></a>SA05 | S | - | Five |
                """);
        assertThat(run("render")).as("mixed states render: %s", errors).isZero();
        assertThat(Files.readString(file.resolveSibling("next.md")))
                .as("the image carries the same progress as the SVG and legend")
                .contains(
                        "![Sprint s: Finished 1, Review 1, Implementing 1, Backlog 1, Accepted 1]("
                                + progressReference()
                                + ")")
                .doesNotContain("Not filed yet / Missing 0", "█", "░");
    }

    @Test
    void markdownFollowsTheHtmlSectionsAndKeepsChipsTogether() throws IOException {
        plan(
                function("One", "issues: [porelo-sample#SA01]\n        notes: [unfiled]")
                        + function("Two", "tested: '2026-02-03'"));
        assertThat(run("render")).as("the shared page structure renders").isZero();
        final String page = Files.readString(file.resolveSibling("next.md"));
        assertThat(page)
                .as("Markdown follows the accepted HTML reading order")
                .containsSubsequence(
                        PlanRender.GENERATED_MARKER + "\n",
                        "# Release next\n\n",
                        "A release\n\n",
                        "*Stand: ",
                        "## Sprint progress\n\n",
                        "](" + progressReference() + ")",
                        PlanTemplate.INTRODUCTION,
                        "## Issues Details\n\n",
                        // A sprint stands in a box of its own, as on the HTML page: one table cell,
                        // Markdown inside.
                        "<table><tr><td>\n\n### Sprint s: Sprint\n\n",
                        "A goal\n\n",
                        "**One ⚪ Not tested**\n\n",
                        "Test: Try it\n\n",
                        "🔲 [SA01](https://github.com/porelo-ai/porelo-sample/blob/main/issues/README.md#sa01 \"porelo-sample#SA01 · Backlog · S · A function from its heading\") ◻️ unfiled",
                        "**Two ✅ Tested 2026-02-03**",
                        "</td></tr></table>\n\n");
        assertThat(page.lines().filter(line -> line.startsWith("![")).count())
                .as("only one progress image is linked")
                .isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "Finished:🟩",
                "Rejected:🟩",
                "Review:🟦",
                "Implementing:🟨",
                "Rework:🟨",
                "Accepted:⬜",
                "Estimated:🟪",
                "Analyzed:⬛",
                "Idea:🟧",
                "Backlog:🔲"
            })
    void chipsUseTheLegendColorForEveryState(String example) throws IOException {
        final String[] parts = example.split(":");
        plan(
                function(
                        "One",
                        "tested: '2026-02-03'\n        issues: [porelo-sample#SA02, porelo-sample#SA99]\n        notes: [unfiled]"));
        final boolean closed = parts[0].equals("Finished") || parts[0].equals("Rejected");
        write(
                directory.resolve("porelo-sample/issues/README.md"),
                "## "
                        + parts[0]
                        + (closed
                                ? "\n| # | Title | What was built |\n|---|---|---|\n| <a id=\"sa02\"></a>SA02 | Indexed title | Built |\n"
                                : "\n| # | Size | Blocked by | What it covers |\n|---|---|---|---|\n| <a id=\"sa02\"></a>SA02 | S | - | Indexed title |\n"));
        assertThat(run("render")).as("every known state renders").isZero();
        assertThat(Files.readString(file.resolveSibling("next.md")))
                .as("the chip's color, label, tooltip and index target match HTML")
                .contains(
                        parts[1]
                                + " [SA02](https://github.com/porelo-ai/porelo-sample/blob/main/issues/README.md#sa02 \"porelo-sample#SA02 · "
                                + parts[0]
                                + " · "
                                + (closed ? "-" : "S")
                                + " · Indexed title\") ◻️ porelo-sample#SA99 ◻️ unfiled");
    }

    @Test
    void altCountsKeepStatesWithTheSameColorSeparateForEachSprint() throws IOException {
        plan(
                function(
                        "One",
                        "tested: '2026-02-03'\n        issues: [porelo-sample#SA01, porelo-sample#SA02, porelo-sample#SA03, porelo-sample#SA04]"));
        write(
                directory.resolve("porelo-sample/issues/README.md"),
                "## Finished\n| <a id=\"sa01\"></a>SA01 | S | - | Done |\n"
                        + "## Rejected\n| <a id=\"sa02\"></a>SA02 | S | - | Declined |\n"
                        + "## Review\n| <a id=\"sa03\"></a>SA03 | S | - | Reviewed |\n"
                        + "## Rework\n| <a id=\"sa04\"></a>SA04 | S | - | Returned |\n");
        Files.writeString(
                file,
                Files.readString(file)
                        + "  - id: other\n    title: 'A [title] <tag>'\n    goal: Another goal\n    functions:\n"
                        + function("Two", "notes: [pending]"));
        assertThat(run("render")).as("two sprints render").isZero();
        assertThat(Files.readString(file.resolveSibling("next.md")))
                .as("alt text reports each sprint and each nonzero state in legend order")
                .contains(
                        "![Sprint s: Finished 1, Rejected 1, Review 1, Rework 1; Sprint other: Not filed yet 1]("
                                + progressReference()
                                + ")");
    }

    @Test
    void markdownLinkTitlesCannotCloseTheChipLink() throws IOException {
        plan(function("One", "issues: [porelo-sample#SA01]"));
        write(
                directory.resolve("porelo-sample/issues/SA01-A-Function.md"),
                "# SA01 — Detail \"quoted\") [escape](https://invalid) & <script>\\tail\n");
        assertThat(run("render")).as("hostile issue details render").isZero();
        assertThat(Files.readString(file.resolveSibling("next.md")))
                .as("quotes, parentheses, entities and backslashes stay inside the title")
                .contains(
                        "[SA01](https://github.com/porelo-ai/porelo-sample/blob/main/issues/README.md#sa01 \"porelo-sample#SA01 · Backlog · S · Detail &quot;quoted&quot;&#41; [escape]&#40;https://invalid&#41; &amp; &lt;script&gt;&#92;tail\")");
    }

    @Test
    void imageAltEscapesMarkdownDelimiters() {
        assertThat(PlanRender.alt("[state]\\<tag>\nnext"))
                .as("alt text cannot close the image or inject markup")
                .isEqualTo("\\[state\\]\\\\&lt;tag&gt; next");
    }

    @Test
    void markdownKeepsInlineFormatting() throws IOException {
        plan(function("One", "issues: [porelo-sample#SA01, porelo-sample#SA02]"));
        Files.writeString(
                file,
                Files.readString(file)
                        .replace(
                                "test: Try it", "test: 'Try `code`, *bold*, _text_ and [labels]'"));
        assertThat(run("render")).as("formatted instructions render").isZero();
        final String page = Files.readString(file.resolveSibling("next.md"));
        assertThat(page)
                .as("Markdown instructions keep their inline syntax")
                .contains("Test: Try `code`, *bold*, _text_ and [labels]");
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "# Heading",
                "- Item",
                "* Item",
                "+ Item",
                "1. Item",
                "![t](https://x/p)",
                "```",
                "~~~",
                "````",
                "~~~~"
            })
    void markdownProtectsBlockStartsAndTableSeparators(String title) throws IOException {
        plan(function(title + " | <tag>", "notes: [planned]"));
        Files.writeString(
                file, Files.readString(file).replace("goal: A release", "goal: '" + title + "'"));
        assertThat(run("render")).as("block-like text renders").isZero();
        final String page = Files.readString(file.resolveSibling("next.md"));
        final String safe = title.startsWith("1.") ? "1\\. Item" : "\\" + title;
        assertThat(page)
                .as("text cannot introduce images, fences, headings, lists or table columns")
                .contains("\n\n" + safe + "\n\n", "**" + safe + " \\| &lt;tag&gt; ⚪ Not tested**");
    }

    @Test
    void aBackslashOfThePlansOwnCannotFreeAnImage() throws IOException {
        plan(function("One", "notes: [planned]"));
        Files.writeString(
                file,
                Files.readString(file).replace("goal: A release", "goal: '\\![t](https://x/p)'"));
        assertThat(run("render")).as("the text renders").isZero();
        // The text's backslash is written escaped, and the image's start after it once more, so
        // CommonMark reads a
        // backslash and an exclamation mark as text rather than an escaped backslash before an
        // image.
        assertThat(Files.readString(file.resolveSibling("next.md")))
                .as("a backslash before an image's start does not free the image")
                .contains("\n\n\\\\\\![t](https://x/p)\n\n");
    }

    @ParameterizedTest
    @ValueSource(strings = {"render", "check"})
    void planDefaultsToForgeEvenWithStaleLocalIndexes(String verb) throws IOException {
        plan(function("One", "issues: [porelo-sample#SA01]"));
        final FakeWeb web = new FakeWeb();
        for (final String repository : java.util.List.of("porelo-project", "porelo-sample")) {
            final Path index = directory.resolve(repository + "/issues/README.md");
            web.serve(
                    "https://raw.githubusercontent.com/porelo-ai/"
                            + repository
                            + "/main/issues/README.md",
                    Files.readString(index));
            Files.writeString(
                    index, "## Stale local state\n| <a id=\"sa01\"></a>SA01 | S | - | Stale |\n");
        }
        assertThat(runWith(web, "render"))
                .as("forge indexes render despite stale local files: %s", errors)
                .isZero();
        assertThat(runWith(web, verb)).as("both verbs use forge main: %s", errors).isZero();
        assertThat(Files.readString(file.resolveSibling("next.md")))
                .as("the page uses the published state and row fallback, not a local issue heading")
                .contains("Backlog", "Row fallback")
                .doesNotContain("Stale", "A function from its heading");
        assertThat(web.asked.stream().map(Object::toString).toList())
                .as("the owning index and sibling index are both fetched from main")
                .contains(
                        "https://raw.githubusercontent.com/porelo-ai/porelo-project/main/issues/README.md",
                        "https://raw.githubusercontent.com/porelo-ai/porelo-sample/main/issues/README.md");
    }

    @Test
    void besideIndexesReadTheSiblingAndNameItInTheRefusal() throws IOException {
        plan(function("One", "issues: [porelo-sample#SA99]"));
        assertThat(runWith(FakeWeb.untouchable(), "render", "--indexes=beside"))
                .as("beside rendering needs no forge")
                .isZero();
        assertThat(runWith(FakeWeb.untouchable(), "check", "--indexes=beside"))
                .as("the sibling's missing row is refused")
                .isEqualTo(Stop.REFUSED);
        assertThat(errors.toString(StandardCharsets.UTF_8))
                .as("the refusal identifies the sibling file")
                .contains(
                        "SA99 has no row in its index "
                                + directory.resolve("porelo-sample/issues/README.md"));
    }

    @Test
    void aRealPlanRendersEverySprintAndFunction() throws IOException {
        realPlan();
        assertThat(run("render")).as("the real file must render without a network").isZero();
        final String page = Files.readString(file.resolveSibling("0.6.md"));
        assertThat(page.lines().findFirst().orElseThrow())
                .as("generated pages must identify themselves to citation checks")
                .isEqualTo("<!-- Generated by porelo-release plan render; do not edit. -->");
        final var titles =
                java.util.regex.Pattern.compile("(?m)^      - title: \"(.*)\"$")
                        .matcher(Files.readString(file))
                        .results()
                        .map(match -> match.group(1))
                        .toList();
        assertThat(titles).as("the real fixture's full function reference set").hasSize(24);
        assertThat(
                        page.lines()
                                .filter(line -> line.startsWith("**"))
                                .map(
                                        line ->
                                                line.substring(
                                                        2,
                                                        line.indexOf(" ✅") > 0
                                                                ? line.indexOf(" ✅")
                                                                : line.indexOf(" ⚪")))
                                .toList())
                .as("rendering must preserve every function in file order")
                .containsExactlyElementsOf(titles);
        assertThat(
                        page.lines()
                                .filter(line -> line.startsWith("### Sprint "))
                                .map(line -> line.split(" ")[2].replace(":", ""))
                                .toList())
                .as("rendering must preserve every sprint's string ID in file order")
                .containsExactly("1", "2", "2b", "3", "4", "5", "6", "7", "8", "9");
        assertThat(page)
                .as("the page must keep the full plan and nested index targets")
                .contains(
                        "Porelo is built in Porelo tasks",
                        "Sprint 2b",
                        "Sprint 9",
                        "2026-10-23",
                        "issues/base/README.md#b189",
                        "◻️",
                        "No channel file and no hand-over directory any more");
    }

    @Test
    void renderingTwiceWritesIdenticalBytes() throws IOException {
        realPlan();
        assertThat(run("render")).as("the first render must succeed").isZero();
        final byte[] first = Files.readAllBytes(file.resolveSibling("0.6.md"));
        assertThat(run("render")).as("the second render must succeed").isZero();
        assertThat(Files.readAllBytes(file.resolveSibling("0.6.md")))
                .as("unchanged inputs must not produce a spurious page change")
                .isEqualTo(first);
    }

    @Test
    void aRenderedPlanWithIndexedIssuesPasses() throws IOException {
        plan(function("One", "issues: [porelo-sample#SA01]"));
        assertThat(run("render")).as("render creates the checked page").isZero();
        assertThat(run("check")).as("a current plan with indexed work passes: %s", errors).isZero();
        assertThat(Files.readString(file.resolveSibling("next.md")))
                .as("issue metadata must come from the heading and state table")
                .contains(
                        "Backlog · S · A function from its heading",
                        "README.md#sa01",
                        "![Sprint s: Backlog 1](" + progressReference() + ")");
    }

    @Test
    void anIssueWithoutAnIndexRowIsRefused() throws IOException {
        plan(function("One", "issues: [porelo-sample#SA99]"));
        refused("porelo-sample#SA99", "no row");
    }

    @Test
    void aMissingRowRefusalNamesTheIndexFileRead() throws IOException {
        plan(function("One", "issues: [porelo-sample#SA99]"));
        refused(
                "porelo-sample#SA99 has no row in its index "
                        + directory.resolve("porelo-sample/issues/README.md"));
    }

    @Test
    void anIssueInTwoFunctionsIsRefused() throws IOException {
        plan(
                function("One", "issues: [porelo-sample#SA01]")
                        + function("Two", "issues: [porelo-sample#SA01]"));
        refused("sprint s", "One", "Two", "SA01");
    }

    @Test
    void aStalePageIsRefused() throws IOException {
        plan(function("One", "issues: [porelo-sample#SA01]"));
        assertThat(run("render")).as("a page exists before it becomes stale").isZero();
        Files.writeString(file.resolveSibling("next.md"), "stale\n");
        refused("next.md", "differs");
    }

    @Test
    void aFinishedIssueWithoutATestDateIsRefused() throws IOException {
        plan(function("One", "issues: [porelo-sample#SA02]"));
        refused("SA02", "sprint s", "One", "Finished");
    }

    @Test
    void aFunctionWithoutAnIssueNoteOrTestDateIsRefused() throws IOException {
        plan(function("One", ""));
        assertThat(run("render")).as("the refusal must come from the fifth check").isZero();
        refused("sprint s", "One", "no issue, no note and no tested date");
    }

    @Test
    void theProgressImageHasItsColoursWithoutAPagesStylesheet() throws IOException {
        plan(function("One", "tested: '2026-02-03'\n        issues: [porelo-sample#SA01]"));
        assertThat(run("render")).isZero();
        final String svg = Files.readString(progressImage());
        // An SVG shown as an image, in a browser or an editor's Markdown view, resolves no CSS
        // variable in an
        // attribute: a fill of var(--done) is drawn black.
        assertThat(svg).as("no colour may rest on a CSS variable").doesNotContain("var(");
        assertThat(
                        java.util.regex.Pattern.compile("fill=\"([^\"]*)\"")
                                .matcher(svg)
                                .results()
                                .map(match -> match.group(1))
                                .toList())
                .as("every fill is a colour of its own")
                .isNotEmpty()
                .allMatch(fill -> fill.matches("#[0-9a-f]{6}"));
    }

    @Test
    void notesNeverCountAsDoneInTheBars() throws IOException {
        plan(
                function("One", "notes: [unfiled work, another step]")
                        + function(
                                "Two",
                                "tested: '2026-02-03'\n        issues: [porelo-sample#SA02]\n"
                                        + "        notes: [a further improvement]"));
        assertThat(run("render")).as("planned notes must be visible").isZero();
        final String page = Files.readString(file.resolveSibling("next.md"));
        assertThat(Files.readString(progressImage()))
                .as("only the indexed Finished issue is done; three unfiled steps remain pending")
                .contains("1 of 4 done", "Not filed yet / Missing: 3");
        assertThat(page.lines().filter(line -> line.contains("✅ Tested ")).count())
                .as("only the explicitly tested function counts as tested")
                .isEqualTo(1);
        assertThat(page)
                .as("unfiled steps remain visible and the notes-only function remains untested")
                .contains(
                        "⚪ Not tested",
                        "◻️ unfiled work",
                        "◻️ another step",
                        "◻️ a further improvement",
                        "![Sprint s: Finished 1, Not filed yet 3](" + progressReference() + ")")
                .doesNotContain("█", "░");
    }

    @Test
    void anUnknownRepositoryIsRefusedAtItsFunction() throws IOException {
        plan(function("One", "issues: [unknown#SA01]"));
        refused("sprint s", "One", "unknown repository");
    }

    @Test
    void aNumberOutsideTheRepositorysPrefixesIsRefused() throws IOException {
        plan(function("One", "issues: [porelo-sample#NO01]"));
        refused("sprint s", "One", "NO01", "prefix");
    }

    @Test
    void anImpossibleDateIsRefused() throws IOException {
        plan(function("One", "tested: '2026-02-30'"));
        refused("sprint s", "One", "2026-02-30");
    }

    @Test
    void aDateOutsideTheRequiredFormIsRefused() throws IOException {
        plan(function("One", "tested: '2026-2-03'"));
        refused("sprint s", "One", "yyyy-MM-dd");
    }

    @Test
    void aDuplicateFunctionTitleIsRefusedWithinItsSprint() throws IOException {
        plan(function("One", "tested: '2026-02-03'") + function("One", "tested: '2026-02-03'"));
        refused("sprint s", "One", "duplicate");
    }

    @Test
    void aMissingRequiredFieldNamesItsPlace() throws IOException {
        plan("      - title: One\n        issues: [porelo-sample#SA01]\n");
        refused("sprint s", "One", "test");
    }

    @Test
    void testedWorkNeedsNoIssueAndFinishedRowsKeepTheirTitle() throws IOException {
        plan(
                function("One", "tested: '2026-02-03'\n        issues: [porelo-sample#SA02]")
                        + function("Two", "tested: '2026-02-03'"));
        assertThat(run("render")).as("tested work may have no issue").isZero();
        assertThat(run("check")).as("tested finished work is valid").isZero();
        assertThat(Files.readString(file.resolveSibling("next.md")))
                .as("deleted issue files keep their title through the Finished row")
                .contains("A finished function");
    }

    @Test
    void htmlContainsThePlanAndEscapesActiveContent() throws IOException {
        plan(function("<script>alert(1)</script>", "issues: [porelo-sample#SA01]"));
        final Path html = root.resolve("page.html");
        assertThat(run("render", "--html", html.toString()))
                .as("the HTML option must be wired")
                .isZero();
        assertThat(Files.readString(html))
                .as("local HTML must not run input or fetch styles")
                .contains(
                        "<style>",
                        "&lt;script&gt;",
                        "href=\"https://github.com/porelo-ai/porelo-sample/blob/main/issues/README.md#sa01\"")
                .doesNotContain("<script>", "<link ", "src=");
    }

    @Test
    void theRealPlanWithNotesOnlyFunctionsPasses() throws IOException {
        realPlan();
        assertThat(run("render")).as("notes-only work must still be visible").isZero();
        assertThat(run("check"))
                .as(
                        "the real plan with its fixture indexes includes valid planned notes: %s",
                        errors)
                .isZero();
    }

    @Test
    void aMissingPageIsRefused() throws IOException {
        plan(function("One", "issues: [porelo-sample#SA01]"));
        refused("next.md", "page missing");
    }

    @Test
    void aChangedIndexMakesThePageStale() throws IOException {
        plan(function("One", "issues: [porelo-sample#SA01]"));
        assertThat(run("render")).as("a page exists before the issue moves").isZero();
        final Path index = directory.resolve("porelo-sample/issues/README.md");
        Files.writeString(index, Files.readString(index).replace("## Backlog", "## Implementing"));
        refused("next.md", "differs");
    }

    @Test
    void anUnavailableIndexIsUnanswered() throws IOException {
        plan(function("One", "issues: [porelo-sample#SA01]"));
        Files.delete(directory.resolve("porelo-sample/issues/README.md"));
        assertThat(run("check"))
                .as("an unreadable source must not become an empty successful check")
                .isEqualTo(Stop.UNANSWERED);
        assertThat(errors.toString(StandardCharsets.UTF_8))
                .as("the unreadable index must be named")
                .contains("porelo-sample/issues/README.md", "not readable");
    }

    @Test
    void theProjectTreeCanHaveAWorktreeDirectoryName() throws IOException {
        final Path moved = root.resolveSibling("project-worktree");
        Files.move(root, moved);
        root = moved;
        file = root.resolve("plan/next.yml");
        plan(function("One", "issues: [porelo-project#PJ01]"));
        assertThat(run("render"))
                .as("the own index is read from the tree regardless of directory name")
                .isZero();
        assertThat(run("check")).as("the tree's issue must have a row").isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {"release", "goal", "sprints", "id", "title", "functions", "test"})
    void everyRequiredFieldIsRefusedWhenAbsent(String field) throws IOException {
        plan(function("One", "issues: [porelo-sample#SA01]"));
        final String before = Files.readString(file);
        final String absent =
                switch (field) {
                    case "sprints" -> before.substring(0, before.indexOf("sprints:"));
                    case "functions" -> before.substring(0, before.indexOf("    functions:"));
                    case "id" -> before.replace("  - id: s", "  -");
                    default -> before.replaceFirst("(?m)^ *" + field + ":.*\n", "");
                };
        Files.writeString(file, absent);
        refused(field, "missing required field");
    }

    @Test
    void anUnknownFieldCannotSilentlyDropAProgressClaim() throws IOException {
        plan(function("One", "testd: '2026-02-03'"));
        refused("sprint s", "One", "unknown field", "testd");
    }

    @Test
    void aMalformedIssueReferenceIsRefused() throws IOException {
        plan(function("One", "issues: [porelo-sample/SA01]"));
        refused("sprint s", "One", "<repository>#<number>");
    }

    @Test
    void aSprintIdMustBeAString() throws IOException {
        plan(function("One", "issues: [porelo-sample#SA01]"));
        Files.writeString(file, Files.readString(file).replace("id: s", "id: 1"));
        refused("id", "string");
    }

    @Test
    void aReleaseCannotEscapeThePlanDirectory() throws IOException {
        plan(function("One", "issues: [porelo-sample#SA01]"));
        Files.writeString(
                file, Files.readString(file).replace("release: next", "release: ../outside"));
        refused("release", "file name");
    }

    @Test
    void htmlCannotOverwriteTheInputPlan() throws IOException {
        plan(function("One", "issues: [porelo-sample#SA01]"));
        final byte[] before = Files.readAllBytes(file);
        assertThat(run("render", "--html", file.toString()))
                .as("render must preserve the plan input")
                .isEqualTo(Stop.REFUSED);
        assertThat(Files.readAllBytes(file))
                .as("the refused output must not replace its input")
                .isEqualTo(before);
    }

    @Test
    void theDefaultPageCannotOverwriteTheInputPlan() throws IOException {
        file = file.resolveSibling("next.md");
        plan(function("One", "issues: [porelo-sample#SA01]"));
        final byte[] before = Files.readAllBytes(file);
        assertThat(run("render"))
                .as("an input with the output name must survive rendering")
                .isEqualTo(Stop.REFUSED);
        assertThat(Files.readAllBytes(file)).as("the plan must keep its bytes").isEqualTo(before);
    }

    @Test
    void anOutputAliasCannotOverwriteTheInputPlan() throws IOException {
        plan(function("One", "issues: [porelo-sample#SA01]"));
        final Path alias = root.resolve("alias.html");
        Files.createSymbolicLink(alias, file);
        final byte[] before = Files.readAllBytes(file);
        assertThat(run("render", "--html", alias.toString()))
                .as("an alias of the plan must not be treated as a separate output")
                .isEqualTo(Stop.REFUSED);
        assertThat(Files.readAllBytes(file))
                .as("the plan must keep its bytes through an alias")
                .isEqualTo(before);
    }

    @Test
    void malformedPlanCommandsAreUsageErrors() {
        for (final String[] args :
                new String[][] {
                    {"plan"},
                    {"plan", "other", "file"},
                    {"plan", "render"},
                    {"plan", "check", "file", "--html", "out"},
                    {"plan", "render", "file", "--html"},
                    {"plan", "render", "file", "--indexes=unknown"},
                    {"plan", "check", "file", "--indexes=unknown"},
                    {"plan", "check", "file", "--indexes"}
                }) {
            assertThat(
                            Main.run(
                                    args,
                                    new PrintStream(new ByteArrayOutputStream()),
                                    new PrintStream(errors),
                                    FakeWeb.untouchable(),
                                    Map.of(),
                                    (tree, manifest, lockfile, work) -> lockfile))
                    .as(
                            "an incomplete or unknown command cannot pass: %s",
                            java.util.Arrays.toString(args))
                    .isEqualTo(Stop.UNANSWERED);
        }
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "project.yml",
                "issues/README.md",
                "../porelo-sample/issues/README.md",
                "../porelo-sample/issues/SA01-A-Function.md"
            })
    void htmlCannotOverwriteAnyReadInput(String target) throws IOException {
        plan(function("One", "issues: [porelo-sample#SA01]"));
        final Path input = root.resolve(target).normalize();
        final byte[] before = Files.readAllBytes(input);
        assertThat(run("render", "--html", input.toString()))
                .as("render must refuse input destination %s: %s", input, errors)
                .isEqualTo(Stop.REFUSED);
        assertThat(Files.readAllBytes(input)).as("input bytes must survive").isEqualTo(before);
        assertThat(Files.exists(file.resolveSibling("next.md")))
                .as("all outputs must be validated before the first write")
                .isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"symlink", "hardlink"})
    void htmlCannotOverwriteAnInputThroughAnAlias(String kind) throws IOException {
        plan(function("One", "issues: [porelo-sample#SA01]"));
        final Path input = root.resolve("project.yml");
        final Path alias = directory.resolve("alias.html");
        if (kind.equals("symlink")) {
            Files.createSymbolicLink(alias, input);
        } else {
            Files.createLink(alias, input);
        }
        final byte[] before = Files.readAllBytes(input);
        assertThat(run("render", "--html", alias.toString()))
                .as("input aliases must also be protected: %s", errors)
                .isEqualTo(Stop.REFUSED);
        assertThat(Files.readAllBytes(input))
                .as("aliased input bytes must survive")
                .isEqualTo(before);
    }

    @Test
    void theDefaultPageCannotOverwriteAnInputInsideThePlanDirectory() throws IOException {
        plan(function("One", "issues: [porelo-sample#SA01]"));
        final Path index = root.resolve("issues/README.md");
        final Path page = file.resolveSibling("next.md");
        Files.move(index, page);
        Files.createSymbolicLink(index, page);
        final byte[] before = Files.readAllBytes(page);
        assertThat(run("render"))
                .as("even an input inside the allowed directory is protected: %s", errors)
                .isEqualTo(Stop.REFUSED);
        assertThat(Files.readAllBytes(page)).as("the index must keep its bytes").isEqualTo(before);
    }

    @Test
    void htmlCannotOverwriteAReadSubgroupIndex() throws IOException {
        plan(function("One", "issues: [porelo-sample#SA01]"));
        final Path index = directory.resolve("porelo-sample/issues/README.md");
        final Path child = index.resolveSibling("base/README.md");
        write(child, Files.readString(index));
        Files.writeString(index, "# Issues\n[Base](base/README.md)\n");
        final byte[] before = Files.readAllBytes(child);
        assertThat(run("render", "--html", child.toString()))
                .as("subgroup indexes are inputs too: %s", errors)
                .isEqualTo(Stop.REFUSED);
        assertThat(Files.readAllBytes(child))
                .as("the subgroup must keep its bytes")
                .isEqualTo(before);
    }

    @ParameterizedTest
    @ValueSource(strings = {"input", "outside", "dangling", "chain"})
    void theDefaultPageCannotFollowAnEscapingSymlink(String kind) throws IOException {
        plan(function("One", "issues: [porelo-sample#SA01]"));
        final Path target =
                kind.equals("input")
                        ? root.resolve("issues/README.md")
                        : root.resolve("outside.md");
        if (!kind.equals("input") && !kind.equals("dangling")) {
            Files.writeString(target, "Keep these bytes");
        }
        final boolean existed = Files.exists(target);
        final byte[] before = existed ? Files.readAllBytes(target) : new byte[0];
        Path link = target;
        if (kind.equals("chain")) {
            link = root.resolve("plan/alias.md");
            Files.createSymbolicLink(link, target);
        }
        Files.createSymbolicLink(file.resolveSibling("next.md"), link);
        assertThat(run("render"))
                .as("a default page must stay inside the plan directory: %s", errors)
                .isEqualTo(Stop.REFUSED);
        assertThat(Files.exists(target))
                .as("a dangling target must remain absent")
                .isEqualTo(existed);
        if (existed) {
            assertThat(Files.readAllBytes(target)).as("the target must survive").isEqualTo(before);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"## Finished ##", "## Finished   ", "### Finished ###"})
    void finishedMarkdownHeadingsStillRequireTested(String heading) throws IOException {
        plan(function("One", "issues: [porelo-sample#SA02]"));
        final Path index = directory.resolve("porelo-sample/issues/README.md");
        Files.writeString(index, Files.readString(index).replace("## Finished", heading));
        assertThat(run("render")).as("valid Markdown headings must render").isZero();
        refused("SA02", "Finished without tested");
    }

    @Test
    void anUnknownStateHeadingIsRefusedByName() throws IOException {
        plan(function("One", "issues: [porelo-sample#SA01]"));
        final Path index = directory.resolve("porelo-sample/issues/README.md");
        Files.writeString(index, Files.readString(index).replace("## Backlog", "## Mystery ##"));
        refused("Mystery", "heading");
    }

    @ParameterizedTest
    @ValueSource(strings = {"```", "~~~", "````"})
    void fencedExampleRowsCannotSatisfyAnIssueReference(String fence) throws IOException {
        plan(function("One", "issues: [porelo-sample#SA99]"));
        final Path index = directory.resolve("porelo-sample/issues/README.md");
        Files.writeString(
                index,
                Files.readString(index)
                        + "\n## Backlog\n"
                        + fence
                        + "markdown\n## Finished\n| <a id=\"sa99\"></a>SA99 | S | - | Example only |\n"
                        + (fence.equals("````") ? "```\n" : "")
                        + fence
                        + "\n");
        assertThat(run("render")).as("the real index remains readable").isZero();
        refused("SA99", "no row");
    }

    @Test
    void aSecondYamlDocumentCannotHideUncheckedWork() throws IOException {
        plan(function("One", "issues: [porelo-sample#SA01]"));
        Files.writeString(
                file, Files.readString(file) + "---\nrelease: hidden\ngoal: Hidden\nsprints: []\n");
        refused("YAML", "document");
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "| # | Size | Blocked by | What it covers | Open questions |\n| <a id=\"sa01\"></a>SA01 | S | - | Actual title | 0 |",
                "| # | Open questions | What it covers | Size | Blocked by |\n| <a id=\"sa01\"></a>SA01 | 0 | Actual title | S | - |"
            })
    void analyzedTitlesFollowTheTableHeaders(String table) throws IOException {
        plan(function("One", "issues: [porelo-sample#SA01]"));
        Files.writeString(
                directory.resolve("porelo-sample/issues/README.md"),
                "## Analyzed\n" + table + "\n");
        assertThat(run("render")).as("a row without an issue file still renders").isZero();
        assertThat(Files.readString(file.resolveSibling("next.md")))
                .as("the coverage column supplies the title regardless of its position")
                .contains("Analyzed · S · Actual title");
    }

    @ParameterizedTest
    @ValueSource(strings = {"—", "–", "-"})
    void issueFileHeadingsAcceptEachReadableDash(String dash) throws IOException {
        plan(function("One", "issues: [porelo-sample#SA01]"));
        Files.writeString(
                directory.resolve("porelo-sample/issues/SA01-A-Function.md"),
                "# SA01 " + dash + " Authoritative file title\n");
        assertThat(run("render")).as("the issue file is readable").isZero();
        assertThat(Files.readString(file.resolveSibling("next.md")))
                .as("a readable issue heading overrides row fallback")
                .contains("Authoritative file title");
    }

    @Test
    void subgroupLinksMayNameAFragment() throws IOException {
        plan(function("One", "issues: [porelo-sample#SA01]"));
        final Path index = directory.resolve("porelo-sample/issues/README.md");
        write(index.resolveSibling("base/README.md"), Files.readString(index));
        Files.writeString(index, "# Issues\n[Base](base/README.md#issues)\n");
        assertThat(run("render")).as("a linked subgroup must be read").isZero();
        assertThat(run("check")).as("the subgroup issue has an index row: %s", errors).isZero();
        assertThat(Files.readString(file.resolveSibling("next.md")))
                .as("links point to the subgroup row")
                .contains("issues/base/README.md#sa01");
    }

    @Test
    void anIssueRepeatedWithinOneFunctionNamesThatDuplicate() throws IOException {
        plan(function("One", "issues: [porelo-sample#SA01, porelo-sample#SA01]"));
        refused("duplicate", "SA01", "sprint s function One");
        assertThat(errors.toString(StandardCharsets.UTF_8))
                .as("one function must not be reported as two assignments")
                .doesNotContain("assigned twice");
    }

    @Test
    void aReleaseIssueIsAnUnknownField() throws IOException {
        plan(function("One", "issues: [porelo-sample#SA01]"));
        Files.writeString(file, "issue: porelo-sample#SA99\n" + Files.readString(file));
        refused("unknown field issue");
    }

    @ParameterizedTest
    @ValueSource(strings = {"markdown", "html"})
    void bothPagesCarryTheFunctionsTestInstructions(String format) throws IOException {
        plan(function("One", "issues: [porelo-sample#SA01]"));
        final Path html = directory.resolve("named-page.html");
        assertThat(run("render", "--html", html.toString())).as("both pages render").isZero();
        final Path page = format.equals("html") ? html : file.resolveSibling("next.md");
        assertThat(Files.readString(page))
                .as("test instructions must reach %s", page)
                .contains("Test: Try it");
    }

    @Test
    void htmlEscapesEveryTextFromThePlanAndIssueIndexes() throws IOException {
        final String payload = "<script>alert(1)</script> & \" '";
        write(
                file,
                """
                release: next
                goal: >-
                  Release goal %1$s
                sprints:
                  - id: s
                    title: >-
                      Sprint title %1$s
                    goal: >-
                      Sprint goal %1$s
                    functions:
                      - title: >-
                          Function title %1$s
                        test: >-
                          Test text %1$s
                        issues: [porelo-sample#SA01, porelo-sample#SA03]
                        notes:
                          - >-
                            Note %1$s
                """
                        .formatted(payload));
        write(
                directory.resolve("porelo-sample/issues/SA01-A-Function.md"),
                "# SA01 — Issue heading " + payload + "\n");
        write(
                directory.resolve("porelo-sample/issues/README.md"),
                """
                # Issues

                ## Backlog

                | # | Size | Blocked by | What it covers |
                |---|---|---|---|
                | <a id="sa01"></a>[SA01](SA01-A-Function.md) | S | - | Row fallback |
                | <a id="sa03"></a>SA03 | S | - | Index coverage %s |
                """
                        .formatted(payload));

        final Path html = root.resolve("page.html");
        assertThat(run("render", "--html", html.toString()))
                .as("untrusted plan and index text must render safely: %s", errors)
                .isZero();
        final String page = Files.readString(html);
        final String svg = Files.readString(progressImage());
        assertThat(svg)
                .as("sprint titles cannot inject executable SVG")
                .contains("Sprint title &lt;script&gt;alert(1)&lt;/script&gt; &amp; \" '")
                .doesNotContain("<script");
        for (final String field :
                java.util.List.of(
                        "Sprint title",
                        "Function title",
                        "Note",
                        "Issue heading",
                        "Index coverage")) {
            assertThat(svg)
                    .as("%s remains escaped in the progress image", field)
                    .doesNotContain(field + " " + payload)
                    .contains(field + " &lt;script&gt;alert(1)&lt;/script&gt; &amp; \" '");
        }
        assertThat(page).as("the HTML is entirely static").doesNotContain("<script");
        for (final String field :
                java.util.List.of(
                        "Sprint title",
                        "Sprint goal",
                        "Function title",
                        "Test text",
                        "Note",
                        "Release goal",
                        "Issue heading",
                        "Index coverage")) {
            assertThat(page)
                    .as("%s must remain visible without injecting HTML or quotes", field)
                    .doesNotContain(field + " " + payload)
                    .contains(field + " &lt;script&gt;alert(1)&lt;/script&gt; &amp; \" '");
        }
    }

    @Test
    void progressSegmentsOccupyTheirCountedShareInLegendOrder() throws Exception {
        plan(
                function(
                        "One",
                        "issues: [porelo-sample#SA01, porelo-sample#SA02, porelo-sample#SA03, porelo-sample#SA04, porelo-sample#SA05, porelo-sample#SA06]\n        notes: [unfiled]"));
        write(
                directory.resolve("porelo-sample/issues/README.md"),
                "## Finished\n| <a id=\"sa01\"></a>SA01 | S | - | Done |\n"
                        + "## Review\n| <a id=\"sa02\"></a>SA02 | S | - | Review |\n"
                        + "| <a id=\"sa03\"></a>SA03 | S | - | Review again |\n"
                        + "## Implementing\n| <a id=\"sa04\"></a>SA04 | S | - | Work |\n"
                        + "## Accepted\n| <a id=\"sa05\"></a>SA05 | S | - | Open |\n"
                        + "## Backlog\n| <a id=\"sa06\"></a>SA06 | S | - | Planned |\n");
        assertThat(run("render")).as("known state counts render: %s", errors).isZero();
        final var document =
                javax.xml.parsers.DocumentBuilderFactory.newInstance()
                        .newDocumentBuilder()
                        .parse(progressImage().toFile());
        final var rectangles = document.getElementsByTagName("rect");
        final var segments = new java.util.ArrayList<org.w3c.dom.Element>();
        for (int i = 0; i < rectangles.getLength(); i++) {
            final var rectangle = (org.w3c.dom.Element) rectangles.item(i);
            if (rectangle.getElementsByTagName("title").getLength() > 0) segments.add(rectangle);
        }
        assertThat(segments).as("all six legend colors have counted segments").hasSize(6);
        final String[] colors = {"done", "review", "impl", "backlog", "accepted", "new"};
        final int[] counts = {1, 2, 1, 1, 1, 1};
        int offset = 0;
        for (int i = 0; i < counts.length; i++) {
            final var segment = segments.get(i);
            assertThat(segment.getAttribute("class"))
                    .as("segment %s follows legend order", i)
                    .isEqualTo("c-" + colors[i]);
            assertThat(Double.parseDouble(segment.getAttribute("x")))
                    .as("%s starts after preceding counts", colors[i])
                    .isCloseTo(400.0 * offset / 7, org.assertj.core.data.Offset.offset(0.000001));
            assertThat(Double.parseDouble(segment.getAttribute("width")))
                    .as("%s occupies its share of seven counted items", colors[i])
                    .isCloseTo(
                            400.0 * counts[i] / 7, org.assertj.core.data.Offset.offset(0.000001));
            offset += counts[i];
        }
        final Path html = root.resolve("geometry.html");
        assertThat(run("render", "--html", html.toString()))
                .as("HTML renders: %s", errors)
                .isZero();
        final String page = Files.readString(html);
        for (final var segment : segments) {
            assertThat(page)
                    .as("the HTML page's inline bar has the image's segment geometry")
                    .contains(
                            "x=\""
                                    + segment.getAttribute("x")
                                    + "\" width=\""
                                    + segment.getAttribute("width")
                                    + "\"");
        }
    }

    @Test
    void theProgressImageIsLinkedAndCheckedForStaleness() throws IOException {
        plan(
                function("One", "issues: [porelo-sample#SA01]")
                        + function("Two", "tested: '2026-02-03'"));
        final Path html = root.resolve("page.html");
        assertThat(run("render", "--html", html.toString()))
                .as("images render: %s", errors)
                .isZero();
        final Path image = progressImage();
        assertThat(Files.isRegularFile(image))
                .as("Markdown needs a standalone progress image")
                .isTrue();
        final String svg = Files.readString(image);
        assertThat(Files.readString(file.resolveSibling("next.md")))
                .as("the image travels with the Markdown page")
                .contains("![Sprint s: Backlog 1](" + progressReference() + ")");
        assertThat(Files.readString(html))
                .as("HTML retains its inline bars and function marks")
                .contains("0 of 1 done")
                .contains("prefers-color-scheme:dark", "⚪")
                .doesNotContain("<script");
        assertThat(Files.readString(html).split(":root\\s*\\{", -1))
                .as(
                        "inline bars inherit the page palette without resetting its dark-mode variables")
                .hasSize(3);
        assertThat(svg)
                .as("an image has counts and no executable or external content")
                .contains(
                        "0 of 1 done",
                        "Backlog: 1",
                        "s. Sprint",
                        "⚪",
                        "✅",
                        "Finished / Rejected",
                        "Review",
                        "Implementing / Rework",
                        "Backlog",
                        "Estimated",
                        "Analyzed",
                        "Accepted",
                        "Idea",
                        "Not filed yet / Missing",
                        "fill=\"#0ca30c\" class=\"c-done\"",
                        "fill=\"#2a78d6\" class=\"c-review\"",
                        "fill=\"#fab219\" class=\"c-impl\"",
                        "fill=\"#8ac8f0\" class=\"c-backlog\"",
                        "fill=\"#9575b5\" class=\"c-estimated\"",
                        "fill=\"#77766f\" class=\"c-analyzed\"",
                        "fill=\"#b9b8b2\" class=\"c-accepted\"",
                        "fill=\"#eb6834\" class=\"c-idea\"",
                        "fill=\"#ffffff\" class=\"c-new\"")
                .doesNotContain("<script", "href=", "<foreignObject", "<image");
        assertThat(run("check")).as("fresh Markdown and image pass: %s", errors).isZero();
        Files.writeString(image, svg.replace("0 of 1 done", "1 of 1 done"));
        refused(image.getFileName().toString(), "differs");
        Files.delete(image);
        refused(image.getFileName().toString(), "missing");
    }

    @Test
    void everyRenderedFileIsDeterministic() throws IOException {
        realPlan();
        final Path html = root.resolve("page.html");
        assertThat(run("render", "--html", html.toString()))
                .as("first render: %s", errors)
                .isZero();
        final Map<Path, byte[]> first = new java.util.LinkedHashMap<>();
        try (var paths = Files.walk(file.getParent())) {
            for (final Path path : paths.filter(Files::isRegularFile).toList()) {
                if (!path.equals(file)) first.put(path, Files.readAllBytes(path));
            }
        }
        first.put(html, Files.readAllBytes(html));
        assertThat(first)
                .as("one progress SVG, Markdown and HTML are the full output set")
                .hasSize(3);
        assertThat(run("render", "--html", html.toString()))
                .as("second render: %s", errors)
                .isZero();
        for (final var entry : first.entrySet()) {
            assertThat(Files.readAllBytes(entry.getKey()))
                    .as("stable bytes for %s", entry.getKey())
                    .isEqualTo(entry.getValue());
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"directory", "file", "dangling", "hardlink", "html", "html-link"})
    void imagesCannotOverwriteInputsOrEscapeThePlanDirectory(String kind) throws IOException {
        plan(function("One", "issues: [porelo-sample#SA01]"));
        final Path folder = file.resolveSibling("next");
        assertThat(run("render")).as("resolve the generated image name: %s", errors).isZero();
        final Path image = progressImage();
        Files.delete(image);
        Files.delete(folder);
        Files.delete(file.resolveSibling("next.md"));
        final Path input = root.resolve("project.yml");
        final byte[] before = Files.readAllBytes(input);
        final Path outside = root.resolve("outside");
        Files.createDirectories(outside);
        if (kind.equals("directory")) {
            Files.createSymbolicLink(folder, outside);
        } else {
            Files.createDirectories(folder);
            if (kind.equals("hardlink")) Files.createLink(image, input);
            else if (!kind.startsWith("html"))
                Files.createSymbolicLink(
                        image, kind.equals("dangling") ? outside.resolve("absent.svg") : input);
        }
        final Path html = root.resolve("alias.html");
        if (kind.equals("html-link")) Files.createSymbolicLink(html, image);
        final int result =
                kind.startsWith("html")
                        ? run("render", "--html", (kind.equals("html") ? image : html).toString())
                        : run("render");
        assertThat(result)
                .as("all outputs must be validated before writing: %s", errors)
                .isEqualTo(Stop.REFUSED);
        assertThat(Files.readAllBytes(input)).as("input survives image alias").isEqualTo(before);
        assertThat(Files.exists(file.resolveSibling("next.md")))
                .as("refusal makes no partial page")
                .isFalse();
        try (var paths = Files.list(outside)) {
            assertThat(paths.toList()).as("no output escapes").isEmpty();
        }
    }

    @Test
    void sprintIdsCannotBecomePaths() throws IOException {
        plan(function("One", "issues: [porelo-sample#SA01]"));
        Files.writeString(file, Files.readString(file).replace("id: s", "id: ../escape"));
        assertThat(run("render"))
                .as("a sprint ID cannot choose an output path: %s", errors)
                .isEqualTo(Stop.REFUSED);
    }

    @Test
    void standUsesTheLatestForgeInputCommitAndNamesUndatedSources() throws IOException {
        plan(function("One", "issues: [porelo-sample#SA01]"));
        final FakeWeb web = new FakeWeb();
        for (final String repository : java.util.List.of("porelo-project", "porelo-sample")) {
            web.serve(
                    "https://raw.githubusercontent.com/porelo-ai/"
                            + repository
                            + "/main/issues/README.md",
                    Files.readString(directory.resolve(repository + "/issues/README.md")));
        }
        final String commits =
                "https://api.github.com/repos/porelo-ai/%s/commits?sha=main&path=issues&per_page=1";
        final String response =
                "[{\"sha\":\"52967194d092c9e5b7053cc731c02240fd84a619\",\"commit\":{\"author\":{\"date\":\"2026-10-22T10:00:00Z\"},\"committer\":{\"date\":\"%s\"}}}]";
        web.serve(commits.formatted("porelo-project"), response.formatted("2026-10-23T09:00:00Z"));
        web.serve(commits.formatted("porelo-sample"), response.formatted("2026-10-23T11:00:00Z"));
        final Path html = root.resolve("page.html");
        assertThat(runWith(web, "render", "--html", html.toString()))
                .as("forge input dates render: %s", errors)
                .isZero();
        for (final Path output : java.util.List.of(html, file.resolveSibling("next.md"))) {
            assertThat(Files.readString(output))
                    .as("Stand dates the inputs, including missing local history")
                    .contains(
                            "Stand: 2026-10-23T11:00:00Z",
                            "no commit time for next.yml",
                            "no file-time fallback")
                    .doesNotContain("Stand: 2026-10-23T09:00:00Z");
        }
        web.serve(commits.formatted("porelo-project"), response.formatted("2026-10-23T12:00:00Z"));
        assertThat(runWith(web, "check"))
                .as("a newer input commit makes the page stale: %s", errors)
                .isEqualTo(Stop.REFUSED);
    }

    @Test
    void aMalformedForgeCommitCannotInventAStandDate() throws IOException {
        plan(function("One", "issues: [porelo-sample#SA01]"));
        final FakeWeb web = new FakeWeb();
        for (final String repository : java.util.List.of("porelo-project", "porelo-sample")) {
            web.serve(
                    "https://raw.githubusercontent.com/porelo-ai/"
                            + repository
                            + "/main/issues/README.md",
                    Files.readString(directory.resolve(repository + "/issues/README.md")));
            web.serve(
                    "https://api.github.com/repos/porelo-ai/"
                            + repository
                            + "/commits?sha=main&path=issues&per_page=1",
                    "[{\"commit\":{}}]");
        }
        assertThat(runWith(web, "render"))
                .as("missing commit dates cannot be accepted: %s", errors)
                .isEqualTo(Stop.UNANSWERED);
        assertThat(Files.exists(file.resolveSibling("next.md")))
                .as("no page with invented metadata is written")
                .isFalse();
    }

    private String progressReference() throws IOException {
        final String page = Files.readString(file.resolveSibling("next.md"));
        final var reference =
                java.util.regex.Pattern.compile("!\\[[^\\n]*]\\(([^)]+)\\)").matcher(page);
        if (!reference.find())
            throw new IOException("No progress image reference in generated page");
        return reference.group(1);
    }

    private Path progressImage() throws IOException {
        return file.resolveSibling(progressReference());
    }

    private void realPlan() throws IOException {
        copyTree(Path.of("src/test/resources/plans/checkouts"), directory);
        Files.copy(
                Path.of("src/test/resources/plans/project.yml"),
                root.resolve("project.yml"),
                java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        file = root.resolve("plan/0.6.yml");
        Files.createDirectories(file.getParent());
        Files.copy(Path.of("src/test/resources/plans/0.6.yml"), file);
    }

    private void refused(String... messages) {
        assertThat(run("check"))
                .as("invalid plans must stop with a refusal: %s", errors)
                .isEqualTo(Stop.REFUSED);
        assertThat(errors.toString(StandardCharsets.UTF_8))
                .as("the refusal must name the offending place")
                .contains(messages);
    }

    private void plan(String functions) throws IOException {
        write(
                file,
                "release: next\ngoal: A release\nsprints:\n  - id: s\n    title: Sprint\n    goal: A goal\n    functions:\n"
                        + functions);
    }

    private static String function(String title, String fields) {
        return "      - title: '" + title + "'\n        test: Try it\n        " + fields + "\n";
    }

    private int run(String verb, String... options) {
        final var beside = new java.util.ArrayList<>(java.util.List.of(options));
        beside.add("--indexes=beside");
        return runWith(FakeWeb.untouchable(), verb, beside.toArray(String[]::new));
    }

    private int runWith(Web web, String verb, String... options) {
        final java.util.List<String> args =
                new java.util.ArrayList<>(java.util.List.of("plan", verb, file.toString()));
        args.addAll(java.util.List.of(options));
        return Main.run(
                args.toArray(String[]::new),
                new PrintStream(new ByteArrayOutputStream()),
                new PrintStream(errors, true, StandardCharsets.UTF_8),
                web,
                Map.of(),
                (tree, manifest, lockfile, work) -> lockfile);
    }

    private static void write(Path path, String text) throws IOException {
        Files.createDirectories(path.getParent());
        Files.writeString(path, text);
    }

    private static void copyTree(Path source, Path target) throws IOException {
        try (var paths = Files.walk(source)) {
            for (final Path path : paths.filter(Files::isRegularFile).toList()) {
                final Path copy = target.resolve(source.relativize(path));
                Files.createDirectories(copy.getParent());
                Files.copy(path, copy, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }
}
