package org.fuin.porelo.release;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.MappingIterator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;

/**
 * Refuses a workflow GitHub would not run: a file under {@code .github/workflows} that does not
 * load as YAML, or names no trigger or no job.
 */
final class CheckWorkflows {

    private static final ObjectMapper YAML = new ObjectMapper(new YAMLFactory());

    private final PrintStream out;

    private final PrintStream err;

    /**
     * Writes to these streams.
     *
     * @param out where a pass is said
     * @param err where each fault is said
     */
    CheckWorkflows(PrintStream out, PrintStream err) {
        this.out = out;
        this.err = err;
    }

    /**
     * Checks every {@code *.yml} and {@code *.yaml} file of a directory.
     *
     * @param directory usually {@code .github/workflows}
     * @return 0 when each loads with a trigger and a job, {@link Stop#REFUSED} when one does not,
     *     {@link Stop#UNANSWERED} when the directory cannot be read
     */
    int check(Path directory) {
        if (!Files.isDirectory(directory)) {
            err.println(
                    "there is no "
                            + directory
                            + " to check - run this from the repository's root, or name the"
                            + " directory");
            return Stop.UNANSWERED;
        }
        final List<Path> files;
        try (Stream<Path> listed = Files.list(directory)) {
            files =
                    listed.filter(
                                    file -> {
                                        final String name = file.getFileName().toString();
                                        return name.endsWith(".yml") || name.endsWith(".yaml");
                                    })
                            .sorted()
                            .toList();
        } catch (IOException ex) {
            err.println("could not list " + directory + ": " + ex.getMessage());
            return Stop.UNANSWERED;
        }
        final List<String> faults = new ArrayList<>();
        for (final Path file : files) {
            final String fault;
            try {
                fault = fault(Files.readString(file, StandardCharsets.UTF_8));
            } catch (IOException ex) {
                err.println("could not read " + file + ": " + ex.getMessage());
                return Stop.UNANSWERED;
            }
            if (fault != null) {
                faults.add(file + ": " + fault);
            }
        }
        if (!faults.isEmpty()) {
            faults.forEach(err::println);
            err.println(faults.size() + " workflow(s) GitHub would not run");
            return Stop.REFUSED;
        }
        out.println(
                "OK    "
                        + files.size()
                        + " workflow(s) in "
                        + directory
                        + ", each loading with a trigger and a job");
        return 0;
    }

    /**
     * Says what keeps a workflow's text from running.
     *
     * @param text the file's text
     * @return the fault, or null when it loads with a trigger and at least one job
     */
    static @Nullable String fault(String text) {
        final List<JsonNode> documents;
        try (MappingIterator<JsonNode> read = YAML.readerFor(JsonNode.class).readValues(text)) {
            // Every document is read, so a broken one after the first is not passed over.
            documents = read.readAll();
        } catch (JsonProcessingException ex) {
            return "does not load as YAML: " + ex.getOriginalMessage();
        } catch (IOException ex) {
            return "does not load as YAML: " + ex.getMessage();
        }
        if (documents.size() > 1) {
            return "holds " + documents.size() + " documents, where GitHub reads one";
        }
        final JsonNode loaded = documents.isEmpty() ? null : documents.get(0);
        if (loaded == null || !loaded.isObject()) {
            return "holds no mapping";
        }
        // YAML 1.1 reads a bare 'on' as true, as PyYAML does; Jackson keeps the word.
        final JsonNode triggers = loaded.has("on") ? loaded.get("on") : loaded.get("true");
        final boolean named =
                triggers != null
                        && (triggers.isTextual()
                                ? !triggers.textValue().isBlank()
                                : (triggers.isObject() || triggers.isArray())
                                        && !triggers.isEmpty());
        if (!named) {
            return "names no trigger";
        }
        final JsonNode jobs = loaded.get("jobs");
        if (jobs == null || !jobs.isObject() || jobs.isEmpty()) {
            return "names no job";
        }
        return null;
    }
}
