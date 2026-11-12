package org.fuin.porelo.machines;

import java.time.Duration;
import java.time.Instant;

/** The time a wait reads and the pause it takes: the system's, or a test's that moves at once. */
interface Waiting {

    /** The system clock, and a real sleep. */
    Waiting SYSTEM =
            new Waiting() {
                @Override
                public Instant now() {
                    return Instant.now();
                }

                @Override
                public void sleep(Duration duration) throws InterruptedException {
                    Thread.sleep(duration.toMillis());
                }
            };

    /**
     * Returns the time now.
     *
     * @return Now.
     */
    Instant now();

    /**
     * Pauses.
     *
     * @param duration How long.
     * @throws InterruptedException If the thread is interrupted.
     */
    void sleep(Duration duration) throws InterruptedException;
}
