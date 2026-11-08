package org.fuin.porelo.release;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StoriesTest {

    @TempDir Path dir;

    private final ByteArrayOutputStream out = new ByteArrayOutputStream();

    private final ByteArrayOutputStream err = new ByteArrayOutputStream();

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
                "[\"porelo#PO412\", \"porelo-frontend#F429\", \"porelo#P08\", \"porelo-ecosystems#EC06\", \"porelo-ai.github.io#SI01\"]",
                "\"2026-11-08\"");

        assertThat(check()).as(said()).isZero();
        assertThat(stdout()).contains("2 story head(s)");
    }

    @Test
    void refusesAHeadThatFailsTheSchemaNamingFileAndField() throws IOException {
        write(
                "ST1.md",
                "# ST1 — A Title\n\n```json\n"
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
        write("ST1.md", "# ST1 — Another Title\n\n" + head("ST1", "A Title", "[]", "null"));

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
        write("ST1.md", "# ST1 — A Title\n\nNo head at all.\n");
        write("ST2.md", "# ST2 — A Title\n\n```json\n{\"id\": \"ST2\",, }\n```\n");

        assertThat(check()).isEqualTo(Stop.REFUSED);
        assertThat(stderr())
                .contains("ST1.md: no ```json head right after the heading")
                .contains("ST2.md: the head is no JSON");
    }

    private int check() {
        final PrintStream o = new PrintStream(out, true, StandardCharsets.UTF_8);
        final PrintStream e = new PrintStream(err, true, StandardCharsets.UTF_8);
        return Main.run(
                new String[] {"stories", "check", dir.toString()},
                o,
                e,
                (address, headers) -> {
                    throw new AssertionError("stories reads nothing over the web");
                },
                Map.of(),
                (tree, manifest, lockfile, work) -> lockfile);
    }

    private void story(String file, String id, String title, String issues, String tested)
            throws IOException {
        write(
                file,
                "# "
                        + id
                        + " — "
                        + title
                        + "\n\n"
                        + head(id, title, issues, tested)
                        + "\nWhat a user gains.\n");
    }

    private static String head(String id, String title, String issues, String tested) {
        return "```json\n{\n  \"id\": \""
                + id
                + "\",\n  \"title\": \""
                + title
                + "\",\n  \"priority\": \"High\",\n  \"gain\": \"A person gains.\",\n  \"test\": \"Run it.\",\n  \"issues\": "
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
