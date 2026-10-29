package org.fuin.porelo.release;

import java.util.List;
import java.util.Locale;

/**
 * One durable issue row, including the index that owns its anchor and the numbers of its own
 * repository it is blocked by.
 */
record IssueIndex(
        String number,
        String state,
        String size,
        String title,
        String path,
        String source,
        List<String> blockedBy) {
    String link(String repository) {
        return "https://github.com/porelo-ai/"
                + repository
                + "/blob/main/"
                + path
                + "#"
                + number.toLowerCase(Locale.ROOT);
    }
}
