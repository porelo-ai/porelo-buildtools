package org.fuin.porelo.stories;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * porelo-project's {@code stories/}: one file a story, its readable part generated from the JSON
 * head at its end, and an index of them all.
 */
public final class StoriesIndex {

    /** The index beside the stories, generated from their heads. */
    static final String INDEX = "README.md";

    /** The index's sections: a story's states in their sequence, as an issue's. */
    private static final List<String> SHOWN =
            List.of(
                    "Idea",
                    "Accepted",
                    "Analyzed",
                    "Estimated",
                    "Backlog",
                    "Implementing",
                    "Review",
                    "Rework",
                    "Finished",
                    "Rejected");

    /** The states an issue of a story in Review may stand in: none earlier than Review. */
    private static final java.util.Set<String> DONE_FOR_REVIEW =
            java.util.Set.of("Review", "Finished", "Rejected");

    /** Jira's priorities, the highest first, each with Jira's icon. */
    private static final Map<String, String> PRIORITIES = priorities();

    /** The line that starts the last paragraph of a story's generated part, the list of issues. */
    private static final String ISSUES = "**Issues:** ";

    private static final Pattern FILE = Pattern.compile("(ST[0-9]+)(-[^/]*)?\\.md");

    /**
     * What the stories' heads give.
     *
     * @param index the index
     * @param files each story's whole text, its generated part as its head gives it
     * @param stories how many stories there are
     */
    private record Generated(String index, Map<Path, String> files, int stories) {}

    private final IssueSource source;
    private final Map<String, Map<String, IssueIndex>> indexes = new LinkedHashMap<>();

    private StoriesIndex(IssueSource source) {
        this.source = source;
    }

    /**
     * The faults found and the files actually written, in write order.
     *
     * @param faults faults relative to the stories directory
     * @param written files changed or created; empty for a check or a refused write
     */
    public record Result(List<String> faults, List<Path> written) {
        /** Copies the result so it remains stable after the operation. */
        public Result {
            faults = List.copyOf(faults);
            written = List.copyOf(written);
        }
    }

    /**
     * Checks heads, issue references, generated story text and README.md without writing.
     *
     * @param storiesDir the stories directory below a project.yml
     * @param source the only source of issue indexes
     * @return faults, with an empty written list
     * @throws IllegalArgumentException when project configuration or an issue state is invalid
     * @throws IOException when an input cannot be read
     */
    public static Result check(Path storiesDir, IssueSource source) throws IOException {
        return new StoriesIndex(source).check(storiesDir);
    }

    /**
     * Checks all inputs before writing generated story text and README.md.
     *
     * @param storiesDir the stories directory below a project.yml
     * @param source the only source of issue indexes
     * @return faults or the changed and created paths actually written; unchanged files are
     *     untouched
     * @throws IllegalArgumentException when project configuration or an issue state is invalid
     * @throws IOException when an input cannot be read or an output cannot be written
     */
    public static Result write(Path storiesDir, IssueSource source) throws IOException {
        return new StoriesIndex(source).write(storiesDir);
    }

    private Map<String, IssueIndex> index(String repository) throws IOException {
        Map<String, IssueIndex> rows = indexes.get(repository);
        if (rows == null) {
            rows = IssueIndexes.read(source.index(repository), repository);
            indexes.put(repository, rows);
        }
        return rows;
    }

    /**
     * Reads every story's head in a directory.
     *
     * @param dir the stories' directory
     * @param faults where each fault is added
     * @return the heads read whole, by id, in the files' order
     * @throws IOException when the directory or a file cannot be read
     */
    static Map<String, Heads.Head> read(Path dir, List<String> faults) throws IOException {
        refuseSymlink(dir);
        if (!Files.isDirectory(dir)) {
            throw new IOException("no such directory");
        }
        final List<Path> files;
        try (Stream<Path> listed = Files.list(dir)) {
            files =
                    listed.filter(file -> file.getFileName().toString().endsWith(".md"))
                            .sorted()
                            .toList();
        }
        for (final Path file : files) refuseSymlink(file);
        final Map<String, Heads.Head> heads = new LinkedHashMap<>();
        for (final Path file : files) {
            if (file.getFileName().toString().equals(INDEX)) continue;
            final Heads.Head head = Heads.read(file, faults);
            if (head == null) {
                continue;
            }
            final String name = file.getFileName().toString();
            final String id = head.data().get("id").asText();
            final String heading = "# " + id + " — " + head.data().get("title").asText();
            if (!head.heading().equals(heading)) {
                faults.add(name + ": the heading is not '" + heading + "'");
            }
            final Matcher named = FILE.matcher(name);
            if (!named.matches() || !named.group(1).equals(id)) {
                faults.add(name + ": the file is not named after its id " + id);
            }
            final Heads.Head before = heads.putIfAbsent(id, head);
            if (before != null) {
                faults.add(name + ": id " + id + " also stands in " + before.file().getFileName());
            }
        }
        return heads;
    }

    private Result write(Path dir) throws IOException {
        final List<String> faults = new ArrayList<>();
        final Generated generated = generate(dir, faults);
        if (!faults.isEmpty()) return new Result(faults, List.of());
        final List<Path> written = new ArrayList<>();
        for (final Map.Entry<Path, String> file : generated.files().entrySet()) {
            if (writeChanged(file.getKey(), file.getValue())) written.add(file.getKey());
        }
        final Path index = dir.resolve(INDEX);
        if (writeChanged(index, generated.index())) written.add(index);
        return new Result(faults, written);
    }

    private static void refuseSymlink(Path path) throws IOException {
        if (Files.isSymbolicLink(path))
            throw new IOException("symbolic link is not allowed: " + path);
    }

    private static boolean writeChanged(Path file, String content) throws IOException {
        refuseSymlink(file);
        final byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        if (Files.exists(file, LinkOption.NOFOLLOW_LINKS)
                && Arrays.equals(Files.readAllBytes(file), bytes)) return false;
        final Path temporary =
                Files.createTempFile(
                        file.toAbsolutePath().getParent(), "." + file.getFileName() + "-", ".tmp");
        try {
            Files.write(temporary, bytes);
            Files.move(
                    temporary,
                    file,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temporary);
        }
        return true;
    }

    private Result check(Path dir) throws IOException {
        final List<String> faults = new ArrayList<>();
        final Generated generated = generate(dir, faults);
        if (faults.isEmpty()) {
            final String again = "; run porelo-release stories index " + dir;
            for (final Map.Entry<Path, String> file : generated.files().entrySet()) {
                if (!Files.readString(file.getKey(), StandardCharsets.UTF_8)
                        .equals(file.getValue())) {
                    faults.add(
                            file.getKey().getFileName()
                                    + ": the text above "
                                    + Heads.DATA
                                    + " is not what its head gives"
                                    + again);
                }
            }
            final Path index = dir.resolve(INDEX);
            if (!Files.isRegularFile(index)
                    || !Files.readString(index, StandardCharsets.UTF_8).equals(generated.index())) {
                faults.add(INDEX + ": differs from what the stories' heads give" + again);
            }
        }
        return new Result(faults, List.of());
    }

    private static boolean isDay(String text) {
        try {
            LocalDate.parse(text);
            return true;
        } catch (java.time.format.DateTimeParseException ex) {
            return false;
        }
    }

    private static int number(Heads.Head head) {
        return Integer.parseInt(head.data().get("id").asText().substring(2));
    }

    /**
     * What the stories' heads give, or nothing to go by when a head or an issue is at fault. A
     * story in Review waits for none of its issues: each is in Review, finished or rejected.
     */
    private Generated generate(Path dir, List<String> faults) throws IOException {
        final Map<String, Heads.Head> heads = read(dir, faults);
        if (!faults.isEmpty()) {
            return new Generated("", Map.of(), heads.size());
        }
        final Project project = Project.read(dir.resolve(INDEX), this::index);
        final Map<String, List<Heads.Head>> byState = new LinkedHashMap<>();
        SHOWN.forEach(state -> byState.put(state, new ArrayList<>()));
        final Map<Path, String> files = new LinkedHashMap<>();
        for (final Heads.Head head : heads.values()) {
            final String name = head.file().getFileName().toString();
            final String state = head.data().get("state").asText();
            final JsonNode tested = head.data().get("tested");
            if (!tested.isNull() && !isDay(tested.asText())) {
                faults.add(name + ": /tested: " + tested.asText() + " is no day");
            }
            if (state.equals("Finished") && head.data().get("tested").isNull()) {
                faults.add(name + ": Finished without a tested day");
            } else if (!state.equals("Finished") && !head.data().get("tested").isNull()) {
                faults.add(name + ": a tested day on a story that is " + state + ", not Finished");
            }
            final List<String> issues = new ArrayList<>();
            for (final JsonNode node : head.data().get("issues")) {
                final String issue = node.asText();
                final String repository = issue.substring(0, issue.indexOf('#'));
                final String number = issue.substring(issue.indexOf('#') + 1);
                if (!project.repositories().containsKey(repository)) {
                    faults.add(name + ": " + issue + " names a repository project.yml does not");
                    continue;
                }
                final IssueIndex row = index(repository).get(number);
                if (row == null) {
                    faults.add(name + ": " + issue + " is in no issue index of " + repository);
                    continue;
                }
                issues.add("- [" + issue + "](" + row.link(repository) + ") (" + row.state() + ")");
                if (state.equals("Review") && !DONE_FOR_REVIEW.contains(row.state())) {
                    faults.add(name + ": in Review while " + issue + " is " + row.state());
                }
            }

            byState.computeIfAbsent(state, unused -> new ArrayList<>()).add(head);
            files.put(head.file(), text(head, state, issues));
        }
        final StringBuilder page =
                new StringBuilder(
                        "# Stories\n\nWritten by `porelo-release stories index` from each story's"
                                + " head; a change goes into the story's file. Each story is one"
                                + " function of value to a user, with the test the person runs at"
                                + " its end and every issue it needs. A story moves through the"
                                + " same states as an issue, set by the same hands: Idea, Accepted"
                                + " or Rejected, Analyzed, Estimated, Backlog, Implementing, Review,"
                                + " Rework or Finished. Work follows the priority.\n\n"
                                + "Priority: ");
        page.append(
                String.join(
                        " · ",
                        PRIORITIES.entrySet().stream()
                                .map(priority -> priority.getValue() + " " + priority.getKey())
                                .toList()));
        page.append(". Issues are counted by their prefix (");
        page.append(
                String.join(
                        ", ",
                        project.repositories().entrySet().stream()
                                .flatMap(
                                        repository ->
                                                repository.getValue().stream()
                                                        .map(
                                                                prefix ->
                                                                        Map.entry(
                                                                                prefix,
                                                                                repository
                                                                                        .getKey())))
                                .sorted(Map.Entry.comparingByKey())
                                .map(prefix -> "`" + prefix.getKey() + "` " + prefix.getValue())
                                .toList()));
        page.append(").\n\n| State | Stories |\n|---|---|\n");
        byState.forEach(
                (state, stories) -> {
                    if (!stories.isEmpty()) {
                        page.append("| [")
                                .append(state)
                                .append("](#")
                                .append(state.toLowerCase(Locale.ROOT).replace(' ', '-'))
                                .append(") | ")
                                .append(stories.size())
                                .append(" |\n");
                    }
                });
        for (final Map.Entry<String, List<Heads.Head>> state : byState.entrySet()) {
            if (state.getValue().isEmpty()) {
                continue;
            }
            page.append("\n## ")
                    .append(state.getKey())
                    .append("\n\n| Story | Priority | Issues | What a user gains |\n")
                    .append("|---|---|---|---|\n");
            state.getValue().stream()
                    .sorted(
                            Comparator.comparingInt(
                                            (Heads.Head head) ->
                                                    List.copyOf(PRIORITIES.keySet())
                                                            .indexOf(
                                                                    head.data()
                                                                            .get("priority")
                                                                            .asText()))
                                    .thenComparingInt(StoriesIndex::number))
                    .forEach(head -> page.append(row(head)));
        }
        return new Generated(page.toString(), files, heads.size());
    }

    /**
     * A story's whole text: its heading, the part its head gives, then what a person wrote below it
     * down to the head, unchanged. The generated part runs from the heading to the issues' line.
     */
    private static String text(Heads.Head head, String state, List<String> issues) {
        final JsonNode data = head.data();
        final String shown =
                " · **State:** "
                        + state
                        + (data.get("tested").isNull()
                                ? ""
                                : " (" + data.get("tested").asText() + ")");
        final String generated =
                "**Priority:** "
                        + priority(data)
                        + shown
                        + "\n\n**What a user gains.** "
                        + data.get("gain").asText()
                        + "\n\n**The test.** "
                        + data.get("test").asText()
                        + "\n\n"
                        + (issues.isEmpty()
                                ? ISSUES + "none yet"
                                : ISSUES.strip() + "\n\n" + String.join("\n", issues))
                        + "\n";
        final List<String> lines = head.lines();
        final int data0 = lines.lastIndexOf(Heads.DATA);
        // The generated part ends with the issues' line, or with the list below it.
        int rest = 1;
        for (int i = 1; i < data0; i++) {
            if (lines.get(i).startsWith(ISSUES.strip())) {
                rest = i + 1;
                if (lines.get(i).equals(ISSUES.strip())) {
                    while (rest < data0 && lines.get(rest).isBlank()) {
                        rest++;
                    }
                    while (rest < data0 && lines.get(rest).startsWith("- ")) {
                        rest++;
                    }
                }
                break;
            }
        }
        while (rest < data0 && lines.get(rest).isBlank()) {
            rest++;
        }
        return lines.getFirst()
                + "\n\n"
                + generated
                + "\n"
                + String.join("\n", lines.subList(rest, lines.size()));
    }

    private static String row(Heads.Head head) {
        final JsonNode data = head.data();
        final String id = data.get("id").asText();
        final Map<String, Integer> counts = new LinkedHashMap<>();
        data.get("issues")
                .forEach(
                        issue ->
                                counts.merge(
                                        issue.asText()
                                                .substring(issue.asText().indexOf('#') + 1)
                                                .replaceAll("[0-9]+$", ""),
                                        1,
                                        Integer::sum));
        final String issues =
                String.join(
                        " · ",
                        counts.entrySet().stream()
                                .sorted(
                                        Map.Entry.<String, Integer>comparingByValue()
                                                .reversed()
                                                .thenComparing(Map.Entry.comparingByKey()))
                                .map(count -> count.getKey() + ":" + count.getValue())
                                .toList());
        return "| <a id=\""
                + id.toLowerCase(Locale.ROOT)
                + "\"></a>["
                + id
                + "]("
                + head.file().getFileName()
                + ") "
                + cell(data.get("title").asText())
                + " | "
                + priority(data)
                + " | "
                + (issues.isEmpty() ? "none yet" : issues)
                + " | "
                + cell(data.get("gain").asText())
                + " |\n";
    }

    private static String cell(String text) {
        return text.replace("|", "\\|");
    }

    private static String priority(JsonNode data) {
        final String priority = data.get("priority").asText();
        return PRIORITIES.get(priority) + " " + priority;
    }

    private static Map<String, String> priorities() {
        final Map<String, String> priorities = new LinkedHashMap<>();
        priorities.put("Highest", "⏫");
        priorities.put("High", "🔼");
        priorities.put("Medium", "🟰");
        priorities.put("Low", "🔽");
        priorities.put("Lowest", "⏬");
        return java.util.Collections.unmodifiableMap(priorities);
    }
}
