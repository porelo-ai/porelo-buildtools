package org.fuin.porelo.stories;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.PathType;
import com.networknt.schema.SchemaValidatorsConfig;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;

class StorySchemaTest {
    private static final ObjectMapper JSON = new ObjectMapper();

    // Prevents malformed heads passing a library check that the documented schema refuses.
    @Test
    void agreesWithTheSchemaOnEveryFixtureHeadAndFaultyHeads() throws Exception {
        final JsonSchema schema;
        try (var in =
                getClass().getResourceAsStream("/org/fuin/porelo/stories/schemas/story-1.json")) {
            schema =
                    JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012)
                            .getSchema(
                                    in,
                                    SchemaValidatorsConfig.builder()
                                            .locale(Locale.ENGLISH)
                                            .pathType(PathType.JSON_POINTER)
                                            .build());
        }
        final List<JsonNode> heads = new ArrayList<>();
        final List<JsonNode> faultyHeads = new ArrayList<>();
        try (var files =
                Files.list(Path.of("src/test/resources/checkouts/porelo-project/stories"))) {
            for (final Path file :
                    files.filter(f -> !f.getFileName().toString().equals("README.md"))
                            .sorted()
                            .toList()) {
                final String text = Files.readString(file);
                final int start = text.lastIndexOf("```json\n") + "```json\n".length();
                heads.add(JSON.readTree(text.substring(start, text.lastIndexOf("```"))));
            }
        }
        assertThat(heads).as("all story heads in the fixture copy").hasSize(294);
        final JsonNode valid = heads.getFirst();
        for (final String field :
                List.of("id", "title", "state", "priority", "gain", "test", "issues", "tested")) {
            final ObjectNode missing = valid.deepCopy();
            missing.remove(field);
            heads.add(missing);
            faultyHeads.add(missing);
            for (final String value :
                    List.of("null", "false", "42", "{}", "[]", "\"\"", "\" \"", "\"bad\\nline\"")) {
                final ObjectNode wrong = valid.deepCopy();
                wrong.set(field, JSON.readTree(value));
                heads.add(wrong);
                if (!(field.equals("tested") && value.equals("null"))
                        && !(field.equals("issues") && value.equals("[]"))) faultyHeads.add(wrong);
            }
        }
        for (final String faulty : List.of("null", "[]", "42", "{}")) {
            final JsonNode head = JSON.readTree(faulty);
            heads.add(head);
            faultyHeads.add(head);
        }
        for (final String patch :
                List.of(
                        "{\"extra\":1}",
                        "{\"id\":\"ST01\"}",
                        "{\"id\":\"ST100000\"}",
                        "{\"tested\":\"2026-13-01\"}",
                        "{\"tested\":\"2026-02-31\"}",
                        "{\"issues\":[\"porelo#AB01\",\"porelo#AB01\"]}",
                        "{\"issues\":[\"HO146\"]}")) {
            final ObjectNode wrong = valid.deepCopy();
            wrong.setAll((ObjectNode) JSON.readTree(patch));
            heads.add(wrong);
            if (!patch.contains("2026-02-31")) faultyHeads.add(wrong);
        }
        try (var faulty = Files.list(Path.of("src/test/resources/faulty-heads"))) {
            final var files = faulty.sorted().toList();
            assertThat(files).as("persisted faulty heads").hasSize(3);
            for (final Path file : files) {
                final JsonNode head = JSON.readTree(Files.readString(file));
                heads.add(head);
                faultyHeads.add(head);
            }
        }
        for (final String field : List.of("title", "gain", "test")) {
            final int max = field.equals("title") ? 120 : 400;
            for (final String value :
                    List.of(
                            "x".repeat(max),
                            "x".repeat(max + 1),
                            "😀".repeat(max),
                            "😀".repeat(max + 1),
                            "line\nline",
                            " leading",
                            "trailing ",
                            "x\u2028x",
                            "a\tb")) {
                final ObjectNode head = valid.deepCopy();
                head.put(field, value);
                heads.add(head);
                if (!List.of("x".repeat(max), "😀".repeat(max), "a\tb").contains(value))
                    faultyHeads.add(head);
            }
        }
        final ObjectNode tooMany = valid.deepCopy();
        final var issues = tooMany.putArray("issues");
        for (int i = 0; i < 201; i++) issues.add("porelo#AB" + (100 + i));
        heads.add(tooMany);
        faultyHeads.add(tooMany);
        final ObjectNode badItems = valid.deepCopy();
        badItems.putArray("issues").add(1).add(1).add(false).addNull();
        heads.add(badItems);
        faultyHeads.add(badItems);
        for (final String value : List.of("ST0", "ST99999", "ST100000")) {
            final ObjectNode head = valid.deepCopy();
            head.put("id", value);
            heads.add(head);
            if (!value.equals("ST99999")) faultyHeads.add(head);
        }
        final ObjectNode objectIssues = valid.deepCopy();
        objectIssues.set("issues", JSON.readTree("{\"a\":1,\"b\":1}"));
        heads.add(objectIssues);
        faultyHeads.add(objectIssues);
        final JsonNode missingJson = JSON.readTree("");
        heads.add(missingJson);
        faultyHeads.add(missingJson);
        assertThat(faultyHeads)
                .as("the independent set of deliberately faulty ordinary heads")
                .hasSize(107);
        for (final JsonNode faulty : faultyHeads)
            assertThat(StorySchema.faults(faulty))
                    .as("every faulty ordinary head has a handwritten fault: %s", faulty)
                    .isNotEmpty();
        for (final JsonNode head : heads) {
            final List<String> expected =
                    schema.validate(head).stream()
                            .map(ValidationMessage::getMessage)
                            .map(message -> message.startsWith(":") ? "/" + message : message)
                            .toList();
            assertThat(StorySchema.faults(head).isEmpty())
                    .as("same verdict for %s", head)
                    .isEqualTo(expected.isEmpty());
            assertThat(StorySchema.faults(head))
                    .as("same ordered diagnostics for %s", head)
                    .isEqualTo(expected);
        }
    }

    private JsonSchema schema() throws Exception {
        try (var in =
                getClass().getResourceAsStream("/org/fuin/porelo/stories/schemas/story-1.json")) {
            return JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012)
                    .getSchema(
                            in,
                            SchemaValidatorsConfig.builder()
                                    .locale(Locale.ENGLISH)
                                    .pathType(PathType.JSON_POINTER)
                                    .build());
        }
    }

    private ObjectNode validHead() throws Exception {
        return (ObjectNode)
                JSON.readTree(
                        """
                {"id":"ST1","title":"A title","state":"Idea","priority":"High","gain":"A gain.","test":"A test.","issues":[],"tested":null}
                """);
    }

    // Deliberate difference 1: no undeclared property is accepted, including a hash-prefixed one.
    @Test
    void refusesUndeclaredHashPropertiesThatNetworkntAccepts() throws Exception {
        final JsonSchema oracle = schema();
        for (final String name : List.of("#extra", "#")) {
            final ObjectNode head = validHead();
            head.put(name, "unexpected");
            assertThat(oracle.validate(head))
                    .as("networknt's undeclared-hash-property quirk")
                    .isEmpty();
            assertThat(StorySchema.faults(head))
                    .as("every faulty undeclared-property head is refused")
                    .isNotEmpty()
                    .anyMatch(fault -> fault.contains(name));
        }
    }

    // Deliberate difference 2: every pattern consumes the whole value, including final line
    // terminators.
    @Test
    void refusesTrailingTerminatorsThatNetworkntAccepts() throws Exception {
        final JsonSchema oracle = schema();
        for (final String field : List.of("id", "title", "gain", "test", "issues", "tested")) {
            for (final String end : List.of("\n", "\r")) {
                final ObjectNode head = validHead();
                switch (field) {
                    case "id" -> head.put(field, "ST1" + end);
                    case "issues" -> head.putArray(field).add("porelo#AB01" + end);
                    case "tested" -> head.put(field, "2026-11-08" + end);
                    default -> head.put(field, "x" + end);
                }
                assertThat(oracle.validate(head))
                        .as("networknt's final-line-terminator quirk for %s", field)
                        .isEmpty();
                assertThat(StorySchema.faults(head))
                        .as("every faulty pattern head is refused: %s", head)
                        .isNotEmpty();
            }
        }
    }
}
