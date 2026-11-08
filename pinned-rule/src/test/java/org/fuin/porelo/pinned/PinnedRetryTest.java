package org.fuin.porelo.pinned;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class PinnedRetryTest {

    @Test
    void pausesDoubleFromFifteenSecondsUpToFiveMinutesUntilFifteenMinutesAreSpent() {
        assertThat(PinnedRetry.pauses())
                .containsExactly(
                        Duration.ofSeconds(15),
                        Duration.ofSeconds(30),
                        Duration.ofSeconds(60),
                        Duration.ofSeconds(120),
                        Duration.ofSeconds(240),
                        Duration.ofSeconds(300),
                        Duration.ofSeconds(135));
        assertThat(PinnedRetry.pauses().stream().reduce(Duration.ZERO, Duration::plus))
                .isEqualTo(Duration.ofMinutes(15));
        assertThat(PinnedRetry.attempts()).isEqualTo(8);
    }

    @Test
    void theDeadlineCutsByTheseNumbers() {
        // Reference values of their own, so a pauseAfter that went wrong cannot carry the shell
        // with it.
        assertThat(PinnedRetry.pauseAfter(1, Duration.ZERO))
                .as("first pause")
                .contains(Duration.ofSeconds(15));
        assertThat(PinnedRetry.pauseAfter(7, Duration.ZERO))
                .as("last pause")
                .contains(Duration.ofSeconds(135));
        assertThat(PinnedRetry.pauseAfter(8, Duration.ZERO))
                .as("after the eighth attempt")
                .isEmpty();
        assertThat(PinnedRetry.pauseAfter(0, Duration.ZERO)).as("no attempt 0").isEmpty();
        assertThat(PinnedRetry.pauseAfter(5, Duration.ofSeconds(890)))
                .as("cut to the deadline")
                .contains(Duration.ofSeconds(10));
        assertThat(PinnedRetry.pauseAfter(1, Duration.ofMillis(899_500)))
                .as("whole seconds, as the shell")
                .contains(Duration.ofSeconds(1));
        assertThat(PinnedRetry.pauseAfter(1, Duration.ofSeconds(900)))
                .as("at the deadline")
                .isEmpty();
        assertThat(PinnedRetry.pauseAfter(1, Duration.ofSeconds(-5)))
                .as("a clock behind counts as none")
                .contains(Duration.ofSeconds(15));
    }
}
