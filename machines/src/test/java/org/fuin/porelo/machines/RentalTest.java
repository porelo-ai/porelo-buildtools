package org.fuin.porelo.machines;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Tests for {@link Rental}. */
class RentalTest {

    /** A source that rents the fake machine, as a provider rents from a stock image. */
    private static Machines renting(FakeMachine machine) {
        return new Machines() {

            @Override
            public Lease acquire(Spec spec) {
                throw new UnsupportedOperationException("a rental starts from a stock image");
            }

            @Override
            public Lease acquireFromStock(Spec spec, String image) {
                return machine.lease(spec);
            }

            @Override
            public String runId() {
                return "run-1";
            }

            @Override
            public void close() {}
        };
    }

    // Prevents a machine rented but never reached from being left with nothing naming it.
    @Test
    void namesTheRentedMachineInItsRecordEvenWhenItNeverAcceptsSsh(@TempDir Path directory)
            throws IOException {
        final Path record = directory.resolve("lease.properties");
        final FakeMachine machine = new FakeMachine();
        machine.probes.add(false);
        final String hostKey =
                ((HostKey.Pinned) machine.lease(FakeMachine.root()).hostKey()).line();

        assertThatThrownBy(
                        () ->
                                Rental.run(
                                        renting(machine),
                                        new Rental.Options(
                                                "ubuntu", Kind.AMD64, "https://x", record, null),
                                        FakeMachine.KEY))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("did not accept ssh");

        // keep = true: nothing deletes this machine but a sweep, and the sweep needs this file to
        // find it.
        assertThat(record).exists();
        assertThat(record)
                .content()
                .contains("server=77")
                .contains("address=192.0.2.7")
                .contains("run=run-1");
        assertThat(machine.commands).as("nothing run on a machine never reached").isEmpty();
        // A leg that reaches the machine with OpenSSH puts this line into known_hosts rather than
        // taking the first key ssh-keyscan returns, which is whatever answers at the address.
        assertThat(record)
                .as("the record names the one host key the machine may show")
                .content()
                .contains("\nhostkey=" + hostKey + "\n");
    }

    // Prevents a rental from handing over a machine whose daemon never came up, or whose record
    // lacks the socket the next step connects to.
    @Test
    void preparesTheMachineAndRecordsTheDaemonsSocket(@TempDir Path directory) throws IOException {
        final Path record = directory.resolve("lease.properties");
        final FakeMachine machine = new FakeMachine();
        machine.probes.add(true);
        machine.answer =
                command ->
                        new Ssh.Output(
                                command.equals("id -u e2e")
                                        ? "1001\n"
                                        : command.startsWith("test -S ") ? "yes\n" : "",
                                "",
                                0);

        final var leased =
                Rental.run(
                        renting(machine),
                        new Rental.Options("ubuntu", Kind.AMD64, "https://x", record, null),
                        FakeMachine.KEY);

        assertThat(leased).containsEntry("socket", "/run/user/1001/porelo/porelod.sock");
        assertThat(record).content().contains("socket=/run/user/1001/porelo/porelod.sock");
        assertThat(machine.commands)
                .as("Porelo installed, the user made, the daemon started and its socket asked")
                .hasSize(6)
                .anySatisfy(command -> assertThat(command).contains("useradd"))
                .anySatisfy(command -> assertThat(command).contains("nohup porelod"))
                .last()
                .isEqualTo("test -S /run/user/1001/porelo/porelod.sock && echo yes || echo no");
    }

    // Prevents a daemon that did not start from passing as a machine ready for the suite.
    @Test
    void refusesAMachineWhoseDaemonLeftNoSocket(@TempDir Path directory) {
        final FakeMachine machine = new FakeMachine();
        machine.probes.add(true);
        machine.answer =
                command ->
                        new Ssh.Output(
                                command.equals("id -u e2e")
                                        ? "1001\n"
                                        : command.startsWith("test -S ") ? "no\n" : "",
                                "",
                                0);

        assertThatThrownBy(
                        () ->
                                Rental.run(
                                        renting(machine),
                                        new Rental.Options(
                                                "ubuntu",
                                                Kind.AMD64,
                                                "https://x",
                                                directory.resolve("lease.properties"),
                                                null),
                                        FakeMachine.KEY))
                .isInstanceOf(IOException.class)
                .hasMessage(
                        "the daemon did not come up as e2e; /run/user/1001/porelo/porelod.sock"
                                + " is not there. Its log is /home/e2e/porelod.log on 192.0.2.7");
    }

    // Prevents a failed step from passing on to the next.
    @Test
    void stopsAtTheFirstStepThatFails(@TempDir Path directory) {
        final FakeMachine machine = new FakeMachine();
        machine.probes.add(true);
        machine.answer = command -> new Ssh.Output("", "no space left", 1);

        assertThatThrownBy(
                        () ->
                                Rental.run(
                                        renting(machine),
                                        new Rental.Options(
                                                "ubuntu",
                                                Kind.AMD64,
                                                "https://x",
                                                directory.resolve("lease.properties"),
                                                null),
                                        FakeMachine.KEY))
                .isInstanceOf(IOException.class)
                .hasMessageStartingWith("the lease failed at: ");
        assertThat(machine.commands).as("nothing after the failed install").hasSize(1);
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
