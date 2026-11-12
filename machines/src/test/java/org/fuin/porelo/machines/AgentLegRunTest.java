package org.fuin.porelo.machines;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The steps of {@link AgentLeg#run} after the machine is rented, over a fake machine and suite. */
class AgentLegRunTest {

    private final FakeMachine machine = new FakeMachine();

    /** Each pass of the Cucumber suite: the address it ran against and what it added. */
    private final List<String> passes = new ArrayList<>();

    private final Machines source =
            new Machines() {

                @Override
                public Lease acquire(Spec spec) {
                    throw new UnsupportedOperationException("a leg starts from stock");
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

    private static AgentLeg.Options options(
            String script, Path candidate, Path cucumber, int accounts) {
        return new AgentLeg.Options(
                "ubuntu",
                Kind.AMD64,
                "https://packages.invalid",
                "porelo-agent-pi",
                script,
                candidate,
                false,
                cucumber,
                accounts,
                List.of(),
                "snapshots");
    }

    private void run(AgentLeg.Options options, Map<String, String> env) throws IOException {
        machine.probes.add(true);
        machine.markUsers = true;
        AgentLeg.run(
                source,
                options,
                FakeMachine.KEY,
                machine,
                (repository, lease, credential, added) ->
                        passes.add(lease.address() + " " + String.join(" ", added)),
                env);
    }

    // Prevents the suite's credentials from reaching the machine's command line, where every
    // process there reads them, and the suite from running as root rather than as a task runs.
    @Test
    void sendsTheSuiteAndItsCredentialsOnlyThroughStandardInputAsTheUser() throws IOException {
        run(
                options("#!/bin/sh\necho suite\n", null, null, 1),
                Map.of("PORELO_E2E_OPENROUTER_API_KEY", "sk-secret", "UNRELATED", "kept here"));

        assertThat(machine.commands)
                .as("the suite sent and run as the leg's user, nothing of it as root")
                .filteredOn(command -> command.contains("acceptance.sh"))
                .satisfiesExactly(
                        sent ->
                                assertThat(sent)
                                        .isEqualTo(
                                                "acceptance: cat > /home/acceptance/acceptance.sh"
                                                        + " && chmod +x /home/acceptance/acceptance.sh"
                                                        + "\t#!/bin/sh\necho suite\n"),
                        suite ->
                                assertThat(suite)
                                        .startsWith("acceptance: cd /home/acceptance && ")
                                        .endsWith(
                                                "./acceptance.sh </dev/null"
                                                        + "\tPORELO_E2E_OPENROUTER_API_KEY='sk-secret'\n"));
        assertThat(machine.commands)
                .as("no command line names the secret, and nothing the suite is not given goes in")
                .allSatisfy(
                        command -> {
                            assertThat(command).doesNotContain("kept here");
                            assertThat(command.split("\t")[0]).doesNotContain("sk-secret");
                        });
        assertThat(machine.releases).as("the machine given back").isEqualTo(1);
    }

    // Prevents a failed suite from passing, or from leaving the machine rented.
    @Test
    void failsOnAFailedSuiteAndStillGivesTheMachineBack() {
        machine.answer =
                command ->
                        command.contains("./acceptance.sh")
                                ? new Ssh.Output("", "1 scenario failed", 1)
                                : new Ssh.Output("", "", 0);

        assertThatThrownBy(() -> run(options("echo", null, null, 1), Map.of()))
                .isInstanceOf(IOException.class);
        assertThat(machine.releases).isEqualTo(1);
    }

    // Prevents the candidate from being dropped for the published package, which would pass just
    // the same, and the leg from installing anything but what it sent.
    @Test
    void installsTheCandidateItSent(@TempDir Path candidate) throws IOException {
        Files.writeString(candidate.resolve("porelo_1.2.3_amd64.deb"), "");

        run(options(null, candidate, null, 1), Map.of());

        assertThat(machine.uploads)
                .hasSize(1)
                .first()
                .asString()
                .endsWith(".deb -> /root/candidate/porelo_1.2.3_amd64.deb");
        assertThat(machine.commands)
                .anySatisfy(
                        command ->
                                assertThat(command)
                                        .contains("apt-get")
                                        .contains("/root/candidate/porelo_1.2.3_amd64.deb"));
    }

    // Prevents scenarios beside each other from sharing one account, and a restart from taking
    // every account's daemon away in the middle of the others' scenarios.
    @Test
    void runsTheScenariosBesideEachOtherThenWhatTouchesTheWholeMachineAlone(@TempDir Path suite)
            throws IOException {
        run(options(null, null, suite, 3), Map.of());

        assertThat(machine.users)
                .as("each extra account set up over its own connection")
                .containsSubsequence("acceptance2", "acceptance3");
        assertThat(machine.commands)
                .as("each extra account set up as itself")
                .containsSubsequence("acceptance2: porelo setup", "acceptance3: porelo setup");
        assertThat(passes)
                .containsExactly(
                        "192.0.2.7 -Dporelo.acceptance.users=acceptance,acceptance2,acceptance3"
                                + " -Dporelo.acceptance.parallel=true"
                                + " -Dporelo.acceptance.parallelism=3"
                                + " -Dcucumber.filter.tags=not @restart",
                        "192.0.2.7 -Dcucumber.filter.tags=@restart");
    }

    // Prevents a single account's suite from being split into passes it does not need.
    @Test
    void runsASingleAccountsSuiteOnceAsItIs(@TempDir Path suite) throws IOException {
        run(options(null, null, suite, 1), Map.of());

        assertThat(passes).containsExactly("192.0.2.7 ");
        assertThat(machine.users).as("no account beside the lease's own").containsExactly("root");
    }
}
