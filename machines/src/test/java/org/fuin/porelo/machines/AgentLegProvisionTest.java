package org.fuin.porelo.machines;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Guards against package installation and account creation without root privileges. */
class AgentLegProvisionTest {

    @Test
    void aPoolLeaseProvisionsAsRootAndCopiesItsOwnAuthorizedKeys() throws IOException {
        for (final String os : List.of("ubuntu", "fedora")) {
            final List<String> sent = provision("run", os);
            assertThat(sent)
                    .as(
                            "both package installation and account creation need noninteractive root on %s",
                            os)
                    .hasSize(2)
                    .allSatisfy(
                            command ->
                                    assertThat(command)
                                            .as("the whole compound command must run as root")
                                            .startsWith("sudo -n sh -c '"));
            assertThat(sent.get(1))
                    .as("the pool key belongs to the lease user, so the acceptance login works")
                    .contains(
                            "/home/run/.ssh/authorized_keys",
                            "/home/acceptance/.ssh/authorized_keys")
                    .doesNotContain("/root/.ssh/authorized_keys");
        }
    }

    @Test
    void aRootLeaseKeepsItsProvisioningCommands() throws IOException {
        for (final String os : List.of("ubuntu", "fedora")) {
            assertThat(provision("root", os))
                    .as("stock root leases keep their package and account commands on %s", os)
                    .containsExactly(
                            AgentLeg.install(os, "https://packages.invalid", "'porelo-agent-pi'"),
                            AgentLeg.account("acceptance"));
        }
    }

    @Test
    void aPoolLeaseCopiesTheKeyFromItsRunAccount() throws IOException {
        assertThat(provision("run", "ubuntu").get(1))
                .as("the acceptance account must receive the lease user's authorized keys")
                .contains("/home/run/.ssh/authorized_keys")
                .doesNotContain("/root/.ssh/authorized_keys");
    }

    @Test
    void aPoolLeaseWritesCandidateBytesAsRoot(@TempDir Path directory) throws IOException {
        final byte[] bytes = {0, 1, 2, -1, -2, 10};
        Files.write(directory.resolve("porelo-agent-pi_1_amd64.deb"), bytes);
        final List<String> commands = new ArrayList<>();
        final List<String> inputs = new ArrayList<>();
        final List<String> uploads = new ArrayList<>();
        try (Lease lease =
                new Lease(
                        0,
                        "pool",
                        "192.0.2.1",
                        Spec.of("leg", "ubuntu", Kind.AMD64, "run", Keys.generated()),
                        HostKey.any("no connection is opened"),
                        () -> {})) {
            final String packages =
                    AgentLeg.sendCandidate(
                            lease,
                            "ubuntu",
                            directory,
                            (command, stdin) -> {
                                commands.add(command);
                                if (stdin != null) {
                                    inputs.add(stdin);
                                }
                                return new Ssh.Output("", "", 0);
                            },
                            (file, remote) -> uploads.add(remote));
            assertThat(commands)
                    .as("preparing and writing root's candidate directory both need root")
                    .hasSize(2)
                    .allSatisfy(
                            command ->
                                    assertThat(command)
                                            .as("SFTP as run cannot write under /root")
                                            .startsWith("sudo -n sh -c '"));
            assertThat(commands.get(1))
                    .as("the privileged shell decodes the binary package into its destination")
                    .contains("base64 -d > ", "/root/candidate/porelo-agent-pi_1_amd64.deb");
            assertThat(inputs)
                    .as("the package's binary bytes travel on stdin")
                    .containsExactly(Base64.getEncoder().encodeToString(bytes));
            assertThat(uploads).as("the run account must not try SFTP into root's home").isEmpty();
            assertThat(packages)
                    .as("the package manager receives the uploaded candidate")
                    .isEqualTo("'/root/candidate/porelo-agent-pi_1_amd64.deb'");
        }
    }

    @Test
    void aRootLeaseKeepsItsCandidateUpload(@TempDir Path directory) throws IOException {
        final Path candidate = directory.resolve("porelo-agent-pi_1_amd64.deb");
        Files.write(candidate, new byte[] {0, 1, -1});
        final List<String> commands = new ArrayList<>();
        final List<Path> uploaded = new ArrayList<>();
        final List<String> destinations = new ArrayList<>();
        try (Lease lease =
                new Lease(
                        0,
                        "stock",
                        "192.0.2.1",
                        Spec.of("leg", "ubuntu", Kind.AMD64, "root", Keys.generated()),
                        HostKey.any("no connection is opened"),
                        () -> {})) {
            AgentLeg.sendCandidate(
                    lease,
                    "ubuntu",
                    directory,
                    (command, stdin) -> {
                        commands.add(command);
                        return new Ssh.Output("", "", 0);
                    },
                    (file, remote) -> {
                        uploaded.add(file);
                        destinations.add(remote);
                    });
        }
        assertThat(commands)
                .as("root prepares the candidate directory as before")
                .containsExactly("mkdir -p /root/candidate/ && rm -f /root/candidate/*");
        assertThat(uploaded)
                .as("root still uploads the candidate with SFTP")
                .containsExactly(candidate);
        assertThat(destinations)
                .as("the candidate's root path is unchanged")
                .containsExactly("/root/candidate/porelo-agent-pi_1_amd64.deb");
    }

    private static List<String> provision(String user, String os) throws IOException {
        final List<String> sent = new ArrayList<>();
        final AgentLeg.Command ssh =
                (command, stdin) -> {
                    sent.add(command);
                    return new Ssh.Output("", "", 0);
                };
        try (Lease lease =
                new Lease(
                        0,
                        "pool",
                        "192.0.2.1",
                        Spec.of("leg", os, Kind.AMD64, user, Keys.generated()),
                        HostKey.any("no connection is opened"),
                        () -> {})) {
            AgentLeg.provision(
                    lease,
                    new AgentLeg.Options(
                            os,
                            Kind.AMD64,
                            "https://packages.invalid",
                            "porelo-agent-pi",
                            null,
                            null,
                            false,
                            null,
                            1),
                    "'porelo-agent-pi'",
                    ssh);
        }
        return sent;
    }
}
