package org.fuin.porelo.machines;

import java.security.PublicKey;
import java.util.List;
import net.schmizz.sshj.common.Buffer;
import net.schmizz.sshj.transport.verification.HostKeyVerifier;
import org.jspecify.annotations.Nullable;

/**
 * The verifier one connection is made with, holding why it refused.
 *
 * <p>sshj turns a refusal into a {@code TransportException} of its own that names an MD5
 * fingerprint and no reason, so the refusal is kept here and thrown in its place.
 */
final class HostKeyCheck implements HostKeyVerifier {

    private final String host;

    private final HostKey expected;

    private volatile HostKey.@Nullable Refused refusal;

    /**
     * Constructor with what to accept.
     *
     * @param host Where the connection goes, for the refusal.
     * @param expected What to accept there.
     */
    HostKeyCheck(String host, HostKey expected) {
        this.host = host;
        this.expected = expected;
    }

    @Override
    public boolean verify(String hostname, int port, PublicKey key) {
        if (!(expected instanceof HostKey.Pinned pinned)) {
            return true;
        }
        final byte[] shown = new Buffer.PlainBuffer().putPublicKey(key).getCompactData();
        if (pinned.matches(shown)) {
            return true;
        }
        refusal = new HostKey.Refused(host, Fingerprint.sha256(shown));
        return false;
    }

    /** The pinned key's type, so a server holding keys of several types shows that one. */
    @Override
    public List<String> findExistingAlgorithms(String hostname, int port) {
        return expected instanceof HostKey.Pinned pinned
                ? List.of(pinned.line().substring(0, pinned.line().indexOf(' ')))
                : List.of();
    }

    /**
     * Returns why the key shown was refused.
     *
     * @return The refusal, or {@code null} when nothing was refused.
     */
    HostKey.@Nullable Refused refusal() {
        return refusal;
    }
}
