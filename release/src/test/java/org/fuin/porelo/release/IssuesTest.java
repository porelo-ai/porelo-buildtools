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

class IssuesTest {

    @TempDir Path work;

    private Path issues;

    private final ByteArrayOutputStream out = new ByteArrayOutputStream();

    private final ByteArrayOutputStream err = new ByteArrayOutputStream();

    @BeforeEach
    void repository() throws IOException {
        issues = Files.createDirectories(work.resolve("issues"));
        Files.copy(
                Path.of("../stories/src/test/resources/workflow.yml"),
                issues.resolve("workflow.yml"));
        Files.writeString(
                issues.resolve("README.md"),
                """
                # Issues

                ## Review

                | # | Size | Blocked by | What it covers |
                |---|---|---|---|
                | <a id="ex12"></a>[EX12](EX12-Twelve.md) | S | - | Twelve. |
                """);
        Files.writeString(
                issues.resolve("EX12-Twelve.md"),
                "# EX12 — Twelve\n\n**Status:** Review.\n**Size:** S - one file.\n\nText.\n");
    }

    // Prevents a conversion, an index and a check from reaching a caller under one wrong exit code.
    @Test
    void convertsIndexesAndChecksThroughTheCommandLine() throws IOException {
        assertThat(run("issues", "check", issues.toString()))
                .as("an issue without a head is refused")
                .isEqualTo(Stop.REFUSED);
        assertThat(stderr()).contains("EX12-Twelve.md: no head right below the heading");

        assertThat(run("issues", "convert", issues.toString())).as(stderr()).isZero();
        assertThat(stdout()).contains("EX12-Twelve.md: head written");
        assertThat(run("issues", "convert", issues.toString()))
                .as("a second run has nothing to do")
                .isZero();
        assertThat(stdout()).contains("nothing to convert");

        assertThat(run("issues", "index", issues.toString())).as(stderr()).isZero();
        assertThat(run("issues", "check", issues.toString())).as(stderr()).isZero();

        final Path readme = issues.resolve("README.md");
        Files.writeString(readme, Files.readString(readme).replace("Twelve.", "Twelve!"));
        assertThat(run("issues", "check", issues.toString()))
                .as("a hand-edited index is refused")
                .isEqualTo(Stop.REFUSED);
        assertThat(stderr()).contains("README.md:", "never edited by hand");
    }

    // Prevents a file whose lines and row disagree from being converted, or passing as converted.
    @Test
    void aConversionWithDifferencesFailsAndNamesEach() throws IOException {
        Files.writeString(
                issues.resolve("EX12-Twelve.md"),
                "# EX12 — Twelve\n\n**Status:** Backlog.\n**Size:** M - two.\n\nText.\n");

        assertThat(run("issues", "convert", issues.toString())).isEqualTo(Stop.REFUSED);

        assertThat(stderr())
                .contains(
                        "EX12-Twelve.md: **Status:** says Backlog, the index row stands under"
                                + " Review",
                        "EX12-Twelve.md: **Size:** says M, the index row S",
                        "2 difference(s)");
        assertThat(Files.readString(issues.resolve("EX12-Twelve.md"))).contains("**Status:**");
    }

    // Prevents a mistyped call from being read as a refused check.
    @Test
    void aMistypedCallIsAUsageError() {
        assertThat(run("issues", "render", issues.toString())).isEqualTo(Stop.UNANSWERED);
        assertThat(run("issues", "check")).isEqualTo(Stop.UNANSWERED);
        assertThat(stderr()).contains("usage: issues index <dir> | issues check <dir>");
    }

    // Prevents a missing workflow from passing as an empty check.
    @Test
    void anUnreadableDirectoryIsUnanswered() throws IOException {
        Files.delete(issues.resolve("workflow.yml"));
        assertThat(run("issues", "check", issues.toString())).isEqualTo(Stop.UNANSWERED);
        assertThat(stderr()).contains("workflow.yml");
    }

    private int run(String... arguments) {
        out.reset();
        err.reset();
        return Main.run(
                arguments,
                new PrintStream(out, true, StandardCharsets.UTF_8),
                new PrintStream(err, true, StandardCharsets.UTF_8),
                null,
                Map.of(),
                null);
    }

    private String stdout() {
        return out.toString(StandardCharsets.UTF_8);
    }

    private String stderr() {
        return err.toString(StandardCharsets.UTF_8);
    }
}
