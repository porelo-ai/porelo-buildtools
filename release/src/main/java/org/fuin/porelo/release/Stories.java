package org.fuin.porelo.release;

import com.networknt.schema.JsonSchema;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/** porelo-project's {@code stories/}: one file a story, each with its JSON head. */
final class Stories {

    /** The schema of a story's head. */
    static final String SCHEMA = "/org/fuin/porelo/release/schemas/story-1.json";

    /** The index beside the stories, generated from their heads. */
    static final String INDEX = "README.md";

    private static final Pattern FILE = Pattern.compile("(ST[0-9]+)(-[^/]*)?\\.md");

    private final PrintStream out;

    private final PrintStream err;

    Stories(PrintStream out, PrintStream err) {
        this.out = out;
        this.err = err;
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
     * Checks every story's head in a directory.
     *
     * @param dir the stories' directory
     * @return 0, {@link Stop#REFUSED} with each fault said, or {@link Stop#UNANSWERED} when it
     *     cannot be read
     */
    int check(Path dir) {
        final List<String> faults = new ArrayList<>();
        final Map<String, Heads.Head> heads;
        try {
            heads = read(dir, faults);
        } catch (IOException ex) {
            err.println("could not read " + dir + ": " + Web.reason(ex));
            return Stop.UNANSWERED;
        }
        if (!faults.isEmpty()) {
            faults.forEach(fault -> err.println(dir.resolve(fault)));
            err.println(faults.size() + " fault(s) in the stories in " + dir);
            return Stop.REFUSED;
        }
        out.println("OK    " + heads.size() + " story head(s) in " + dir + ", each by the schema");
        return 0;
    }
}
