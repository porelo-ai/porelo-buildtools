package org.fuin.porelo.machines;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.util.List;
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
}
