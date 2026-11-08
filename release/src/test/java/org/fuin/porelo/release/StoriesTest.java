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
                "\"2026-11-08\"");
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
                        + " \"issues\": [\"HO146\"], \"state\": \"Review\", \"priority\": \"Urgent\"}\n```\n");

        assertThat(check()).isEqualTo(Stop.REFUSED);
        assertThat(stderr())
                .as("each failure by file and field")
                .contains("ST1.md: /issues/0: ")
                .contains("ST1.md: /: ")
                .contains("tested")
                .contains("state")
                .contains("ST1.md: /priority: ");
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
    void indexesEachStoryByItsLeastAdvancedIssueHigherPriorityFirst() throws IOException {
        story(
                "ST1.md",
                "ST1",
                "Waits On Backlog",
                "Low",
                "[\"porelo-host#HO146\", \"porelo#P09\"]",
                "null");
        story(
                "ST2.md",
                "ST2",
                "In Review",
                "Medium",
                "[\"porelo-host#HO146\", \"porelo-host#HO147\"]",
                "null");
        story("ST3.md", "ST3", "Also In Review", "Highest", "[\"porelo-host#HO146\"]", "null");
        story("ST4.md", "ST4", "No Issue Yet", "Lowest", "[]", "null");
        story(
                "ST5.md",
                "ST5",
                "Done And Tried",
                "High",
                "[\"porelo-ai.github.io#SI01\"]",
                "\"2026-11-08\"");
        story(
                "ST6.md",
                "ST6",
                "All Finished",
                "High",
                "[\"porelo-ai.github.io#SI01\", \"porelo-host#HO148\"]",
                "null");

        story(
                "ST7.md",
                "ST7",
                "Back For Rework",
                "High",
                "[\"porelo-host#HO146\", \"porelo-host#HO149\"]",
                "null");

        assertThat(run("index")).as(said()).isZero();

        assertThat(Files.readString(dir.resolve("README.md")))
                .as(
                        "by state in the indexes' order, the higher priority first, a rejected issue holding none back")
                .contains("Priority: ⏫ Highest · 🔼 High · 🟰 Medium · 🔽 Low · ⏬ Lowest.")
                .contains(
                        "(`B` porelo, `EC` porelo-ecosystems, `HO` porelo-host, `P` porelo, `SI` porelo-ai.github.io)")
                .containsSubsequence(
                        "| State | Stories |",
                        "| [Ready for your test](#ready-for-your-test) | 1 |",
                        "| [Review](#review) | 2 |",
                        "| [Implementing](#implementing) | 1 |",
                        "| [Backlog](#backlog) | 1 |",
                        "| [Idea](#idea) | 1 |",
                        "| [Tested](#tested) | 1 |",
                        "## Ready for your test",
                        "[ST6](ST6.md) All Finished | Ready for your test | 🔼 High | HO:1 · SI:1 |",
                        "## Review",
                        "| <a id=\"st3\"></a>[ST3](ST3.md) Also In Review | Review | ⏫ Highest | HO:1 | A person gains. |",
                        "[ST2](ST2.md) In Review | Review | 🟰 Medium | HO:2 |",
                        "## Implementing",
                        "[ST7](ST7.md) Back For Rework | Implementing | 🔼 High | HO:2 |",
                        "## Backlog",
                        "[ST1](ST1.md) Waits On Backlog | Backlog | 🔽 Low | HO:1 · P:1 |",
                        "## Idea",
                        "[ST4](ST4.md) No Issue Yet | Idea | ⏬ Lowest | none yet |",
                        "## Tested",
                        "[ST5](ST5.md) Done And Tried | Tested | 🔼 High | SI:1 |");
        assertThat(Files.readString(dir.resolve("ST6.md")))
                .contains("**Priority:** 🔼 High · **State:** Ready for your test");
        assertThat(check()).as(said()).isZero();
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

                        **Priority:** 🔽 Low · **Tested:** 2026-11-08

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

        assertThat(run("cut", "0.6.0", "--after", "2026-11-01")).as(said()).isZero();

        assertThat(stdout())
                .isEqualTo(
                        """
                        ## 0.6.0

                        The stories tested after 2026-11-01:

                        - [ST4](https://github.com/porelo-ai/porelo-project/blob/main/stories/ST4.md) Tested Next Day (2026-11-02)
                        - [ST3](https://github.com/porelo-ai/porelo-project/blob/main/stories/ST3-Words.md) Tested After (2026-11-08)
                        """);
    }

    @Test
    void refusesACutWithoutADayOrAVersion() throws IOException {
        story("ST1.md", "ST1", "A Title", "[]", "null");

        assertThat(run("cut", "0.6.0", "--after", "1.11.2026")).isEqualTo(2);
        assertThat(run("cut", "../x", "--after", "2026-11-01")).isEqualTo(2);
        assertThat(stderr()).contains("stories cut <version> <dir> --after <yyyy-mm-dd>");
    }

    private void sets(String repository, String... rows) throws IOException {
        final Path issues = Files.createDirectories(work.resolve(repository).resolve("issues"));
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
        write(
                file,
                "# "
                        + id
                        + " — "
                        + title
                        + "\n\nA gain.\n\n"
                        + head(id, title, priority, issues, tested));
    }

    private static String head(
            String id, String title, String priority, String issues, String tested) {
        return "## Data\n\n```json\n{\n  \"id\": \""
                + id
                + "\",\n  \"title\": \""
                + title
                + "\",\n  \"priority\": \""
                + priority
                + "\",\n  \"gain\": \"A person gains.\",\n  \"test\": \"Run it.\",\n  \"issues\": "
                + issues
                + ",\n  \"tested\": "
                + tested
                + "\n}\n```\n";
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
