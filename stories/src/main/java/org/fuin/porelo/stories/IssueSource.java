package org.fuin.porelo.stories;

import java.io.IOException;
import java.nio.file.Path;

/** Supplies a repository's issue index without prescribing a transport. */
@FunctionalInterface
public interface IssueSource {
    /**
     * Reads the repository's issues/README.md, including any linked indexes it needs. Multiple
     * pages use length-framed provenance headers as described by {@link IssueIndexes#read}. Sources
     * must reject reserved marker lines in a raw page before framing it.
     *
     * @param repository the repository name from project.yml
     * @return the index Markdown
     * @throws IOException when the index cannot be read
     */
    String index(String repository) throws IOException;

    /**
     * Reads checkouts beside the project's checkout, with no network fallback. The project itself
     * is read from projectRoot even when its directory has another name.
     *
     * @param projectRoot the checkout containing project.yml
     * @return a source that reads local issue indexes only
     */
    static IssueSource local(Path projectRoot) {
        return new LocalIssues(projectRoot);
    }
}
