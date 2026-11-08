package org.fuin.porelo.release;

import com.fasterxml.jackson.databind.JsonNode;
import com.networknt.schema.JsonSchema;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
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
     * An issue's states from the least advanced on; a story stands in its least advanced issue's.
     */
    private static final List<String> PROGRESS =
            List.of(
                    "Idea",
                    "Accepted",
                    "Analyzed",
                    "Estimated",
                    "Backlog",
                    "Implementing",
                    "Rework",
                    "Review",
                    "Finished");

    /** The index's sections, in the issue indexes' order: work under way first, done last. */
    private static final List<String> SHOWN =
            List.of(
                    "Review",
                    "Rework",
                    "Implementing",
                    "Backlog",
                    "Estimated",
                    "Analyzed",
                    "Accepted",
                    "Idea",
                    "Finished",
                    "Tested");

    /** Jira's priorities, the highest first. */
    private static final List<String> PRIORITIES =
            List.of("Highest", "High", "Medium", "Low", "Lowest");

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
                    least = Math.min(least, PROGRESS.indexOf(row.state()));
                }
            }
            final String state;
            if (!head.data().get("tested").isNull()) {
                state = "Tested";
            } else if (!any) {
                state = "Idea";
            } else {
                state = PROGRESS.get(least);
            }
            byState.computeIfAbsent(state, unused -> new ArrayList<>()).add(head);
            files.put(head.file(), text(head, state, issues));
        }
        final StringBuilder page =
                new StringBuilder(
                        "# Stories\n\nWritten by `porelo-release stories index` from each story's"
                                + " head; a change goes into the story's file. A story stands in"
                                + " the state of its least advanced issue, as an idea while it has"
                                + " none, and as tested once its test held; within a state, the"
                                + " higher priority comes first.\n");
        for (final Map.Entry<String, List<Heads.Head>> state : byState.entrySet()) {
            if (state.getValue().isEmpty()) {
                continue;
            }
            page.append("\n## ")
                    .append(state.getKey())
                    .append("\n\n| Story | Priority | Title | Issues | Tested |\n")
                    .append("|---|---|---|---|---|\n");
            state.getValue().stream()
                    .sorted(
                            Comparator.comparingInt(
                                            (Heads.Head head) ->
                                                    PRIORITIES.indexOf(
                                                            head.data().get("priority").asText()))
                                    .thenComparingInt(
                                            head ->
                                                    Integer.parseInt(
                                                            head.data()
                                                                    .get("id")
                                                                    .asText()
                                                                    .substring(2))))
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
        final String tested = data.get("tested").isNull() ? null : data.get("tested").asText();
        final String generated =
                "**Priority:** "
                        + data.get("priority").asText()
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

    private static String row(Heads.Head head) {
        final JsonNode data = head.data();
        final List<String> issues = new ArrayList<>();
        data.get("issues").forEach(issue -> issues.add(issue.asText()));
        return "| ["
                + data.get("id").asText()
                + "]("
                + head.file().getFileName()
                + ") | "
                + data.get("priority").asText()
                + " | "
                + data.get("title").asText().replace("|", "\\|")
                + " | "
                + (issues.isEmpty() ? "-" : String.join(", ", issues))
                + " | "
                + (data.get("tested").isNull() ? "-" : data.get("tested").asText())
                + " |\n";
    }
}
