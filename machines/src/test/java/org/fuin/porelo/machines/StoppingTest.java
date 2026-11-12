package org.fuin.porelo.machines;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link Stopping}: the watch over a process held in memory and looks given by hand, the
 * stop over a real process.
 */
class StoppingTest {

    /** A process that runs until told it has ended. */
    private static final class Step implements Stopping.WatchedProcess {

        volatile boolean alive = true;

        @Override
        public boolean isAlive() {
            return alive;
        }

        @Override
        public long pid() {
            return 4711;
        }
    }

    /** Looks given one at a time: each {@link #look()} returns once the watch has looked again. */
    private static final class Looks implements Stopping.Tick {

        private final Semaphore due = new Semaphore(0);

        private final Semaphore looked = new Semaphore(0);

        @Override
        public void next() throws InterruptedException {
            looked.release();
            due.acquire();
        }

        /** Waits for the look the watch makes as it starts. */
        void first() throws InterruptedException {
            assertThat(looked.tryAcquire(5, TimeUnit.SECONDS)).as("the watch looked").isTrue();
        }

        /** Lets one more look happen and waits until it is done. */
        void look() throws InterruptedException {
            due.release();
            first();
        }

        /** Lets one more look happen without waiting for it. */
        void letLook() {
            due.release();
        }
    }

    // Prevents a cancelled build's leg from going on driving its machine.
    @Test
    void stopsOnceWhatStartedTheLegHasGone() throws Exception {
        // What a cancel on a build server does: the step's shell is signalled and ends, and nothing
        // below it is told.
        final Step step = new Step();
        final Looks looks = new Looks();
        final CountDownLatch stopped = new CountDownLatch(1);
        final AtomicInteger exits = new AtomicInteger(-1);

        try (Stopping stopping =
                new Stopping(List.of(step), stopped::countDown, exits::set, looks)) {
            looks.first();
            looks.look();
            assertThat(stopped.getCount()).as("nothing while the step runs").isOne();

            step.alive = false;
            looks.letLook();

            assertThat(stopped.await(5, TimeUnit.SECONDS))
                    .as("stopped at the next look after the step ended")
                    .isTrue();
        }
        assertThat(exits)
                .as("and the leg ends, saying it was cancelled")
                .hasValue(Stopping.CANCELLED);
    }

    // Prevents a leg that ended on its own from stopping a machine it no longer holds.
    @Test
    void aLegThatEndsOnItsOwnStopsNothing() throws Exception {
        final Step step = new Step();
        final Looks looks = new Looks();
        final AtomicInteger stops = new AtomicInteger();
        final AtomicInteger exits = new AtomicInteger(-1);

        try (Stopping stopping =
                new Stopping(List.of(step), stops::incrementAndGet, exits::set, looks)) {
            looks.first();
        }
        step.alive = false;
        looks.letLook();

        assertThat(stops).as("after close, the step ending is none of its business").hasValue(0);
        assertThat(exits).as("nor does the leg end for it").hasValue(-1);
    }

    // Prevents close from cutting the machine's deletion off half-way.
    @Test
    void aLegClosingWhileItStopsLetsTheStopFinish() throws Exception {
        // Measured on a cancelled build: the suite ended, the leg's own thread closed this, and the
        // machine's deletion was interrupted half-way.
        final Step step = new Step();
        step.alive = false;
        final CountDownLatch began = new CountDownLatch(1);
        final CountDownLatch deleted = new CountDownLatch(1);
        final AtomicInteger finished = new AtomicInteger();
        final Stopping stopping =
                new Stopping(
                        List.of(step),
                        () -> {
                            began.countDown();
                            try {
                                deleted.await();
                                finished.incrementAndGet();
                            } catch (InterruptedException ex) {
                                Thread.currentThread().interrupt();
                            }
                        },
                        code -> {},
                        new Looks());
        assertThat(began.await(5, TimeUnit.SECONDS)).isTrue();

        final Thread closing = Thread.ofPlatform().start(stopping::close);
        while (closing.getState() != Thread.State.TIMED_WAITING
                && closing.getState() != Thread.State.TERMINATED) {
            Thread.onSpinWait();
        }
        assertThat(closing.getState())
                .as("close waits for the stop")
                .isNotEqualTo(Thread.State.TERMINATED);
        deleted.countDown();
        closing.join(TimeUnit.SECONDS.toMillis(5));

        assertThat(finished).as("the stop ran to its end, and close waited for it").hasValue(1);
        assertThat(closing.isAlive()).as("close returned once it had").isFalse();
    }

    @Tag("integration")
    @Test
    void endsEveryProcessTheLegStartedAndGivesTheMachineBack() throws Exception {
        final Process suite = new ProcessBuilder("sleep", "60").start();
        final AtomicInteger released = new AtomicInteger();

        Stopping.stop(() -> released.incrementAndGet());

        assertThat(suite.waitFor(2, TimeUnit.SECONDS)).as("the suite the leg started").isTrue();
        assertThat(released).as("the machine given back").hasValue(1);
    }

    @Test
    void namesEveryAncestorButTheFirstProcess() {
        final List<ProcessHandle> ancestors = Stopping.ancestors();

        assertThat(ancestors)
                .isNotEmpty()
                .first()
                .isEqualTo(ProcessHandle.current().parent().orElseThrow());
        assertThat(ancestors).noneMatch(process -> process.pid() == 1);
    }
}
