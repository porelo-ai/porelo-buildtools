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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.regex.Pattern;

/**
 * Where a project keeps its issues, as its {@code project.yml} says in {@code tracker.kind}, the
 * shape porelo reads: {@code github} for GitHub Issues; no {@code tracker} for the Markdown issues
 * under each repository's {@code issues/}. Another kind is refused, since this reads no other.
 *
 * <p>The {@code project.yml} read is the nearest one above the directory a command is given, so the
 * project's own repository and a test's fixture each answer for themselves. A git checkout of a
 * repository of {@value #OWNER}, by its {@code origin}, with none above it belongs to the project
 * whose {@code project.yml} stands on porelo-project's {@code main}, read without a token. Where
 * {@value #PROJECT_FILE} names an https address, the file there answers instead of the origin: a
 * host's forge keeps the project's repositories under the project's own name, not {@value #OWNER}.
 * Any other directory with none above it, a test's fixture among them, is a project of its own with
 * Markdown issues, and nothing is read over the network for it. What cannot be read stops the
 * command unanswered: taking the Markdown issues for granted would fail every Build after the
 * cut-over, and taking the tracker would pass a check that still has issues to read.
 */
record Tracker(boolean github, String where) {

    /** The project's file where no {@code project.yml} stands above a checkout of the project. */
    static final URI PROJECT =
            URI.create(
                    "https://raw.githubusercontent.com/porelo-ai/porelo-project/main/project.yml");

    /**
     * The variable naming the https address of the project's file, for a checkout whose origin is
     * not {@value #OWNER}'s.
     */
    static final String PROJECT_FILE = "PORELO_PROJECT_FILE";

    /** The owner of the project's repositories on every forge. */
    static final String OWNER = "porelo-ai";

    /** The one kind of tracker this reads. */
    static final String GITHUB = "github";

    /**
     * A repository of {@value #OWNER} as a remote names it: by an address or as {@code
     * host:owner/repository}, the owner in any case, with or without {@code .git} and a last slash.
     */
    private static final Pattern PROJECT_REMOTE =
            Pattern.compile(
                    "^(?:[a-z][a-z0-9+.-]*://[^/]+/|[^/:@]+@[^/:]+:|[^/:]+:)"
                            + OWNER
                            + "/[A-Za-z0-9._-]+?(?:\\.git)?/?$",
                    Pattern.CASE_INSENSITIVE);

    private static final ObjectMapper YAML =
            new ObjectMapper(
                    new YAMLFactory().enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION));

    /**
     * Variables that point git at another repository than the directory's own, as a hook or {@code
     * git rebase --exec} sets them.
     */
    private static final List<String> ELSEWHERE =
            List.of(
                    "GIT_DIR",
                    "GIT_WORK_TREE",
                    "GIT_COMMON_DIR",
                    "GIT_INDEX_FILE",
                    "GIT_OBJECT_DIRECTORY",
                    "GIT_ALTERNATE_OBJECT_DIRECTORIES",
                    "GIT_NAMESPACE");

    /** What git says, and only says, where no checkout stands at or above a directory. */
    private static final String NO_CHECKOUT =
            "fatal: not a git repository (or any of the parent directories): .git";

    /** The {@code origin} of the git checkout a directory lies in. */
    interface Origin {

        /**
         * Asks git, from the nearest directory that exists, for {@code remote.origin.url} as the
         * checkout's own config holds it, no {@code insteadOf} applied.
         */
        Origin GIT = git(Map.of());

        /**
         * Reads the origin.
         *
         * @param directory the directory, which need not exist yet
         * @return the origin's address; empty outside a checkout or in one without {@code origin}
         * @throws IOException when it cannot be told, git missing among the reasons
         */
        Optional<String> of(Path directory) throws IOException;

        /**
         * Asks git as {@link #GIT} does, with variables of its own.
         *
         * @param environment variables set for git besides the inherited ones, but never one that
         *     points it at another repository
         * @return the reader
         */
        static Origin git(Map<String, String> environment) {
            return directory -> gitOrigin(directory, environment);
        }
    }

    /**
     * Reads where the project of a directory keeps its issues, its checkout's origin read as given.
     *
     * @param directory the directory a command was given, or the repository's root
     * @param web where porelo-project's file is read when none stands above a checkout of the
     *     project
     * @param origin where the origin of the directory's checkout is read
     * @param environment the command's variables, {@value #PROJECT_FILE} among them
     * @return the answer, with the file it was read from
     * @throws Stop refused when the file says something else than this reads or {@value
     *     #PROJECT_FILE} is no plain https address, unanswered when a file or the origin cannot be
     *     read
     */
    static Tracker of(Path directory, Web web, Origin origin, Map<String, String> environment)
            throws Stop {
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
        final String named = environment.get(PROJECT_FILE);
        if (named != null) {
            return named(named, web);
        }
        final Optional<String> remote;
        try {
            remote = origin.of(directory);
        } catch (IOException | UncheckedIOException ex) {
            throw Stop.unanswered(
                    "no project.yml above "
                            + directory
                            + ", and the origin of its checkout could not be told: "
                            + Web.reason(ex),
                    ex);
        }
        if (remote.isEmpty() || !PROJECT_REMOTE.matcher(remote.get().strip()).matches()) {
            return new Tracker(false, "no project.yml above " + directory);
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

    private static Tracker named(String value, Web web) throws Stop {
        final URI address;
        try {
            address = new URI(value);
        } catch (java.net.URISyntaxException ex) {
            // Not passed on: the parser's words repeat the value, which may hold a credential.
            throw Stop.refused(PROJECT_FILE + " is no address");
        }
        if (!"https".equals(address.getScheme())
                || address.getHost() == null
                || address.getRawUserInfo() != null
                || address.getRawQuery() != null
                || address.getRawFragment() != null) {
            throw Stop.refused(
                    PROJECT_FILE
                            + " must be an https address of a project.yml, with no credential,"
                            + " query or fragment");
        }
        try {
            final String text =
                    web.text(address)
                            .orElseThrow(() -> new IOException("it answered 404 (not found)"));
            return read(text, address.toString());
        } catch (IOException | UncheckedIOException ex) {
            throw Stop.unanswered(
                    "the project's file at "
                            + address
                            + ", as "
                            + PROJECT_FILE
                            + " names it, could not be read: "
                            + Web.reason(ex),
                    ex);
        }
    }

    private static Optional<String> gitOrigin(Path directory, Map<String, String> environment)
            throws IOException {
        Path at = directory.toAbsolutePath().normalize();
        while (at != null && !Files.isDirectory(at)) {
            at = at.getParent();
        }
        if (at == null) {
            return Optional.empty();
        }
        final Said checkout = git(at, environment, "rev-parse", "--git-dir");
        if (checkout.code() == 128 && checkout.err().equals(NO_CHECKOUT)) {
            return Optional.empty();
        }
        checkout.orFail();
        final Said url = git(at, environment, "config", "--local", "--get", "remote.origin.url");
        // 1: the key is not set.
        if (url.code() == 1 && url.err().isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(url.orFail());
    }

    /** What one git command said. */
    private record Said(String command, int code, String out, String err) {

        /** The output, or the failure as an exception. */
        String orFail() throws IOException {
            if (code != 0) {
                throw new IOException(command + " exited " + code + ": " + err);
            }
            return out;
        }
    }

    private static Said git(Path at, Map<String, String> environment, String... arguments)
            throws IOException {
        final List<String> command = new ArrayList<>(List.of("git", "-C", at.toString()));
        command.addAll(List.of(arguments));
        final ProcessBuilder builder = new ProcessBuilder(command);
        builder.environment().putAll(environment);
        ELSEWHERE.forEach(builder.environment()::remove);
        builder.environment().put("LC_ALL", "C");
        final Process git = builder.start();
        try {
            git.getOutputStream().close();
            final CompletableFuture<byte[]> err =
                    CompletableFuture.supplyAsync(
                            () -> {
                                try {
                                    return git.getErrorStream().readAllBytes();
                                } catch (IOException ex) {
                                    throw new UncheckedIOException(ex);
                                }
                            });
            final String out =
                    new String(git.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            final int code = git.waitFor();
            final String said = new String(err.join(), StandardCharsets.UTF_8).strip();
            return new Said(String.join(" ", command), code, out.strip(), said);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IOException("interrupted while git read the origin", ex);
        } catch (CompletionException ex) {
            throw new IOException("could not read what git said", ex.getCause());
        } finally {
            git.destroy();
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
