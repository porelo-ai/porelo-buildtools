package org.fuin.porelo.machines;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;

/**
 * Which host key a connection accepts, said by every caller rather than defaulted.
 *
 * <p><strong>Two answers, and the second is a written risk.</strong> A rented machine is created
 * with a host key made for it in the same run, so a connection to it accepts that key and nothing
 * else: whoever sits on the path between the runner and the provider could otherwise pose as the
 * machine, take what a leg sends, and answer a leg as passed. A machine that is not rented - a
 * local virtual machine on this host's own bridge - has no key anybody made for it, and accepting
 * whatever it shows is the accepted risk in {@code doc/decisions.md}; the caller names its reason.
 */
public sealed interface HostKey {

    /**
     * Returns the one key to accept.
     *
     * @param line An OpenSSH public key line, such as {@code ssh-ed25519 AAAA...}, a comment after
     *     it allowed.
     * @return The pinned key.
     * @throws IllegalArgumentException If the line is not a public key.
     */
    static HostKey pinned(String line) {
        return new Pinned(line);
    }

    /**
     * Returns an answer that accepts any key, for a machine nobody made a key for.
     *
     * @param reason Why that is acceptable here, for whoever reads the call.
     * @return The answer.
     * @throws IllegalArgumentException If no reason is given.
     */
    static HostKey any(String reason) {
        return new Any(reason);
    }

    /**
     * The one key a machine may show.
     *
     * @param line Its OpenSSH public key line, type and key without the comment.
     */
    record Pinned(String line) implements HostKey {

        /**
         * Checks the line and drops a comment after the key.
         *
         * @param line An OpenSSH public key line.
         */
        public Pinned {
            final String[] parts = line == null ? new String[0] : line.strip().split("\\s+");
            if (parts.length < 2) {
                throw new IllegalArgumentException(
                        "'" + line + "' is not an OpenSSH public key line: type, then the key");
            }
            final byte[] blob = decoded(parts[1]);
            // The type inside the encoding is what the server's key is compared on, so a line whose
            // two halves disagree would pin a key nobody can show.
            if (!parts[0].equals(typeOf(blob))) {
                throw new IllegalArgumentException(
                        "the line names " + parts[0] + " but its key is " + typeOf(blob));
            }
            line = parts[0] + " " + parts[1];
        }

        /**
         * Returns the key's SSH wire encoding, which is what a server's key is compared on.
         *
         * @return The encoding, a fresh copy.
         */
        public byte[] blob() {
            return Base64.getDecoder().decode(line.substring(line.indexOf(' ') + 1));
        }

        /**
         * Tells whether a key shown is this one.
         *
         * @param shown The wire encoding of what the server showed.
         * @return Whether they are the same key.
         */
        boolean matches(byte[] shown) {
            return Arrays.equals(blob(), shown);
        }

        private static byte[] decoded(String key) {
            try {
                return Base64.getDecoder().decode(key);
            } catch (IllegalArgumentException ex) {
                throw new IllegalArgumentException(
                        "'" + key + "' is not a base64 key: " + ex.getMessage(), ex);
            }
        }

        private static String typeOf(byte[] blob) {
            if (blob.length < 4) {
                return "nothing";
            }
            final int length = ByteBuffer.wrap(blob).getInt();
            if (length < 1 || length > blob.length - 4) {
                return "nothing";
            }
            return new String(blob, 4, length, StandardCharsets.US_ASCII);
        }
    }

    /**
     * Any key the machine shows.
     *
     * @param reason Why that is acceptable for this machine.
     */
    record Any(String reason) implements HostKey {

        /**
         * Refuses an answer without its reason.
         *
         * @param reason Why that is acceptable.
         */
        public Any {
            if (reason == null || reason.isBlank()) {
                throw new IllegalArgumentException(
                        "Accepting any host key needs a reason; a rented machine pins its own.");
            }
        }
    }

    /** A machine that showed another key than the one it was made with. */
    final class Refused extends IOException {

        private static final long serialVersionUID = 1L;

        /**
         * Constructor with what was shown.
         *
         * @param host Where the connection went.
         * @param fingerprint The SHA-256 fingerprint of the key it showed.
         */
        Refused(String host, String fingerprint) {
            super(
                    host
                            + " showed host key "
                            + fingerprint
                            + ", not the one it was created with; refusing to connect");
        }
    }
}
