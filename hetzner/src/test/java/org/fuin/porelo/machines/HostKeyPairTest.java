package org.fuin.porelo.machines;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import net.schmizz.sshj.SSHClient;
import net.schmizz.sshj.common.Buffer;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link HostKeyPair} and the cloud-config that carries it: a key sshd cannot read, or
 * one beside keys nobody made, leaves a machine every leg refuses.
 */
class HostKeyPairTest {

    /**
     * Returns the public line of the key a private key holds, as an ssh client reads that key.
     *
     * @param privateKey An OpenSSH private key.
     * @return Its public half, as {@code ssh-ed25519 AAAA...}.
     */
    static String publicLineOf(String privateKey) throws IOException {
        try (SSHClient reader = new SSHClient()) {
            final byte[] blob =
                    new Buffer.PlainBuffer()
                            .putPublicKey(reader.loadKeys(privateKey, null, null).getPublic())
                            .getCompactData();
            return "ssh-ed25519 " + java.util.Base64.getEncoder().encodeToString(blob);
        }
    }

    @Test
    void writesAPrivateKeyAnSshClientReadsBackToTheSamePublicKey() throws IOException {
        final HostKeyPair pair = HostKeyPair.generate();

        final String privateKey = pair.privateKey();

        assertThat(privateKey)
                .as("the format cloud-init writes and sshd reads as it is")
                .startsWith("-----BEGIN OPENSSH PRIVATE KEY-----\n")
                .endsWith("\n-----END OPENSSH PRIVATE KEY-----\n");
        assertThat(publicLineOf(privateKey))
                .as("the private key read back holds the public key that is pinned")
                .isEqualTo(pair.publicLine());
        // The file holds the public key beside the private one, and a reader may take it from
        // there: only a signature shows the private half is the one that belongs to it, which is
        // what sshd proves the key with.
        assertThat(signsForThePinnedKey(privateKey, pair.publicLine()))
                .as("what the private key signs, the pinned public key verifies")
                .isTrue();
    }

    private static boolean signsForThePinnedKey(String privateKey, String publicLine)
            throws IOException {
        final byte[] data = "the exchange hash".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        final net.schmizz.sshj.signature.Signature signer =
                new com.hierynomus.sshj.signature.SignatureEdDSA.Factory().create();
        try (SSHClient reader = new SSHClient()) {
            signer.initSign(reader.loadKeys(privateKey, null, null).getPrivate());
        }
        signer.update(data);
        final byte[] signature =
                new Buffer.PlainBuffer()
                        .putString("ssh-ed25519")
                        .putBytes(signer.sign())
                        .getCompactData();
        final net.schmizz.sshj.signature.Signature verifier =
                new com.hierynomus.sshj.signature.SignatureEdDSA.Factory().create();
        try {
            verifier.initVerify(
                    new Buffer.PlainBuffer(
                                    java.util.Base64.getDecoder()
                                            .decode(
                                                    publicLine.substring(
                                                            publicLine.indexOf(' ') + 1)))
                            .readPublicKey());
        } catch (Buffer.BufferException ex) {
            throw new IOException(ex);
        }
        verifier.update(data);
        return verifier.verify(signature);
    }

    @Test
    void makesADifferentKeyEachTime() {
        assertThat(HostKeyPair.generate().publicLine())
                .as("a key per machine, so one machine's key says nothing about another")
                .isNotEqualTo(HostKeyPair.generate().publicLine());
    }

    @Test
    void neverShowsThePrivateHalfWhenPrinted() {
        final HostKeyPair pair = HostKeyPair.generate();

        assertThat(pair.toString())
                .as("a log line or a failed assertion carries no private key")
                .doesNotContain("PRIVATE KEY")
                .doesNotContain(pair.privateKey().lines().skip(1).findFirst().orElseThrow());
    }

    @Test
    void givesTheMachineThatOneKeyTurnsOffTheGeneratedOnesAndConfiguresNothingElse()
            throws IOException {
        final HostKeyPair pair = HostKeyPair.generate();

        final List<String> lines = Hetzner.userData(pair).lines().toList();

        assertThat(lines)
                .as("cloud-init reads only a file that says what it is")
                .first()
                .isEqualTo("#cloud-config");
        assertThat(indentedBy(lines, 0))
                .as("what the cloud-config sets, and nothing else")
                .containsExactly("ssh_deletekeys: true", "ssh_genkeytypes: []", "ssh_keys:");
        final List<String> keys = indentedBy(lines, 2);
        assertThat(keys)
                .as("the one pair, and no key of another type")
                .containsExactly("ed25519_private: |", "ed25519_public: " + pair.publicLine());
        assertThat(publicLineOf(String.join("\n", indentedBy(lines, 4)) + "\n"))
                .as("the private key in the block is the pair's")
                .isEqualTo(pair.publicLine());
    }

    /** The lines indented by exactly so many spaces, without the indentation. */
    private static List<String> indentedBy(List<String> lines, int spaces) {
        final List<String> found = new ArrayList<>();
        for (final String line : lines) {
            if (line.startsWith("#")) {
                continue;
            }
            final int indent = line.length() - line.stripLeading().length();
            if (indent == spaces) {
                found.add(line.strip());
            }
        }
        return found;
    }
}
