package org.fuin.porelo.release;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The project above a file, read from its {@code project.yml}, and the issue prefixes of its
 * repositories.
 */
record ProjectFile(Path root, String own, Map<String, Set<String>> repositories) {
    private static final ObjectMapper YAML =
            new ObjectMapper(
                    new YAMLFactory().enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION));
    private static final Pattern UPSTREAM =
            Pattern.compile(
                    "^(?:git@github\\.com:|https://github\\.com/)porelo-ai/([A-Za-z0-9][A-Za-z0-9_.-]*?)(?:\\.git)?$");

    static ProjectFile above(Path file, IssueLinks links) throws IOException, Stop {
        Path root = file.toAbsolutePath().normalize().getParent();
        while (root != null && !Files.isRegularFile(root.resolve("project.yml"))) {
            root = root.getParent();
        }
        if (root == null) {
            throw Stop.unanswered(file + ": no project.yml above it");
        }
        final JsonNode project = yaml(root.resolve("project.yml"));
        final String own = repository(required(project, "project", "project.yml"), "project");
        links.tree(own, root);
        final Map<String, Set<String>> repositories = new LinkedHashMap<>();
        final Set<String> ownPrefixes = new HashSet<>();
        links.issueIndex(root, own)
                .keySet()
                .forEach(number -> ownPrefixes.add(number.replaceAll("\\d+$", "")));
        repositories.put(own, Set.copyOf(ownPrefixes));
        final JsonNode declared = required(project, "repositories", "project.yml");
        if (!declared.isObject()) {
            throw Stop.refused("project.yml repositories: expected a mapping");
        }
        final var entries = declared.properties().iterator();
        while (entries.hasNext()) {
            final var entry = entries.next();
            final String where = "project.yml repositories." + entry.getKey();
            final String name = repository(entry.getValue(), where);
            final Set<String> prefixes = new HashSet<>(strings(entry.getValue(), "issues", where));
            if (prefixes.isEmpty()
                    || prefixes.stream().anyMatch(prefix -> !prefix.matches("[A-Z]{1,3}"))) {
                throw Stop.refused(where + ": issues must name uppercase prefixes");
            }
            if (repositories.putIfAbsent(name, Set.copyOf(prefixes)) != null) {
                throw Stop.refused(where + ": duplicate repository " + name);
            }
        }
        return new ProjectFile(root, own, Map.copyOf(repositories));
    }

    private static JsonNode yaml(Path file) throws IOException, Stop {
        try (var input = Files.newInputStream(file);
                var parser = YAML.createParser(input)) {
            final JsonNode data = YAML.readTree(parser);
            if (parser.nextToken() != null) {
                throw Stop.refused(file + ": expected exactly one YAML document");
            }
            if (data == null || !data.isObject()) {
                throw Stop.refused(file + ": expected a YAML mapping");
            }
            return data;
        } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
            throw Stop.refused(file + ": invalid YAML: " + ex.getOriginalMessage(), ex);
        }
    }

    private static JsonNode required(JsonNode data, String key, String where) throws Stop {
        final JsonNode value = data.get(key);
        if (value == null || value.isNull()) {
            throw Stop.refused(where + ": missing required field " + key);
        }
        return value;
    }

    private static String string(JsonNode data, String key, String where) throws Stop {
        final JsonNode value = required(data, key, where);
        if (!value.isTextual() || value.textValue().isBlank()) {
            throw Stop.refused(where + ": " + key + " must be a nonblank string");
        }
        return value.textValue();
    }

    private static List<String> strings(JsonNode data, String key, String where) throws Stop {
        if (!data.has(key)) {
            return List.of();
        }
        final JsonNode value = required(data, key, where);
        if (!value.isArray()) {
            throw Stop.refused(where + ": " + key + " must be a list");
        }
        final List<String> values = new ArrayList<>();
        for (final JsonNode each : value) {
            if (!each.isTextual() || each.textValue().isBlank()) {
                throw Stop.refused(where + ": " + key + " must contain nonblank strings");
            }
            values.add(each.textValue());
        }
        return List.copyOf(values);
    }

    private static String repository(JsonNode data, String where) throws Stop {
        // A project.yml not yet renamed still names the central remote "upstream".
        final String key = data.has("central") ? "central" : "upstream";
        final String central = string(data, key, where);
        final Matcher match = UPSTREAM.matcher(central);
        if (!match.matches()) {
            throw Stop.refused(where + ": unsupported repository " + key + " " + central);
        }
        return match.group(1);
    }
}
