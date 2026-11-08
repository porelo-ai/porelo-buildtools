package org.fuin.porelo.release;

import com.fasterxml.jackson.databind.JsonNode;
import com.networknt.schema.JsonSchema;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
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
final class Stories {

    /** The schema of a story's head. */
    static final String SCHEMA = "/org/fuin/porelo/release/schemas/story-1.json";

    /** The index beside the stories, generated from their heads. */
    static final String INDEX = "README.md";

    /**
     * An issue's states from the least advanced on, Rework counted as Implementing; a story stands
     * in its least advanced issue's.
     */
    private static final List<String> PROGRESS =
            List.of(
                    "Idea",
                    "Accepted",
                    "Analyzed",
                    "Estimated",
                    "Backlog",
                    "Implementing",
                    "Review",
                    "Finished");

    /** A story whose issues are all finished waits for the person's test. */
    private static final String READY = "Ready for your test";

    /** The index's sections, the most advanced first, the tested last. */
    private static final List<String> SHOWN =
            List.of(
                    READY,
                    "Review",
                    "Implementing",
                    "Backlog",
                    "Estimated",
                    "Analyzed",
                    "Accepted",
                    "Idea",
                    "Tested");

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

    private final PrintStream out;

    private final PrintStream err;

    private final IssueLinks links;

    Stories(PrintStream out, PrintStream err, Web web) {
        this.out = out;
        this.err = err;
        this.links = new IssueLinks(web, IssueLinks.Sources.LOCAL_FIRST);
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
        if (!Files.isDirectory(dir)) {
            throw new IOException("no such directory");
        }
        final JsonSchema schema = Heads.schema(SCHEMA);
        final List<Path> files;
        try (Stream<Path> listed = Files.list(dir)) {
            files =
                    listed.filter(file -> file.getFileName().toString().endsWith(".md"))
                            .filter(file -> !file.getFileName().toString().equals(INDEX))
                            .sorted()
                            .toList();
        }
        final Map<String, Heads.Head> heads = new LinkedHashMap<>();
        for (final Path file : files) {
            final Heads.Head head = Heads.read(file, schema, faults);
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

    /**
     * Writes each story's readable part from its head, and the index of the stories beside them.
     *
     * @param dir the stories' directory
     * @return 0, {@link Stop#REFUSED} with each fault said, or {@link Stop#UNANSWERED} when
     *     something cannot be read
     */
    int index(Path dir) {
        final List<String> faults = new ArrayList<>();
        try {
            final Generated generated = generate(dir, faults);
            if (report(dir, faults)) {
                return Stop.REFUSED;
            }
            for (final Map.Entry<Path, String> file : generated.files().entrySet()) {
                if (!Files.readString(file.getKey(), StandardCharsets.UTF_8)
                        .equals(file.getValue())) {
                    Files.writeString(file.getKey(), file.getValue(), StandardCharsets.UTF_8);
                }
            }
            Files.writeString(dir.resolve(INDEX), generated.index(), StandardCharsets.UTF_8);
            out.println(
                    "OK    "
                            + generated.stories()
                            + " story file(s) and "
                            + dir.resolve(INDEX)
                            + " written from the stories' heads");
            return 0;
        } catch (IOException ex) {
            err.println("could not read or write " + dir + ": " + Web.reason(ex));
            return Stop.UNANSWERED;
        } catch (Stop stop) {
            err.println(stop.getMessage());
            return stop.code();
        }
    }

    /**
     * Checks every story's head in a directory, the issues they name, each story's readable part
     * and the index beside them.
     *
     * @param dir the stories' directory
     * @return 0, {@link Stop#REFUSED} with each fault said, or {@link Stop#UNANSWERED} when it
     *     cannot be read
     */
    int check(Path dir) {
        final List<String> faults = new ArrayList<>();
        final Generated generated;
        try {
            generated = generate(dir, faults);
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
                        || !Files.readString(index, StandardCharsets.UTF_8)
                                .equals(generated.index())) {
                    faults.add(INDEX + ": differs from what the stories' heads give" + again);
                }
            }
        } catch (IOException ex) {
            err.println("could not read " + dir + ": " + Web.reason(ex));
            return Stop.UNANSWERED;
        } catch (Stop stop) {
            err.println(stop.getMessage());
            return stop.code();
        }
        if (report(dir, faults)) {
            return Stop.REFUSED;
        }
        out.println(
                "OK    "
                        + generated.stories()
                        + " story head(s) in "
                        + dir
                        + ", each by the schema, each issue in its index, each story's text and "
                        + INDEX
                        + " as they give it");
        return 0;
    }

    /**
     * Says, for a release's notes, the stories tested after the day of the last release.
     *
     * @param version the release
     * @param dir the stories' directory
     * @param after the day of the last release; a story tested that day went with it
     * @return 0, {@link Stop#REFUSED} with each fault said, or {@link Stop#UNANSWERED} when
     *     something cannot be read
     */
    int cut(String version, Path dir, LocalDate after) {
        final List<String> faults = new ArrayList<>();
        try {
            final Map<String, Heads.Head> heads = read(dir, faults);
            if (report(dir, faults)) {
                return Stop.REFUSED;
            }
            final String own = Plan.project(dir.resolve(INDEX), links).own();
            final StringBuilder notes =
                    new StringBuilder(
                            "## " + version + "\n\nThe stories tested after " + after + ":\n\n");
            heads.values().stream()
                    .filter(head -> !head.data().get("tested").isNull())
                    .filter(
                            head ->
                                    LocalDate.parse(head.data().get("tested").asText())
                                            .isAfter(after))
                    .sorted(
                            Comparator.comparing(
                                            (Heads.Head head) -> head.data().get("tested").asText())
                                    .thenComparingInt(Stories::number))
                    .forEach(
                            head ->
                                    notes.append("- [")
                                            .append(head.data().get("id").asText())
                                            .append("](https://github.com/porelo-ai/")
                                            .append(own)
                                            .append("/blob/main/")
                                            .append(dir.toAbsolutePath().normalize().getFileName())
                                            .append('/')
                                            .append(head.file().getFileName())
                                            .append(") ")
                                            .append(head.data().get("title").asText())
                                            .append(" (")
                                            .append(head.data().get("tested").asText())
                                            .append(")\n"));
            out.print(notes);
            return 0;
        } catch (IOException ex) {
            err.println("could not read " + dir + ": " + Web.reason(ex));
            return Stop.UNANSWERED;
        } catch (Stop stop) {
            err.println(stop.getMessage());
            return stop.code();
        }
    }

    private static int number(Heads.Head head) {
        return Integer.parseInt(head.data().get("id").asText().substring(2));
    }

    private boolean report(Path dir, List<String> faults) {
        if (faults.isEmpty()) {
            return false;
        }
        faults.forEach(fault -> err.println(dir.resolve(fault)));
        err.println(faults.size() + " fault(s) in the stories in " + dir);
        return true;
    }

    /**
     * What the stories' heads give, or nothing to go by when a head or an issue is at fault.
     *
     * <p>A story's state is the least advanced of its issues' states, a rejected issue holding none
     * back; a story with no issue is an idea, and one with a tested date is tested.
     */
    private Generated generate(Path dir, List<String> faults) throws IOException, Stop {
        final Map<String, Heads.Head> heads = read(dir, faults);
        if (!faults.isEmpty()) {
            return new Generated("", Map.of(), heads.size());
        }
        final Plan.Project project = Plan.project(dir.resolve(INDEX), links);
        final Map<String, List<Heads.Head>> byState = new LinkedHashMap<>();
        SHOWN.forEach(state -> byState.put(state, new ArrayList<>()));
        final Map<Path, String> files = new LinkedHashMap<>();
        for (final Heads.Head head : heads.values()) {
            final String name = head.file().getFileName().toString();
            int least = PROGRESS.indexOf("Finished");
            boolean any = false;
            final List<String> issues = new ArrayList<>();
            for (final JsonNode node : head.data().get("issues")) {
                final String issue = node.asText();
                final String repository = issue.substring(0, issue.indexOf('#'));
                final String number = issue.substring(issue.indexOf('#') + 1);
                if (!project.repositories().containsKey(repository)) {
                    faults.add(name + ": " + issue + " names a repository project.yml does not");
                    continue;
                }
                final IssueIndex row = links.issueIndex(project.root(), repository).get(number);
                if (row == null) {
                    faults.add(name + ": " + issue + " is in no issue index of " + repository);
                    continue;
                }
                issues.add("- [" + issue + "](" + row.link(repository) + ") (" + row.state() + ")");
                if (!row.state().equals("Rejected")) {
                    any = true;
                    least =
                            Math.min(
                                    least,
                                    PROGRESS.indexOf(
                                            row.state().equals("Rework")
                                                    ? "Implementing"
                                                    : row.state()));
                }
            }
            final String state;
            if (!head.data().get("tested").isNull()) {
                state = "Tested";
            } else if (!any) {
                state = "Idea";
            } else if (PROGRESS.get(least).equals("Finished")) {
                state = READY;
            } else {
                state = PROGRESS.get(least);
            }
            byState.computeIfAbsent(state, unused -> new ArrayList<>()).add(head);
            files.put(head.file(), text(head, state, issues));
        }
        final StringBuilder page =
                new StringBuilder(
                        "# Stories\n\nWritten by `porelo-release stories index` from each story's"
                                + " head; a change goes into the story's file. Each story is one"
                                + " function of value to a user, with the test the person runs at"
                                + " its end and every issue it needs. Work follows the priority. A"
                                + " story's state is its least advanced issue's (a rejected issue"
                                + " does not count); when all its issues are finished it waits for"
                                + " the person's test, and the test makes it Tested. A story with"
                                + " no issue yet is an Idea.\n\n"
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
                    .append("\n\n| Story | State | Priority | Issues | What a user gains |\n")
                    .append("|---|---|---|---|---|\n");
            state.getValue().stream()
                    .sorted(
                            Comparator.comparingInt(
                                            (Heads.Head head) ->
                                                    List.copyOf(PRIORITIES.keySet())
                                                            .indexOf(
                                                                    head.data()
                                                                            .get("priority")
                                                                            .asText()))
                                    .thenComparingInt(Stories::number))
                    .forEach(head -> page.append(row(head, state.getKey())));
        }
        return new Generated(page.toString(), files, heads.size());
    }

    /**
     * A story's whole text: its heading, the part its head gives, then what a person wrote below it
     * down to the head, unchanged. The generated part runs from the heading to the issues' line.
     */
    private static String text(Heads.Head head, String state, List<String> issues) {
        final JsonNode data = head.data();
        final String tested = data.get("tested").isNull() ? null : data.get("tested").asText();
        final String generated =
                "**Priority:** "
                        + priority(data)
                        + (tested == null ? " · **State:** " + state : " · **Tested:** " + tested)
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

    private static String row(Heads.Head head, String state) {
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
                + state
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
