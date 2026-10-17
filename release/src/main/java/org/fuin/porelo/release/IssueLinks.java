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

    private final Web web;

    @Nullable private final Path checkouts;

    /**
     * Another repository's index, by repository and path: its anchors, or why it could not be read.
     */
    private final Map<String, Index> elsewhere = new LinkedHashMap<>();

    /** The links into an index that could not be read, by that index. */
    private final Map<String, Integer> unchecked = new LinkedHashMap<>();

    final List<String> unanchored = new ArrayList<>();

    final List<String> unlinked = new ArrayList<>();

    final List<String> broken = new ArrayList<>();

    final List<String> rowless = new ArrayList<>();

    /** The numbers that have a row in this repository's indexes, finished ones among them. */
    final Set<String> rows = new HashSet<>();

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
        final Set<String> prefixes = new HashSet<>(ownPrefixes);
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
        final String[] lines = text.split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            final Matcher row = ROW.matcher(lines[i]);
            if (row.find()) {
                final String number = row.group(2);
                rows.add(number);
                if (!number.toLowerCase(java.util.Locale.ROOT).equals(row.group(1))) {
                    unanchored.add(relative(root, page) + ":" + (i + 1) + " -> " + number);
                }
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
            final Optional<String> text;
            if (checkouts != null) {
                final Path file = checkouts.resolve(repository).resolve(path);
                text =
                        Files.isRegularFile(file)
                                ? Optional.of(Files.readString(file, StandardCharsets.UTF_8))
                                : Optional.empty();
            } else {
                text =
                        web.text(
                                URI.create(
                                        "https://raw.githubusercontent.com/porelo-ai/"
                                                + repository
                                                + "/main/"
                                                + path));
            }
            if (text.isEmpty()) {
                return new Index(Set.of(), Set.of(), "not published");
            }
            final Set<String> numbers = new HashSet<>();
            for (final String line : text.get().split("\n")) {
                final Matcher row = ROW.matcher(line);
                if (row.find()) {
                    numbers.add(row.group(2));
                }
            }
            return new Index(anchorsIn(text.get()), numbers, null);
        } catch (IOException | UncheckedIOException ex) {
            return new Index(Set.of(), Set.of(), ex.getMessage());
        }
    }

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
