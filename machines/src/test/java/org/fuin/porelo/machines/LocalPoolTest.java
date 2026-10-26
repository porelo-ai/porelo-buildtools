package org.fuin.porelo.machines;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Tests for {@link LocalPool} and the pool file it reads, without a VM. */
class LocalPoolTest {

    /** A public key line, only ever compared, never connected with. */
    private static final String HOST_KEY =
            "ssh-ed25519 AAAAC3NzaC1lZDI1NTE5AAAAIMTK44JZFt9K/uBv2nRMfOf9qu3wDOBjfHRIj7MRTPl9";

    @TempDir Path dir;

    private final List<String> resets = new ArrayList<>();

    @Test
    void twoRunsAtOnceGetTwoDifferentAccounts() throws IOException {
        final LocalPool pool = pool(Duration.ZERO);
        try (Lease first = pool.acquire(spec());
                Lease second = pool.acquire(spec())) {
            assertThat(List.of(first.name(), second.name()))
                    .as("two runs on one account would see each other's work")
                    .containsExactly("run1@pool-ubuntu", "run2@pool-ubuntu");
        }
    }

    @Test
    void aRunBeyondThePoolsSizeWaitsThenRefusesNamingTheSize() throws IOException {
        final LocalPool pool = pool(Duration.ofMillis(300));
        final List<Lease> held = new ArrayList<>();
        try {
            for (int i = 0; i < 4; i++) {
                held.add(pool.acquire(spec()));
            }
            final long started = System.nanoTime();
            assertThatThrownBy(() -> pool.acquire(spec()))
                    .as("a fifth run on four accounts must not share one")
                    .isInstanceOf(IOException.class)
                    .hasMessageContaining("All 4 accounts")
                    .hasMessageContaining("'ubuntu'");
            assertThat(Duration.ofNanos(System.nanoTime() - started))
                    .as("it waits for an account before it refuses")
                    .isGreaterThanOrEqualTo(Duration.ofMillis(300));
        } finally {
            for (final Lease lease : held) {
                lease.close();
            }
        }
    }

    @Test
    void anAccountHeldByAnotherProcessIsNotHandedOut() throws Exception {
        final Path lock = dir.resolve("state/pool-ubuntu/run1.lock");
        Files.createDirectories(lock.getParent());
        final Path holder = dir.resolve("Holder.java");
        Files.writeString(
                holder,
                """
                public class Holder {
                    public static void main(String[] args) throws Exception {
                        try (var channel = java.nio.channels.FileChannel.open(java.nio.file.Path.of(args[0]),
                                java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.WRITE);
                                var lock = channel.lock()) {
                            System.out.println("held");
                            System.out.flush();
                            Thread.sleep(60_000);
                        }
                    }
                }
                """);
        final ProcessBuilder holding =
                new ProcessBuilder(
                                Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                                holder.toString(),
                                lock.toString())
                        // A JVM announces JAVA_TOOL_OPTIONS on stderr, never in the "held" read.
                        .redirectError(ProcessBuilder.Redirect.INHERIT);
        // The machine's JVM options, -verbose:class say, could write to the stdout read here.
        holding.environment()
                .keySet()
                .removeAll(Set.of("JAVA_TOOL_OPTIONS", "JDK_JAVA_OPTIONS", "_JAVA_OPTIONS"));
        final Process other = holding.start();
        try {
            final String said =
                    new String(other.getInputStream().readNBytes(5), StandardCharsets.UTF_8);
            assertThat(said).as("the other process holds the lock").isEqualTo("held\n");
            try (Lease lease = pool(Duration.ZERO).acquire(spec())) {
                assertThat(lease.name())
                        .as("an account another run holds is skipped")
                        .isEqualTo("run2@pool-ubuntu");
            }
        } finally {
            other.destroyForcibly().waitFor(10, TimeUnit.SECONDS);
        }
    }

    @Test
    void givingAnAccountBackMakesItAnew() throws IOException {
        pool(Duration.ZERO).acquire(spec()).close();
        assertThat(resets)
                .as("the account given back is made anew")
                .containsExactly("pool-ubuntu/run1");
        assertThat(dir.resolve("state/pool-ubuntu/run1.dirty"))
                .as("a reset account is clean")
                .doesNotExist();
    }

    @Test
    void anAccountADeadRunLeftIsMadeAnewBeforeItIsHandedOut() throws IOException {
        final Path dirty = dir.resolve("state/pool-ubuntu/run1.dirty");
        Files.createDirectories(dirty.getParent());
        Files.writeString(dirty, "a run whose process died\n");
        try (Lease lease = pool(Duration.ZERO).acquire(spec())) {
            assertThat(resets)
                    .as("a dead run's account holds that run until it is made anew")
                    .containsExactly("pool-ubuntu/run1");
        }
    }

    @Test
    void aResetThatFailsLeavesTheAccountToBeMadeAnewNextTime() throws IOException {
        final LocalPool pool =
                new LocalPool(
                        Pool.read(poolFile()),
                        Keys.generated(),
                        dir.resolve("state"),
                        (vm, account) -> {
                            throw new IOException("the VM refused");
                        },
                        Duration.ZERO,
                        Duration.ofMillis(10));
        final Lease lease = pool.acquire(spec());
        assertThatThrownBy(lease::close)
                .isInstanceOf(IOException.class)
                .hasMessageContaining("refused");
        assertThat(dir.resolve("state/pool-ubuntu/run1.dirty"))
                .as("an account whose reset failed is still marked")
                .exists();
    }

    @Test
    void anOsThePoolHasNoVmForIsRefusedNamingWhatItHas() throws IOException {
        final LocalPool pool = pool(Duration.ZERO);
        assertThatThrownBy(
                        () ->
                                pool.acquire(
                                        Spec.of(
                                                "x",
                                                "debian",
                                                Kind.AMD64,
                                                "claude",
                                                Keys.generated())))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("'debian'")
                .hasMessageContaining("ubuntu");
    }

    @Test
    void aMissingPoolFileIsRefused() {
        assertThatThrownBy(() -> Pool.read(dir.resolve("absent.json")))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("No pool file");
    }

    @Test
    void aPoolFileOfAnotherFormatIsRefused() throws IOException {
        assertThatThrownBy(() -> Pool.read(write(poolJson().replace("pool/1", "pool/2"))))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("porelo-machines-pool/2");
    }

    @Test
    void aPoolFileNamingAnAccountTwiceIsRefused() throws IOException {
        assertThatThrownBy(() -> Pool.read(write(poolJson().replace("\"run4\"", "\"run1\""))))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("'run1' twice");
    }

    @Test
    void aPoolFileWithoutAFieldIsRefusedNamingIt() throws IOException {
        assertThatThrownBy(() -> Pool.read(write(poolJson().replace("\"address\"", "\"place\""))))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("'address'");
    }

    @Test
    void aPoolFileNamingNoAccountNameIsRefused() throws IOException {
        assertThatThrownBy(() -> Pool.read(write(poolJson().replace("\"run4\"", "\"../root\""))))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("no account name");
    }

    @Test
    void aPoolFileCarryingRunKeyIsRefusedNamingIt() throws IOException {
        assertThatThrownBy(
                        () ->
                                Pool.read(
                                        write(
                                                poolJson()
                                                        .replace(
                                                                "\"vms\":",
                                                                "\"runKey\": \"/k/run\", \"vms\":"))))
                .as("an old host's pool file naming a key file must be refused, not silently used")
                .isInstanceOf(IOException.class)
                .hasMessageContaining("runKey");
    }

    @Test
    void anAccountGivenBackIsNotReadyUntilItsVmSaysItIsMadeAnew() throws IOException {
        final List<String> asked = new ArrayList<>();
        final List<String> states = new ArrayList<>(List.of("resetting", "resetting", "ready"));
        final LocalPool.Reset reset =
                LocalPool.givenBack(
                        (vm, account, command) -> {
                            asked.add(account + ": " + command);
                            return command.startsWith("cat ")
                                    ? new Ssh.Output(states.remove(0) + "\n", "", 0)
                                    : new Ssh.Output("", "", 0);
                        },
                        Duration.ofSeconds(5),
                        Duration.ofMillis(1));
        reset.reset(vm(), "run1");
        assertThat(asked)
                .as("the account gives itself back, then waits for the VM's ready")
                .containsExactly(
                        "run1: sudo -n porelo-pool-give-back",
                        "run1: cat /var/lib/porelo-pool/run1",
                        "run1: cat /var/lib/porelo-pool/run1",
                        "run1: cat /var/lib/porelo-pool/run1");
    }

    @Test
    void anAccountItsVmNeverMakesAnewIsNotHandedOutAgain() {
        final LocalPool.Reset reset =
                LocalPool.givenBack(
                        (vm, account, command) ->
                                command.startsWith("cat ")
                                        ? new Ssh.Output("resetting\n", "", 0)
                                        : new Ssh.Output("", "", 0),
                        Duration.ofMillis(200),
                        Duration.ofMillis(10));
        assertThatThrownBy(() -> reset.reset(vm(), "run1"))
                .as("an account half reset must stay marked, not be handed out")
                .isInstanceOf(IOException.class)
                .hasMessageContaining("did not make run1 anew")
                .hasMessageContaining("resetting");
    }

    @Test
    void aGiveBackTheVmRefusesIsSaid() {
        final LocalPool.Reset reset =
                LocalPool.givenBack(
                        (vm, account, command) ->
                                new Ssh.Output("", "sudo: a password is required", 1),
                        Duration.ofSeconds(1),
                        Duration.ofMillis(10));
        assertThatThrownBy(() -> reset.reset(vm(), "run1"))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("could not give run1 back")
                .hasMessageContaining("password");
    }

    private static Pool.Vm vm() {
        return new Pool.Vm("pool-ubuntu", "ubuntu", "192.0.2.20", HOST_KEY, List.of("run1"));
    }

    private LocalPool pool(Duration patience) throws IOException {
        return new LocalPool(
                Pool.read(poolFile()),
                Keys.generated(),
                dir.resolve("state"),
                (vm, account) -> resets.add(vm.name() + "/" + account),
                patience,
                Duration.ofMillis(10));
    }

    private Path poolFile() throws IOException {
        return write(poolJson());
    }

    private Path write(String json) throws IOException {
        final Path file = dir.resolve("pool.json");
        Files.writeString(file, json);
        return file;
    }

    private static String poolJson() {
        return """
                {"format": "porelo-machines-pool/1",
                 "vms": [{"name": "pool-ubuntu", "os": "ubuntu", "address": "192.0.2.20",
                          "hostKey": "%s", "accounts": ["run1", "run2", "run3", "run4"]}]}
                """
                .formatted(HOST_KEY);
    }

    private static Spec spec() {
        return Spec.of("leg", "ubuntu", Kind.AMD64, "claude", Keys.generated());
    }

    @Test
    void snapshotRequestsAreRefusedBeforeTakingAnyAccount() throws IOException {
        // Taking or resetting an account for a snapshot would disturb the reusable pool.
        final LocalPool source = pool(Duration.ZERO);
        final Spec request =
                Spec.of("image", "ubuntu", Kind.AMD64_SNAPSHOT, "root", Keys.generated());
        assertThatThrownBy(() -> source.acquire(request))
                .as("a pool account cannot supply a boot image")
                .isInstanceOf(IOException.class)
                .hasMessageContaining("amd64-snapshot")
                .hasMessageContaining("existing run accounts");
        assertThatThrownBy(() -> source.acquireFromStock(request, "ubuntu-26.04"))
                .as("stock acquisition has the same refusal")
                .isInstanceOf(IOException.class)
                .hasMessageContaining("amd64-snapshot");
        assertThat(dir.resolve("state")).as("a refused kind takes no pool locks").doesNotExist();
        assertThat(resets).as("a refused kind resets no account").isEmpty();
    }

    @Test
    void eachPoolEntryCanSupplyBuildCapacityWithoutChangingItsFormat() throws IOException {
        try (LocalPool source = pool(Duration.ZERO);
                Lease lease =
                        source.acquire(
                                Spec.of(
                                        "build",
                                        "ubuntu",
                                        Kind.AMD64_BUILD,
                                        "root",
                                        Keys.generated()))) {
            assertThat(lease.address())
                    .as("the existing pool entry meets build capacity")
                    .isEqualTo("192.0.2.20");
            assertThat(lease.asRoot("true"))
                    .as("the build uses the rented account's grant")
                    .contains("sudo");
        }
    }
}
