package org.fuin.porelo.release;

import java.io.IOException;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
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
import org.jspecify.annotations.Nullable;

/**
 * Refuses the four ways a page or a file can cite an issue and go stale.
 *
 * <p>A finished issue is deleted, file and index row together, so a link to its file breaks exactly
 * when it is done, a pointer to it points at nothing, and a number anywhere outside the issues
 * names something nobody can look up. The index is the one page that may name files, because naming
 * them is what an index is; issues may name each other's files and numbers, since they are deleted
 * together with every mention of their number - but a number of this repository's own there must
 * still name an issue that exists, or it is the mention a deletion missed.
 *
 * <p>Every committed text file is read: in a git checkout what {@code git ls-files} names, in an
 * exported tree every file. A binary file is skipped and counted.
 */
final class CheckCitations {

    /**
     * The file at a repository's root naming whole files that hold an issue number's shape on
     * purpose.
     */
    static final String EXEMPT = "citations-exempt.txt";

    /**
     * A file under {@code issues/} that is deleted when its subject is settled: a numbered issue or
     * a design.
     */
    private static final Pattern DIES =
            Pattern.compile("/[A-Z]+\\d{2,3}-[^/]*\\.md$|_design\\.md$");

    /** A Markdown link's target, without the title some links carry. */
    private static final Pattern LINK = Pattern.compile("\\]\\(([^)\\s]+)");

    /**
     * An issue cited as the rules ask: the number, then the index. Bold, code and a wrapped line
     * may sit between them, since pages wrap and a citation is often emphasized.
     */
    private static final Pattern POINTER =
            Pattern.compile("\\b([A-Z]{1,3}\\d{2,3})\\b[\\s*_`]{0,10}\\(?\\[index\\]\\(([^)\\s]+)");

    /** An issue's own file, named by its number: its prefix, then its digits. */
    private static final Pattern ISSUE_FILE = Pattern.compile("^(([A-Z]{1,3})\\d{2,3})-.*\\.md$");

    /** The shape of an issue number. */
    private static final Pattern NUMBER = Pattern.compile("\\b[A-Z]{1,2}\\d{2,3}\\b");

    /** An issue number with its prefix apart, for the repository's own among the issues. */
    private static final Pattern NUMBER_OF_ANY_PREFIX =
            Pattern.compile("\\b([A-Z]{1,3})\\d{2,3}\\b");

    /** Names of an issue number's shape that are something else: PKCE's challenge method. */
    private static final Set<String> NOT_CITATIONS = Set.of("S256");

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

    private final Web web;

    @Nullable private final Path checkouts;

    /**
     * Constructor.
     *
     * @param out where the result goes
     * @param err where a refusal goes
     */
    CheckCitations(PrintStream out, PrintStream err) {
        this(out, err, Web.overHttp(), null);
    }

    /**
     * Constructor.
     *
     * @param out where the result goes
     * @param err where a refusal goes
     * @param web where another repository's index is read
     * @param checkouts a directory holding the repositories beside each other, read instead of the
     *     web; or null
     */
    CheckCitations(PrintStream out, PrintStream err, Web web, @Nullable Path checkouts) {
        this.out = out;
        this.err = err;
        this.web = web;
        this.checkouts = checkouts;
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
            final List<Path> handwritten = new ArrayList<>();
            for (final Path path : committed(root)) {
                if (generated(root, path)) continue;
                if (generatedPlan(path)) generatedFiles++;
                else handwritten.add(path);
            }
            files = handwritten.stream().sorted().toList();
            exempt = exempt(root);
        } catch (IOException | UncheckedIOException ex) {
            err.println("could not list the files under " + root + ": " + Web.reason(ex));
            return Stop.UNANSWERED;
        }
        final List<Path> pages =
                files.stream().filter(path -> path.toString().endsWith(".md")).toList();
        if (pages.isEmpty()) {
            err.println(
                    root
                            + ": no Markdown page under it, so nothing is checked - is it a repository's root?");
            return Stop.REFUSED;
        }
        final Set<String> issues =
                files.stream()
                        .filter(path -> isIssue(root, path))
                        .map(path -> ISSUE_FILE.matcher(path.getFileName().toString()))
                        .filter(Matcher::matches)
                        .map(match -> match.group(1))
                        .collect(Collectors.toSet());
        final Set<String> prefixes =
                files.stream()
                        .filter(path -> isIssue(root, path))
                        .map(path -> ISSUE_FILE.matcher(path.getFileName().toString()))
                        .filter(Matcher::matches)
                        .map(match -> match.group(2))
                        .collect(Collectors.toSet());
        final Set<String> names =
                files.stream().map(path -> relative(root, path)).collect(Collectors.toSet());
        final List<String> stale =
                exempt.stream()
                        .filter(name -> !names.contains(name))
                        .map(name -> EXEMPT + " names " + name + ", which is not a committed file")
                        .toList();
        final List<String> links = new ArrayList<>();
        final List<String> pointers = new ArrayList<>();
        final List<String> numbers = new ArrayList<>();
        final List<String> gone = new ArrayList<>();
        final IssueLinks issueLinks = new IssueLinks(web, checkouts);
        int read = 0;
        int binary = 0;
        try {
            // Only the project's requirements and plans name another repository's issues, so a
            // wait kept anywhere else cannot go stale when that repository renumbers.
            if (!isProject(root, names)) {
                issueLinks.refuseCitationsOfOtherRepositories(root);
            }
            issueLinks.read(
                    root, pages.stream().filter(page -> isIssue(root, page)).toList(), prefixes);
            final Set<String> named = new java.util.HashSet<>(issues);
            // A finished issue keeps its row when its file goes, so a row is as good as a file.
            named.addAll(issueLinks.rows);
            for (final Path page : pages) {
                final Optional<String> text = text(page);
                if (text.isPresent()) {
                    links.addAll(linksToFilesThatDie(root, page, text.get()));
                    pointers.addAll(pointersToIssuesThatAreGone(root, page, text.get(), issues));
                }
            }
            for (final Path file : files) {
                final String name = relative(root, file);
                if (isIssue(root, file)) {
                    final Optional<String> text = text(file);
                    if (text.isPresent()) {
                        gone.addAll(ownNumbersThatAreGone(root, file, text.get(), prefixes, named));
                    }
                    continue;
                }
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
        issueLinks.notes().forEach(note -> out.println("NOTE  " + note));
        final List<String> unanchored = issueLinks.unanchored;
        final List<String> unlinked = issueLinks.unlinked;
        final List<String> broken = issueLinks.broken;
        final List<String> rowless = issueLinks.rowless;
        final List<String> crossed = issueLinks.crossed;
        // Another repository's number is said once, as that, though a fixture shares its prefix.
        gone.removeAll(crossed);
        if (stale.isEmpty()
                && crossed.isEmpty()
                && links.isEmpty()
                && pointers.isEmpty()
                && numbers.isEmpty()
                && gone.isEmpty()
                && unanchored.isEmpty()
                && unlinked.isEmpty()
                && broken.isEmpty()
                && rowless.isEmpty()) {
            out.println(
                    "OK    citations in "
                            + root
                            + ": "
                            + pages.size()
                            + " page(s), "
                            + read
                            + " text file(s), "
                            + binary
                            + " binary and "
                            + generatedFiles
                            + " generated file(s) skipped, "
                            + exempt.size()
                            + " exempt, "
                            + issues.size()
                            + " issue(s)");
            return 0;
        }
        report(
                stale,
                "an exemption for a file that is not there: remove the line, so the list says what is true");
        report(
                links,
                "a link to an issue's file, from a page that is not an issues index: link to the index and"
                        + " name the number, because a finished issue takes its file with it");
        report(
                pointers,
                "a pointer to an issue that no longer exists: a number with the index beside it sends"
                        + " somebody to an issue, and is worth nothing once it is gone");
        report(
                gone,
                "a number of this repository's own issues, among the issues, that names no issue file: the"
                        + " mention its deletion missed");
        report(
                numbers,
                "an issue number outside the issues: name the constraint, not the issue that recorded it;"
                        + " a line holding the shape on purpose says not-a-citation, a whole such file is named in "
                        + EXEMPT);
        report(
                unanchored,
                "an index row that does not open with the anchor of its number, <a id=\"<number in lower"
                        + " case>\"></a>: a link to the row needs it");
        report(
                unlinked,
                "an issue number under issues/ that is not a link to its row: write [<NUMBER>](<index>#<number>),"
                        + " never a link to its file, since the row outlives the file");
        report(broken, "a link to an index row that is not there, or that names another number");
        report(rowless, "a number no index has a row for: rewrite the text, it cannot be linked");
        report(
                crossed,
                "a citation of another repository's issue, outside "
                        + IssueLinks.PROJECT
                        + ": state in words the contract it stands for; the project's plan names"
                        + " the issue and what waits for it");
        err.println(
                (stale.size()
                                + crossed.size()
                                + links.size()
                                + pointers.size()
                                + numbers.size()
                                + gone.size()
                                + unanchored.size()
                                + unlinked.size()
                                + broken.size()
                                + rowless.size())
                        + " fault(s) in "
                        + root);
        return Stop.REFUSED;
    }

    /**
     * Returns the prefixes an issue number is made of, as this check reads them: those of the
     * repository's own issue files and index rows, and of every index its issues link to.
     *
     * @param root the repository's root
     * @param web where another repository's index is read
     * @param checkouts a directory holding the repositories beside each other, read instead of the
     *     web; or null
     * @param out where an index that could not be read is named
     * @return the prefixes, such as {@code BT}
     * @throws IOException when the issues cannot be listed or read
     */
    static Set<String> issuePrefixes(Path root, Web web, @Nullable Path checkouts, PrintStream out)
            throws IOException {
        final List<Path> pages =
                committed(root).stream()
                        .filter(path -> isIssue(root, path) && path.toString().endsWith(".md"))
                        .filter(path -> !generated(root, path))
                        .sorted()
                        .toList();
        final Set<String> own =
                pages.stream()
                        .map(path -> ISSUE_FILE.matcher(path.getFileName().toString()))
                        .filter(Matcher::matches)
                        .map(match -> match.group(2))
                        .collect(Collectors.toSet());
        final IssueLinks issueLinks = new IssueLinks(web, checkouts);
        issueLinks.read(root, pages, own);
        issueLinks.notes().forEach(note -> out.println("NOTE  " + note));
        return Set.copyOf(issueLinks.prefixes);
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
                    .filter(Files::isRegularFile)
                    .toList();
        }
        try (Stream<Path> tree = Files.walk(root)) {
            return tree.filter(Files::isRegularFile).toList();
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

    private static List<String> linksToFilesThatDie(Path root, Path page, String text) {
        // An index names files because that is what an index does; issues are deleted with every
        // mention of theirs.
        if (isIssue(root, page)) {
            return List.of();
        }
        final List<String> faults = new ArrayList<>();
        final Matcher link = LINK.matcher(text);
        while (link.find()) {
            final String target = link.group(1);
            if (target.contains("issues/")
                    && DIES.matcher(target.replaceFirst("#.*$", "")).find()) {
                faults.add(
                        relative(root, page) + ":" + lineOf(text, link.start()) + " -> " + target);
            }
        }
        return faults;
    }

    private static List<String> pointersToIssuesThatAreGone(
            Path root, Path page, String text, Set<String> issues) {
        final List<String> faults = new ArrayList<>();
        final Matcher pointer = POINTER.matcher(text);
        while (pointer.find()) {
            // An index elsewhere is another repository's, whose issues this one cannot see.
            if (pointer.group(2).contains("://")) {
                continue;
            }
            if (!issues.contains(pointer.group(1))) {
                faults.add(
                        relative(root, page)
                                + ":"
                                + lineOf(text, pointer.start())
                                + " -> "
                                + pointer.group(1));
            }
        }
        return faults;
    }

    private static List<String> numbersIn(Path root, Path file, String text) {
        final List<String> faults = new ArrayList<>();
        final Matcher number = NUMBER.matcher(blank(MARKED, blank(LINT_CODES, text)));
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
        return faults;
    }

    private static List<String> ownNumbersThatAreGone(
            Path root, Path file, String text, Set<String> prefixes, Set<String> issues) {
        final List<String> faults = new ArrayList<>();
        final Matcher number = NUMBER_OF_ANY_PREFIX.matcher(blank(MARKED, text));
        while (number.find()) {
            if (prefixes.contains(number.group(1)) && !issues.contains(number.group())) {
                faults.add(
                        relative(root, file)
                                + ":"
                                + lineOf(text, number.start())
                                + " -> "
                                + number.group());
            }
        }
        return faults;
    }

    private static String blank(Pattern pattern, String text) {
        return pattern.matcher(text).replaceAll(found -> " ".repeat(found.group().length()));
    }

    private static boolean isIssue(Path root, Path path) {
        final String name = relative(root, path);
        return name.startsWith("issues/") || name.contains("/issues/");
    }

    /**
     * Whether the root is a project's own repository: a committed {@code project.yml} naming its
     * project.
     */
    private static boolean isProject(Path root, Set<String> committed) throws IOException {
        if (!committed.contains("project.yml")) {
            return false;
        }
        final var tree =
                new com.fasterxml.jackson.databind.ObjectMapper(
                                new com.fasterxml.jackson.dataformat.yaml.YAMLFactory())
                        .readTree(root.resolve("project.yml").toFile());
        final var project = tree == null ? null : tree.get("project");
        return project != null && project.isObject();
    }

    private static boolean generatedPlan(Path path) throws IOException {
        if (!path.getFileName().toString().equals("README.md")) return false;
        final Path plan = path.resolveSibling("plan.yml");
        if (!Files.isRegularFile(plan)) return false;
        // Only a plan beside it makes the page a rendered one: a YAML mapping naming its release
        // and sprints.
        try {
            final var tree =
                    new com.fasterxml.jackson.databind.ObjectMapper(
                                    new com.fasterxml.jackson.dataformat.yaml.YAMLFactory())
                            .readTree(plan.toFile());
            if (tree == null || !tree.isObject() || !tree.has("release") || !tree.has("sprints"))
                return false;
        } catch (IOException ex) {
            return false;
        }
        try (var reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            return PlanRender.GENERATED_MARKER.equals(reader.readLine());
        } catch (CharacterCodingException ex) {
            return false;
        }
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
