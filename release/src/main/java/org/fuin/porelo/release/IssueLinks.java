package org.fuin.porelo.release;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.fuin.porelo.stories.IssueIndex;
import org.fuin.porelo.stories.IssueIndexes;
import org.fuin.porelo.stories.IssueSource;
import org.fuin.porelo.stories.Workflow;
import org.jspecify.annotations.Nullable;

/**
 * Reads how the pages under {@code issues/} name an issue: every number a link to its row in an
 * index, every row anchored by its number, every such link reaching an anchor that is there.
 *
 * <p>The row outlives the file - a finished issue keeps its row - so a link to the row does not
 * break when the issue is done. Only this repository's own pages are read: a link into another
 * repository's index is counted and left unchecked, so another repository's state never decides
 * this one's check.
 */
final class IssueLinks {

    /** Where the repositories' indexes are published. */
    private static final Pattern GITHUB =
            Pattern.compile(
                    "^https://github\\.com/porelo-ai/([\\w.-]+)/blob/main/([^#\\s]+)#([a-z]{1,3}\\d{2,3})$");

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

    private static final Pattern FENCE = Pattern.compile("^ {0,3}(`{3,}|~{3,})(.*)$");

    /**
     * Source policy: the plan commands read beside or from the forge; the citation checks read no
     * source at all, only the pages they are given.
     */
    enum Sources {
        LOCAL_FIRST,
        FORGE,
        OWN
    }

    /** What a check of one repository is given instead of the web: nothing may be read there. */
    private static final Web NOWHERE =
            (address, sent) -> {
                throw new IllegalStateException(
                        "a check of one repository reads nothing outside it, but asked " + address);
            };

    private final Web web;
    private final Sources sources;

    private final Map<String, Path> trees = new HashMap<>();

    private final Set<Path> inputs = new HashSet<>();
    private final Set<URI> planSources = new LinkedHashSet<>();

    /** All successfully read sources that supply the plan's issue metadata. */
    Set<URI> planSources() {
        return Set.copyOf(planSources);
    }

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

    /** The links into another repository's index, which are not checked here. */
    private int elsewhere;

    final List<String> unanchored = new ArrayList<>();

    final List<String> unlinked = new ArrayList<>();

    final List<String> broken = new ArrayList<>();

    final List<String> rowless = new ArrayList<>();

    /** The numbers that have a row in this repository's indexes, finished ones among them. */
    final Set<String> rows = new HashSet<>();

    /** The prefixes an issue number is made of, once read: this repository's own and its rows'. */
    final Set<String> prefixes = new HashSet<>();

    /**
     * Returns the reader of one repository's own pages, which opens no other repository's files.
     *
     * @return the reader
     */
    static IssueLinks ownOnly() {
        return new IssueLinks(NOWHERE, Sources.OWN);
    }

    /**
     * Constructor.
     *
     * @param web where an index on the forge is read
     * @param sources where the indexes are read from
     */
    IssueLinks(Web web, Sources sources) {
        this.web = web;
        this.sources = sources;
    }

    /**
     * Reads the pages under the root's own {@code issues/}.
     *
     * @param root the repository's root
     * @param pages every Markdown page under the root's own {@code issues/}, none of a fixture
     * @param ownPrefixes the prefixes of this repository's issue files
     * @throws IOException when a page cannot be read
     */
    void read(Path root, List<Path> pages, Set<String> ownPrefixes) throws IOException {
        final Map<Path, Set<String>> anchors = new HashMap<>();
        final Map<Path, String> texts = new LinkedHashMap<>();
        final Map<Path, Map<Integer, IssueIndex>> pageRows = new HashMap<>();
        prefixes.addAll(ownPrefixes);
        for (final Path page : pages) {
            final String text = Files.readString(page, StandardCharsets.UTF_8);
            texts.put(page, text);
            if (page.getFileName().toString().equals("README.md")) {
                final Path workflowFile =
                        Files.exists(root.resolve("stories/workflow.yml"))
                                ? root.resolve("stories/workflow.yml")
                                : root.resolve("issues/workflow.yml");
                final Workflow workflow = Workflow.read(workflowFile);
                final Map<String, IssueIndex> parsed =
                        IssueIndexes.read(
                                indexPage(relative(root, page), page.toString(), text),
                                root.toAbsolutePath().normalize().getFileName().toString(),
                                workflow);
                final Map<Integer, IssueIndex> positions = new HashMap<>();
                final Set<String> rowAnchors = new HashSet<>();
                for (final IssueIndex row : parsed.values()) {
                    prefixes.add(prefixOf(row.number()));
                    rows.add(row.number());
                    final IssueIndex.Location location = row.location();
                    positions.put(location.line(), row);
                    if (location.anchor() != null) rowAnchors.add(location.anchor());
                    if (!row.number()
                            .toLowerCase(java.util.Locale.ROOT)
                            .equals(location.anchor())) {
                        unanchored.add(
                                relative(root, page)
                                        + ":"
                                        + location.line()
                                        + " -> "
                                        + row.number());
                    }
                }
                pageRows.put(page, positions);
                anchors.put(page.toAbsolutePath().normalize(), rowAnchors);
            } else {
                anchors.put(page.toAbsolutePath().normalize(), anchorsIn(text));
            }
        }
        for (final Map.Entry<Path, String> page : texts.entrySet()) {
            readPage(
                    root,
                    page.getKey(),
                    page.getValue(),
                    anchors,
                    prefixes,
                    ownPrefixes,
                    pageRows.getOrDefault(page.getKey(), Map.of()));
        }
    }

    /**
     * The line for the result: what is left to a check across repositories, which no check of one
     * repository can make.
     */
    String note() {
        return "not checked here: "
                + elsewhere
                + " link(s) into another repository's index, and citations of another repository's"
                + " issue; cross references between repositories are checked apart from any one of"
                + " them";
    }

    private void readPage(
            Path root,
            Path page,
            String text,
            Map<Path, Set<String>> anchors,
            Set<String> prefixes,
            Set<String> ownPrefixes,
            Map<Integer, IssueIndex> parsedRows) {
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
                final IssueIndex row = parsedRows.get(i + 1);
                if (row != null) {
                    final int end = row.location().numberEnd();
                    line = " ".repeat(end) + line.substring(end);
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
                if (rows.contains(found)) {
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
        final boolean number = named.matches() && !NOT_ISSUES.contains(named.group(1));
        final Matcher toRow = TO_A_ROW.matcher(target);
        if (!toRow.find()) {
            if (number) {
                unlinked.add(where + target);
            }
            return;
        }
        final String anchor = toRow.group(1);
        if (number && !named.group(1).toLowerCase(java.util.Locale.ROOT).equals(anchor)) {
            broken.add(where + named.group(1) + " linked to " + target);
            return;
        }
        if (GITHUB.matcher(target).matches()) {
            elsewhere++;
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

    /** Reads a plan's indexes through the selected source policy. */
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
            throw new IOException(source + ": index not readable: " + Web.reason(ex), ex);
        }
        planIndexSources.computeIfAbsent(repository, key -> new ArrayList<>()).add(source);
        final Map<String, IssueIndex> parsed;
        try {
            parsed =
                    IssueIndexes.read(
                            indexPage(path, source, text), repository, workflow(root, repository));
        } catch (IllegalArgumentException ex) {
            throw Stop.refused(Objects.requireNonNull(ex.getMessage()), ex);
        }
        for (final IssueIndex row : parsed.values()) {
            String title = row.title();
            final String file = row.location().file();
            if (file != null) {
                final String issuePath = indexPath(path, file);
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
                                    row.number(),
                                    row.state(),
                                    row.size(),
                                    title,
                                    path,
                                    source,
                                    row.blockedBy(),
                                    row.location()));
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

    private Workflow workflow(Path root, String repository) throws IOException {
        final String path =
                repository.equals("porelo-project")
                        ? "stories/workflow.yml"
                        : "issues/workflow.yml";
        final URI location = sourceLocation(root, repository, path);
        final Path file =
                location.getScheme().equals("file")
                        ? Path.of(location)
                        : root.resolve("stories/workflow.yml");
        final Workflow workflow = Workflow.read(file);
        inputs.add(file.toRealPath());
        return workflow;
    }

    static final class IndexFailure extends RuntimeException {
        private static final long serialVersionUID = 1L;
        final Stop stop;

        IndexFailure(Stop stop) {
            super(stop);
            this.stop = stop;
        }
    }

    IssueSource storySource(Path root) {
        return repository -> {
            final StringBuilder index = new StringBuilder();
            final Map<String, IssueIndex> rows;
            try {
                rows = issueIndex(root, repository);
            } catch (Stop stop) {
                throw new IndexFailure(stop);
            }
            final Workflow workflow = workflow(root, repository);
            for (final IssueIndex row :
                    rows.values().stream()
                            .sorted(
                                    java.util.Comparator.comparingInt(
                                            (IssueIndex row) -> workflow.order(row.state())))
                            .toList()) {
                final String page =
                        "## "
                                + row.state()
                                + "\n| # | Size | Blocked by | What it covers | Title |\n"
                                + "| ["
                                + row.number()
                                + "](#"
                                + row.number().toLowerCase(java.util.Locale.ROOT)
                                + ") | "
                                + row.size()
                                + " | "
                                + String.join(", ", row.blockedBy())
                                + " | "
                                + row.title().replace("|", "\\|")
                                + " | "
                                + row.title().replace("|", "\\|")
                                + " |\n";
                index.append(indexPage(row.path(), row.source(), page));
            }
            return index.toString();
        };
    }

    private static String indexPage(String path, String source, String text) throws IOException {
        text = text.replace("\r\n", "\n");
        if (text.lines()
                .anyMatch(line -> line.stripLeading().startsWith("<!-- porelo-issue-index "))) {
            throw new IOException("marker inside index page: " + path);
        }
        final String metadata =
                new ObjectMapper()
                        .writeValueAsString(
                                Map.of("path", path, "source", source, "length", text.length()));
        return "<!-- porelo-issue-index " + metadata + " -->\n" + text;
    }

    private Optional<String> issueSource(Path root, String repository, String path) {
        try {
            return source(root, repository, path);
        } catch (IOException | UncheckedIOException ex) {
            // A readable row remains useful when its optional issue file cannot be read.
            return Optional.empty();
        }
    }

    private Optional<String> source(Path root, String repository, String path) throws IOException {
        return source(sourceLocation(root, repository, path));
    }

    private Optional<String> source(URI location) throws IOException {
        if (sources == Sources.OWN) {
            throw new IllegalStateException(
                    "a check of one repository reads nothing outside it, but asked " + location);
        }
        final Optional<String> text =
                location.getScheme().equals("file")
                        ? localSource(Path.of(location))
                        : web.text(location);
        if (text.isPresent()) planSources.add(location);
        return text;
    }

    private URI sourceLocation(Path root, String repository, String path) {
        if (sources == Sources.FORGE) {
            return forgeLocation(repository, path);
        }
        final Path tree = trees.get(repository);
        if (tree != null) {
            return tree.resolve(path).toUri();
        }
        if (root.getFileName() != null && root.getFileName().toString().equals(repository)) {
            return root.resolve(path).toUri();
        }
        if (Files.isDirectory(root.resolveSibling(repository))) {
            return root.resolveSibling(repository).resolve(path).toUri();
        }
        return forgeLocation(repository, path);
    }

    private static URI forgeLocation(String repository, String path) {
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
}
