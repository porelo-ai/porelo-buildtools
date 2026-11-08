package org.fuin.porelo.machines;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * The one rule by which every pinned download is tried again: the GraalVM here, and shfmt, the
 * wrapper's Maven and the like in {@code .github/actions/pinned-jdk/pinned.sh}, which follows the
 * same numbers and is held to them by a test.
 *
 * <p>The pauses double from fifteen seconds, none longer than five minutes, until fifteen minutes
 * of pauses are spent; the last one is cut to fit. GitHub's release downloads answered 504 for over
 * ten minutes on 2026-11-08, longer than the two and a half minutes the attempts before this
 * waited.
 */
final class PinnedRetry {

    /** The pause after the first failed attempt. */
    static final Duration FIRST = Duration.ofSeconds(15);

    /** The longest single pause. */
    static final Duration LONGEST = Duration.ofMinutes(5);

    /** All pauses together, before the download fails. */
    static final Duration BUDGET = Duration.ofMinutes(15);

    private PinnedRetry() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * The pauses between attempts, in order.
     *
     * @return one pause after each failed attempt but the last
     */
    static List<Duration> pauses() {
        final List<Duration> pauses = new ArrayList<>();
        Duration spent = Duration.ZERO;
        Duration next = FIRST;
        while (spent.compareTo(BUDGET) < 0) {
            final Duration left = BUDGET.minus(spent);
            final Duration pause = next.compareTo(left) > 0 ? left : next;
            pauses.add(pause);
            spent = spent.plus(pause);
            next = next.multipliedBy(2).compareTo(LONGEST) > 0 ? LONGEST : next.multipliedBy(2);
        }
        return List.copyOf(pauses);
    }

    /**
     * How often a download is tried, the first time included.
     *
     * @return one more than the pauses
     */
    static int attempts() {
        return pauses().size() + 1;
    }
}
