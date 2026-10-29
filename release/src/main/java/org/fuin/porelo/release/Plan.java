package org.fuin.porelo.release;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/**
 * A release's ordered sprints and the functions a person can test, with the waits between issues
 * that every release's plan shares.
 */
record Plan(String release, String goal, List<Sprint> sprints, Map<String, List<String>> after) {
    /** The file beside the release directories that keeps which issue waits for which, once. */
    static final String AFTER = "after.yml";

    private static final ObjectMapper YAML =
            new ObjectMapper(
                    new YAMLFactory().enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION));
    private static final Pattern UPSTREAM =
            Pattern.compile(
                    "^(?:git@github\\.com:|https://github\\.com/)porelo-ai/([A-Za-z0-9][A-Za-z0-9_.-]*?)(?:\\.git)?$");

    /** A sprint keeps its functions in the order the plan assigns them. */
    record Sprint(String id, String title, String goal, List<Function> functions) {}

    /** A date records a person's test; notes are work not filed yet. */
    record Function(
            String title,
            String test,
            @Nullable String tested,
            List<String> issues,
            List<String> covers,
            List<String> notes) {}

    /** Where an issue is placed: its release, that release's place among all, and its sprint. */
    record Placement(String release, int order, String sprint, int index) {
        /** Whether this place comes after another, by release and then by sprint. */
        boolean isAfter(Placement other) {
            return order != other.order ? order > other.order : index > other.index;
        }
    }

    /**
     * Every release's places, and the requirements some release names under {@code covers}: the
     * acceptance of a whole, which nothing waits for in place of its part.
     */
    record Releases(Map<String, Placement> placed, Set<String> covered) {}

    /** The project that owns the plan and the prefixes its repositories declare. */
    record Project(Path root, String own, Map<String, Set<String>> repositories) {}

    static Project project(Path file, IssueLinks links) throws IOException, Stop {
        Path root = file.toAbsolutePath().normalize().getParent();
        while (root != null && !Files.isRegularFile(root.resolve("project.yml"))) {
            root = root.getParent();
        }
        if (root == null) {
            throw Stop.unanswered(file + ": no project.yml above the plan");
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
        return new Project(root, own, Map.copyOf(repositories));
    }

    static Plan read(Path file, Project project) throws IOException, Stop {
        final JsonNode data = yaml(file);
        keys(data, Set.of("release", "goal", "sprints"), file.toString());
        final String release = string(data, "release", file.toString());
        if (!release.matches("[A-Za-z0-9][A-Za-z0-9._-]*")) {
            throw Stop.refused(file + ": release is not a file name: " + release);
        }
        // The page is written as README.md beside the plan, so a plan elsewhere would replace
        // another
        // directory's README.
        final Path folder = file.toAbsolutePath().normalize().getParent();
        if (!file.getFileName().toString().equals("plan.yml")
                || folder == null
                || !folder.getFileName().toString().equals(release)) {
            throw Stop.refused(file + ": a plan must be " + release + "/plan.yml");
        }
        final String goal = string(data, "goal", file.toString());
        final List<Sprint> sprints = new ArrayList<>();
        final Set<String> ids = new HashSet<>();
        for (final JsonNode sprint : array(data, "sprints", file.toString())) {
            final String id = string(sprint, "id", file + " sprint");
            final String where = file + " sprint " + id;
            if (!id.matches("[A-Za-z0-9][A-Za-z0-9._-]*")) {
                throw Stop.refused(where + ": sprint id is not a file name");
            }
            keys(sprint, Set.of("id", "title", "goal", "functions"), where);
            if (!ids.add(id)) {
                throw Stop.refused(where + ": duplicate sprint id");
            }
            final String title = string(sprint, "title", where);
            final String sprintGoal = string(sprint, "goal", where);
            final List<Function> functions = new ArrayList<>();
            final Set<String> titles = new HashSet<>();
            for (final JsonNode function : array(sprint, "functions", where)) {
                final String functionTitle = string(function, "title", where + " function");
                final String place = where + " function " + functionTitle;
                keys(
                        function,
                        Set.of("title", "test", "tested", "issues", "covers", "notes"),
                        place);
                if (!titles.add(functionTitle)) {
                    throw Stop.refused(place + ": duplicate function title");
                }
                final String test = string(function, "test", place);
                final String tested = optional(function, "tested", place);
                if (tested != null) {
                    date(tested, place);
                }
                final List<String> issues = strings(function, "issues", place);
                for (final String named : issues) {
                    reference(named, project, place);
                }
                final List<String> covers = strings(function, "covers", place);
                for (final String named : covers) {
                    reference(named, project, place);
                }
                functions.add(
                        new Function(
                                functionTitle,
                                test,
                                tested,
                                issues,
                                covers,
                                strings(function, "notes", place)));
            }
            sprints.add(new Sprint(id, title, sprintGoal, List.copyOf(functions)));
        }
        return new Plan(release, goal, List.copyOf(sprints), after(folder, project));
    }

    /**
     * Returns where each issue is placed among every release beside a plan, the releases ordered by
     * their numbers; this plan's place wins over another release's.
     *
     * @param file the release's plan
     * @param plan the plan read from it
     * @param project the project the plans belong to
     * @return each placed issue, under {@code issues} or {@code covers}, and its place, with the
     *     issues any release covers
     */
    static Releases releases(Path file, Plan plan, Project project) throws IOException, Stop {
        final Path folder = file.toAbsolutePath().normalize().getParent();
        final Path releases = folder == null ? null : folder.getParent();
        final List<Plan> plans = new ArrayList<>(List.of(plan));
        if (releases != null) {
            try (var directories = Files.list(releases)) {
                for (final Path other :
                        directories
                                .filter(directory -> !directory.equals(folder))
                                .map(directory -> directory.resolve("plan.yml"))
                                .filter(Files::isRegularFile)
                                .sorted()
                                .toList()) {
                    plans.add(read(other, project));
                }
            }
        }
        final List<String> order =
                plans.stream().map(Plan::release).sorted(Plan::compareReleases).toList();
        final Map<String, Placement> placed = new LinkedHashMap<>();
        final Set<String> covered = new HashSet<>();
        for (final Plan each : plans) {
            final int at = order.indexOf(each.release());
            for (int i = 0; i < each.sprints().size(); i++) {
                final Sprint sprint = each.sprints().get(i);
                for (final Function function : sprint.functions()) {
                    covered.addAll(function.covers());
                    for (final String named : placedIn(function)) {
                        placed.putIfAbsent(
                                named, new Placement(each.release(), at, sprint.id(), i));
                    }
                }
            }
        }
        return new Releases(java.util.Collections.unmodifiableMap(placed), Set.copyOf(covered));
    }

    /** The issues a function places: its own and the parents it covers. */
    static List<String> placedIn(Function function) {
        final List<String> named = new ArrayList<>(function.issues());
        named.addAll(function.covers());
        return named;
    }

    /** Orders releases by their numbers, part by part, so that 0.10 comes after 0.9. */
    private static int compareReleases(String one, String other) {
        final String[] mine = one.split("[.-]");
        final String[] theirs = other.split("[.-]");
        for (int i = 0; i < Math.min(mine.length, theirs.length); i++) {
            final int compared =
                    mine[i].matches("\\d+") && theirs[i].matches("\\d+")
                            ? new java.math.BigInteger(mine[i])
                                    .compareTo(new java.math.BigInteger(theirs[i]))
                            : mine[i].compareTo(theirs[i]);
            if (compared != 0) {
                return compared;
            }
        }
        return Integer.compare(mine.length, theirs.length);
    }

    /**
     * Returns the file of waits for a plan: beside its release's directory.
     *
     * @param file the release's plan
     * @return {@code plan/after.yml} for {@code plan/<release>/plan.yml}
     */
    static Path afterFile(Path file) {
        final Path folder = file.toAbsolutePath().normalize().getParent();
        // A plan is always <release>/plan.yml, so a plan at a root has no release directory.
        return folder == null ? Path.of(AFTER) : folder.resolveSibling(AFTER);
    }

    /** Reads the waits beside the release directories; none when the file is not there. */
    private static Map<String, List<String>> after(Path folder, Project project)
            throws IOException, Stop {
        final Path file = folder.resolveSibling(AFTER);
        if (Files.notExists(file, java.nio.file.LinkOption.NOFOLLOW_LINKS)) {
            return Map.of();
        }
        // A directory, a broken link or a file that cannot be looked at is not taken for no waits.
        if (!Files.isRegularFile(file)) {
            throw Stop.refused(file + ": not a readable file of waits");
        }
        final JsonNode data = yaml(file);
        keys(data, Set.of("after"), file.toString());
        final JsonNode waits = required(data, "after", file.toString());
        if (!waits.isObject()) {
            throw Stop.refused(file + ": after must be a mapping of an issue to what it waits for");
        }
        final Map<String, List<String>> after = new LinkedHashMap<>();
        final var entries = waits.properties().iterator();
        while (entries.hasNext()) {
            final var entry = entries.next();
            final String issue = entry.getKey();
            final String where = file + " " + issue;
            reference(issue, project, where);
            final List<String> targets = strings(waits, issue, where);
            if (targets.isEmpty()) {
                throw Stop.refused(where + ": waits for nothing; remove the entry");
            }
            final Set<String> seen = new HashSet<>();
            for (final String target : targets) {
                reference(target, project, where);
                if (target.equals(issue)) {
                    throw Stop.refused(where + ": waits for itself");
                }
                if (!seen.add(target)) {
                    throw Stop.refused(where + ": waits for " + target + " twice");
                }
            }
            after.put(issue, targets);
        }
        return java.util.Collections.unmodifiableMap(after);
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

    private static void keys(JsonNode data, Set<String> allowed, String where) throws Stop {
        final var names = data.fieldNames();
        while (names.hasNext()) {
            final String name = names.next();
            if (!allowed.contains(name)) {
                throw Stop.refused(where + ": unknown field " + name);
            }
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

    private static @Nullable String optional(JsonNode data, String key, String where) throws Stop {
        return data.has(key) ? string(data, key, where) : null;
    }

    private static JsonNode array(JsonNode data, String key, String where) throws Stop {
        final JsonNode value = required(data, key, where);
        if (!value.isArray()) {
            throw Stop.refused(where + ": " + key + " must be a list");
        }
        return value;
    }

    private static List<String> strings(JsonNode data, String key, String where) throws Stop {
        if (!data.has(key)) {
            return List.of();
        }
        final List<String> values = new ArrayList<>();
        for (final JsonNode value : array(data, key, where)) {
            if (!value.isTextual() || value.textValue().isBlank()) {
                throw Stop.refused(where + ": " + key + " must contain nonblank strings");
            }
            values.add(value.textValue());
        }
        return List.copyOf(values);
    }

    private static String repository(JsonNode data, String where) throws Stop {
        final String upstream = string(data, "upstream", where);
        final Matcher match = UPSTREAM.matcher(upstream);
        if (!match.matches()) {
            throw Stop.refused(where + ": unsupported repository upstream " + upstream);
        }
        return match.group(1);
    }

    private static void reference(String named, Project project, String where) throws Stop {
        final String[] parts = named.split("#", -1);
        if (parts.length != 2) {
            throw Stop.refused(where + ": expected <repository>#<number>, got " + named);
        }
        final Set<String> prefixes = project.repositories().get(parts[0]);
        if (prefixes == null) {
            throw Stop.refused(where + ": unknown repository in " + named);
        }
        if (prefixes.stream().noneMatch(prefix -> parts[1].matches(prefix + "\\d{2,3}"))) {
            throw Stop.refused(where + ": number matches no repository prefix: " + named);
        }
    }

    private static void date(String value, String where) throws Stop {
        try {
            if (!value.matches("\\d{4}-\\d{2}-\\d{2}")) {
                throw new DateTimeParseException("expected yyyy-MM-dd", value, 0);
            }
            LocalDate.parse(value);
        } catch (DateTimeParseException ex) {
            throw Stop.refused(where + ": tested must be a date as yyyy-MM-dd, got " + value, ex);
        }
    }
}
