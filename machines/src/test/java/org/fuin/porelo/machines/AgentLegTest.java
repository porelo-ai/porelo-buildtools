package org.fuin.porelo.machines;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Tests for how an agent's leg installs what it is testing. */
class AgentLegTest {

    @Test
    void namesItsAccountsTheFirstAsAlwaysAndMakesEachLingeringWithThisRunsKey() {
        assertThat(AgentLeg.accounts(1)).containsExactly("acceptance");
        assertThat(AgentLeg.accounts(3))
                .containsExactly("acceptance", "acceptance2", "acceptance3");
        assertThat(AgentLeg.account("acceptance2"))
                .contains("useradd -m -s /bin/bash acceptance2")
                .contains("loginctl enable-linger acceptance2")
                .contains("/home/acceptance2/.ssh/authorized_keys");
    }

    @Test
    void sendsTheSuitesCredentialOnItsInputNeverOnItsCommandLine() {
        // A command line is in the far shell's arguments for the whole suite, and in the message a
        // failed leg ends with.
        final AgentLeg.Remote suite =
                AgentLeg.acceptance(
                        Map.of(
                                "PORELO_E2E_OPENROUTER_API_KEY", "sk-or-v1-secret",
                                "PORELO_E2E_MODEL", "some/model",
                                "HOME", "/home/runner"));

        assertThat(suite.command())
                .as("the command line carries no value of the environment")
                .doesNotContain("sk-or-v1-secret")
                .doesNotContain("some/model")
                .endsWith("./acceptance.sh </dev/null");
        assertThat(suite.stdin())
                .as("each passed value, quoted for the shell, and nothing else of the environment")
                .isEqualTo(
                        "PORELO_E2E_OPENROUTER_API_KEY='sk-or-v1-secret'\n"
                                + "PORELO_E2E_MODEL='some/model'\n");
    }

    @Test
    void installsTheAgentInTheSameCommandAsPorelo() {
        // Neither 'dpkg -i' nor 'rpm -i' resolves a dependency, and installing porelo separately by
        // hand would prove less than an operator's own package manager does.
        assertThat(AgentLeg.install("ubuntu", "https://x/artifactory", "porelo-agent-claude"))
                .contains("apt-get install -y -qq porelo porelo-agent-claude");
        assertThat(AgentLeg.install("fedora", "https://x/artifactory", "porelo-agent-omp"))
                .contains("dnf install -y -q porelo porelo-agent-omp");
    }

    @Test
    void installsACandidateByPathInThatSameCommand() {
        // A package built in the run and published nowhere still goes in beside porelo, so the
        // repository, the index and 'Depends: porelo' are all still exercised.
        assertThat(AgentLeg.install("ubuntu", "https://x/artifactory", "/root/candidate/*.deb"))
                .contains("install -y -qq porelo /root/candidate/*.deb");
    }

    @Test
    void installsTheFilterAsAPreparedMachineHasIt() {
        // Measured on an agent repository's lease on 2026-09-29: with porelo alone, 'talk pass'
        // passed nothing and 'talk held' listed nothing, while the same scenario held on a prepared
        // machine.
        for (final String os : new String[] {"ubuntu", "fedora"}) {
            final String script = AgentLeg.install(os, "https://x", "porelo-agent-pi");
            assertThat(script)
                    .contains("for p in porelo-message-sluice-filter; do")
                    .as("the local transport is retired")
                    .doesNotContain("porelo-message-transport-local");
            // After the command that installs porelo and any candidate, so a candidate filter wins.
            assertThat(script.indexOf("for p in")).isGreaterThan(script.indexOf("install -y -q"));
        }
        assertThat(AgentLeg.install("ubuntu", "https://x", "p"))
                .contains("dpkg -s \"$p\" >/dev/null 2>&1 && continue");
        assertThat(AgentLeg.install("fedora", "https://x", "p"))
                .contains("rpm -q \"$p\" >/dev/null 2>&1 && continue");
    }

    @Test
    void takesTheRepositoryAndItsKeyFromWhereThePackagesArePublished() {
        final String script =
                AgentLeg.install(
                        "ubuntu", "https://fuinorg.jfrog.io/artifactory", "porelo-agent-pi");
        assertThat(script)
                .contains("https://fuinorg.jfrog.io/artifactory/porelo-dist-deb snapshots main");
        assertThat(script).contains("/api/security/keypair/porelo-packages/public");
        assertThat(
                        AgentLeg.install(
                                "fedora",
                                "https://fuinorg.jfrog.io/artifactory",
                                "porelo-agent-pi"))
                .contains("https://fuinorg.jfrog.io/artifactory/porelo-dist-rpm/snapshots");
    }

    @Test
    void usesEachDistributionsOwnRepositoryFormat() {
        assertThat(AgentLeg.install("fedora", "https://x", "p"))
                .contains("/etc/yum.repos.d/porelo.repo");
        assertThat(AgentLeg.install("ubuntu", "https://x", "p"))
                .contains("/etc/apt/sources.list.d/porelo.list");
    }

    @Test
    void quotesACredentialSoAShellTakesItWhole() {
        // It travels as an environment assignment on the far shell, never as an argument: argv is
        // readable by every process on that machine.
        assertThat(AgentLeg.quote("plain")).isEqualTo("'plain'");
        assertThat(AgentLeg.quote("it's")).isEqualTo("'it'\\''s'");
        assertThat(AgentLeg.quote("a b; rm -rf /")).isEqualTo("'a b; rm -rf /'");
    }

    @Test
    void leavesNoPlaceholderUnreplaced() {
        assertThat(AgentLeg.install("ubuntu", "https://x", "p")).doesNotContain("@");
        assertThat(AgentLeg.install("fedora", "https://x", "p")).doesNotContain("@");
    }

    @Test
    void readsADebianPackageNameOffItsFileName() {
        assertThat(AgentLeg.packageName("porelo_0.1.0~snapshot.151_amd64.deb", ".deb"))
                .isEqualTo("porelo");
        assertThat(AgentLeg.packageName("porelo-message-sluice-filter_1.0.0_amd64.deb", ".deb"))
                .as("a name full of dashes is still one name")
                .isEqualTo("porelo-message-sluice-filter");
    }

    @Test
    void readsAnRpmPackageNameOffItsFileName() {
        assertThat(AgentLeg.packageName("porelo-0.1.0-1.x86_64.rpm", ".rpm")).isEqualTo("porelo");
        assertThat(AgentLeg.packageName("porelo-agent-claude-1.2.3-1.x86_64.rpm", ".rpm"))
                .isEqualTo("porelo-agent-claude");
    }

    /**
     * Two different packages are a set to install together - an agent repository builds several,
     * and so does the messaging one. Two builds of the same package are the ambiguity that is
     * refused, and the names have to tell the cases apart before either file is on a machine.
     */
    @Test
    void tellsTwoPackagesApartFromTwoBuildsOfOne() {
        assertThat(AgentLeg.packageName("porelo-message-sluice-filter_1.0.0_amd64.deb", ".deb"))
                .isNotEqualTo(
                        AgentLeg.packageName(
                                "porelo-message-transport-local_1.0.0_amd64.deb", ".deb"));
        assertThat(AgentLeg.packageName("porelo_0.1.0~snapshot.150_amd64.deb", ".deb"))
                .isEqualTo(AgentLeg.packageName("porelo_0.1.0~snapshot.151_amd64.deb", ".deb"));
    }

    @Test
    void aFileWithoutThatShapeIsItsOwnPackage() {
        assertThat(AgentLeg.packageName("something.deb", ".deb")).isEqualTo("something");
    }

    @Test
    void namesEachCandidateOnTheMachineQuotedSoAShellTakesItWhole() throws Exception {
        assertThat(
                        AgentLeg.candidatePaths(
                                List.of(
                                        "porelo_0.1.0~snapshot.151_amd64.deb",
                                        "porelo-agent-claude-1.2.3-1.x86_64.rpm",
                                        "porelo+extra.deb")))
                .isEqualTo(
                        "'/root/candidate/porelo_0.1.0~snapshot.151_amd64.deb'"
                                + " '/root/candidate/porelo-agent-claude-1.2.3-1.x86_64.rpm'"
                                + " '/root/candidate/porelo+extra.deb'");
    }

    @Test
    void refusesACandidateWhoseFileNameAShellWouldReadAsMoreThanAName() {
        // The name goes into a command run as root on the machine; a working tree is not trusted to
        // hold only what a build made there.
        for (final String name :
                new String[] {"a;reboot.deb", "$(id).deb", "a b.deb", "a'b.deb", "-rf.deb"}) {
            assertThatThrownBy(() -> AgentLeg.candidatePaths(List.of("porelo_1_amd64.deb", name)))
                    .as(name)
                    .isInstanceOf(IOException.class)
                    .hasMessageContaining(name)
                    .hasMessageContaining("[A-Za-z0-9._+~-]");
        }
    }

    @Test
    void quotesTheOperatorsPackageAndRefusesOneThatIsNotAName() throws Exception {
        assertThat(AgentLeg.packageArgument("porelo-agent-claude"))
                .isEqualTo("'porelo-agent-claude'");
        assertThat(
                        AgentLeg.install(
                                "ubuntu", "https://x", AgentLeg.packageArgument("porelo-agent-pi")))
                .contains("apt-get install -y -qq porelo 'porelo-agent-pi'");
        assertThatThrownBy(() -> AgentLeg.packageArgument("porelo-agent-pi; reboot"))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("porelo-agent-pi; reboot")
                .hasMessageContaining("--package");
    }

    @Test
    void handsTheSuiteTheHostKeyARentedMachineWasMadeWith() {
        // Without it the suite's own connections accept whatever answers at the address.
        final String line = HostKeyPair.generate().publicLine();

        assertThat(AgentLeg.suite("192.0.2.7", HostKey.pinned(line), List.of()))
                .as("the suite's command")
                .contains(
                        "-Dporelo.acceptance.host=192.0.2.7",
                        "-Dporelo.acceptance.hostkey=" + line);
    }

    @Test
    void namesNoHostKeyForAMachineNobodyMadeOneFor() {
        // The kit then connects to the local virtual machines as it always has: the written risk.
        assertThat(
                        AgentLeg.suite(
                                "192.168.122.10",
                                HostKey.any("a local virtual machine"),
                                List.of()))
                .as("the suite's command")
                .noneMatch(argument -> argument.startsWith("-Dporelo.acceptance.hostkey"));
    }

    @Test
    void theLegPassesKindAndAccountCountToItsMachineSource() {
        // Losing the count would rent one-account capacity for simultaneous scenarios.
        final RecordingMachines source = new RecordingMachines();
        final AgentLeg.Options options =
                new AgentLeg.Options(
                        "ubuntu",
                        Kind.AMD64_BUILD,
                        "https://packages.invalid",
                        "porelo-agent",
                        null,
                        null,
                        false,
                        null,
                        4);
        assertThatThrownBy(() -> AgentLeg.run(source, options, Keys.generated()))
                .as("the leg reaches the configured source")
                .hasMessage("acquisition recorded");
        final Spec request = source.requested.getFirst();
        assertThat(request.kind())
                .as("the leg preserves its requested capacity")
                .isEqualTo(Kind.AMD64_BUILD);
        assertThat(request.accounts()).as("all concurrent accounts reach the source").isEqualTo(4);
    }

    @Test
    void aReleaseLegInstallsFromTheReleasesChannel() {
        // The 0.4.0 tag legs installed from the snapshots the release had emptied, and stopped at
        // "Unable to locate package porelo".
        assertThat(AgentLeg.install("ubuntu", "https://x/artifactory", AgentLeg.RELEASES, "p"))
                .contains("https://x/artifactory/porelo-dist-deb releases main")
                .doesNotContain("snapshots");
        assertThat(AgentLeg.install("fedora", "https://x/artifactory", AgentLeg.RELEASES, "p"))
                .contains("baseurl=https://x/artifactory/porelo-dist-rpm/releases")
                .doesNotContain("snapshots");
        assertThat(AgentLeg.install("ubuntu", "https://x/artifactory", "p"))
                .as("a leg that names no channel installs the snapshots, as before")
                .contains("porelo-dist-deb snapshots main");
        assertThatThrownBy(() -> AgentLeg.install("ubuntu", "https://x", "nightly", "p"))
                .hasMessageContaining("neither snapshots nor releases");
    }

    @Test
    void aCandidateNeedsNoPackageName() throws IOException {
        // 'acceptance --candidate' documents that it needs no name, and 0.4.0 refused it with
        // "--package '' is not a package name".
        final AgentLeg.Options options =
                new AgentLeg.Options(
                        "ubuntu",
                        Kind.AMD64,
                        "https://packages.invalid",
                        "",
                        null,
                        Path.of("packages"),
                        false,
                        null,
                        1);
        assertThat(AgentLeg.installs(options, candidate -> "'/root/candidate/a.deb'"))
                .isEqualTo("'/root/candidate/a.deb'");
        final AgentLeg.Options named =
                new AgentLeg.Options(
                        "ubuntu",
                        Kind.AMD64,
                        "https://packages.invalid",
                        "",
                        null,
                        null,
                        false,
                        null,
                        1);
        assertThatThrownBy(() -> AgentLeg.installs(named, candidate -> "never"))
                .as("without a candidate the name is still checked")
                .hasMessageContaining("is not a package name");
    }
}
