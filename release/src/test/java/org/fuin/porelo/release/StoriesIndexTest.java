package org.fuin.porelo.release;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import org.fuin.porelo.stories.IssueSource;
import org.fuin.porelo.stories.StoriesIndex;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StoriesIndexTest {
    @TempDir Path work;

    // Prevents the extracted generator from changing existing stories or their durable row links.
    @Test
    void writesWhatTheCommandWrote() throws Exception {
        final Path fixture = Path.of("../stories/src/test/resources/checkouts");
        try (var files = Files.walk(fixture)) {
            for (final Path file : files.filter(Files::isRegularFile).toList()) {
                final Path copy = work.resolve(fixture.relativize(file));
                Files.createDirectories(copy.getParent());
                Files.copy(file, copy);
            }
        }
        final Path project = work.resolve("porelo-project");
        final Path stories = project.resolve("stories");
        try (final ByteArrayOutputStream said = new ByteArrayOutputStream();
                final PrintStream output = new PrintStream(said, true, StandardCharsets.UTF_8)) {
            final int status =
                    Main.run(
                            new String[] {"stories", "index", stories.toString()},
                            output,
                            output,
                            (uri, headers) -> {
                                throw new AssertionError("unexpected network: " + uri);
                            },
                            Map.of(),
                            (tree, manifest, lockfile, root) -> lockfile);
            assertThat(status).as(said.toString(StandardCharsets.UTF_8)).isZero();
            final Map<Path, byte[]> expected = new LinkedHashMap<>();
            try (var files = Files.list(stories)) {
                for (final Path file :
                        files.filter(path -> path.toString().endsWith(".md")).sorted().toList())
                    expected.put(file, Files.readAllBytes(file));
            }
            final java.util.zip.ZipFile golden =
                    new java.util.zip.ZipFile("../stories/src/test/resources/command-output.zip");
            try (golden) {
                Files.delete(stories.resolve("README.md"));
                final StoriesIndex.Result result =
                        StoriesIndex.write(stories, IssueSource.local(project));
                assertThat(result.faults())
                        .as("the fixture has no rejected heads or issues")
                        .isEmpty();
                for (final var file : expected.entrySet()) {
                    assertThat(Files.readAllBytes(file.getKey()))
                            .as("library versus command: %s", file.getKey())
                            .isEqualTo(file.getValue());
                    try (var entry =
                            golden.getInputStream(
                                    golden.getEntry(file.getKey().getFileName().toString()))) {
                        assertMigration(
                                new String(entry.readAllBytes(), StandardCharsets.UTF_8),
                                new String(file.getValue(), StandardCharsets.UTF_8),
                                file.getKey().getFileName().toString());
                    }
                }
                final StoriesIndex.Result checked =
                        StoriesIndex.check(stories, IssueSource.local(project));
                assertThat(checked.faults()).as("a regenerated tree passes check").isEmpty();
                assertThat(checked.written()).as("check writes no files").isEmpty();
                assertThat(result.written())
                        .as("a write reports the README it creates")
                        .contains(stories.resolve("README.md"));
            }
        }
    }

    // Prevents callers treating a malformed configuration as ordinary stale generated prose.
    @Test
    void keepsProjectFailuresAsCommandFailures() throws Exception {
        final Path stories = Files.createDirectories(work.resolve("stories"));
        Files.copy(
                Path.of("../stories/src/test/resources/workflow.yml"),
                stories.resolve("workflow.yml"));
        Files.writeString(
                stories.resolve("ST1.md"),
                """
                # ST1 — A title

                ## Data

                ```json
                {"id":"ST1","title":"A title","state":"Idea","priority":"High","gain":"A gain.","test":"A test.","issues":[],"tested":null}
                ```
                """);
        try (final ByteArrayOutputStream said = new ByteArrayOutputStream();
                final PrintStream output = new PrintStream(said, true, StandardCharsets.UTF_8)) {
            Files.writeString(work.resolve("project.yml"), "project: []\nrepositories: {}\n");
            final Web web =
                    (uri, headers) -> {
                        throw new AssertionError("unexpected network: " + uri);
                    };
            assertThat(new Stories(output, output, web).check(stories))
                    .as("malformed project is refused")
                    .isEqualTo(Stop.REFUSED);
            assertThat(said.toString(StandardCharsets.UTF_8))
                    .as("configuration failures remain direct command errors")
                    .isEqualTo("project: missing required field upstream\n");
            Files.delete(work.resolve("project.yml"));
            said.reset();
            assertThat(new Stories(output, output, web).check(stories))
                    .as("missing project is unanswered")
                    .isEqualTo(Stop.UNANSWERED);
            assertThat(said.toString(StandardCharsets.UTF_8))
                    .as("missing-project diagnostics keep their original form")
                    .isEqualTo(stories.resolve("README.md") + ": no project.yml above the plan\n");
        }
    }

    // Prevents cut from acquiring a dependency on a generated output it never previously read.
    @Test
    void cutIgnoresAnUnreadableGeneratedIndex() throws Exception {
        final Path stories = Files.createDirectories(work.resolve("stories"));
        Files.copy(
                Path.of("../stories/src/test/resources/workflow.yml"),
                stories.resolve("workflow.yml"));
        Files.createDirectories(work.resolve("issues"));
        Files.writeString(work.resolve("issues/README.md"), "# Issues\n");
        Files.writeString(
                work.resolve("project.yml"),
                "project:\n  central: git@github.com:porelo-ai/porelo-project.git\nrepositories: {}\n");
        Files.writeString(
                stories.resolve("ST1.md"),
                """
                # ST1 — A title

                ## Data

                ```json
                {"id":"ST1","title":"A title","state":"Finished","priority":"High","gain":"A gain.","test":"A test.","issues":[],"tested":"2026-11-08"}
                ```
                """);
        Files.write(stories.resolve("README.md"), new byte[] {(byte) 0xc3, (byte) 0x28});
        try (final ByteArrayOutputStream said = new ByteArrayOutputStream();
                final PrintStream output = new PrintStream(said, true, StandardCharsets.UTF_8)) {
            final Web web =
                    (uri, headers) -> {
                        throw new AssertionError("unexpected network: " + uri);
                    };
            assertThat(
                            new Stories(output, output, web)
                                    .cut("0.6.0", stories, java.time.LocalDate.parse("2026-11-01")))
                    .as(said.toString(StandardCharsets.UTF_8))
                    .isZero();
            assertThat(said.toString(StandardCharsets.UTF_8))
                    .as("release notes depend only on the validated heads and issues")
                    .contains(
                            "- [ST1](https://github.com/porelo-ai/porelo-project/blob/main/stories/ST1.md) A title (2026-11-08)");
            assertThat(Files.readAllBytes(stories.resolve("README.md")))
                    .as("cut never repairs outputs")
                    .containsExactly((byte) 0xc3, (byte) 0x28);
        }
    }

    @Test
    void keepsMissingProjectAsAnUnansweredCommand() throws Exception {
        final Path stories = Files.createDirectories(work.resolve("stories"));
        Files.copy(
                Path.of("../stories/src/test/resources/workflow.yml"),
                stories.resolve("workflow.yml"));
        try (final ByteArrayOutputStream said = new ByteArrayOutputStream();
                final PrintStream output = new PrintStream(said, true, StandardCharsets.UTF_8)) {
            final Web web =
                    (uri, headers) -> {
                        throw new AssertionError("unexpected network: " + uri);
                    };
            assertThat(new Stories(output, output, web).check(stories))
                    .as("missing project is unanswered")
                    .isEqualTo(Stop.UNANSWERED);
            assertThat(said.toString(StandardCharsets.UTF_8))
                    .as("missing-project diagnostics keep their original form")
                    .isEqualTo(stories.resolve("README.md") + ": no project.yml above the plan\n");
        }
    }

    private static final Map<String, String> COMPUTED_CHANGES =
            Map.ofEntries(
                    Map.entry("ST2", "Finished"),
                    Map.entry("ST5", "Review"),
                    Map.entry("ST12", "Review"),
                    Map.entry("ST26", "Rework"),
                    Map.entry("ST42", "Rejected"),
                    Map.entry("ST105", "Rejected"),
                    Map.entry("ST118", "Finished"),
                    Map.entry("ST145", "Finished"),
                    Map.entry("ST193", "Backlog"),
                    Map.entry("ST194", "Backlog"),
                    Map.entry("ST196", "Rejected"),
                    Map.entry("ST201", "Rejected"),
                    Map.entry("ST290", "Idea"),
                    Map.entry("ST292", "Implementing"),
                    Map.entry("ST294", "Analyzed"));

    private static void assertMigration(String original, String actual, String name)
            throws Exception {
        if (name.equals("README.md")) {
            final Map<String, String> oldStates = new LinkedHashMap<>();
            final Map<String, String> newStates = new LinkedHashMap<>();
            assertThat(storyRows(actual, newStates))
                    .as("all 294 durable rows retain their bytes")
                    .isEqualTo(storyRows(original, oldStates))
                    .hasSize(294);
            final Map<String, String> expected = new LinkedHashMap<>(oldStates);
            expected.putAll(COMPUTED_CHANGES);
            assertThat(newStates).as("exactly the measured state changes").isEqualTo(expected);
            final java.util.List<String> counts =
                    actual.lines()
                            .filter(line -> line.startsWith("| [") && !line.startsWith("| [ST"))
                            .toList();
            final var workflow =
                    org.fuin.porelo.stories.Workflow.read(
                            Path.of("../stories/src/test/resources/workflow.yml"));
            assertThat(counts)
                    .isEqualTo(
                            workflow.states().stream()
                                    .filter(state -> expected.containsValue(state))
                                    .map(
                                            state ->
                                                    "| ["
                                                            + state
                                                            + "](#"
                                                            + state.toLowerCase(
                                                                    java.util.Locale.ROOT)
                                                            + ") | "
                                                            + expected.values().stream()
                                                                    .filter(state::equals)
                                                                    .count()
                                                            + " |")
                                    .toList());
            return;
        }
        final var json = new com.fasterxml.jackson.databind.ObjectMapper();
        final var before =
                (com.fasterxml.jackson.databind.node.ObjectNode)
                        json.readTree(
                                original.substring(
                                        original.lastIndexOf("```json\n") + 8,
                                        original.lastIndexOf("```")));
        final var after =
                json.readTree(
                        actual.substring(
                                actual.lastIndexOf("```json\n") + 8, actual.lastIndexOf("```")));
        final String id = before.get("id").asText();
        final String stated = before.get("state").asText();
        final String expectedState = COMPUTED_CHANGES.getOrDefault(id, stated);
        assertThat(actual.substring(0, actual.lastIndexOf("## Data")))
                .as("readable bytes retained except computed state: %s", name)
                .isEqualTo(
                        original.substring(0, original.lastIndexOf("## Data"))
                                .replace("**State:** " + stated, "**State:** " + expectedState));
        if (!before.get("issues").isEmpty()) before.remove("state");
        before.put("$schema", "story-2.json");
        assertThat(after)
                .as("head only drops state and declares story-2: %s", name)
                .isEqualTo(before);
    }

    private static Map<String, String> storyRows(String page, Map<String, String> states) {
        String state = "";
        final Map<String, String> rows = new LinkedHashMap<>();
        for (String line : page.split("\n")) {
            if (line.startsWith("## ")) state = line.substring(3);
            final var id =
                    java.util.regex.Pattern.compile("^\\| <a id=\"st[0-9]+\"></a>\\[(ST[0-9]+)]")
                            .matcher(line);
            if (id.find()) {
                rows.put(id.group(1), line);
                states.put(id.group(1), state);
            }
        }
        return rows;
    }
}
