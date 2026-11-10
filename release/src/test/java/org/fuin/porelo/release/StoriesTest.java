package org.fuin.porelo.release;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StoriesTest {

    @TempDir Path work;

    private Path dir;

    private final ByteArrayOutputStream out = new ByteArrayOutputStream();

    private final ByteArrayOutputStream err = new ByteArrayOutputStream();

    @BeforeEach
    void project() throws IOException {
        final Path project = work.resolve("porelo-project");
        dir = Files.createDirectories(project.resolve("stories"));
        Files.createDirectories(project.resolve("issues"));
        Files.copy(
                Path.of("../stories/src/test/resources/workflow.yml"), dir.resolve("workflow.yml"));
        Files.writeString(project.resolve("issues/README.md"), "# Issues\n");
        Files.writeString(
                project.resolve("project.yml"),
                """
                project:
                    upstream: "git@github.com:porelo-ai/porelo-project.git"
                repositories:
                    core:
                        upstream: "git@github.com:porelo-ai/porelo.git"
                        issues: ["B", "P"]
                    host:
                        upstream: "git@github.com:porelo-ai/porelo-host.git"
                        issues: ["HO"]
                    ecosystems:
                        upstream: "git@github.com:porelo-ai/porelo-ecosystems.git"
                        issues: ["EC"]
                    site:
                        upstream: "git@github.com:porelo-ai/porelo-ai.github.io.git"
                        issues: ["SI"]
                """);
        sets(
                "porelo",
                "base",
                "Review",
                "B515",
                "providers",
                "Implementing",
                "P08",
                "providers",
                "Backlog",
                "P09");
        index(
                "porelo-host",
                "Review",
                "HO146",
                "Finished",
                "HO147",
                "Rejected",
                "HO148",
                "Rework",
                "HO149");
        index("porelo-ecosystems", "Idea", "EC06");
        index("porelo-ai.github.io", "Finished", "SI01");
    }

    @Test
    void acceptsStoriesWhoseHeadsFitTheSchemaAndTheirHeadings() throws IOException {
        story(
                "ST1-A-Person-Rents-A-Host.md",
                "ST1",
                "A Person Rents A Host",
                "[\"porelo-host#HO146\"]",
                "null");
        story(
                "ST2.md",
                "ST2",
                "A Person Tests It",
                "[\"porelo#P08\", \"porelo-ecosystems#EC06\", \"porelo-ai.github.io#SI01\"]",
                "null");
        assertThat(run("index")).as(said()).isZero();

        assertThat(check()).as(said()).isZero();
        assertThat(stdout()).contains("2 story head(s)");
    }

    @Test
    void refusesAHeadThatFailsTheSchemaNamingFileAndField() throws IOException {
        write(
                "ST1.md",
                "# ST1 — A Title\n\n## Data\n\n```json\n"
                        + "{\"id\": \"ST1\", \"title\": \"A Title\", \"gain\": \"g\", \"test\": \"t\","
                        + " \"issues\": [\"HO146\"], \"state\": \"Tested\", \"priority\": \"Urgent\"}\n```\n");

        assertThat(check()).isEqualTo(Stop.REFUSED);
        assertThat(stderr())
                .as("each failure by file and field")
                .contains("ST1.md: /issues/0: ")
                .contains("ST1.md: /: ")
                .contains("tested")
                .contains("ST1.md: /state: ")
                .contains("ST1.md: /priority: ");
    }

    @Test
    void refusesAHeadWithMoreThanOneJsonValue() throws IOException {
        write(
                "ST1.md",
                "# ST1 — A Title\n\n"
                        + head("ST1", "A Title", "High", "[]", "null")
                        + "\nText after the head.\n\n```\nmore\n```\n");

        assertThat(check()).isEqualTo(Stop.REFUSED);
        assertThat(stderr()).contains("ST1.md: the head is no JSON");
    }

    @Test
    void saysAFaultAsWrittenWithoutTakingItForAPath() throws IOException {
        write(
                "ST1.md",
                "# ST1 — Old\n\n" + head("ST1", "See https://example.com", "High", "[]", "null"));

        assertThat(check()).isEqualTo(Stop.REFUSED);
        assertThat(stderr())
                .contains("ST1.md: the heading is not '# ST1 — See https://example.com'");
    }

    @Test
    void refusesAHeadingThatDiffersFromItsHead() throws IOException {
        write("ST1.md", "# ST1 — Another Title\n\n" + head("ST1", "A Title", "High", "[]", "null"));

        assertThat(check()).isEqualTo(Stop.REFUSED);
        assertThat(stderr()).contains("ST1.md: the heading is not '# ST1 — A Title'");
    }

    @Test
    void refusesAFileNameThatDiffersFromItsId() throws IOException {
        story("ST12-Words.md", "ST1", "A Title", "[]", "null");

        assertThat(check()).isEqualTo(Stop.REFUSED);
        assertThat(stderr()).contains("ST12-Words.md: the file is not named after its id ST1");
    }

    @Test
    void refusesARepeatedId() throws IOException {
        story("ST1.md", "ST1", "A Title", "[]", "null");
        story("ST1-Again.md", "ST1", "A Title", "[]", "null");

        assertThat(check()).isEqualTo(Stop.REFUSED);
        assertThat(stderr()).contains("ST1.md: id ST1 also stands in ST1-Again.md");
    }

    @Test
    void refusesAFileWithoutAHeadOrWithBrokenJson() throws IOException {
        write("ST1.md", "# ST1 — A Title\n\n```json\n{}\n```\n\nNo head under Data at the end.\n");
        write("ST2.md", "# ST2 — A Title\n\n## Data\n\n```json\n{\"id\": \"ST2\",, }\n```\n");

        assertThat(check()).isEqualTo(Stop.REFUSED);
        assertThat(stderr())
                .contains("ST1.md: no ```json head under ## Data at the end")
                .contains("ST2.md: the head is no JSON");
    }

    @Test
    void indexesEachStoryByItsStateHigherPriorityFirst() throws IOException {
        story(
                "ST1.md",
                "ST1",
                "Waits On Backlog",
                "Low",
                "Backlog",
                "[\"porelo-host#HO146\", \"porelo#P09\"]",
                "null");
        story(
                "ST2.md",
                "ST2",
                "In Review",
                "Medium",
                "Review",
                "[\"porelo-host#HO146\", \"porelo-host#HO147\", \"porelo-host#HO148\"]",
                "null");
        story(
                "ST3.md",
                "ST3",
                "Also In Review",
                "Highest",
                "Review",
                "[\"porelo-ai.github.io#SI01\"]",
                "null");
        story("ST4.md", "ST4", "No Issue Yet", "Lowest", "Idea", "[]", "null");
        story(
                "ST5.md",
                "ST5",
                "Done And Tried",
                "High",
                "Finished",
                "[\"porelo-ai.github.io#SI01\"]",
                "\"2026-11-08\"");
        story("ST6.md", "ST6", "Not Wanted", "Medium", "Rejected", "[\"porelo#P09\"]", "null");
        story(
                "ST7.md",
                "ST7",
                "Back For Rework",
                "High",
                "Rework",
                "[\"porelo-host#HO149\"]",
                "null");

        story(
                "ST10.md",
                "ST10",
                "Same Rank Later Id",
                "Highest",
                "Review",
                "[\"porelo-ai.github.io#SI01\"]",
                "null");

        assertThat(run("index")).as(said()).isZero();

        assertThat(Files.readString(dir.resolve("README.md")))
                .as("by the head's state in the states' sequence, the higher priority first")
                .contains("Priority: ⏫ Highest · 🔼 High · 🟰 Medium · 🔽 Low · ⏬ Lowest.")
                .contains(
                        "(`B` porelo, `EC` porelo-ecosystems, `HO` porelo-host, `P` porelo, `SI` porelo-ai.github.io)")
                .containsSubsequence(
                        "| State | Stories |",
                        "| [Idea](#idea) | 1 |",
                        "| [Backlog](#backlog) | 2 |",
                        "| [Review](#review) | 1 |",
                        "| [Rework](#rework) | 1 |",
                        "| [Finished](#finished) | 3 |",
                        "## Idea",
                        "[ST4](ST4.md) No Issue Yet | ⏬ Lowest | none yet |",
                        "## Backlog",
                        "[ST1](ST1.md) Waits On Backlog | 🔽 Low | HO:1 · P:1 |",
                        "## Review",
                        "[ST2](ST2.md) In Review | 🟰 Medium | HO:3 |",
                        "## Rework",
                        "[ST7](ST7.md) Back For Rework | 🔼 High | HO:1 |",
                        "## Finished",
                        "[ST3](ST3.md) Also In Review | ⏫ Highest | SI:1 |",
                        "[ST10](ST10.md) Same Rank Later Id | ⏫ Highest |",
                        "[ST5](ST5.md) Done And Tried | 🔼 High | SI:1 |");
        assertThat(Files.readString(dir.resolve("ST5.md")))
                .contains("**Priority:** 🔼 High · **State:** Finished (2026-11-08)");
        assertThat(check()).as(said()).isZero();
    }

    @Test
    void computesAStoryWhileRefusingATestedDayOutOfPlace() throws IOException {
        story(
                "ST1.md",
                "ST1",
                "Too Early",
                "High",
                "Review",
                "[\"porelo-host#HO146\", \"porelo-host#HO149\", \"porelo#P08\"]",
                "null");
        story("ST2.md", "ST2", "Untested", "High", "Finished", "[]", "null");
        story("ST3.md", "ST3", "Tested Early", "High", "Backlog", "[]", "\"2026-11-08\"");

        story("ST4.md", "ST4", "No Such Day", "High", "Finished", "[]", "\"2026-11-31\"");

        assertThat(check()).isEqualTo(Stop.REFUSED);

        assertThat(stderr())
                .doesNotContain("in Review while")
                .contains("ST2.md: Finished without a tested day")
                .contains("ST3.md: a tested day on a story that is Backlog, not a completed story")
                .contains("ST4.md: /tested: 2026-11-31 is no day");
        assertThat(run("index")).as("index refuses what check refuses").isEqualTo(Stop.REFUSED);
    }

    // Prevents the operator's state being overruled by the issues, or a mismatch being refused.
    @Test
    void keepsAStatedStateBesideIssuesAndWarnsWhereTheIssuesGiveAnother() throws IOException {
        storyTwo("ST1.md", "ST1", "Put Back", "Backlog", "[\"porelo-host#HO146\"]", "null");
        storyTwo("ST2.md", "ST2", "Left To Its Issues", null, "[\"porelo-host#HO146\"]", "null");
        storyTwo(
                "ST3.md", "ST3", "Stated As Computed", "Review", "[\"porelo-host#HO146\"]", "null");

        assertThat(run("index")).as(said()).isZero();

        assertThat(stdout())
                .as("one warning for the story whose issues give another state")
                .contains(
                        "WARN  ST1: stated Backlog, its issues give Review (porelo-host#HO146 Review)")
                .doesNotContain("WARN  ST2")
                .doesNotContain("WARN  ST3");
        assertThat(Files.readString(dir.resolve("ST1.md")))
                .contains("**State:** Backlog")
                .contains("\"state\": \"Backlog\"");
        assertThat(Files.readString(dir.resolve("ST2.md"))).contains("**State:** Review");
        assertThat(Files.readString(dir.resolve("README.md")))
                .containsSubsequence(
                        "## Backlog", "[ST1](ST1.md) Put Back", "## Review", "[ST2](ST2.md)");
        out.reset();
        assertThat(check()).as(said()).isZero();
        assertThat(stdout())
                .contains(
                        "WARN  ST1: stated Backlog, its issues give Review (porelo-host#HO146 Review)");
    }

    // Prevents a stated completed story escaping its test because it has issues.
    @Test
    void refusesAStatedFinishedStoryBesideIssuesWithoutATestedDay() throws IOException {
        storyTwo("ST1.md", "ST1", "Said Done", "Finished", "[\"porelo-host#HO147\"]", "null");
        storyTwo("ST2.md", "ST2", "Done By Issues", null, "[\"porelo-host#HO147\"]", "null");

        assertThat(check()).isEqualTo(Stop.REFUSED);

        assertThat(stderr())
                .contains("ST1.md: Finished without a tested day")
                .doesNotContain("ST2.md");
    }

    @Test
    void refusesAnIndexThatDiffersFromItsGeneration() throws IOException {
        story("ST1.md", "ST1", "A Title", "[]", "null");
        assertThat(run("index")).as(said()).isZero();
        story("ST2.md", "ST2", "Another", "[]", "null");

        assertThat(check()).isEqualTo(Stop.REFUSED);
        assertThat(stderr())
                .contains(
                        "README.md: differs from what the stories' heads give; run porelo-release stories index");
    }

    @Test
    void refusesAStoryNamingAnIssueThatDoesNotExist() throws IOException {
        story("ST1.md", "ST1", "A Title", "[\"porelo-host#HO999\", \"porelo-gone#GO01\"]", "null");

        assertThat(check()).isEqualTo(Stop.REFUSED);
        assertThat(run("index")).isEqualTo(Stop.REFUSED);
        assertThat(stderr())
                .contains("ST1.md: porelo-host#HO999 is in no issue index of porelo-host")
                .contains("ST1.md: porelo-gone#GO01 names a repository project.yml does not");
        assertThat(dir.resolve("README.md")).doesNotExist();
    }

    @Test
    void writesEachStorysReadablePartFromItsHeadAndKeepsWhatAPersonWroteBelowIt()
            throws IOException {
        write(
                "ST1-Words.md",
                "# ST1 — A Title\n\nAn old text, before the generator.\n\n## Notes\n\nBy hand.\n\n"
                        + head(
                                "ST1",
                                "A Title",
                                "Medium",
                                "Implementing",
                                "[\"porelo#B515\", \"porelo#P08\"]",
                                "null"));
        story("ST2.md", "ST2", "Tried", "Low", "[]", "\"2026-11-08\"");

        assertThat(run("index")).as(said()).isZero();

        final String ready =
                """
                # ST1 — A Title

                **Priority:** 🟰 Medium · **State:** Implementing

                **What a user gains.** A person gains.

                **The test.** Run it.

                **Issues:**

                - [porelo#B515](https://github.com/porelo-ai/porelo/blob/main/issues/base/README.md#b515) (Review)
                - [porelo#P08](https://github.com/porelo-ai/porelo/blob/main/issues/providers/README.md#p08) (Implementing)

                An old text, before the generator.

                ## Notes

                By hand.

                """;
        assertThat(Files.readString(dir.resolve("ST1-Words.md")))
                .as("each issue linked to the set index its row stands in")
                .startsWith(ready)
                .endsWith("```\n");
        assertThat(Files.readString(dir.resolve("ST2.md")))
                .startsWith(
                        """
                        # ST2 — Tried

                        **Priority:** 🔽 Low · **State:** Finished (2026-11-08)

                        **What a user gains.** A person gains.

                        **The test.** Run it.

                        **Issues:** none yet

                        A gain.

                        ## Data
                        """);
        final String once = Files.readString(dir.resolve("ST1-Words.md"));
        assertThat(run("index")).as(said()).isZero();
        assertThat(Files.readString(dir.resolve("ST1-Words.md")))
                .as("written again, the same")
                .isEqualTo(once);
        assertThat(check()).as(said()).isZero();
    }

    @Test
    void refusesAStoryWhoseReadablePartDiffersFromItsHead() throws IOException {
        story("ST1.md", "ST1", "A Title", "[\"porelo#P08\"]", "null");
        assertThat(run("index")).as(said()).isZero();
        final Path file = dir.resolve("ST1.md");
        Files.writeString(file, Files.readString(file).replace("(Implementing)", "(Finished)"));

        assertThat(check()).isEqualTo(Stop.REFUSED);
        assertThat(stderr())
                .contains(
                        "ST1.md: the text above ## Data is not what its head gives; run porelo-release stories index");
    }

    @Test
    void cutsTheStoriesTestedAfterTheLastReleaseForTheReleaseNotes() throws IOException {
        story("ST1-Old.md", "ST1", "Tested Before", "[]", "\"2026-10-01\"");
        story("ST2.md", "ST2", "On The Release Day", "[]", "\"2026-11-01\"");
        story("ST3-Words.md", "ST3", "Tested After", "[]", "\"2026-11-08\"");
        story("ST4.md", "ST4", "Tested Next Day", "[]", "\"2026-11-02\"");
        story("ST5.md", "ST5", "Not Yet", "[]", "null");
        story("ST10.md", "ST10", "Same Day Later Id", "[]", "\"2026-11-08\"");

        assertThat(run("cut", "0.6.0", "--after", "2026-11-01")).as(said()).isZero();

        assertThat(stdout())
                .isEqualTo(
                        """
                        ## 0.6.0

                        The stories tested after 2026-11-01:

                        - [ST4](https://github.com/porelo-ai/porelo-project/blob/main/stories/ST4.md) Tested Next Day (2026-11-02)
                        - [ST3](https://github.com/porelo-ai/porelo-project/blob/main/stories/ST3-Words.md) Tested After (2026-11-08)
                        - [ST10](https://github.com/porelo-ai/porelo-project/blob/main/stories/ST10.md) Same Day Later Id (2026-11-08)
                        """);
    }

    @Test
    void refusesToCutStoriesWhoseStateAndTestedDayDisagree() throws IOException {
        story("ST1.md", "ST1", "Tested Early", "High", "Rework", "[]", "\"2026-11-08\"");

        assertThat(run("cut", "0.6.0", "--after", "2026-11-01")).isEqualTo(Stop.REFUSED);
        assertThat(stderr())
                .contains("ST1.md: a tested day on a story that is Rework, not a completed story");
        assertThat(stdout()).isEmpty();
    }

    @Test
    void refusesACutWithoutADayOrAVersion() throws IOException {
        story("ST1.md", "ST1", "A Title", "[]", "null");

        assertThat(run("cut", "0.6.0", "--after", "1.11.2026")).isEqualTo(2);
        assertThat(run("cut", "../x", "--after", "2026-11-01")).isEqualTo(2);
        assertThat(run("cut", "0.6.0", "--after", "2026-02-30")).isEqualTo(2);
        assertThat(stderr()).contains("stories cut <version> <dir> --after <yyyy-mm-dd>");
    }

    private void sets(String repository, String... rows) throws IOException {
        final Path issues = Files.createDirectories(work.resolve(repository).resolve("issues"));
        Files.copy(
                Path.of("../stories/src/test/resources/workflow.yml"),
                issues.resolve("workflow.yml"),
                java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        final Map<String, StringBuilder> sets = new java.util.LinkedHashMap<>();
        for (int i = 0; i < rows.length; i += 3) {
            sets.computeIfAbsent(rows[i], set -> new StringBuilder("# Issues\n"))
                    .append(row(rows[i + 1], rows[i + 2]));
        }
        final StringBuilder top = new StringBuilder("# Requirements\n\n");
        for (final Map.Entry<String, StringBuilder> set : sets.entrySet()) {
            Files.createDirectories(issues.resolve(set.getKey()));
            Files.writeString(
                    issues.resolve(set.getKey()).resolve("README.md"), set.getValue().toString());
            top.append("[")
                    .append(set.getKey())
                    .append("](")
                    .append(set.getKey())
                    .append("/README.md)\n");
        }
        Files.writeString(issues.resolve("README.md"), top.toString());
    }

    private static String row(String state, String number) {
        return "\n## "
                + state
                + "\n\n| <a id=\""
                + number.toLowerCase(Locale.ROOT)
                + "\"></a>["
                + number
                + "]("
                + number
                + "-X.md) | S | - | An issue. |\n";
    }

    private void index(String repository, String... rows) throws IOException {
        final Path issues = Files.createDirectories(work.resolve(repository).resolve("issues"));
        Files.copy(
                Path.of("../stories/src/test/resources/workflow.yml"),
                issues.resolve("workflow.yml"),
                java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        final StringBuilder text = new StringBuilder("# Issues\n");
        for (int i = 0; i < rows.length; i += 2) {
            text.append("\n## ")
                    .append(rows[i])
                    .append("\n\n| <a id=\"")
                    .append(rows[i + 1].toLowerCase(Locale.ROOT))
                    .append("\"></a>[")
                    .append(rows[i + 1])
                    .append("](")
                    .append(rows[i + 1])
                    .append("-X.md) | S | - | An issue. |\n");
        }
        Files.writeString(issues.resolve("README.md"), text.toString());
    }

    private int check() {
        return run("check");
    }

    private int run(String command) {
        return run(command, null, null, null);
    }

    private int run(String command, String version, String flag, String day) {
        final String[] args =
                version == null
                        ? new String[] {"stories", command, dir.toString()}
                        : new String[] {"stories", command, version, dir.toString(), flag, day};
        final PrintStream o = new PrintStream(out, true, StandardCharsets.UTF_8);
        final PrintStream e = new PrintStream(err, true, StandardCharsets.UTF_8);
        return Main.run(
                args,
                o,
                e,
                (address, headers) -> {
                    throw new AssertionError(
                            "stories reads the checkouts beside, nothing over the web");
                },
                Map.of(),
                (tree, manifest, lockfile, work) -> lockfile);
    }

    private void story(String file, String id, String title, String issues, String tested)
            throws IOException {
        story(file, id, title, "High", issues, tested);
    }

    private void story(
            String file, String id, String title, String priority, String issues, String tested)
            throws IOException {
        story(
                file,
                id,
                title,
                priority,
                "null".equals(tested) ? "Idea" : "Finished",
                issues,
                tested);
    }

    private void story(
            String file,
            String id,
            String title,
            String priority,
            String state,
            String issues,
            String tested)
            throws IOException {
        write(
                file,
                "# "
                        + id
                        + " — "
                        + title
                        + "\n\nA gain.\n\n"
                        + head(id, title, priority, state, issues, tested));
    }

    private static String head(
            String id, String title, String priority, String issues, String tested) {
        return head(
                id, title, priority, "null".equals(tested) ? "Idea" : "Finished", issues, tested);
    }

    private static String head(
            String id, String title, String priority, String state, String issues, String tested) {
        return "## Data\n\n```json\n{\n  \"id\": \""
                + id
                + "\",\n  \"title\": \""
                + title
                + "\",\n  \"state\": \""
                + state
                + "\",\n  \"priority\": \""
                + priority
                + "\",\n  \"gain\": \"A person gains.\",\n  \"test\": \"Run it.\",\n  \"issues\": "
                + issues
                + ",\n  \"tested\": "
                + tested
                + "\n}\n```\n";
    }

    /** A story-2 head, with a stated state or, given null, none. */
    private void storyTwo(
            String file, String id, String title, String state, String issues, String tested)
            throws IOException {
        write(
                file,
                "# "
                        + id
                        + " — "
                        + title
                        + "\n\nA gain.\n\n## Data\n\n```json\n{\n  \"$schema\": \"story-2.json\",\n  \"id\": \""
                        + id
                        + "\",\n  \"title\": \""
                        + title
                        + (state == null ? "" : "\",\n  \"state\": \"" + state)
                        + "\",\n  \"priority\": \"High\",\n  \"gain\": \"A person gains.\",\n"
                        + "  \"test\": \"Run it.\",\n  \"issues\": "
                        + issues
                        + ",\n  \"tested\": "
                        + tested
                        + "\n}\n```\n");
    }

    private void write(String file, String text) throws IOException {
        Files.writeString(dir.resolve(file), text, StandardCharsets.UTF_8);
    }

    private String stdout() {
        return out.toString(StandardCharsets.UTF_8);
    }

    private String stderr() {
        return err.toString(StandardCharsets.UTF_8);
    }

    private String said() {
        return stdout() + stderr();
    }
}
