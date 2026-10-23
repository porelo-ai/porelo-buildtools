package org.fuin.porelo.release;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/**
 * Reads how the pages under {@code issues/} name an issue: every number a link to its row in an
 * index, every row anchored by its number, every such link reaching an anchor that is there.
 *
 * <p>The row outlives the file - a finished issue keeps its row - so a link to the row does not
 * break when the issue is done. A row of this repository is read from the tree; one of another
 * repository from that repository's index on {@code main}, or from a checkout beside this one. An
 * index that cannot be read is named, and the links into it are left unchecked rather than failed:
 * a repository not published yet has no index to read.
 */
final class IssueLinks {

    /** Where the repositories' indexes are published. */
    private static final Pattern GITHUB =
            Pattern.compile(
                    "^https://github\\.com/porelo-ai/([\\w.-]+)/blob/main/([^#\\s]+)#([a-z]{1,3}\\d{2,3})$");

    /**
     * An index row's first cell: an optional anchor, then the number, perhaps linked to its file.
     */
    private static final Pattern ROW =
            Pattern.compile(
                    "^\\|\\s*(?:<a id=\"([^\"]*)\"></a>)?\\s*\\[?([A-Z]{1,3}\\d{2,3})\\b(?:\\]\\([^)]*\\))?");

    /** An anchor anywhere on a page. */
    private static final Pattern ANCHOR = Pattern.compile("<a id=\"([^\"]+)\"></a>");

    /** A Markdown link with its text and target. */
    private static final Pattern LINK = Pattern.compile("\\[([^\\]\\n]*)\\]\\(([^)\\s]+)\\)");

    /** A target ending in an anchor of an issue number's shape. */
    private static final Pattern TO_A_ROW = Pattern.compile("#([a-z]{1,3}\\d{2,3})$");

    /** The text of a link that is one issue number, perhaps emphasized. */
    private static final Pattern NUMBER_TEXT =
            Pattern.compile("^[*_`]*([A-Z]{1,3}\\d{2,3})[*_`]*$");

    /** An issue number with its prefix apart. */
    private static final Pattern NUMBER = Pattern.compile("\\b([A-Z]{1,3})\\d{2,3}\\b");

    /** A code span on one line. */
    private static final Pattern CODE_SPAN = Pattern.compile("`[^`\\n]*`");

    /** A line that says it holds the shape on purpose. */
    private static final String MARKED = "not-a-citation";

    /** Names of an issue number's shape that are something else. */
    private static final Set<String> NOT_ISSUES = Set.of("S256");

    private static final Set<String> STATES =
            Set.of(
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
    private static final Pattern STATE_HEADING = Pattern.compile("^ {0,3}#{2,6}\\s+(.+)$");
    private static final Pattern FENCE = Pattern.compile("^ {0,3}(`{3,}|~{3,})(.*)$");

    private final Web web;

    @Nullable private final Path checkouts;

    /**
     * Another repository's index, by repository and path: its anchors, or why it could not be read.
     */
    private final Map<String, Index> elsewhere = new LinkedHashMap<>();

    private final Map<String, Path> trees = new HashMap<>();

    private final Set<Path> inputs = new HashSet<>();

    /** Local files actually read while resolving the plan's issue metadata. */
    Set<Path> inputs() {
        return Set.copyOf(inputs);
    }

    private final Map<String, Map<String, IssueIndex>> planIndexes = new HashMap<>();

    private final Map<String, List<String>> planIndexSources = new HashMap<>();

    /** Names the read indexes with matching prefixes, or all read indexes when none match. */
    String issueIndexSource(String named) {
        final String[] parts = named.split("#", 2);
        final List<String> matching =
                Objects.requireNonNull(planIndexes.get(parts[0])).values().stream()
                        .filter(row -> prefixOf(row.number()).equals(prefixOf(parts[1])))
                        .map(IssueIndex::source)
                        .distinct()
                        .sorted()
                        .toList();
        return String.join(
                ", ",
                matching.isEmpty()
                        ? Objects.requireNonNull(planIndexSources.get(parts[0]))
                        : matching);
    }

    /** Registers the owning tree even when its worktree directory has another name. */
    void tree(String repository, Path root) {
        trees.put(repository, root);
    }

    /** The links into an index that could not be read, by that index. */
    private final Map<String, Integer> unchecked = new LinkedHashMap<>();

    final List<String> unanchored = new ArrayList<>();

    final List<String> unlinked = new ArrayList<>();

    final List<String> broken = new ArrayList<>();

    final List<String> rowless = new ArrayList<>();

    /** The numbers that have a row in this repository's indexes, finished ones among them. */
    final Set<String> rows = new HashSet<>();

    /**
     * The prefixes an issue number is made of, once read: this repository's own, its rows', and
     * those of every index its pages link to.
     */
    final Set<String> prefixes = new HashSet<>();

    /**
     * Constructor.
     *
     * @param web where another repository's index is read
     * @param checkouts a directory holding the repositories beside each other, read instead of the
     *     web; or null
     */
    IssueLinks(Web web, @Nullable Path checkouts) {
        this.web = web;
        this.checkouts = checkouts;
    }

    /**
     * Reads the pages under {@code issues/}.
     *
     * @param root the repository's root
     * @param pages every Markdown page under {@code issues/}
     * @param ownPrefixes the prefixes of this repository's issue files
     * @throws IOException when a page cannot be read
     */
    void read(Path root, List<Path> pages, Set<String> ownPrefixes) throws IOException {
        final Map<Path, Set<String>> anchors = new HashMap<>();
        final Map<Path, String> texts = new LinkedHashMap<>();
        for (final Path page : pages) {
            final String text = Files.readString(page, StandardCharsets.UTF_8);
            texts.put(page, text);
            anchors.put(page.toAbsolutePath().normalize(), anchorsIn(text));
            if (page.getFileName().toString().equals("README.md")) {
                readRows(root, page, text);
            }
        }
        prefixes.addAll(ownPrefixes);
        rows.forEach(number -> prefixes.add(prefixOf(number)));
        // Every index linked to is read first, so its prefix is known before the plain numbers are
        // looked for.
        for (final String text : texts.values()) {
            final Matcher link = LINK.matcher(text);
            while (link.find()) {
                final Matcher there = GITHUB.matcher(link.group(2));
                if (there.matches()) {
                    index(there.group(1), there.group(2))
                            .ifPresent(
                                    index ->
                                            index.numbers.forEach(
                                                    number -> prefixes.add(prefixOf(number))));
                }
            }
        }
        for (final Map.Entry<Path, String> page : texts.entrySet()) {
            readPage(root, page.getKey(), page.getValue(), anchors, prefixes, ownPrefixes);
        }
    }

    /**
     * Lines for the result: the indexes that could not be read and how many links into them went
     * unchecked.
     */
    List<String> notes() {
        final List<String> notes = new ArrayList<>();
        elsewhere.forEach(
                (name, index) -> {
                    if (index.unread != null) {
                        notes.add("not read: " + name + " (" + index.unread + ")");
                    }
                });
        final int links = unchecked.values().stream().mapToInt(Integer::intValue).sum();
        if (links > 0) {
            notes.add(links + " link(s) into them not checked");
        }
        return notes;
    }

    private void readRows(Path root, Path page, String text) {
        for (final Row row : indexRows(text)) {
            rows.add(row.number());
            if (!row.number().toLowerCase(java.util.Locale.ROOT).equals(row.anchor())) {
                unanchored.add(relative(root, page) + ":" + row.line() + " -> " + row.number());
            }
        }
    }

    private void readPage(
            Path root,
            Path page,
            String text,
            Map<Path, Set<String>> anchors,
            Set<String> prefixes,
            Set<String> ownPrefixes) {
        final boolean isIndex = page.getFileName().toString().equals("README.md");
        final String[] lines = text.split("\n", -1);
        boolean fenced = false;
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            if (line.strip().startsWith("```")) {
                fenced = !fenced;
                continue;
            }
            if (fenced || line.startsWith("#") || line.contains(MARKED)) {
                continue;
            }
            final String where = relative(root, page) + ":" + (i + 1) + " -> ";
            line = blank(CODE_SPAN, line);
            if (isIndex) {
                // The row's own first cell names its file, as an index does.
                final Matcher row = ROW.matcher(line);
                if (row.find()) {
                    line = " ".repeat(row.end()) + line.substring(row.end());
                }
            }
            final Matcher link = LINK.matcher(line);
            final StringBuilder rest = new StringBuilder();
            int last = 0;
            while (link.find()) {
                checkLink(page, where, link.group(1), link.group(2), anchors);
                rest.append(line, last, link.start()).append(" ".repeat(link.end() - link.start()));
                last = link.end();
            }
            rest.append(line.substring(last));
            final Matcher number = NUMBER.matcher(rest);
            while (number.find()) {
                final String found = number.group();
                if (!prefixes.contains(number.group(1)) || NOT_ISSUES.contains(found)) {
                    continue;
                }
                if (hasRow(found)) {
                    unlinked.add(where + found);
                } else if (!ownPrefixes.contains(number.group(1))) {
                    // One of this repository's own is the deletion a mention missed, which is said
                    // apart.
                    rowless.add(where + found);
                }
            }
        }
    }

    private void checkLink(
            Path page, String where, String text, String target, Map<Path, Set<String>> anchors) {
        final Matcher named = NUMBER_TEXT.matcher(text.strip());
        final Matcher toRow = TO_A_ROW.matcher(target);
        if (!toRow.find()) {
            if (named.matches()) {
                unlinked.add(where + target);
            }
            return;
        }
        final String anchor = toRow.group(1);
        if (named.matches() && !named.group(1).toLowerCase(java.util.Locale.ROOT).equals(anchor)) {
            broken.add(where + named.group(1) + " linked to " + target);
            return;
        }
        final Matcher there = GITHUB.matcher(target);
        if (there.matches()) {
            final Optional<Index> index = index(there.group(1), there.group(2));
            if (index.isEmpty()) {
                unchecked.merge(there.group(1) + " " + there.group(2), 1, Integer::sum);
            } else if (!index.get().anchors.contains(anchor)) {
                broken.add(where + target);
            }
            return;
        }
        if (target.contains("://")) {
            return;
        }
        final Path folder = page.toAbsolutePath().getParent();
        if (folder == null) {
            return;
        }
        // A link to an anchor on the same page names no file.
        final Path file =
                target.startsWith("#")
                        ? page.toAbsolutePath().normalize()
                        : folder.resolve(target.substring(0, target.indexOf('#'))).normalize();
        final Set<String> here = anchors.get(file);
        if (here == null || !here.contains(anchor)) {
            broken.add(where + target);
        }
    }

    private boolean hasRow(String number) {
        if (rows.contains(number)) {
            return true;
        }
        return elsewhere.values().stream().anyMatch(index -> index.numbers.contains(number));
    }

    /** Returns another repository's index, read once; empty when it could not be read. */
    private Optional<Index> index(String repository, String path) {
        final Index index =
                elsewhere.computeIfAbsent(repository + " " + path, name -> load(repository, path));
        return index.unread == null ? Optional.of(index) : Optional.empty();
    }

    private Index load(String repository, String path) {
        try {
            final Optional<String> text = source(null, repository, path);
            if (text.isEmpty()) {
                return new Index(Set.of(), Set.of(), "not published");
            }
            final Set<String> numbers = new HashSet<>();
            indexRows(text.get()).forEach(row -> numbers.add(row.number()));
            return new Index(anchorsIn(text.get()), numbers, null);
        } catch (IOException | UncheckedIOException ex) {
            return new Index(Set.of(), Set.of(), ex.getMessage());
        }
    }

    /** Reads a plan's indexes through the same tree, neighboring checkout, and web sources. */
    Map<String, IssueIndex> issueIndex(Path root, String repository) throws IOException, Stop {
        final Map<String, IssueIndex> cached = planIndexes.get(repository);
        if (cached != null) {
            return cached;
        }
        final Map<String, IssueIndex> result = new LinkedHashMap<>();
        issueIndex(root, repository, "issues/README.md", new HashSet<>(), result);
        final Map<String, IssueIndex> snapshot = Map.copyOf(result);
        planIndexes.put(repository, snapshot);
        return snapshot;
    }

    private void issueIndex(
            Path root,
            String repository,
            String path,
            Set<String> visited,
            Map<String, IssueIndex> result)
            throws IOException, Stop {
        if (!visited.add(path)) {
            return;
        }
        final URI location = sourceLocation(root, repository, path);
        final String source =
                location.getScheme().equals("file")
                        ? Path.of(location).toString()
                        : location.toString();
        final String text;
        try {
            text = source(location).orElseThrow(() -> new IOException("not found"));
        } catch (IOException | UncheckedIOException ex) {
            throw new IOException(source + ": index not readable: " + ex.getMessage(), ex);
        }
        planIndexSources.computeIfAbsent(repository, key -> new ArrayList<>()).add(source);
        for (final Row row : indexRows(text)) {
            if (!STATES.contains(row.state())) {
                throw Stop.refused(source + ": unknown state heading: " + row.state());
            }
            String title = row.title();
            if (row.file() != null) {
                final String issuePath = indexPath(path, row.file());
                if (issuePath != null) {
                    final Optional<String> issue = issueSource(root, repository, issuePath);
                    if (issue.isPresent()) {
                        final Pattern heading =
                                Pattern.compile(
                                        "^#\\s+" + row.number() + "\\s+[—–-]\\s+(.+?)\\s*$");
                        title =
                                issue.get()
                                        .lines()
                                        .map(heading::matcher)
                                        .filter(Matcher::matches)
                                        .map(match -> match.group(1))
                                        .findFirst()
                                        .orElse(title);
                    }
                }
            }
            final IssueIndex previous =
                    result.put(
                            row.number(),
                            new IssueIndex(
                                    row.number(), row.state(), row.size(), title, path, source));
            if (previous != null) {
                throw new IOException(
                        source
                                + ": duplicate index row "
                                + row.number()
                                + "; also in "
                                + previous.source());
            }
        }
        final Matcher link = LINK.matcher(unfenced(text));
        while (link.find()) {
            final String child = indexPath(path, link.group(2));
            if (child != null && child.endsWith("/README.md")) {
                issueIndex(root, repository, child, visited, result);
            }
        }
    }

    private Optional<String> issueSource(Path root, String repository, String path) {
        try {
            return source(root, repository, path);
        } catch (IOException | UncheckedIOException ex) {
            // A readable row remains useful when its optional issue file cannot be read.
            return Optional.empty();
        }
    }

    private Optional<String> source(@Nullable Path root, String repository, String path)
            throws IOException {
        return source(sourceLocation(root, repository, path));
    }

    private Optional<String> source(URI location) throws IOException {
        return location.getScheme().equals("file")
                ? localSource(Path.of(location))
                : web.text(location);
    }

    private URI sourceLocation(@Nullable Path root, String repository, String path) {
        final Path tree = trees.get(repository);
        if (tree != null) {
            return tree.resolve(path).toUri();
        }
        if (root != null && root.getFileName().toString().equals(repository)) {
            return root.resolve(path).toUri();
        }
        final Path beside = root == null ? null : root.resolveSibling(repository).resolve(path);
        if (root != null && beside != null && Files.isDirectory(root.resolveSibling(repository))) {
            return beside.toUri();
        }
        if (checkouts != null) {
            return checkouts.resolve(repository).resolve(path).toUri();
        }
        return URI.create(
                "https://raw.githubusercontent.com/porelo-ai/" + repository + "/main/" + path);
    }

    private Optional<String> localSource(Path file) throws IOException {
        if (!Files.isRegularFile(file)) {
            return Optional.empty();
        }
        final String text = Files.readString(file, StandardCharsets.UTF_8);
        inputs.add(file.toRealPath());
        return Optional.of(text);
    }

    private static @Nullable String indexPath(String index, String target) {
        target = target.split("#", 2)[0];
        if (target.isEmpty() || target.contains(":") || target.startsWith("/")) {
            return null;
        }
        final Path path = Path.of(index).resolveSibling(target).normalize();
        return path.startsWith("issues") ? path.toString().replace('\\', '/') : null;
    }

    /** The citation check and plan reader share one interpretation of an index row. */
    private static List<Row> indexRows(String text) {
        final List<Row> result = new ArrayList<>();
        String state = "Unknown";
        Map<String, Integer> columns = Map.of();
        final String[] lines = unfenced(text).split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            final String line = lines[i];
            final Matcher heading = STATE_HEADING.matcher(line);
            if (heading.matches()) {
                state = heading.group(1).strip().replaceFirst("\\s+#+$", "").stripTrailing();
                columns = Map.of();
            }
            final String[] cells = line.split("(?<!\\\\)\\|", -1);
            if (line.startsWith("|")
                    && java.util.Arrays.stream(cells).anyMatch(cell -> cell.strip().equals("#"))) {
                final Map<String, Integer> header = new HashMap<>();
                for (int column = 1; column < cells.length - 1; column++) {
                    header.put(cells[column].strip().toLowerCase(java.util.Locale.ROOT), column);
                }
                columns = header;
            }
            final Matcher row = ROW.matcher(line);
            if (!row.find()) {
                continue;
            }
            final Matcher link = LINK.matcher(cells[1]);
            final String file = link.find() ? link.group(2) : null;
            final boolean closed = state.equals("Finished") || state.equals("Rejected");
            final String size = closed ? "-" : cell(cells, columns.getOrDefault("size", 2));
            final String title =
                    cell(
                            cells,
                            columns.getOrDefault(
                                    closed ? "title" : "what it covers", closed ? 2 : 4));
            result.add(new Row(row.group(2), row.group(1), state, size, title, file, i + 1));
        }
        return result;
    }

    private static String cell(String[] cells, int column) {
        return column < cells.length - 1 ? cells[column].strip() : "";
    }

    private static String unfenced(String text) {
        final StringBuilder result = new StringBuilder();
        String fence = "";
        for (final String line : text.split("\n", -1)) {
            final Matcher marker = FENCE.matcher(line);
            if (marker.matches()) {
                final String run = marker.group(1);
                if (fence.isEmpty()) {
                    fence = run;
                } else if (run.charAt(0) == fence.charAt(0)
                        && run.length() >= fence.length()
                        && marker.group(2).isBlank()) {
                    fence = "";
                }
                result.append('\n');
            } else {
                result.append(fence.isEmpty() ? line : "").append('\n');
            }
        }
        return result.toString();
    }

    private record Row(
            String number,
            @Nullable String anchor,
            String state,
            String size,
            String title,
            @Nullable String file,
            int line) {}

    private static Set<String> anchorsIn(String text) {
        final Set<String> anchors = new HashSet<>();
        final Matcher anchor = ANCHOR.matcher(text);
        while (anchor.find()) {
            anchors.add(anchor.group(1));
        }
        return anchors;
    }

    private static String prefixOf(String number) {
        return number.replaceAll("\\d+$", "");
    }

    private static String blank(Pattern pattern, String text) {
        return pattern.matcher(text).replaceAll(found -> " ".repeat(found.group().length()));
    }

    private static String relative(Path root, Path path) {
        return root.relativize(path).toString().replace('\\', '/');
    }

    /** An index read: its anchors and the numbers of its rows, or why it could not be read. */
    private record Index(Set<String> anchors, Set<String> numbers, @Nullable String unread) {}
}
