package org.fuin.porelo.machines;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.security.interfaces.EdECPrivateKey;
import java.util.Arrays;
import java.util.Base64;

/**
 * A host key made for one machine, on the runner that rents it.
 *
 * <p><strong>Made here rather than read off the machine.</strong> A key the machine makes itself is
 * first seen over the very connection it should prove, so taking it then proves nothing. Made here,
 * its public half is known before the machine exists, and its private half reaches the machine only
 * through the provider's API in the server's {@code user_data}.
 *
 * <p><strong>Written in OpenSSH's own format, by hand.</strong> cloud-init writes {@code
 * ed25519_private} to {@code /etc/ssh/ssh_host_ed25519_key} as it is given, and sshd reads that
 * file as {@code openssh-key-v1}; the JDK makes the key but writes only PKCS#8, and no library this
 * module has writes the other. Unencrypted, since sshd has nobody to ask for a passphrase.
 */
final class HostKeyPair {

    private static final String TYPE = "ssh-ed25519";

    /** The DER prefix the JDK puts before the 32 bytes of an Ed25519 public key. */
    private static final byte[] X509_PREFIX = {
        0x30, 0x2a, 0x30, 0x05, 0x06, 0x03, 0x2b, 0x65, 0x70, 0x03, 0x21, 0x00
    };

    /** The block size of the cipher {@code none}, which the private section is padded to. */
    private static final int BLOCK = 8;

    private final byte[] publicKey;

    private final byte[] seed;

    private HostKeyPair(byte[] publicKey, byte[] seed) {
        this.publicKey = publicKey;
        this.seed = seed;
    }

    /**
     * Makes a new pair.
     *
     * @return The pair.
     * @throws IllegalStateException If this JDK cannot make an Ed25519 key, which every JDK since
     *     15 can.
     */
    static HostKeyPair generate() {
        final KeyPair pair;
        try {
            pair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("no Ed25519 to make a host key with", ex);
        }
        final byte[] encoded = pair.getPublic().getEncoded();
        if (encoded.length != X509_PREFIX.length + 32
                || !Arrays.equals(X509_PREFIX, Arrays.copyOf(encoded, X509_PREFIX.length))) {
            throw new IllegalStateException(
                    "the JDK encoded an Ed25519 public key in a form not known here");
        }
        final byte[] seed =
                ((EdECPrivateKey) pair.getPrivate())
                        .getBytes()
                        .orElseThrow(
                                () ->
                                        new IllegalStateException(
                                                "the JDK keeps the Ed25519 key's seed to itself"));
        return new HostKeyPair(
                Arrays.copyOfRange(encoded, X509_PREFIX.length, encoded.length), seed);
    }

    /**
     * Returns the public half as one line of {@code known_hosts} or {@code authorized_keys} takes.
     *
     * @return {@code ssh-ed25519 AAAA...}, without a comment.
     */
    String publicLine() {
        return TYPE + " " + Base64.getEncoder().encodeToString(publicBlob());
    }

    /**
     * Returns the private half as sshd reads a host key file.
     *
     * @return An unencrypted {@code OPENSSH PRIVATE KEY} block, ending in a newline.
     */
    String privateKey() {
        final ByteArrayOutputStream body = new ByteArrayOutputStream();
        body.writeBytes("openssh-key-v1\0".getBytes(StandardCharsets.US_ASCII));
        string(body, "none".getBytes(StandardCharsets.US_ASCII));
        string(body, "none".getBytes(StandardCharsets.US_ASCII));
        string(body, new byte[0]);
        uint32(body, 1);
        string(body, publicBlob());
        string(body, privateSection());
        final String encoded =
                Base64.getMimeEncoder(70, new byte[] {'\n'}).encodeToString(body.toByteArray());
        return "-----BEGIN OPENSSH PRIVATE KEY-----\n"
                + encoded
                + "\n-----END OPENSSH PRIVATE KEY-----\n";
    }

    /** The key, its public half twice and two equal check words, padded to the block size. */
    private byte[] privateSection() {
        final ByteArrayOutputStream section = new ByteArrayOutputStream();
        // The check words say a decryption worked; with no cipher they only have to agree.
        final int check = new SecureRandom().nextInt();
        uint32(section, check);
        uint32(section, check);
        string(section, TYPE.getBytes(StandardCharsets.US_ASCII));
        string(section, publicKey);
        final byte[] both = new byte[seed.length + publicKey.length];
        System.arraycopy(seed, 0, both, 0, seed.length);
        System.arraycopy(publicKey, 0, both, seed.length, publicKey.length);
        string(section, both);
        string(section, new byte[0]);
        for (int pad = 1; section.size() % BLOCK != 0; pad++) {
            section.write(pad);
        }
        return section.toByteArray();
    }

    private byte[] publicBlob() {
        final ByteArrayOutputStream blob = new ByteArrayOutputStream();
        string(blob, TYPE.getBytes(StandardCharsets.US_ASCII));
        string(blob, publicKey);
        return blob.toByteArray();
    }

    private static void string(ByteArrayOutputStream out, byte[] value) {
        uint32(out, value.length);
        out.writeBytes(value);
    }

    private static void uint32(ByteArrayOutputStream out, int value) {
        out.writeBytes(ByteBuffer.allocate(4).putInt(value).array());
    }

    /** Says what it is without the private half, so a log line cannot carry it. */
    @Override
    public String toString() {
        return "HostKeyPair[" + publicLine() + ", private half redacted]";
    }
}
