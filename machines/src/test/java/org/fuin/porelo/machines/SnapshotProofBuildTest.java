package org.fuin.porelo.machines;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Tests for {@link Snapshots#proofBuild}: the build looked at from apart, and bounded. */
class SnapshotProofBuildTest {

    private final FakeMachine machine = new FakeMachine();

    private final Lease lease = machine.lease(FakeMachine.root());

    /** What each look answers, in order; once empty, the last repeats. */
    private final Deque<String> looks = new ArrayDeque<>();

    private String lastLook = "|\n";

    private final ByteArrayOutputStream said = new ByteArrayOutputStream();

    private PrintStream before;

    @BeforeEach
    void answerTheLooks() {
        machine.answer =
                command -> {
                    if (command.startsWith("cat " + Snapshots.PROOF_STATUS)) {
                        if (!looks.isEmpty()) {
                            lastLook = looks.poll();
                        }
                        return new Ssh.Output(lastLook, "", 0);
                    }
                    if (command.startsWith("tail -n 60")) {
                        return new Ssh.Output("[ERROR] the last lines\n", "", 0);
                    }
                    return new Ssh.Output("", "", 0);
                };
        before = System.out;
        System.setOut(new PrintStream(said, true, StandardCharsets.UTF_8));
    }

    @AfterEach
    void restoreTheOutput() {
        System.setOut(before);
    }

    private String said() {
        return said.toString(StandardCharsets.UTF_8);
    }

    // Prevents a proof build that hangs from saying nothing until the step's timeout: fedora's of
    // 46f0cde was silent for 56 minutes and left no clue why.
    @Test
    void saysWhereTheBuildStandsAndEndsWhenItDoes() throws Exception {
        looks.addAll(
                List.of(
                        "|\n",
                        "|[INFO] Building Porelo Daemon 0.5.1-SNAPSHOT [4/12]\n",
                        "|[3/8] Building universe...\n",
                        "0\n|[8/8] Creating image...\n"));

        Snapshots.proofBuild(lease, "su - build -c 'make'", Duration.ofMinutes(40), machine);

        assertThat(said())
                .contains("0 min: resolving, nothing built yet")
                .contains("0 min: [INFO] Building Porelo Daemon 0.5.1-SNAPSHOT [4/12]")
                .contains("1 min: [3/8] Building universe...")
                .contains("the proof build ended after 1 min");
        assertThat(machine.sleeps).hasSize(3).containsOnly(Snapshots.PROOF_LOOK);
    }

    // Prevents the build from dying with the connection, or the connection waiting on the build.
    @Test
    void runsTheBuildApartFromTheConnectionAndKeepsItsStatus() throws Exception {
        looks.add("0\n|\n");

        Snapshots.proofBuild(lease, "su - build -c 'make'", Duration.ofMinutes(40), machine);

        assertThat(machine.commands.get(0))
                .startsWith("rm -f " + Snapshots.PROOF_LOG + " " + Snapshots.PROOF_STATUS)
                .contains("\tsu - build -c 'make'\necho $? > " + Snapshots.PROOF_STATUS + ".part");
        assertThat(machine.commands.get(1))
                .startsWith("setsid nohup sh /tmp/proof-build.sh > " + Snapshots.PROOF_LOG)
                .endsWith("< /dev/null &");
    }

    @Test
    void aFailedBuildIsNamedWithItsExitAndItsLastLines() {
        looks.add("1\n|[INFO] Building Porelo App 0.5.1-SNAPSHOT [9/12]\n");

        assertThatThrownBy(
                        () -> Snapshots.proofBuild(lease, "make", Duration.ofMinutes(40), machine))
                .hasMessageContaining("failed with exit 1 after 0 min")
                .hasMessageContaining("[ERROR] the last lines");
    }

    // Prevents the step's own timeout from ending the build before the build says why it is slow.
    @Test
    void stopsAtTheLimitWithWhatTheMachineShows() {
        looks.add("|[5/8] Inlining methods...\n");

        assertThatThrownBy(
                        () -> Snapshots.proofBuild(lease, "make", Duration.ofMinutes(40), machine))
                .hasMessageContaining("ran past 40 min, at: [5/8] Inlining methods...");

        assertThat(machine.sleeps).hasSize(80);
        assertThat(machine.commands).last().isEqualTo(Snapshots.PROOF_STATE);
        assertThat(Snapshots.PROOF_STATE)
                .contains("tail -n 60 " + Snapshots.PROOF_LOG)
                .contains("free -m")
                .contains("ausearch -m avc");
        // Still there, said every five minutes, so a slow stage is told from a stuck one.
        assertThat(said()).contains("5 min: [5/8] Inlining methods...");
        assertThat(said()).contains("35 min: [5/8] Inlining methods...");
    }

    // Prevents one dropped connection from ending a build that goes on apart from it, and a
    // machine that no longer answers from holding the step past the limit.
    @Test
    void aLookWithoutAnAnswerIsSaidAndTheLimitStillHolds() {
        machine.answer =
                command -> {
                    if (command.startsWith("cat " + Snapshots.PROOF_STATUS)) {
                        throw new java.io.UncheckedIOException(new java.io.IOException("reset"));
                    }
                    return new Ssh.Output("", "", 0);
                };

        assertThatThrownBy(
                        () -> Snapshots.proofBuild(lease, "make", Duration.ofMinutes(40), machine))
                .hasMessageContaining("ran past 40 min, and the machine no longer answered");

        assertThat(machine.sleeps).hasSize(80);
        assertThat(said()).contains("0 min: the machine did not answer");
    }

    @Test
    void aLookAfterADroppedOneGoesOn() throws Exception {
        final int[] seen = {0};
        machine.answer =
                command -> {
                    if (command.startsWith("cat " + Snapshots.PROOF_STATUS) && seen[0]++ == 0) {
                        throw new java.io.UncheckedIOException(new java.io.IOException("reset"));
                    }
                    return new Ssh.Output(
                            command.startsWith("cat ") ? "0\n|[8/8] Creating image...\n" : "",
                            "",
                            0);
                };

        Snapshots.proofBuild(lease, "make", Duration.ofMinutes(40), machine);

        assertThat(said()).contains("the machine did not answer").contains("ended after 0 min");
    }

    @Test
    void theLimitLeavesTheStepTimeForTheImage() {
        assertThat(Snapshots.PROOF_LIMIT).isLessThanOrEqualTo(Duration.ofMinutes(40));
    }
}
