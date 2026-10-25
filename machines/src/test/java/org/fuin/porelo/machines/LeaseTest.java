package org.fuin.porelo.machines;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/** Tests for creating and destroying a machine - the path where a mistake bills 81 EUR a month. */
class LeaseTest {

    @org.junit.jupiter.api.Test
    void installsOnlyAnAgentPackageNamedAsOneAndQuoted() {

        // The name went into the package manager's command line as it was given.
        org.assertj.core.api.Assertions.assertThat(Lease.install("porelo-agent-pi"))
                .contains("install -y -qq 'porelo-agent-pi'")
                .contains("install -y -q 'porelo-agent-pi'");
        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> Lease.install("porelo-agent-pi; reboot"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void givesTheMachineBackOnceWhenClosedTwice() throws IOException {
        // A cancel closes it from its own thread while the leg's own finally may close it too.
        final AtomicInteger releases = new AtomicInteger();
        final Lease lease =
                new Lease(
                        7,
                        "porelo-leg",
                        "192.0.2.1",
                        new Spec(
                                "porelo-leg",
                                "ubuntu",
                                Kind.AMD64,
                                "root",
                                Keys.generated(),
                                false),
                        HostKey.any("no machine is connected to"),
                        releases::incrementAndGet);

        lease.close();
        lease.close();

        assertThat(releases).hasValue(1);
    }

    @Test
    void triesAgainWhenGivingTheMachineBackFailed() throws IOException {
        final AtomicInteger tries = new AtomicInteger();
        final Lease lease =
                new Lease(
                        7,
                        "porelo-leg",
                        "192.0.2.1",
                        new Spec(
                                "porelo-leg",
                                "ubuntu",
                                Kind.AMD64,
                                "root",
                                Keys.generated(),
                                false),
                        HostKey.any("no machine is connected to"),
                        () -> {
                            if (tries.incrementAndGet() == 1) {
                                throw new IOException("interrupted calling DELETE /servers/7");
                            }
                        });

        assertThatThrownBy(lease::close).hasMessageContaining("interrupted");
        lease.close();
        lease.close();

        assertThat(tries)
                .as("failed once, given back on the next close, and not again")
                .hasValue(2);
    }
}
