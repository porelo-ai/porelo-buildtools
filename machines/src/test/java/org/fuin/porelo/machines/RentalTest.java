package org.fuin.porelo.machines;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Tests for {@link Rental}. */
class RentalTest {

    @Test
    void namesTheRentedMachineInItsRecordEvenWhenItNeverAcceptsSsh(@TempDir Path directory) {
        final Path record = directory.resolve("lease.properties");
        final String hostKey = HostKeyPair.generate().publicLine();
        // A name that never resolves, and no patience: the machine is rented and never answers.
        final Machines rented =
                new Machines() {

                    @Override
                    public Lease acquire(Spec spec) {
                        throw new UnsupportedOperationException(
                                "a rental starts from a stock image");
                    }

                    @Override
                    public Lease acquireFromStock(Spec spec, String image) {
                        return new Lease(
                                77,
                                spec.name(),
                                "porelo-test.invalid",
                                spec,
                                HostKey.pinned(hostKey),
                                () -> {},
                                Duration.ZERO);
                    }

                    @Override
                    public String runId() {
                        return "run-1";
                    }

                    @Override
                    public void close() {}
                };

        assertThatThrownBy(
                        () ->
                                Rental.run(
                                        rented,
                                        new Rental.Options(
                                                "ubuntu", Kind.AMD64, "https://x", record, null),
                                        Keys.generated()))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("did not accept ssh");

        // keep = true: nothing deletes this machine but a sweep, and the sweep needs this file to
        // find it.
        assertThat(record).exists();
        assertThat(record)
                .content()
                .contains("server=77")
                .contains("address=porelo-test.invalid")
                .contains("run=run-1");
        // A leg that reaches the machine with OpenSSH puts this line into known_hosts rather than
        // taking the first key ssh-keyscan returns, which is whatever answers at the address.
        assertThat(record)
                .as("the record names the one host key the machine may show")
                .content()
                .contains("\nhostkey=" + hostKey + "\n");
    }

    @Test
    void theRentalPassesItsKindAndKeepsTheMachineForTheNextStep() {
        // A lease that loses keep would delete the machine before the caller uses it.
        final RecordingMachines source = new RecordingMachines();
        assertThatThrownBy(
                        () ->
                                Rental.run(
                                        source,
                                        new Rental.Options(
                                                "ubuntu",
                                                Kind.AMD64_BUILD,
                                                "https://packages.invalid",
                                                null,
                                                null),
                                        Keys.generated()))
                .as("the rental reaches the configured source")
                .hasMessage("acquisition recorded");
        final Spec request = source.requested.getFirst();
        assertThat(request.kind())
                .as("the requested build capacity reaches acquisition")
                .isEqualTo(Kind.AMD64_BUILD);
        assertThat(request.keep()).as("the machine must outlive the lease command").isTrue();
    }
}
