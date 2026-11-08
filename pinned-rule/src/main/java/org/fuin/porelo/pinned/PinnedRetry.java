package org.fuin.porelo.pinned;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The one rule by which every pinned download is tried again: the GraalVM here, and shfmt, the
 * wrapper's Maven and the like in {@code .github/actions/pinned-jdk/pinned.sh}, and
 * porelo-release's uploads to Artifactory, which follows the same numbers and is held to them by a
 * test.
 *
 * <p>The pauses double from fifteen seconds, none longer than five minutes, until fifteen minutes
 * of pauses are spent; the last one is cut to fit. GitHub's release downloads answered 504 for over
 * ten minutes on 2026-11-08, longer than the two and a half minutes the attempts before this
 * waited.
 *
 * <p>The fifteen minutes are also a deadline, counted from the start of the first attempt with the
 * attempts' own time in it: no attempt starts later, and a pause is cut so the next one starts by
 * then. On 2026-11-08 a failing attempt took two minutes before it answered, so eight of them and
 * their pauses would have taken half an hour.
 */
public final class PinnedRetry {

    /** The pause after the first failed attempt. */
    public static final Duration FIRST = Duration.ofSeconds(15);

    /** The longest single pause. */
    public static final Duration LONGEST = Duration.ofMinutes(5);

    /**
     * All pauses together, and the time after the first attempt's start by which the last starts.
     */
    public static final Duration BUDGET = Duration.ofMinutes(15);

    private PinnedRetry() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * The pauses between attempts, in order.
     *
     * @return one pause after each failed attempt but the last
     */
    public static List<Duration> pauses() {
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
     * The pause after a failed attempt, by the rule and the deadline, in whole seconds as {@code
     * pinned.sh} counts them.
     *
     * @param attempt the attempt that failed, from 1
     * @param elapsed the time since the first attempt started; less than none counts as none
     * @return the pause before the next attempt, or empty when none is to start
     */
    public static Optional<Duration> pauseAfter(int attempt, Duration elapsed) {
        final List<Duration> pauses = pauses();
        final Duration spent =
                elapsed.isNegative() ? Duration.ZERO : Duration.ofSeconds(elapsed.toSeconds());
        final Duration left = BUDGET.minus(spent);
        if (attempt < 1 || attempt > pauses.size() || !left.isPositive()) {
            return Optional.empty();
        }
        final Duration pause = pauses.get(attempt - 1);
        return Optional.of(pause.compareTo(left) > 0 ? left : pause);
    }

    /**
     * How often a download is tried at most, the first time included.
     *
     * @return one more than the pauses
     */
    public static int attempts() {
        return pauses().size() + 1;
    }
}
