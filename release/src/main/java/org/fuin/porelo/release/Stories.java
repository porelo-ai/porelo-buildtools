package org.fuin.porelo.release;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.fuin.porelo.stories.IssueSource;
import org.fuin.porelo.stories.StoriesIndex;

/**
 * porelo-project's {@code stories/}: one file a story, its readable part generated from the JSON
 * head at its end, and an index of them all.
 */
final class Stories {

    static final String INDEX = "README.md";
    private final PrintStream out;
    private final PrintStream err;
    private final IssueLinks links;

    Stories(PrintStream out, PrintStream err, Web web) {
        this.out = out;
        this.err = err;
        this.links = new IssueLinks(web, IssueLinks.Sources.LOCAL_FIRST);
    }

    private IssueSource source(Path dir) {
        return repository -> {
            try {
                final Plan.Project project = Plan.project(dir.resolve(INDEX), links);
                return links.storySource(project.root()).index(repository);
            } catch (Stop stop) {
                throw new IssueLinks.IndexFailure(stop);
            }
        };
    }

    private static int count(Path dir) throws IOException {
        try (var files = Files.list(dir)) {
            return (int)
                    files.filter(file -> file.getFileName().toString().endsWith(".md"))
                            .filter(file -> !file.getFileName().toString().equals(INDEX))
                            .count();
        }
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
            final StoriesIndex.Result result = StoriesIndex.write(dir, source(dir));
            faults.addAll(result.faults());
            if (report(dir, faults)) return Stop.REFUSED;
            out.println(
                    "OK    "
                            + count(dir)
                            + " story file(s) and "
                            + dir.resolve(INDEX)
                            + " written from the stories' heads");
            return 0;
        } catch (IOException ex) {
            return unreadable("could not read or write ", dir, ex);
        } catch (IllegalArgumentException ex) {
            err.println(ex.getMessage());
            return Stop.REFUSED;
        } catch (IssueLinks.IndexFailure ex) {
            err.println(ex.stop.getMessage());
            return ex.stop.code();
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
        final int stories;
        try {
            faults.addAll(StoriesIndex.check(dir, source(dir)).faults());
            stories = count(dir);
        } catch (IOException ex) {
            return unreadable("could not read ", dir, ex);
        } catch (IllegalArgumentException ex) {
            err.println(ex.getMessage());
            return Stop.REFUSED;
        } catch (IssueLinks.IndexFailure ex) {
            err.println(ex.stop.getMessage());
            return ex.stop.code();
        }
        if (report(dir, faults)) {
            return Stop.REFUSED;
        }
        out.println(
                "OK    "
                        + stories
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
            faults.addAll(cutFaults(dir));
            if (report(dir, faults)) {
                return Stop.REFUSED;
            }
            final List<Story> heads = read(dir);
            final String own = Plan.project(dir.resolve(INDEX), links).own();
            final StringBuilder notes =
                    new StringBuilder(
                            "## " + version + "\n\nThe stories tested after " + after + ":\n\n");
            heads.stream()
                    .filter(head -> !head.data().get("tested").isNull())
                    .filter(
                            head ->
                                    LocalDate.parse(head.data().get("tested").asText())
                                            .isAfter(after))
                    .sorted(
                            Comparator.comparing((Story head) -> head.data().get("tested").asText())
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
            return unreadable("could not read ", dir, ex);
        } catch (IllegalArgumentException ex) {
            err.println(ex.getMessage());
            return Stop.REFUSED;
        } catch (IssueLinks.IndexFailure ex) {
            err.println(ex.stop.getMessage());
            return ex.stop.code();
        } catch (Stop stop) {
            err.println(stop.getMessage());
            return stop.code();
        }
    }

    private int unreadable(String operation, Path dir, IOException ex) {
        if (ex instanceof NoSuchFileException missing
                && "no project.yml above the plan".equals(missing.getReason())) {
            err.println(ex.getMessage());
        } else err.println(operation + dir + ": " + Web.reason(ex));
        return Stop.UNANSWERED;
    }

    private List<String> cutFaults(Path dir) throws IOException {
        try {
            return inputFaults(dir, dir);
        } catch (IOException ex) {
            // A cut has never depended on the generated index being readable.
            if (!Files.isRegularFile(dir.resolve(INDEX))) throw ex;
            try {
                Files.readString(dir.resolve(INDEX), StandardCharsets.UTF_8);
            } catch (IOException output) {
                return cutWithoutIndex(dir, ex);
            }
            throw ex;
        }
    }

    private List<String> inputFaults(Path checked, Path original) throws IOException {
        return StoriesIndex.check(checked, source(original)).faults().stream()
                .filter(fault -> !fault.endsWith("; run porelo-release stories index " + checked))
                .toList();
    }

    private List<String> cutWithoutIndex(Path dir, IOException original) throws IOException {
        Path root = dir.toAbsolutePath().normalize().getParent();
        while (root != null && !Files.isRegularFile(root.resolve("project.yml")))
            root = root.getParent();
        if (root == null) throw original;
        final Path temporary = Files.createTempDirectory("porelo-stories-cut-");
        try {
            Files.copy(root.resolve("project.yml"), temporary.resolve("project.yml"));
            final Path stories = Files.createDirectory(temporary.resolve("stories"));
            try (var files = Files.list(dir)) {
                for (final Path file :
                        files.filter(path -> path.getFileName().toString().endsWith(".md"))
                                .filter(path -> !path.getFileName().toString().equals(INDEX))
                                .toList()) {
                    Files.copy(file, stories.resolve(file.getFileName()));
                }
            }
            return inputFaults(stories, dir);
        } finally {
            try (var files = Files.walk(temporary)) {
                for (final Path file : files.sorted(Comparator.reverseOrder()).toList())
                    Files.delete(file);
            }
        }
    }

    private static int number(Story head) {
        return Integer.parseInt(head.data().get("id").asText().substring(2));
    }

    private boolean report(Path dir, List<String> faults) {
        if (faults.isEmpty()) {
            return false;
        }
        // Said as written: a fault taken for a path would lose the second slash of an address.
        faults.forEach(fault -> err.println(dir + "/" + fault));
        err.println(faults.size() + " fault(s) in the stories in " + dir);
        return true;
    }

    private record Story(Path file, JsonNode data) {}

    private static List<Story> read(Path dir) throws IOException {
        final List<Story> result = new ArrayList<>();
        final ObjectMapper json = new ObjectMapper();
        try (var files = Files.list(dir)) {
            for (final Path file :
                    files.filter(path -> path.getFileName().toString().endsWith(".md"))
                            .filter(path -> !path.getFileName().toString().equals(INDEX))
                            .sorted()
                            .toList()) {
                final String text = Files.readString(file, StandardCharsets.UTF_8);
                final int start = text.lastIndexOf("```json\n") + "```json\n".length();
                result.add(
                        new Story(
                                file,
                                json.readTree(text.substring(start, text.lastIndexOf("```")))));
            }
        }
        return result;
    }
}
