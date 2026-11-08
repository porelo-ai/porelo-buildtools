package org.fuin.porelo.release;

import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.PathType;
import com.networknt.schema.SchemaValidatorsConfig;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * Reads the JSON head of a Markdown file: its first line is the heading, and the file ends in a
 * section {@code ## Data} holding the head fenced {@code ```json}, checked by a JSON schema. The
 * text a person reads comes first. A story's file has one; an issue's will have one too, with a
 * schema of its own.
 */
final class Heads {

    /**
     * A file's head.
     *
     * @param file the file
     * @param heading its first line
     * @param data the head
     * @param lines the file's lines
     */
    record Head(Path file, String heading, JsonNode data, List<String> lines) {}

    /** The section the head stands in, the file's last. */
    static final String DATA = "## Data";

    private static final ObjectMapper JSON =
            new ObjectMapper()
                    .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
                    .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);

    private Heads() {}

    /**
     * Loads a schema this tool carries.
     *
     * @param resource where it is on the class path
     * @return the schema
     * @throws IOException when it is not there
     */
    static JsonSchema schema(String resource) throws IOException {
        try (InputStream in = Heads.class.getResourceAsStream(resource)) {
            if (in == null) {
                throw new IOException("the schema " + resource + " is not on the class path");
            }
            // In English whatever the machine's language, so a refusal reads alike everywhere; a
            // field named as a JSON pointer.
            return JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012)
                    .getSchema(
                            new String(in.readAllBytes(), StandardCharsets.UTF_8),
                            SchemaValidatorsConfig.builder()
                                    .locale(Locale.ENGLISH)
                                    .pathType(PathType.JSON_POINTER)
                                    .build());
        }
    }

    /**
     * Reads a file's head and checks it against the schema.
     *
     * @param file the file
     * @param schema its schema
     * @param faults where each fault is added, naming the file and the field
     * @return the head, or {@code null} when it could not be read or failed the schema
     * @throws IOException when the file cannot be read
     */
    static @Nullable Head read(Path file, JsonSchema schema, List<String> faults)
            throws IOException {
        final String name = String.valueOf(file.getFileName());
        final List<String> lines =
                List.of(Files.readString(file, StandardCharsets.UTF_8).split("\n", -1));
        if (!lines.getFirst().startsWith("# ")) {
            faults.add(name + ": the first line is no heading");
            return null;
        }
        int end = lines.size();
        while (end > 1 && lines.get(end - 1).isBlank()) {
            end--;
        }
        final int section = lines.lastIndexOf(DATA);
        int open = section + 1;
        while (section > 0 && open < end && lines.get(open).isBlank()) {
            open++;
        }
        if (section <= 0
                || open >= end
                || !lines.get(open).equals("```json")
                || !lines.get(end - 1).equals("```")
                || end - 1 <= open) {
            faults.add(name + ": no ```json head under " + DATA + " at the end");
            return null;
        }
        // A JSON string holds no raw line break, so no line of the head is three backticks alone.
        final int close = end - 1 - (open + 1);
        final JsonNode data;
        try {
            data = JSON.readTree(String.join("\n", lines.subList(open + 1, open + 1 + close)));
        } catch (JacksonException ex) {
            faults.add(name + ": the head is no JSON: " + ex.getOriginalMessage());
            return null;
        }
        final Set<ValidationMessage> errors = schema.validate(data);
        for (final ValidationMessage error : errors) {
            // The message opens with the field's pointer, empty for the head as a whole.
            final String message = error.getMessage();
            faults.add(name + ": " + (message.startsWith(":") ? "/" + message : message));
        }
        return errors.isEmpty() ? new Head(file, lines.getFirst(), data, lines) : null;
    }
}
