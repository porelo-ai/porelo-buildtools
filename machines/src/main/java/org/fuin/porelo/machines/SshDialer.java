package org.fuin.porelo.machines;

import java.io.IOException;

/** How a machine is reached: by {@link Ssh} on a real one, by a fake in a test. */
interface SshDialer {

    /** Reaches machines over SSH. */
    SshDialer SSH =
            new SshDialer() {
                @Override
                public RemoteSession open(
                        String address, String user, Credential credential, HostKey hostKey)
                        throws IOException {
                    return Ssh.to(address, user, credential, hostKey);
                }

                @Override
                public boolean reachable(
                        String address, String user, Credential credential, HostKey hostKey)
                        throws HostKey.Refused {
                    return Ssh.reachable(address, user, credential, hostKey);
                }
            };

    /**
     * Opens a connection.
     *
     * @param address Where the machine is.
     * @param user Who to log in as.
     * @param credential The key to log in with.
     * @param hostKey Which host key to accept.
     * @return The open connection.
     * @throws IOException If it cannot be opened.
     */
    RemoteSession open(String address, String user, Credential credential, HostKey hostKey)
            throws IOException;

    /**
     * Says whether the machine answers and accepts the key, once.
     *
     * @param address Where the machine is.
     * @param user Who to log in as.
     * @param credential The key to log in with.
     * @param hostKey Which host key to accept.
     * @return {@code true} if it is up and the key works.
     * @throws HostKey.Refused If it showed another host key than the one pinned.
     */
    boolean reachable(String address, String user, Credential credential, HostKey hostKey)
            throws HostKey.Refused;
}
