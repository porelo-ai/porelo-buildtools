package org.fuin.porelo.release;

import java.io.IOException;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Refuses an issue number in a committed file: told by its shape or as a GitHub reference.
 *
 * <p>Issues live in a tracker and are closed there, so a number in code, a page or anything that
 * ships names something that goes stale; the constraint itself is written instead. A line that
 * holds the shape on purpose says {@code not-a-citation}, and a whole such file is named in {@link
 * #EXEMPT}, a test's fixture in a nested {@code issues} or {@code stories} directory too.
 *
 * <p>Every committed text file is read: in a git checkout what {@code git ls-files} names, in an
 * exported tree every file. A binary file is skipped and counted. Nothing outside the repository is
 * read, so another repository's state never decides this one's check.
 */
final class CheckCitations {

    /**
     * The file at a repository's root naming whole files that hold an issue number's shape on
     * purpose.
     */
    static final String EXEMPT = "citations-exempt.txt";

    /** The shape of an issue number. */
    static final Pattern SHAPE = Pattern.compile("\\b[A-Z]{1,2}\\d{2,3}\\b");

    /**
     * A reference GitHub links to an issue of the project, the owner in any case: the issue's
     * address, with or without its scheme, {@code porelo-ai/<repository>#<n>}, or {@code GH-<n>}. A
     * bare {@code #<n>} is no such reference in a file, where it is far more often a colour, a
     * stack frame or an anchor; a commit message holds it to the rule too.
     */
    static final Pattern GITHUB_REFERENCE =
            Pattern.compile(
                    "(?<!\\w)(?:(?:https?://)?(?:www\\.)?github\\.com/porelo-ai/[\\w.-]+/issues/\\d+"
                            + "|porelo-ai/[\\w.-]+#\\d+|GH-\\d+)\\b",
                    Pattern.CASE_INSENSITIVE);

    /** Names of an issue number's shape that are something else: PKCE's challenge method. */
    static final Set<String> NOT_CITATIONS = Set.of("S256");

    /**
     * A linter's codes on the line it silences ({@code # noqa: E402}), which have an issue number's
     * shape.
     */
    private static final Pattern LINT_CODES = Pattern.compile("noqa:[A-Z0-9, ]*");

    /**
     * A line that says in its own text that it holds an issue number's shape on purpose - test data
     * for something that reads such text - so the exception stands where a reviewer reads it.
     */
    private static final Pattern MARKED = Pattern.compile("(?m)^.*not-a-citation.*$");

    /**
     * Files no person writes, whose text has an issue number's shape by chance: an image's path
     * data, a lock file's hashes.
     */
    private static final Pattern NOT_WRITTEN =
            Pattern.compile("(^|/)(package-lock\\.json|[^/]+\\.lock|[^/]+\\.svg)$");

    /** Directories nothing in them is written by a person: build output, tool state, git's own. */
    private static final List<String> GENERATED =
            List.of(
                    "/target/",
                    "/build/",
                    "/.git/",
                    "/.idea/",
                    "/.dart_tool/",
                    "/node_modules/",
                    "/.gradle/");

    private final PrintStream out;

    private final PrintStream err;

    /**
     * Constructor.
     *
     * @param out where the result goes
     * @param err where a refusal goes
     */
    CheckCitations(PrintStream out, PrintStream err) {
        this.out = out;
        this.err = err;
    }

    /**
     * Checks every committed file under a repository's root.
     *
     * @param root the repository's root
     * @return 0 when nothing cites an issue in a way that goes stale, {@link Stop#REFUSED} when
     *     something does or there is nothing to check, {@link Stop#UNANSWERED} when a file cannot
     *     be read or listed
     */
    int check(Path root) {
        final List<Path> files;
        final Set<String> exempt;
        int generatedFiles = 0;
        try {
            files =
                    committed(root).stream()
                            .filter(path -> !generated(root, path))
                            .sorted()
                            .toList();
            exempt = exempt(root);
        } catch (IOException | UncheckedIOException ex) {
            err.println("could not list the files under " + root + ": " + Web.reason(ex));
            return Stop.UNANSWERED;
        }
        if (files.stream().noneMatch(path -> path.toString().endsWith(".md"))) {
            err.println(
                    root
                            + ": no Markdown page under it, so nothing is checked - is it a"
                            + " repository's root?");
            return Stop.REFUSED;
        }
        final Set<String> names =
                files.stream().map(path -> relative(root, path)).collect(Collectors.toSet());
        final List<String> stale =
                exempt.stream()
                        .filter(name -> !names.contains(name))
                        .map(name -> EXEMPT + " names " + name + ", which is not a committed file")
                        .toList();
        final List<String> numbers = new ArrayList<>();
        int read = 0;
        int binary = 0;
        try {
            for (final Path file : files) {
                final String name = relative(root, file);
                if (name.equals(EXEMPT) || exempt.contains(name)) {
                    continue;
                }
                if (NOT_WRITTEN.matcher(name).find()) {
                    generatedFiles++;
                    continue;
                }
                final Optional<String> text = text(file);
                if (text.isEmpty()) {
                    binary++;
                    continue;
                }
                read++;
                numbers.addAll(numbersIn(root, file, text.get()));
            }
        } catch (IOException | UncheckedIOException ex) {
            err.println("could not read under " + root + ": " + Web.reason(ex));
            return Stop.UNANSWERED;
        }
        if (stale.isEmpty() && numbers.isEmpty()) {
            out.println(
                    "OK    citations in "
                            + root
                            + ": "
                            + read
                            + " text file(s), "
                            + binary
                            + " binary and "
                            + generatedFiles
                            + " generated file(s) skipped, "
                            + exempt.size()
                            + " exempt");
            return 0;
        }
        report(
                stale,
                "an exemption for a file that is not there: remove the line, so the list says what"
                        + " is true");
        report(
                numbers,
                "an issue number: name the constraint, not the issue that recorded it; a line"
                        + " holding the shape on purpose says not-a-citation, a whole such file is"
                        + " named in "
                        + EXEMPT);
        err.println((stale.size() + numbers.size()) + " fault(s) in " + root);
        return Stop.REFUSED;
    }

    private void report(List<String> faults, String why) {
        if (!faults.isEmpty()) {
            err.println(why + ":");
            faults.forEach(fault -> err.println("    " + fault));
        }
    }

    /**
     * Returns what is committed: in a git checkout what git tracks, so a log or a file a tool left
     * beside the work is not read; in a tree exported from a commit, which holds nothing else,
     * every file.
     */
    static List<Path> committed(Path root) throws IOException {
        return entries(root).stream().filter(Files::isRegularFile).toList();
    }

    /**
     * Lists every committed entry, a link among them wherever it points or whether it points
     * anywhere at all; {@link #committed} keeps the files of them.
     */
    private static List<Path> entries(Path root) throws IOException {
        if (Files.exists(root.resolve(".git"))) {
            final Process git =
                    new ProcessBuilder("git", "-C", root.toString(), "ls-files", "-z")
                            .redirectError(ProcessBuilder.Redirect.DISCARD)
                            .start();
            final String listed =
                    new String(git.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            try {
                if (git.waitFor() != 0) {
                    throw new IOException("git ls-files exited with " + git.exitValue());
                }
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new IOException("interrupted while git listed the files", ex);
            }
            return Stream.of(listed.split("\0"))
                    .filter(name -> !name.isEmpty())
                    .map(root::resolve)
                    .toList();
        }
        try (Stream<Path> tree = Files.walk(root)) {
            return tree.filter(path -> !Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS))
                    .toList();
        }
    }

    /**
     * Returns the paths {@link #EXEMPT} names, each relative to the root; {@code #} starts a
     * comment.
     */
    private static Set<String> exempt(Path root) throws IOException {
        final Path list = root.resolve(EXEMPT);
        if (!Files.isRegularFile(list)) {
            return Set.of();
        }
        final Set<String> names = new LinkedHashSet<>();
        for (final String line : Files.readAllLines(list, StandardCharsets.UTF_8)) {
            final String name = line.replaceFirst("#.*$", "").strip();
            if (!name.isEmpty()) {
                names.add(name);
            }
        }
        return names;
    }

    /**
     * Returns a file's text, or nothing for a binary file: one holding a NUL byte or bytes that are
     * not UTF-8.
     */
    private static Optional<String> text(Path file) throws IOException {
        final byte[] bytes = Files.readAllBytes(file);
        for (final byte b : bytes) {
            if (b == 0) {
                return Optional.empty();
            }
        }
        try {
            return Optional.of(
                    StandardCharsets.UTF_8
                            .newDecoder()
                            .onMalformedInput(CodingErrorAction.REPORT)
                            .onUnmappableCharacter(CodingErrorAction.REPORT)
                            .decode(ByteBuffer.wrap(bytes))
                            .toString());
        } catch (CharacterCodingException ex) {
            return Optional.empty();
        }
    }

    private static List<String> numbersIn(Path root, Path file, String text) {
        final List<String> faults = new ArrayList<>();
        final String read = blank(MARKED, blank(LINT_CODES, text));
        final Matcher number = SHAPE.matcher(read);
        while (number.find()) {
            if (!NOT_CITATIONS.contains(number.group())) {
                faults.add(
                        relative(root, file)
                                + ":"
                                + lineOf(text, number.start())
                                + " -> "
                                + number.group());
            }
        }
        final Matcher reference = GITHUB_REFERENCE.matcher(read);
        while (reference.find()) {
            faults.add(
                    relative(root, file)
                            + ":"
                            + lineOf(text, reference.start())
                            + " -> "
                            + reference.group());
        }
        return faults;
    }

    private static String blank(Pattern pattern, String text) {
        return pattern.matcher(text).replaceAll(found -> " ".repeat(found.group().length()));
    }

    private static boolean generated(Path root, Path path) {
        final String name = "/" + relative(root, path);
        return GENERATED.stream().anyMatch(name::contains);
    }

    private static int lineOf(String text, int offset) {
        return (int) text.substring(0, offset).chars().filter(c -> c == '\n').count() + 1;
    }

    private static String relative(Path root, Path path) {
        return root.relativize(path).toString().replace('\\', '/');
    }
}
