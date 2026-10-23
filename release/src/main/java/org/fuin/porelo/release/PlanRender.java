package org.fuin.porelo.release;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/** Builds both pages from the same ordered plan and issue snapshot. */
final class PlanRender {
    private PlanRender() {}

    /** A render's two serializations, with no clock or machine-specific paths. */
    record Pages(String markdown, String html) {}

    static Map<String, IssueIndex> indexes(Plan plan, Plan.Project project, IssueLinks links)
            throws IOException, Stop {
        final Set<String> repositories = new LinkedHashSet<>();
        if (plan.issue() != null) {
            repositories.add(plan.issue().split("#")[0]);
        }
        for (final Plan.Sprint sprint : plan.sprints()) {
            for (final Plan.Function function : sprint.functions()) {
                function.issues().forEach(issue -> repositories.add(issue.split("#")[0]));
            }
        }
        final Map<String, IssueIndex> issues = new LinkedHashMap<>();
        for (final String repository : repositories) {
            links.issueIndex(project.root(), repository)
                    .forEach((number, row) -> issues.put(repository + "#" + number, row));
        }
        return Map.copyOf(issues);
    }

    static Path page(Path file, Plan plan) {
        return file.toAbsolutePath().resolveSibling(plan.release() + ".md");
    }

    static void write(Path file, Plan plan, Pages pages, @Nullable Path html, Set<Path> inputs)
            throws IOException, Stop {
        final Path markdown = page(file, plan);
        final Path directory =
                java.util.Objects.requireNonNull(file.toAbsolutePath().getParent()).toRealPath();
        if (!destination(markdown).startsWith(directory)) {
            throw Stop.refused("Markdown output resolves outside plan directory: " + markdown);
        }
        for (final Path input : inputs) {
            if (sameFile(input, markdown) || (html != null && sameFile(input, html))) {
                throw Stop.refused("Output must differ from input: " + input);
            }
        }
        if (html != null && sameFile(markdown, html)) {
            throw Stop.refused("HTML output must differ from Markdown page: " + html);
        }
        Files.writeString(markdown, pages.markdown());
        if (html != null) {
            Files.writeString(html, pages.html());
        }
    }

    private static Path destination(Path file) throws IOException {
        Path absolute = file.toAbsolutePath();
        final Set<Path> visited = new LinkedHashSet<>();
        while (Files.isSymbolicLink(absolute)) {
            if (!visited.add(absolute) || visited.size() > 40) {
                throw new IOException("Output symlink chain cannot be resolved: " + file);
            }
            absolute = absolute.resolveSibling(Files.readSymbolicLink(absolute));
        }
        if (Files.exists(absolute)) {
            return absolute.toRealPath();
        }
        return java.util.Objects.requireNonNull(absolute.getParent())
                .toRealPath()
                .resolve(absolute.getFileName())
                .normalize();
    }

    private static boolean sameFile(Path first, Path second) throws IOException {
        return first.toAbsolutePath().normalize().equals(second.toAbsolutePath().normalize())
                || (Files.exists(first) && Files.exists(second) && Files.isSameFile(first, second));
    }

    static Pages render(Plan plan, Map<String, IssueIndex> issues) {
        final Page page = new Page();
        page.heading(1, "Release " + plan.release());
        page.text(plan.goal());
        if (plan.issue() != null) {
            page.issue(plan.issue(), issues.get(plan.issue()));
        }
        for (final Plan.Sprint sprint : plan.sprints()) {
            page.heading(2, "Sprint " + sprint.id() + " · " + sprint.title());
            page.text(sprint.goal());
            final Map<String, Integer> counts = new LinkedHashMap<>();
            final Set<String> counted = new LinkedHashSet<>();
            for (final Plan.Function function : sprint.functions()) {
                for (final String named : function.issues()) {
                    if (counted.add(named)) {
                        final IssueIndex row = issues.get(named);
                        counts.merge(row == null ? "Missing" : row.state(), 1, Integer::sum);
                    }
                }
            }
            final StringBuilder bar = new StringBuilder("Issues: ");
            counts.forEach(
                    (state, count) ->
                            bar.append(state)
                                    .append(' ')
                                    .append(count)
                                    .append(' ')
                                    .append((state.equals("Finished") ? "█" : "░").repeat(count))
                                    .append(' '));
            page.text(counts.isEmpty() ? "Issues: none" : bar.toString().stripTrailing());
            for (final Plan.Function function : sprint.functions()) {
                page.heading(3, function.title());
                page.text(
                        function.tested() == null
                                ? "[ ] Not tested"
                                : "[x] Tested " + function.tested());
                page.text("Test: " + function.test());
                for (final String named : function.issues()) {
                    page.issue(named, issues.get(named));
                }
                for (final String note : function.notes()) {
                    page.text("not filed yet: " + note);
                }
            }
        }
        return new Pages(page.markdown.toString(), page.html.append("</main></html>\n").toString());
    }

    private static String escape(String text) {
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    private static String markdown(String text) {
        return text.replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("|", "\\|")
                .replace("\n", " ")
                .replace("\r", " ")
                .replaceFirst("^( {0,3})(#|[*+\\-](?=\\s|$))", "$1\\\\$2")
                .replaceFirst("^( {0,3}\\d{1,9})\\.(?=\\s|$)", "$1\\\\.");
    }

    private static final class Page {
        private final StringBuilder markdown = new StringBuilder();
        private final StringBuilder html =
                new StringBuilder(
                        """
                <!doctype html>
                <html lang="en"><meta charset="utf-8">
                <meta name="viewport" content="width=device-width, initial-scale=1">
                <title>Release plan</title>
                <style>
                body{font:1rem/1.6 system-ui,sans-serif;margin:2rem auto;padding:0 1rem;max-width:70rem;color:#222;background:#fff}
                h2{border-top:1px solid #aaa;padding-top:1rem}a{color:#164ea3}p{white-space:pre-wrap}
                </style>
                <main>
                """);

        void heading(int level, String text) {
            markdown.append("#".repeat(level)).append(' ').append(markdown(text)).append("\n\n");
            html.append("<h")
                    .append(level)
                    .append('>')
                    .append(escape(text))
                    .append("</h")
                    .append(level)
                    .append(">\n");
        }

        void text(String text) {
            markdown.append(markdown(text)).append("\n\n");
            html.append("<p>").append(escape(text)).append("</p>\n");
        }

        void issue(String named, @Nullable IssueIndex row) {
            if (row == null) {
                text(named + " — Missing index row");
                return;
            }
            final String repository = named.split("#")[0];
            final String target = row.link(repository);
            final String description =
                    " — " + row.state() + " · " + row.size() + " · " + row.title();
            markdown.append("- [")
                    .append(named)
                    .append("](")
                    .append(target)
                    .append(')')
                    .append(markdown(description))
                    .append("\n\n");
            html.append("<p><a href=\"")
                    .append(escape(target))
                    .append("\">")
                    .append(escape(named))
                    .append("</a>")
                    .append(escape(description))
                    .append("</p>\n");
        }
    }
}
