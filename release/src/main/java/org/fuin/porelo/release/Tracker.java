package org.fuin.porelo.release;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;

/**
 * Where a project keeps its issues, as its {@code project.yml} says in {@code tracker.kind}, the
 * shape porelo reads: {@code github} for GitHub Issues; no {@code tracker} for the Markdown issues
 * under each repository's {@code issues/}. Another kind is refused, since this reads no other.
 *
 * <p>The {@code project.yml} read is the nearest one above the directory a command is given, so the
 * project's own repository and a test's fixture each answer for themselves. A repository with none
 * above it belongs to the project whose {@code project.yml} stands on porelo-project's {@code
 * main}, read without a token. What cannot be read stops the command unanswered: taking the
 * Markdown issues for granted would fail every Build after the cut-over, and taking the tracker
 * would pass a check that still has issues to read.
 */
record Tracker(boolean github, String where) {

    /** The project's file where no {@code project.yml} stands above a directory. */
    static final URI PROJECT =
            URI.create(
                    "https://raw.githubusercontent.com/porelo-ai/porelo-project/main/project.yml");

    /** The one kind of tracker this reads. */
    static final String GITHUB = "github";

    private static final ObjectMapper YAML =
            new ObjectMapper(
                    new YAMLFactory().enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION));

    /**
     * Reads where the project of a directory keeps its issues.
     *
     * @param directory the directory a command was given, or the repository's root
     * @param web where porelo-project's file is read when none stands above the directory
     * @return the answer, with the file it was read from
     * @throws Stop refused when the file says something else than this reads, unanswered when it
     *     cannot be read
     */
    static Tracker of(Path directory, Web web) throws Stop {
        Path at = directory.toAbsolutePath().normalize();
        while (at != null && !Files.exists(at.resolve("project.yml"), LinkOption.NOFOLLOW_LINKS)) {
            at = at.getParent();
        }
        if (at != null) {
            final Path file = at.resolve("project.yml");
            if (!Files.isRegularFile(file)) {
                throw Stop.unanswered("could not read " + file + ": it is no regular file");
            }
            try {
                return read(Files.readString(file, StandardCharsets.UTF_8), file.toString());
            } catch (IOException | UncheckedIOException ex) {
                throw Stop.unanswered("could not read " + file + ": " + Web.reason(ex), ex);
            }
        }
        try {
            final String text =
                    web.text(PROJECT)
                            .orElseThrow(() -> new IOException("it answered 404 (not found)"));
            return read(text, PROJECT.toString());
        } catch (IOException | UncheckedIOException ex) {
            throw Stop.unanswered(
                    "no project.yml above "
                            + directory
                            + ", and the project's at "
                            + PROJECT
                            + " could not be read: "
                            + Web.reason(ex),
                    ex);
        }
    }

    /**
     * Reads {@code tracker.kind} from a {@code project.yml}'s text.
     *
     * @param text the file's text
     * @param where the file, for what is said
     * @return the answer
     * @throws Stop refused when the text is no mapping, or its tracker is no mapping whose kind is
     *     {@value #GITHUB}
     */
    static Tracker read(String text, String where) throws Stop {
        final JsonNode data;
        try (var parser = YAML.createParser(text)) {
            data = YAML.readTree(parser);
            if (parser.nextToken() != null) {
                throw Stop.refused(where + ": expected exactly one YAML document");
            }
        } catch (IOException ex) {
            throw Stop.refused(where + ": invalid YAML: " + Web.reason(ex), ex);
        }
        if (data == null || !data.isObject()) {
            throw Stop.refused(where + ": expected a YAML mapping");
        }
        final JsonNode tracker = data.get("tracker");
        if (tracker == null) {
            return new Tracker(false, where);
        }
        final JsonNode kind = tracker.isObject() ? tracker.get("kind") : null;
        if (kind == null || !kind.isTextual() || !kind.textValue().equals(GITHUB)) {
            throw Stop.refused(
                    where
                            + ": tracker.kind is "
                            + kind
                            + "; the one kind known is \""
                            + GITHUB
                            + "\", and without a tracker the issues are Markdown under issues/");
        }
        return new Tracker(true, where);
    }

    /**
     * Says, for a command that has nothing to do once the issues are in the tracker, why it passes.
     *
     * @param command what passes, such as {@code issues check}
     * @return the line, naming the file the tracker was read from
     */
    String passes(String command) {
        return "OK    "
                + command
                + ": the issues are kept in GitHub Issues, as the tracker in "
                + where
                + " says, so nothing here is checked or written";
    }
}
