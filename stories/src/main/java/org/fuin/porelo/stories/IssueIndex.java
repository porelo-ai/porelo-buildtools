package org.fuin.porelo.stories;

import java.util.List;
import java.util.Locale;

/**
 * One durable issue row, including the index that owns its anchor and the numbers of its own
 * repository it is blocked by.
 *
 * @param number the issue number, including its prefix and leading zeros
 * @param state the index section containing the row
 * @param size the size, or a dash for closed rows
 * @param title the row's title
 * @param path the repository-relative index path owning the anchor
 * @param source the index's descriptive source location
 * @param blockedBy issue numbers from the blocked-by column
 */
public record IssueIndex(
        String number,
        String state,
        String size,
        String title,
        String path,
        String source,
        List<String> blockedBy) {
    /** Copies the dependency list to keep this row immutable. */
    public IssueIndex {
        blockedBy = List.copyOf(blockedBy);
    }

    /**
     * Links to this row's durable anchor on the repository's main branch.
     *
     * @param repository the repository name
     * @return the row URL
     */
    String link(String repository) {
        return "https://github.com/porelo-ai/"
                + repository
                + "/blob/main/"
                + path
                + "#"
                + number.toLowerCase(Locale.ROOT);
    }
}
