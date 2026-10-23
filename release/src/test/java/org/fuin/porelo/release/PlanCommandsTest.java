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
    void aRealPlanRendersEverySprintAndFunction() throws IOException {
        realPlan();
        assertThat(run("render")).as("the real file must render without a network").isZero();
        final String page = Files.readString(file.resolveSibling("0.6.md"));
        final var titles =
                java.util.regex.Pattern.compile("(?m)^      - title: \"(.*)\"$")
                        .matcher(Files.readString(file))
                        .results()
                        .map(match -> match.group(1))
                        .toList();
        assertThat(titles).as("the real fixture's full function reference set").hasSize(24);
        assertThat(
                        page.lines()
                                .filter(line -> line.startsWith("### "))
                                .map(line -> line.substring(4))
                                .toList())
                .as("rendering must preserve every function in file order")
                .containsExactlyElementsOf(titles);
        assertThat(
                        page.lines()
                                .filter(line -> line.startsWith("## Sprint "))
                                .map(line -> line.split(" ")[2])
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
                        "not filed yet",
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
                        "— Backlog · S · A function from its heading",
                        "README.md#sa01",
                        "Issues: Backlog 1");
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
    void notesNeverCountAsDoneInTheBars() throws IOException {
        plan(
                function("One", "notes: [unfiled work, another step]")
                        + function(
                                "Two",
                                "tested: '2026-02-03'\n        issues: [porelo-sample#SA02]\n"
                                        + "        notes: [a further improvement]"));
        assertThat(run("render")).as("planned notes must be visible").isZero();
        final String page = Files.readString(file.resolveSibling("next.md"));
        assertThat(page.lines().filter(line -> line.startsWith("Issues: ")).toList())
                .as("only the one indexed Finished issue contributes to the bar")
                .containsExactly("Issues: Finished 1 \\[\\#\\]");
        assertThat(page.lines().filter(line -> line.startsWith("\\[x\\] Tested ")).count())
                .as("only the explicitly tested function counts as tested")
                .isEqualTo(1);
        assertThat(page)
                .as("unfiled steps remain visible and the notes-only function remains untested")
                .contains(
                        "\\[ \\] Not tested",
                        "not filed yet: unfiled work",
                        "not filed yet: another step",
                        "not filed yet: a further improvement");
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
                    {"plan", "render", "file", "--html"}
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
    void aReleaseIssueWithoutAnIndexRowIsRefused() throws IOException {
        plan(function("One", "issues: [porelo-sample#SA01]"));
        Files.writeString(file, "issue: porelo-sample#SA99\n" + Files.readString(file));
        assertThat(run("render")).as("the page exists so only membership can fail").isZero();
        refused("porelo-sample#SA99", "no row in its index");
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
                    .contains(field + " &lt;script&gt;alert(1)&lt;/script&gt; &amp; &quot; &#39;");
        }
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
        final java.util.List<String> args =
                new java.util.ArrayList<>(java.util.List.of("plan", verb, file.toString()));
        args.addAll(java.util.List.of(options));
        return Main.run(
                args.toArray(String[]::new),
                new PrintStream(new ByteArrayOutputStream()),
                new PrintStream(errors, true, StandardCharsets.UTF_8),
                FakeWeb.untouchable(),
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
