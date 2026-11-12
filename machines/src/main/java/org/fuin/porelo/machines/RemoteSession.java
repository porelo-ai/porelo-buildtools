package org.fuin.porelo.machines;

import java.io.IOException;
import java.nio.file.Path;
import org.jspecify.annotations.Nullable;

/**
 * An open connection to a machine: what a lease, a rental and a leg do on it, apart from how it is
 * reached, so their flows are proven over a fake and only {@link Ssh} needs a machine.
 */
public interface RemoteSession extends AutoCloseable {

    /**
     * Runs a command with no terminal, the way a script would.
     *
     * @param command What to run.
     * @return What it wrote and what it exited with.
     * @throws IOException If the command cannot be run.
     */
    default Ssh.Output run(String command) throws IOException {
        return run(command, null);
    }

    /**
     * Runs a command with no terminal and feeds it standard input, the way a secret reaches a
     * machine.
     *
     * @param command What to run.
     * @param stdin What to feed it, or {@code null} for nothing.
     * @return What it wrote and what it exited with.
     * @throws IOException If the command cannot be run.
     */
    Ssh.Output run(String command, @Nullable String stdin) throws IOException;

    /**
     * Copies a local file to the machine.
     *
     * @param local What to send.
     * @param remote Where to put it, an absolute path.
     * @throws IOException If it cannot be sent.
     */
    void upload(Path local, String remote) throws IOException;

    /**
     * Copies a file from the machine.
     *
     * @param remote What to fetch, an absolute path.
     * @param local Where to put it.
     * @throws IOException If it cannot be fetched.
     */
    void download(String remote, Path local) throws IOException;

    /** Drops the connection, for a restart that is about to take it away anyway. */
    void disconnect();

    /**
     * Connects again after a restart.
     *
     * @throws IOException If the machine cannot be reached.
     */
    void reconnect() throws IOException;

    @Override
    void close() throws IOException;
}
