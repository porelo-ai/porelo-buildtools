package org.fuin.porelo.machines;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * A run account of the host's local VM pool, rented for one run and clean again when given back.
 *
 * <p><strong>Held by a file lock, which a dead process gives up by itself.</strong> Each account
 * has a lock file under the state directory; whoever holds the lock holds the account. A run that
 * is killed releases its lock with its process, so nothing has to be swept - but the account it
 * held is still full of that run. So acquiring writes a {@code dirty} marker beside the lock, the
 * reset removes it, and an account found with the marker is reset before it is handed out.
 *
 * <p><strong>The reset is the operator's choice (c)</strong>: each VM holds one run account, and a
 * VM given back goes back to its base snapshot on the host, so nothing a run did - as root through
 * the package manager too - reaches the next run. <strong>The account gives itself back</strong>,
 * with the one root-owned command it may run, so no key that reaches the host or another VM is in a
 * job: the builds that rent from the pool include task branches nobody reviewed.
 */
public final class LocalPool implements Machines {

    /** Makes a run account clean again. */
    @FunctionalInterface
    public interface Reset {

        /**
         * Makes the account clean again.
         *
         * @param vm The VM it is on.
         * @param account The account.
         * @throws IOException If the VM refused or could not be reached.
         */
        void reset(Pool.Vm vm, String account) throws IOException;
    }

    /** What an account runs to have its VM make it anew; porelo-host installs it. */
    static final String GIVE_BACK = "sudo -n porelo-pool-give-back";

    /** Where each VM says how far an account's reset is: {@code resetting}, then {@code ready}. */
    static final String STATE = "/var/lib/porelo-pool/";

    /** Runs one command as a run account on its VM. */
    @FunctionalInterface
    public interface AsAccount {

        /**
         * Runs the command over a connection of its own.
         *
         * @param vm The VM.
         * @param account The account.
         * @param command The command.
         * @return What it said.
         * @throws IOException If the VM could not be reached.
         */
        Ssh.Output run(Pool.Vm vm, String account, String command) throws IOException;
    }

    /** How long a VM may take to make an account anew. */
    private static final Duration RESET_PATIENCE = Duration.ofMinutes(2);

    private final Pool pool;

    private final Credential credential;

    private final Path state;

    private final Reset reset;

    private final Duration patience;

    private final Duration interval;

    /**
     * Constructor with everything it needs.
     *
     * @param pool The pool.
     * @param credential The run accounts' key.
     * @param state Where the accounts' locks and markers are kept.
     * @param reset How an account is made clean again.
     * @param patience How long to wait for a free account.
     * @param interval How long between looks while every account is held.
     */
    public LocalPool(
            Pool pool,
            Credential credential,
            Path state,
            Reset reset,
            Duration patience,
            Duration interval) {
        this.pool = pool;
        this.credential = credential;
        this.state = state;
        this.reset = reset;
        this.patience = patience;
        this.interval = interval;
    }

    /**
     * Returns a pool on this host, each account giving itself back over ssh.
     *
     * @param pool The pool.
     * @param credential The run accounts' key.
     * @param state Where the accounts' locks and markers are kept.
     * @param patience How long to wait for a free account.
     * @return The pool.
     */
    public static LocalPool onThisHost(
            Pool pool, Credential credential, Path state, Duration patience) {
        return new LocalPool(
                pool,
                credential,
                state,
                givenBack(overSsh(credential), RESET_PATIENCE, Duration.ofSeconds(3)),
                patience,
                Duration.ofSeconds(5));
    }

    @Override
    public Lease acquire(Spec spec) throws IOException {
        if (spec.kind() == Kind.AMD64_SNAPSHOT) {
            throw new IOException(
                    "The local VM pool supplies existing run accounts; amd64-snapshot needs a newly created machine whose disk can become a boot image.");
        }
        final List<Pool.Vm> vms =
                pool.vms().stream().filter(vm -> vm.os().equals(spec.os())).toList();
        if (vms.isEmpty()) {
            throw new IOException(
                    "The local pool has no VM for '"
                            + spec.os()
                            + "'. It has: "
                            + pool.vms().stream().map(Pool.Vm::os).distinct().toList()
                            + ".");
        }
        final Instant deadline = Instant.now().plus(patience);
        while (true) {
            for (final Pool.Vm vm : vms) {
                for (final String account : vm.accounts()) {
                    final Lease lease = tryAccount(spec, vm, account);
                    if (lease != null) {
                        return lease;
                    }
                }
            }
            if (Instant.now().isAfter(deadline)) {
                throw new IOException(
                        "All "
                                + pool.size(spec.os())
                                + " accounts of the local pool for '"
                                + spec.os()
                                + "' were held for "
                                + patience.toSeconds()
                                + " s; the pool's size is the limit.");
            }
            try {
                Thread.sleep(interval.toMillis());
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new IOException("interrupted waiting for an account of the local pool", ex);
            }
        }
    }

    /**
     * Takes an account if nobody holds it, resetting it first if a dead run left it.
     *
     * @param spec What was asked for.
     * @param vm The VM.
     * @param account The account.
     * @return The lease, or {@code null} when somebody holds the account.
     * @throws IOException If its lock cannot be made or its reset fails.
     */
    private @Nullable Lease tryAccount(Spec spec, Pool.Vm vm, String account) throws IOException {
        final Path directory = state.resolve(vm.name());
        Files.createDirectories(directory);
        final FileChannel channel =
                FileChannel.open(
                        directory.resolve(account + ".lock"),
                        StandardOpenOption.CREATE,
                        StandardOpenOption.WRITE);
        final FileLock lock;
        try {
            lock = channel.tryLock();
        } catch (OverlappingFileLockException ex) {
            // Held by this process already, under another lease.
            channel.close();
            return null;
        }
        if (lock == null) {
            channel.close();
            return null;
        }
        final Path dirty = directory.resolve(account + ".dirty");
        try {
            if (Files.exists(dirty)) {
                System.out.println(
                        "resetting " + account + " on " + vm.name() + ", which a run left behind");
                reset.reset(vm, account);
                Files.delete(dirty);
            }
            Files.writeString(dirty, spec.name() + "\n");
        } catch (IOException | RuntimeException ex) {
            channel.close();
            throw ex;
        }
        System.out.println("using    " + account + " on " + vm.name() + " at " + vm.address());
        return new Lease(
                0,
                account + "@" + vm.name(),
                vm.address(),
                new Spec(spec.name(), spec.os(), spec.kind(), account, credential, spec.keep()),
                HostKey.pinned(vm.hostKey()),
                () -> {
                    try {
                        reset.reset(vm, account);
                        Files.delete(dirty);
                    } finally {
                        channel.close();
                    }
                });
    }

    @Override
    public Lease acquireFromStock(Spec spec, String image) throws IOException {
        // A pool account is on a VM that was installed once; a leg that needs a particular starting
        // point has to rent one.
        System.out.println(
                "(ignoring the stock image '" + image + "': a pool account is already installed)");
        return acquire(spec);
    }

    @Override
    public String runId() {
        return "local-pool";
    }

    /**
     * Returns the key the pool's run accounts connect with.
     *
     * @return The key.
     */
    Credential credential() {
        return credential;
    }

    @Override
    public void close() {
        // Each lease gives its account back; nothing is held beyond them.
    }

    /**
     * Makes an account anew by having it give itself back, and waits until its VM says it is.
     *
     * <p><strong>Not ready until the VM says so.</strong> The give-back answers before the reset
     * runs, since the reset ends every process of the account, the connection that asked included;
     * the VM writes {@code resetting} before it answers and {@code ready} when the account is made
     * anew, so an account is never handed out half reset.
     *
     * @param as How a command runs as the account.
     * @param patience How long the VM may take.
     * @param interval How long between looks.
     * @return The reset.
     */
    static Reset givenBack(AsAccount as, Duration patience, Duration interval) {
        return (vm, account) -> {
            final Ssh.Output given = as.run(vm, account, GIVE_BACK);
            if (given.status() != 0) {
                throw new IOException(
                        "could not give " + account + " back on " + vm.name() + ": " + given.all());
            }
            final Instant deadline = Instant.now().plus(patience);
            String said = "";
            while (Instant.now().isBefore(deadline)) {
                try {
                    said = as.run(vm, account, "cat " + STATE + account).out().strip();
                    if ("ready".equals(said)) {
                        return;
                    }
                } catch (IOException ex) {
                    // The VM is down while it goes back to its snapshot, and answers once it is.
                    said = ex.getMessage();
                }
                try {
                    Thread.sleep(interval.toMillis());
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    throw new IOException(
                            "interrupted waiting for " + account + " to be made anew", ex);
                }
            }
            throw new IOException(
                    vm.name()
                            + " did not make "
                            + account
                            + " anew within "
                            + patience.toSeconds()
                            + " s; it last said: "
                            + said);
        };
    }

    private static AsAccount overSsh(Credential key) {
        return (vm, account, command) -> {
            try (Ssh ssh = Ssh.to(vm.address(), account, key, HostKey.pinned(vm.hostKey()))) {
                return ssh.run(command);
            }
        };
    }
}
