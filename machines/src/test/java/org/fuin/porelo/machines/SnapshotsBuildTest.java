package org.fuin.porelo.machines;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The steps of {@link Snapshots#build}, over a fake machine and a fake catalog of images. */
class SnapshotsBuildTest {

    private final FakeMachine machine = new FakeMachine();

    /** What the catalog was asked, in order. */
    private final List<String> asked = new ArrayList<>();

    private final Machines catalog =
            new Machines() {

                @Override
                public Lease acquire(Spec spec) {
                    throw new UnsupportedOperationException("a snapshot starts from stock");
                }

                @Override
                public Lease acquireFromStock(Spec spec, String image) {
                    asked.add("rent " + image + " for " + spec.name().replaceAll("-\\d.*", ""));
                    return machine.lease(spec);
                }

                @Override
                public void shutdown(long id) {
                    asked.add("shutdown " + id);
                }

                @Override
                public long snapshot(long id, String description, String os, String inputs) {
                    asked.add("snapshot " + id + " " + os + " " + inputs);
                    return 4242;
                }

                @Override
                public String runId() {
                    return "run-1";
                }

                @Override
                public void close() {}
            };

    private long build(String os, Path archive, String musl) throws IOException {
        return Snapshots.build(
                catalog, os, Kind.AMD64_SNAPSHOT, List.of(), FakeMachine.KEY, archive, musl);
    }

    // Prevents an image taken of a running machine, a half-written disk, or a machine left rented.
    @Test
    void preparesThenStopsThenTakesTheImageAndGivesTheMachineBack() throws IOException {
        machine.probes.add(true);
        machine.onRelease = () -> asked.add("released");

        assertThat(build("ubuntu", null, "the installer")).isEqualTo(4242);

        assertThat(asked)
                .containsExactly(
                        "rent ubuntu-26.04 for porelo-snapshot-ubuntu",
                        "shutdown 77",
                        "snapshot 77 ubuntu " + Snapshots.inputs("ubuntu"),
                        "released");
        assertThat(machine.commands)
                .containsExactly(
                        Snapshots.recipe("ubuntu"),
                        "cat > /tmp/install-musl.sh && chmod +x /tmp/install-musl.sh\tthe installer",
                        "su - build -c 'bash /tmp/install-musl.sh'");
        assertThat(machine.releases).as("the machine given back once").isEqualTo(1);
    }

    // Prevents a snapshot from being proven by anything less than building the product on it, and
    // its checkout from staying in the image.
    @Test
    void buildsTheProductOnceAndLeavesOnlyItsDependencies(@TempDir Path directory)
            throws IOException {
        final Path tree = Files.writeString(directory.resolve("tree.tar"), "");
        machine.probes.add(true);

        build("ubuntu", tree, null);

        assertThat(machine.uploads).containsExactly("tree.tar -> /tmp/tree.tar");
        assertThat(machine.commands.subList(1, machine.commands.size()))
                .as("swap on, unpack, build, swap off, the tree removed")
                .satisfiesExactly(
                        swap -> assertThat(swap).isEqualTo(Snapshots.SWAP_ON),
                        unpack ->
                                assertThat(unpack).contains("tar -x -C ~/porelo -f /tmp/tree.tar"),
                        product -> assertThat(product).contains(Snapshots.TREE_BUILD),
                        off -> assertThat(off).isEqualTo(Snapshots.SWAP_OFF),
                        removed ->
                                assertThat(removed)
                                        .isEqualTo(
                                                "su - build -c 'rm -rf ~/porelo' && rm -f"
                                                        + " /tmp/tree.tar"));
    }

    // Prevents a Fedora image whose files were installed unlabelled, or that runs without
    // Porelo's socket policy, from being taken.
    @Test
    void turnsSelinuxOnAcrossARestartAndLoadsPorelosPolicyOnFedora(@TempDir Path directory)
            throws IOException {
        final Path tree = Files.writeString(directory.resolve("tree.tar"), "");
        final String[] boots = {"one", "two"};
        final int[] read = {0};
        machine.probes.add(true);
        machine.answer =
                command ->
                        new Ssh.Output(
                                command.startsWith("cat /proc") ? boots[read[0]++] : "", "", 0);

        build("fedora", tree, null);

        assertThat(machine.commands)
                .containsSubsequence(
                        "sed -i 's/^SELINUX=.*/SELINUX=enforcing/' /etc/selinux/config"
                                + " && touch /.autorelabel",
                        "cat /proc/sys/kernel/random/boot_id",
                        "systemctl reboot",
                        "cat /proc/sys/kernel/random/boot_id",
                        "getenforce | grep -qx Enforcing",
                        "cd /home/build/porelo && bash selinux/install-selinux-policy.sh",
                        "semodule -l | grep -qx porelo_socket");
    }

    // Prevents an image of a machine whose preparation failed, and a failed build from leaving
    // the machine rented.
    @Test
    void takesNoImageOfAFailedStepAndStillGivesTheMachineBack() {
        machine.probes.add(true);
        machine.answer =
                command ->
                        command.startsWith("su - build")
                                ? new Ssh.Output("", "musl: no such file", 1)
                                : new Ssh.Output("", "", 0);

        assertThatThrownBy(() -> build("ubuntu", null, "the installer"))
                .isInstanceOf(IOException.class)
                .hasMessage(
                        "preparing the image failed at: su - build -c 'bash /tmp/install-musl.sh'\n"
                                + "musl: no such file");
        assertThat(asked).as("neither stopped nor imaged").hasSize(1);
        assertThat(machine.releases).isEqualTo(1);
    }

    // Prevents a recipe that failed from being read as a prepared machine.
    @Test
    void namesTheSystemWhoseRecipeFailed() {
        machine.probes.add(true);
        machine.answer = command -> new Ssh.Output("", "E: no network", 100);

        assertThatThrownBy(() -> build("ubuntu", null, null))
                .isInstanceOf(IOException.class)
                .hasMessage("could not prepare ubuntu: E: no network");
        assertThat(machine.releases).isEqualTo(1);
    }
}
