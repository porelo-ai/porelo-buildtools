package org.fuin.porelo.machines;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** A clock that moves only when it is slept on, at once, and remembers each pause. */
final class MovingClock implements Waiting {

    final List<Duration> sleeps = new ArrayList<>();

    private Instant now = Instant.parse("2026-11-12T00:00:00Z");

    @Override
    public Instant now() {
        return now;
    }

    @Override
    public void sleep(Duration duration) {
        sleeps.add(duration);
        now = now.plus(duration);
    }

    /**
     * Returns how long was slept in all.
     *
     * @return The sum of the pauses.
     */
    Duration slept() {
        return sleeps.stream().reduce(Duration.ZERO, Duration::plus);
    }
}
