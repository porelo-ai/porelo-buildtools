package org.fuin.porelo.machines;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The waits of {@link Lease} for sshd and for a restart, over a fake machine and clock. */
class LeaseWaitTest {

    private final FakeMachine machine = new FakeMachine();

    private final Lease lease = machine.lease(FakeMachine.root());

    // Prevents a lease from giving up on a machine that is still booting.
    @Test
    void awaitsSshUntilTheMachineAnswers() throws IOException {
        machine.probes.addAll(List.of(false, false, true));

        lease.awaitSsh();

        assertThat(machine.sleeps)
                .as("a pause between the probes, none after the answer")
                .containsExactly(Duration.ofSeconds(3), Duration.ofSeconds(3));
    }

    // Prevents a machine that never answers from holding the caller, and the money, for ever.
    @Test
    void givesUpOnSshAfterItsPatience() {
        machine.probes.add(false);

        assertThatThrownBy(lease::awaitSsh)
                .isInstanceOf(IOException.class)
                .hasMessage("192.0.2.7 did not accept ssh within 5 minutes");
        assertThat(machine.sleeps.stream().reduce(Duration.ZERO, Duration::plus))
                .as("it waited its patience and a pause, no longer")
                .isBetween(Duration.ofMinutes(5), Duration.ofMinutes(5).plusSeconds(3));
    }

    // Prevents a machine showing another host key from being asked again as one still booting.
    @Test
    void stopsAtOnceOnAnotherHostKey() {
        machine.probes.add(new HostKey.Refused("192.0.2.7", "SHA256:other"));

        assertThatThrownBy(lease::awaitSsh).isInstanceOf(HostKey.Refused.class);
        assertThat(machine.sleeps).as("not asked again").isEmpty();
    }

    // Prevents a restart from passing on the old boot, which still answers for a moment.
    @Test
    void restartsUntilTheBootIdChanges() throws IOException {
        final String[] boots = {"one", "one", "two"};
        final int[] read = {0};
        machine.answer =
                command ->
                        command.equals("cat /proc/sys/kernel/random/boot_id")
                                ? new Ssh.Output(boots[read[0]++] + "\n", "", 0)
                                : new Ssh.Output("", "", 255);
        machine.reconnects.add(new IOException("connection refused"));

        lease.restart();

        assertThat(machine.commands)
                .as("the boot read, the reboot asked as root, then the boot read again until new")
                .containsExactly(
                        "cat /proc/sys/kernel/random/boot_id",
                        "systemctl reboot",
                        "cat /proc/sys/kernel/random/boot_id",
                        "cat /proc/sys/kernel/random/boot_id");
        assertThat(machine.disconnects).as("the old connection dropped").isEqualTo(1);
        assertThat(machine.sleeps).hasSize(3);
    }

    // Prevents a machine that never comes back from holding the caller for ever.
    @Test
    void givesUpOnARestartThatNeverEnds() {
        machine.answer = command -> new Ssh.Output("one\n", "", 0);

        assertThatThrownBy(lease::restart)
                .isInstanceOf(IOException.class)
                .hasMessage("m did not come back from a restart within 5 minutes");
    }

    // Prevents a machine that comes back with another host key from being taken as itself.
    @Test
    void stopsARestartAtAnotherHostKey() {
        machine.answer = command -> new Ssh.Output("one\n", "", 0);
        machine.reconnects.add(new HostKey.Refused("192.0.2.7", "SHA256:other"));

        assertThatThrownBy(lease::restart).isInstanceOf(HostKey.Refused.class);
        assertThat(machine.sleeps).as("not asked again").hasSize(1);
    }

    // Prevents a non-root lease from running its script outside the privileged shell.
    @Test
    void runsTheRebootAsRootThroughSudoForAnotherUser() throws IOException {
        final FakeMachine other = new FakeMachine();
        final String[] boots = {"one", "two"};
        final int[] read = {0};
        other.answer =
                command ->
                        command.startsWith("cat ")
                                ? new Ssh.Output(boots[read[0]++], "", 0)
                                : new Ssh.Output("", "", 0);

        other.lease(FakeMachine.as("porelo")).restart();

        assertThat(other.commands).contains("sudo -n sh -c 'systemctl reboot'");
    }
}
