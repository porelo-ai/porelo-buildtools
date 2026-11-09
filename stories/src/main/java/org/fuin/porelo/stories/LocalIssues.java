package org.fuin.porelo.stories;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

final class LocalIssues implements IssueSource {
    private static final Pattern LINK = Pattern.compile("\\[([^\\]\\n]*)\\]\\(([^)\\s]+)\\)");
    private final Path root;

    LocalIssues(Path root) {
        this.root = root.toAbsolutePath().normalize();
    }

    @Override
    public String index(String repository) throws IOException {
        if (!repository.matches("[A-Za-z0-9][A-Za-z0-9_.-]*"))
            throw new IOException("invalid repository: " + repository);
        final Path tree = repository.equals(own()) ? root : root.resolveSibling(repository);
        final StringBuilder text = new StringBuilder();
        read(tree, "issues/README.md", new HashSet<>(), text);
        // A missing or ambiguous row is an unusable source, including across linked pages.
        IssueIndexes.read(
                text.toString(),
                repository,
                Workflow.read(
                        tree.resolve(
                                repository.equals("porelo-project")
                                        ? "stories/workflow.yml"
                                        : "issues/workflow.yml")));
        return text.toString();
    }

    private String own() throws IOException {
        final Path file = root.resolve("project.yml");
        if (!Files.isRegularFile(file)) return root.getFileName().toString();
        return Project.own(file);
    }

    private static void read(Path tree, String path, Set<String> visited, StringBuilder text)
            throws IOException {
        if (!visited.add(path)) return;
        final Path file = tree.resolve(path);
        if (!Files.isRegularFile(file))
            throw new IOException(file + ": index not readable: not found");
        if (!file.toRealPath().startsWith(tree.toRealPath().resolve("issues"))) {
            throw new IOException(file + ": index is outside the checkout's issues directory");
        }
        final String page = Files.readString(file, StandardCharsets.UTF_8);
        text.append(IssueIndexes.page(path, file.toString(), page)).append('\n');
        final var links = LINK.matcher(IssueIndexes.unfenced(page));
        while (links.find()) {
            final String child = IssueIndexes.indexPath(path, links.group(2));
            if (child != null && child.endsWith("/README.md")) read(tree, child, visited, text);
        }
    }
}
