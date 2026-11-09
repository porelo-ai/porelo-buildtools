package org.fuin.porelo.stories;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

record Project(Path root, String own, Map<String, Set<String>> repositories) {
    interface Indexes {
        Map<String, IssueIndex> read(String repository) throws IOException;
    }

    private static final ObjectMapper YAML =
            new ObjectMapper(
                    new YAMLFactory().enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION));
    private static final Pattern UPSTREAM =
            Pattern.compile(
                    "^(?:git@github\\.com:|https://github\\.com/)porelo-ai/([A-Za-z0-9][A-Za-z0-9_.-]*?)(?:\\.git)?$");

    static String own(Path file) throws IOException {
        return repository(required(yaml(file), "project", "project.yml"), "project");
    }

    static Project read(Path file, Indexes indexes) throws IOException {
        Path root = file.toAbsolutePath().normalize().getParent();
        while (root != null && !Files.isRegularFile(root.resolve("project.yml"))) {
            root = root.getParent();
        }
        if (root == null) {
            throw new NoSuchFileException(file.toString(), null, "no project.yml above the plan");
        }
        final JsonNode project = yaml(root.resolve("project.yml"));
        final String own = repository(required(project, "project", "project.yml"), "project");
        final Map<String, Set<String>> repositories = new LinkedHashMap<>();
        final Set<String> ownPrefixes = new HashSet<>();
        indexes.read(own)
                .keySet()
                .forEach(number -> ownPrefixes.add(number.replaceAll("\\d+$", "")));
        repositories.put(own, Set.copyOf(ownPrefixes));
        final JsonNode declared = required(project, "repositories", "project.yml");
        if (!declared.isObject()) {
            throw new IllegalArgumentException("project.yml repositories: expected a mapping");
        }
        final var entries = declared.properties().iterator();
        while (entries.hasNext()) {
            final var entry = entries.next();
            final String where = "project.yml repositories." + entry.getKey();
            final String name = repository(entry.getValue(), where);
            final Set<String> prefixes = new HashSet<>(strings(entry.getValue(), "issues", where));
            if (prefixes.isEmpty()
                    || prefixes.stream().anyMatch(prefix -> !prefix.matches("[A-Z]{1,3}"))) {
                throw new IllegalArgumentException(where + ": issues must name uppercase prefixes");
            }
            if (repositories.putIfAbsent(name, Set.copyOf(prefixes)) != null) {
                throw new IllegalArgumentException(where + ": duplicate repository " + name);
            }
        }
        return new Project(root, own, Map.copyOf(repositories));
    }

    private static JsonNode yaml(Path file) throws IOException {
        try (var input = Files.newInputStream(file);
                var parser = YAML.createParser(input)) {
            final JsonNode data = YAML.readTree(parser);
            if (parser.nextToken() != null) {
                throw new IllegalArgumentException(file + ": expected exactly one YAML document");
            }
            if (data == null || !data.isObject()) {
                throw new IllegalArgumentException(file + ": expected a YAML mapping");
            }
            return data;
        } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
            throw new IllegalArgumentException(
                    file + ": invalid YAML: " + ex.getOriginalMessage(), ex);
        }
    }

    private static JsonNode required(JsonNode data, String key, String where)
            throws IllegalArgumentException {
        final JsonNode value = data.get(key);
        if (value == null || value.isNull()) {
            throw new IllegalArgumentException(where + ": missing required field " + key);
        }
        return value;
    }

    private static String string(JsonNode data, String key, String where)
            throws IllegalArgumentException {
        final JsonNode value = required(data, key, where);
        if (!value.isTextual() || value.textValue().isBlank()) {
            throw new IllegalArgumentException(where + ": " + key + " must be a nonblank string");
        }
        return value.textValue();
    }

    private static JsonNode array(JsonNode data, String key, String where)
            throws IllegalArgumentException {
        final JsonNode value = required(data, key, where);
        if (!value.isArray()) {
            throw new IllegalArgumentException(where + ": " + key + " must be a list");
        }
        return value;
    }

    private static List<String> strings(JsonNode data, String key, String where)
            throws IllegalArgumentException {
        if (!data.has(key)) {
            return List.of();
        }
        final List<String> values = new ArrayList<>();
        for (final JsonNode value : array(data, key, where)) {
            if (!value.isTextual() || value.textValue().isBlank()) {
                throw new IllegalArgumentException(
                        where + ": " + key + " must contain nonblank strings");
            }
            values.add(value.textValue());
        }
        return List.copyOf(values);
    }

    private static String repository(JsonNode data, String where) throws IllegalArgumentException {
        // A project.yml not yet renamed still names the central remote "upstream".
        final String key = data.has("central") ? "central" : "upstream";
        final String central = string(data, key, where);
        final Matcher match = UPSTREAM.matcher(central);
        if (!match.matches()) {
            throw new IllegalArgumentException(
                    where + ": unsupported repository " + key + " " + central);
        }
        return match.group(1);
    }
}
