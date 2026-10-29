package org.fuin.porelo.machines;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Tests for {@link Main}'s arguments and refusals.
 *
 * <p><strong>Nothing here can reach the API, by construction.</strong> An earlier version of this
 * test called the real entry point and asserted that it refused for want of a token. It passed
 * here, where nothing sets one, and on CI - where the workflow does - it authenticated against the
 * real project, ran {@code sweep --mine}, and deleted the server the build was running on. So the
 * way in is a {@link Supplier} the test controls, and every test below hands over one that fails
 * the test if anything asks it for a connection.
 */
class MainTest {

    /** A sink a test can read, so nothing reaches a job's annotations. */
    private static final Consumer<String> QUIET = message -> {};

    /** The sweep's now, so the servers' expiries decide the same on every run. */
    private static final Clock SWEEP_TIME =
            Clock.fixed(Instant.parse("2026-10-18T04:13:00Z"), ZoneOffset.UTC);

    /** Fails the test rather than opening anything, so a parsing test cannot become a sweep. */
    private static final Supplier<Machines> NEVER =
            () -> {
                throw new AssertionError(
                        "the arguments were accepted and something tried to connect");
            };

    @Test
    void saysWhatIsWrongWithoutAnnotatingAnybodysJob() throws IOException {
        // Every '::error::' line GitHub sees becomes an annotation, including one a unit test
        // caused on purpose - and gating on GITHUB_ACTIONS could not help, because the tests run
        // inside CI where it is set. So a test hands over its own sink and nothing is printed.
        final List<String> said = new ArrayList<>();
        assertThat(Main.run(new String[] {"sweep", "--nonsense"}, said::add, NEVER)).isEqualTo(2);
        assertThat(said).containsExactly("unknown option: --nonsense");
    }

    @Test
    void saysHowToUseItWhenAskedForNothing() throws IOException {
        assertThat(Main.run(new String[0], QUIET, NEVER)).isEqualTo(2);
    }

    @Test
    void refusesAnOptionItDoesNotKnowRatherThanIgnoringIt() throws IOException {
        // An ignored option in a sweep means deleting on a rule nobody asked for, or not deleting
        // on one they did.
        assertThat(Main.run(new String[] {"sweep", "--nonsense"}, QUIET, NEVER)).isEqualTo(2);
    }

    @Test
    void refusesToSweepByAgeAndNamesWhatDeletesServersInUse() throws IOException {
        // '--older-than 0 --now' deleted every server in use. Refused whatever the number, so a
        // caller still sweeping by age fails rather than going on under a rule it did not ask for.
        final List<String> said = new ArrayList<>();
        assertThat(Main.run(new String[] {"sweep", "--older-than", "0", "--now"}, said::add, NEVER))
                .isEqualTo(2);
        assertThat(said)
                .as("the refusal names the option that deletes servers within their time")
                .singleElement()
                .asString()
                .contains("--including-in-use");
        assertThat(Main.run(new String[] {"sweep", "--older-than", "60", "--now"}, QUIET, NEVER))
                .as("the hourly sweep's old line is refused too, before anything connects")
                .isEqualTo(2);
    }

    @Test
    void refusesATimeLimitThatIsNoneOrLongerThanAJobMayRun() throws IOException {
        // Too long keeps a forgotten server from the sweep for as long; none would have the sweep
        // take the machine while its job is still running.
        for (final String command : List.of("lease", "acceptance", "snapshot")) {
            for (final String minutes : List.of("0", "361", "soon", "-5")) {
                final List<String> said = new ArrayList<>();
                assertThat(
                                Main.run(
                                        new String[] {command, "--os", "ubuntu", "--for", minutes},
                                        said::add,
                                        NEVER))
                        .as("%s --for %s", command, minutes)
                        .isEqualTo(2);
                assertThat(said).as("%s --for %s", command, minutes).isNotEmpty();
            }
        }
    }

    @Test
    void takesATimeLimitAndGoesOnToRent() {
        // Accepted means it went on to rent a machine - which NEVER refuses, so the test ends
        // there.
        assertThatThrownBy(
                        () ->
                                Main.run(
                                        new String[] {"lease", "--os", "ubuntu", "--for", "360"},
                                        QUIET,
                                        NEVER))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("tried to connect");
    }

    @Test
    void saysWhereTheTokenShouldComeFromWhenThereIsNone() {
        // Never an argument: /proc/<pid>/cmdline is world readable and neither supported
        // distribution mounts /proc with hidepid.
        assertThatThrownBy(() -> Main.token((String) null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(Main.API_TOKEN);
        assertThatThrownBy(() -> Main.token("  ")).isInstanceOf(IllegalStateException.class);
        assertThat(Main.token("a-token")).isEqualTo("a-token");
    }

    @Test
    void identifiesARunWellEnoughToNameAServerBy() {
        // A name built from a timestamp collides when two runs start in the same second, which is
        // what two pushes landing together produce - and the API refuses the second with "server
        // name is already used", failing a leg for a reason unrelated to the change.
        assertThat(Main.runId("12345", "ubuntu")).isNotEqualTo(Main.runId("12346", "ubuntu"));
        assertThat(Main.runId("12345", "ubuntu")).isNotEqualTo(Main.runId("12345", "fedora"));
    }

    @Test
    void namesTheLegSoTwoMatrixLegsAreNotOneRun() {
        // Both legs share GITHUB_RUN_ID, so the leg is what makes a server's label unique - and a
        // sweep deletes by that label. Without it each leg would delete the other's machine.
        assertThat(Main.runId("12345", "ubuntu")).isEqualTo("12345-ubuntu");
        assertThat(Main.runId("12345", "fedora")).isEqualTo("12345-fedora");
        assertThat(Main.runId("12345", null)).isEqualTo("12345");
        assertThat(Main.runId(null, null)).startsWith("local-");
        // Run by hand, the leg still has to reach the machine's name: several agents share one
        // cloud account, and a server nobody can attribute is one nobody dares delete and anybody
        // may delete by mistake. A timestamp makes it unique, not attributable.
        assertThat(Main.runId(null, "acc-smith-claude-code-ubuntu"))
                .startsWith("local-")
                .endsWith("-acc-smith-claude-code-ubuntu");
    }

    @Test
    void acceptanceRunsScenariosWithoutAScriptForAnAgentThatReplacedIt() {
        // Accepted means it went on to rent a machine - which NEVER refuses, so the test ends
        // there.
        assertThatThrownBy(
                        () ->
                                Main.run(
                                        new String[] {
                                            "acceptance",
                                            "--candidate",
                                            "packages",
                                            "--cucumber",
                                            "."
                                        },
                                        QUIET,
                                        NEVER))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("tried to connect");
    }

    @Test
    void acceptanceOnThePoolAsksNoProviderAndStopsWithoutItsFile() {
        // NEVER fails the test if a server is asked for: --pool must not rent one.
        assertThatThrownBy(
                        () ->
                                Main.run(
                                        new String[] {
                                            "acceptance",
                                            "--candidate",
                                            "packages",
                                            "--cucumber",
                                            ".",
                                            "--key",
                                            "key",
                                            "--pool",
                                            "/nonexistent/pool.json"
                                        },
                                        QUIET,
                                        NEVER))
                .as("a run asked for the pool goes to the pool, never to a provider")
                .isInstanceOf(IOException.class)
                .hasMessageContaining("No pool file");
    }

    @Test
    void acceptanceRefusesAccountsOnThePool() throws IOException {
        final List<String> said = new ArrayList<>();
        assertThat(
                        Main.run(
                                new String[] {
                                    "acceptance",
                                    "--candidate",
                                    "packages",
                                    "--cucumber",
                                    ".",
                                    "--accounts",
                                    "2",
                                    "--pool",
                                    "pool.json"
                                },
                                said::add,
                                NEVER))
                .as("accounts made on a pool VM would outlive the run")
                .isEqualTo(2);
        assertThat(said).singleElement().asString().contains("--pool");
    }

    @Test
    void keepsThePoolsLocksWhereEveryRunOfTheAccountFindsThem() {
        assertThat(Main.poolState(java.util.Map.of("HOME", "/home/runner")))
                .isEqualTo(java.nio.file.Path.of("/home/runner/.local/state/porelo-machines/pool"));
        assertThat(Main.poolState(java.util.Map.of("HOME", "/h", "XDG_STATE_HOME", "/s")))
                .isEqualTo(java.nio.file.Path.of("/s/porelo-machines/pool"));
    }

    @Test
    void acceptanceOnThePoolWithoutAKeyFailsBeforeConnectingNamingBothWaysToGiveIt(
            @TempDir Path dir) throws IOException {
        final Path pool = writePool(dir);
        assertThatThrownBy(
                        () ->
                                Main.acceptance(
                                        new String[] {
                                            "acceptance",
                                            "--candidate",
                                            "packages",
                                            "--cucumber",
                                            ".",
                                            "--pool",
                                            pool.toString()
                                        },
                                        QUIET,
                                        Map.of(),
                                        NEVER,
                                        (machines, options, credential) -> {
                                            throw new AssertionError(
                                                    "a run without a key must stop before any connection");
                                        }))
                .as("without a run key there is nothing to authenticate to the pool VM with")
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(Main.POOL_SSH)
                .hasMessageContaining("--key");
    }

    @Test
    void acceptanceOnThePoolHandsLocalPoolTheKeyFromTheEnvironmentWhenBothAreSet(@TempDir Path dir)
            throws IOException {
        final Path pool = writePool(dir);
        final Path keyFile = dir.resolve("key");
        Files.writeString(keyFile, "from-a-file");
        Files.setPosixFilePermissions(keyFile, PosixFilePermissions.fromString("rw-------"));
        final List<Credential> handed = new ArrayList<>();

        Main.acceptance(
                new String[] {
                    "acceptance",
                    "--candidate",
                    "packages",
                    "--cucumber",
                    ".",
                    "--pool",
                    pool.toString(),
                    "--key",
                    keyFile.toString()
                },
                QUIET,
                Map.of(
                        Main.POOL_SSH,
                        "from-env",
                        "XDG_STATE_HOME",
                        dir.resolve("state").toString()),
                NEVER,
                (machines, options, credential) -> handed.add(((LocalPool) machines).credential()));

        assertThat(handed)
                .as("CI sets the environment and a checkout on it may also pass --key")
                .singleElement()
                .extracting(
                        each -> {
                            try {
                                return each.material();
                            } catch (IOException ex) {
                                throw new AssertionError(ex);
                            }
                        })
                .isEqualTo("from-env\n");
    }

    private static Path writePool(Path dir) throws IOException {
        final Path file = dir.resolve("pool.json");
        Files.writeString(
                file,
                """
                {"format": "porelo-machines-pool/1",
                 "vms": [{"name": "pool-ubuntu", "os": "ubuntu", "address": "192.0.2.20",
                          "hostKey": "ssh-ed25519 AAAAC3NzaC1lZDI1NTE5AAAAIMTK44JZFt9K/uBv2nRMfOf9qu3wDOBjfHRIj7MRTPl9",
                          "accounts": ["run1"]}]}
                """);
        return file;
    }

    @Test
    void acceptanceWithNeitherAScriptNorScenariosHasNothingToProve() throws IOException {
        final List<String> said = new ArrayList<>();
        assertThat(
                        Main.run(
                                new String[] {"acceptance", "--candidate", "packages"},
                                said::add,
                                NEVER))
                .isEqualTo(2);
        assertThat(said).singleElement().asString().contains("--script or --cucumber");
    }

    @Test
    void acceptanceTakesAccountsForScenariosAndRefusesThemWithoutAny() throws IOException {
        assertThatThrownBy(
                        () ->
                                Main.run(
                                        new String[] {
                                            "acceptance",
                                            "--candidate",
                                            "packages",
                                            "--cucumber",
                                            ".",
                                            "--accounts",
                                            "4"
                                        },
                                        QUIET,
                                        NEVER))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("tried to connect");
        final List<String> said = new ArrayList<>();
        assertThat(
                        Main.run(
                                new String[] {
                                    "acceptance",
                                    "--candidate",
                                    "packages",
                                    "--script",
                                    "a.sh",
                                    "--accounts",
                                    "4"
                                },
                                said::add,
                                NEVER))
                .isEqualTo(2);
        assertThat(
                        Main.run(
                                new String[] {
                                    "acceptance",
                                    "--candidate",
                                    "packages",
                                    "--cucumber",
                                    ".",
                                    "--accounts",
                                    "0"
                                },
                                said::add,
                                NEVER))
                .isEqualTo(2);
        assertThat(said).hasSize(2);
        assertThat(said.get(0)).contains("needs --cucumber");
        assertThat(said.get(1)).contains("from 1 to 99");
    }

    @Test
    void aLeaseTakesACandidateSoItCanRunThePoreloNobodyPushedYet() {
        assertThatThrownBy(
                        () ->
                                Main.run(
                                        new String[] {
                                            "lease", "--os", "ubuntu", "--candidate", "packages"
                                        },
                                        QUIET,
                                        NEVER))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("tried to connect");
    }

    @Test
    void installsMuslWithTheInstallerItCarriesAndPassesItsExitCodeOn() throws IOException {
        // One installer for every repository: a checkout that kept its own copy let the two drift.
        final List<String> ran = new ArrayList<>();

        final int code =
                Main.musl(
                        new String[] {"musl"},
                        QUIET,
                        script -> {
                            ran.add(script);
                            return 3;
                        });

        assertThat(ran)
                .as("the installer this build carries, run once")
                .containsExactly(Snapshots.muslInstaller());
        assertThat(code).as("the installer's own exit code").isEqualTo(3);
    }

    @Test
    void refusesAnOptionTheMuslInstallerDoesNotTake() throws IOException {
        final List<String> said = new ArrayList<>();

        final int code =
                Main.musl(
                        new String[] {"musl", "--prefix", "/opt"},
                        said::add,
                        script -> {
                            throw new AssertionError("refused before anything ran");
                        });

        assertThat(code).isEqualTo(2);
        assertThat(said).singleElement().asString().contains("--prefix").contains("MUSL_PREFIX");
    }

    @Test
    void eachRentingCommandPassesItsKindToTheMachineSource() {
        // A lost kind rents the wrong capacity and may change the snapshot's disk floor.
        for (final String command : List.of("snapshot", "acceptance", "lease")) {
            for (final Kind kind :
                    command.equals("snapshot")
                            ? List.of(Kind.AMD64_SNAPSHOT)
                            : List.of(Kind.values())) {
                final RecordingMachines source = new RecordingMachines();
                final List<String> args =
                        new ArrayList<>(
                                List.of(
                                        command,
                                        "--os",
                                        "ubuntu",
                                        "--kind",
                                        kind.toString(),
                                        "--for",
                                        "42"));
                if (command.equals("acceptance")) {
                    args.addAll(List.of("--package", "porelo-agent", "--cucumber", "."));
                }
                assertThatThrownBy(
                                () ->
                                        Main.run(
                                                args.toArray(String[]::new),
                                                QUIET,
                                                Map.of(Main.SSH_KEY, "test key"),
                                                () -> source))
                        .as("%s reaches acquisition with %s", command, kind)
                        .isInstanceOf(IOException.class)
                        .hasMessage("acquisition recorded");
                assertThat(source.requested).as("%s rents once", command).hasSize(1);
                assertThat(source.requested.getFirst().kind())
                        .as("%s preserves the requested kind", command)
                        .isEqualTo(kind);
                assertThat(source.requested.getFirst().providerOverride())
                        .as("a request by kind has no type override")
                        .isEmpty();
                assertThat(source.limit)
                        .as("%s preserves its cleanup deadline", command)
                        .isEqualTo(Duration.ofMinutes(42));
                assertThat(source.closed)
                        .as("%s closes its source after failure", command)
                        .isTrue();
            }
        }
    }

    @Test
    void defaultsDistinguishSingleAccountsBuildsAndSnapshots() throws IOException {
        for (final String command : List.of("snapshot", "acceptance", "lease")) {
            final RecordingMachines source = new RecordingMachines();
            final List<String> args = new ArrayList<>(List.of(command, "--os", "ubuntu"));
            if (command.equals("acceptance")) {
                args.addAll(List.of("--package", "porelo-agent", "--cucumber", "."));
            }
            assertThatThrownBy(
                            () ->
                                    Main.run(
                                            args.toArray(String[]::new),
                                            QUIET,
                                            Map.of(Main.SSH_KEY, "test key"),
                                            () -> source))
                    .as("%s reaches acquisition", command)
                    .hasMessage("acquisition recorded");
            assertThat(source.requested.getFirst().kind())
                    .as("%s default capacity", command)
                    .isEqualTo(command.equals("snapshot") ? Kind.AMD64_SNAPSHOT : Kind.AMD64);
        }
        final RecordingMachines source = new RecordingMachines();
        final List<AgentLeg.Options> seen = new ArrayList<>();
        Main.acceptance(
                new String[] {
                    "acceptance", "--package", "porelo-agent", "--cucumber", ".", "--accounts", "4"
                },
                QUIET,
                Map.of(Main.SSH_KEY, "test key"),
                () -> source,
                (machines, options, key) -> seen.add(options));
        assertThat(seen.getFirst().kind())
                .as("several accounts need build capacity")
                .isEqualTo(Kind.AMD64_BUILD);
        assertThat(seen.getFirst().accounts()).as("the account count reaches the leg").isEqualTo(4);
    }

    @Test
    void unknownOrConflictingKindsNeverStartRenting() throws IOException {
        for (final String command : List.of("snapshot", "acceptance", "lease")) {
            assertThatThrownBy(
                            () ->
                                    Main.run(
                                            new String[] {command, "--kind", "unknown"},
                                            QUIET,
                                            NEVER))
                    .as("%s refuses an unknown kind before opening a source", command)
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("unknown");
            if (command.equals("snapshot")) {
                for (final Kind reusable : List.of(Kind.AMD64, Kind.AMD64_BUILD)) {
                    final List<String> refused = new ArrayList<>();
                    assertThat(
                                    Main.run(
                                            new String[] {
                                                command,
                                                "--os",
                                                "ubuntu",
                                                "--kind",
                                                reusable.toString()
                                            },
                                            refused::add,
                                            NEVER))
                            .as("snapshot provisioning never opens a source for a reusable kind")
                            .isEqualTo(2);
                    assertThat(refused)
                            .as("the refusal names the required lifecycle")
                            .anySatisfy(
                                    message ->
                                            assertThat(message)
                                                    .as("required kind")
                                                    .contains("amd64-snapshot"));
                }
            }
            final List<String> said = new ArrayList<>();
            final List<String> args =
                    new ArrayList<>(
                            List.of(
                                    command, "--os", "ubuntu", "--kind", "amd64", "--type",
                                    "legacy"));
            if (command.equals("acceptance")) {
                args.addAll(List.of("--package", "porelo-agent", "--cucumber", "."));
            }
            assertThat(Main.run(args.toArray(String[]::new), said::add, NEVER))
                    .as("%s refuses two capacity selections", command)
                    .isEqualTo(2);
            assertThat(said)
                    .as("the refusal names both selections")
                    .anySatisfy(
                            message ->
                                    assertThat(message)
                                            .as("both options")
                                            .contains("--kind", "--type"));
        }
    }

    @Test
    void currentEnvironmentNamesWinAndPreviousNamesRemainFallbacks() throws IOException {
        // Selecting the wrong account or key can rent a machine that cleanup cannot reach.
        assertThat(Main.apiToken(Map.of(Main.API_TOKEN, "current", Main.OLD_API_TOKEN, "previous")))
                .as("the current token wins")
                .isEqualTo("current");
        assertThat(Main.apiToken(Map.of(Main.OLD_API_TOKEN, "previous")))
                .as("a previous caller still authenticates")
                .isEqualTo("previous");
        assertThat(
                        Main.credential(
                                        Map.of(
                                                Main.SSH_KEY,
                                                "current",
                                                Main.OLD_SSH_KEY,
                                                "previous"),
                                        null)
                                .material())
                .as("the current key wins")
                .isEqualTo("current\n");
        assertThat(Main.credential(Map.of(Main.OLD_SSH_KEY, "previous"), null).material())
                .as("a previous caller still connects")
                .isEqualTo("previous\n");
        for (final String blank : List.of("", " ", "\t\n")) {
            assertThat(Main.apiToken(Map.of(Main.API_TOKEN, blank, Main.OLD_API_TOKEN, "previous")))
                    .as("an empty or blank current token falls back to the previous name")
                    .isEqualTo("previous");
            assertThat(
                            Main.credential(
                                            Map.of(
                                                    Main.SSH_KEY,
                                                    blank,
                                                    Main.OLD_SSH_KEY,
                                                    "previous"),
                                            null)
                                    .material())
                    .as("an empty or blank current key falls back to the previous name")
                    .isEqualTo("previous\n");
        }
        for (final Map<String, String> env :
                List.of(
                        Map.<String, String>of(),
                        Map.of(Main.API_TOKEN, "", Main.SSH_KEY, ""),
                        Map.of(Main.API_TOKEN, " ", Main.SSH_KEY, "\t\n"))) {
            assertThatThrownBy(() -> Main.apiToken(env))
                    .as("no nonblank token under either name fails closed")
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining(Main.API_TOKEN)
                    .hasMessageContaining(Main.OLD_API_TOKEN);
            assertThatThrownBy(() -> Main.credential(env, null))
                    .as("no nonblank key under either name fails closed")
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining(Main.SSH_KEY)
                    .hasMessageContaining(Main.OLD_SSH_KEY);
        }
    }

    @Test
    void aTagRunInstallsTheReleaseUnlessItsLegNamesAChannel(@TempDir Path dir) throws IOException {
        final Path pool = writePool(dir);
        final List<String> channels = new ArrayList<>();
        for (final List<String> extra :
                List.of(List.<String>of(), List.of("--channel", "snapshots"))) {
            final List<String> args =
                    new ArrayList<>(
                            List.of(
                                    "acceptance",
                                    "--candidate",
                                    "packages",
                                    "--cucumber",
                                    ".",
                                    "--pool",
                                    pool.toString()));
            args.addAll(extra);
            Main.acceptance(
                    args.toArray(String[]::new),
                    QUIET,
                    Map.of(
                            Main.POOL_SSH,
                            "from-env",
                            "XDG_STATE_HOME",
                            dir.resolve("state").toString(),
                            "GITHUB_REF_TYPE",
                            "tag"),
                    NEVER,
                    (machines, options, credential) -> channels.add(options.channel()));
        }
        assertThat(channels)
                .as("a tag run takes releases, and an explicit --channel wins")
                .containsExactly("releases", "snapshots");
    }

    @Test
    void aBranchRunInstallsTheSnapshotsAndAnUnknownChannelIsRefused() {
        final List<String> said = new ArrayList<>();
        assertThat(Main.channel(null, Map.of("GITHUB_REF_TYPE", "branch"), said::add))
                .isEqualTo("snapshots");
        assertThat(Main.channel(null, Map.of(), said::add)).isEqualTo("snapshots");
        assertThat(Main.channel("nightly", Map.of(), said::add)).isNull();
        assertThat(said).singleElement().asString().contains("snapshots or releases, not nightly");
    }
}
