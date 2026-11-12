package org.fuin.porelo.machines;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;

/**
 * A machine reached without a network, and the clock its waits read: each sleep moves the clock at
 * once, and each answer is the test's.
 */
final class FakeMachine implements SshDialer, Waiting, RemoteSession {

    /** Every command run, in order, with its standard input after a tab where it had one. */
    final List<String> commands = new ArrayList<>();

    /** Every pause taken. */
    final List<Duration> sleeps = new ArrayList<>();

    /** What each probe answers, in order; once empty, the last answer repeats. */
    final Deque<Object> probes = new ArrayDeque<>();

    /**
     * What each reconnect does, in order: null connects, an exception is thrown; once empty,
     * connects.
     */
    final Deque<@Nullable IOException> reconnects = new ArrayDeque<>();

    /** What a command answers. */
    Function<String, Ssh.Output> answer = command -> new Ssh.Output("", "", 0);

    int opened;

    /** Whether each command goes into {@link #commands} after the user of its connection. */
    boolean markUsers;

    /** What giving the machine back also does, for a test that orders it among other calls. */
    Runnable onRelease = () -> {};

    /** Who each connection was opened as, in order. */
    final List<String> users = new ArrayList<>();

    int disconnects;

    int closes;

    /** How often the machine was given back. */
    int releases;

    final List<String> uploads = new ArrayList<>();

    /** One host key and one login key for every fake, as making one takes a while. */
    private static final String HOST_KEY = HostKeyPair.generate().publicLine();

    static final Credential KEY = Keys.generated();

    private Instant now = Instant.parse("2026-11-12T00:00:00Z");

    private Object lastProbe = Boolean.FALSE;

    /** A lease of this machine, as root, with five minutes' patience for sshd. */
    Lease lease(Spec spec) {
        return new Lease(
                77,
                spec.name(),
                "192.0.2.7",
                spec,
                HostKey.pinned(HOST_KEY),
                () -> {
                    releases++;
                    onRelease.run();
                },
                Duration.ofMinutes(5),
                this,
                this);
    }

    /** A spec as root, the way a rental asks. */
    static Spec root() {
        return as("root");
    }

    /** A spec for another user. */
    static Spec as(String user) {
        return new Spec("m", "ubuntu", Kind.AMD64, user, KEY, true, 1, List.of());
    }

    @Override
    public RemoteSession open(String address, String user, Credential credential, HostKey hostKey) {
        opened++;
        users.add(user);
        return new Connection(user);
    }

    @Override
    public boolean reachable(String address, String user, Credential credential, HostKey hostKey)
            throws HostKey.Refused {
        if (!probes.isEmpty()) {
            lastProbe = probes.poll();
        }
        if (lastProbe instanceof HostKey.Refused refused) {
            throw refused;
        }
        return (Boolean) lastProbe;
    }

    @Override
    public Instant now() {
        return now;
    }

    @Override
    public void sleep(Duration duration) {
        sleeps.add(duration);
        now = now.plus(duration);
    }

    @Override
    public Ssh.Output run(String command, @Nullable String stdin) throws IOException {
        commands.add(stdin == null ? command : command + "\t" + stdin);
        return answer.apply(command);
    }

    @Override
    public void upload(Path local, String remote) {
        uploads.add(local.getFileName() + " -> " + remote);
    }

    @Override
    public void download(String remote, Path local) {
        throw new UnsupportedOperationException("no test downloads");
    }

    @Override
    public void disconnect() {
        disconnects++;
    }

    @Override
    public void reconnect() throws IOException {
        final IOException fails = reconnects.poll();
        if (fails != null) {
            throw fails;
        }
    }

    @Override
    public void close() {
        closes++;
    }

    /** One connection, as one user: its commands go to the machine, named by that user. */
    private final class Connection implements RemoteSession {

        private final String user;

        Connection(String user) {
            this.user = user;
        }

        @Override
        public Ssh.Output run(String command, @Nullable String stdin) throws IOException {
            return FakeMachine.this.run(markUsers ? user + ": " + command : command, stdin);
        }

        @Override
        public void upload(Path local, String remote) {
            FakeMachine.this.upload(local, remote);
        }

        @Override
        public void download(String remote, Path local) {
            FakeMachine.this.download(remote, local);
        }

        @Override
        public void disconnect() {
            FakeMachine.this.disconnect();
        }

        @Override
        public void reconnect() throws IOException {
            FakeMachine.this.reconnect();
        }

        @Override
        public void close() {
            FakeMachine.this.close();
        }
    }
}
