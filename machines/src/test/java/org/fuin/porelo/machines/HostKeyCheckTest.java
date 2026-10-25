package org.fuin.porelo.machines;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.security.PublicKey;
import java.util.Base64;
import net.schmizz.sshj.common.Buffer;
import org.junit.jupiter.api.Test;

/** Tests for {@link HostKeyCheck}: what stands between a leg and somebody posing as its machine. */
class HostKeyCheckTest {

    /** The ed25519 key {@code FingerprintTest} knows, as the key a machine shows. */
    private static final String SHOWN =
            "AAAAC3NzaC1lZDI1NTE5AAAAIMh8XLtV5WSoFlG7vzDFViJQ5vMwxMqBC9gd69pscNpC";

    /** What {@code ssh-keygen -lf} says about {@link #SHOWN}, recorded as the reference answer. */
    private static final String SHOWN_FINGERPRINT =
            "SHA256:m40rQBoiPilLMRGi1kPcAPxlYztsGHCPJBY6LtvZ0Ik";

    private static PublicKey shown() throws Buffer.BufferException {
        return new Buffer.PlainBuffer(Base64.getDecoder().decode(SHOWN)).readPublicKey();
    }

    @Test
    void refusesAKeyOtherThanThePinnedOneNamingTheHostAndWhatItShowed() throws Exception {
        final HostKey made = HostKey.pinned(HostKeyPair.generate().publicLine());
        final HostKeyCheck check = new HostKeyCheck("192.0.2.7", made);

        final boolean accepted = check.verify("192.0.2.7", 22, shown());

        assertThat(accepted)
                .as("a machine showing another key than the one made for it is refused")
                .isFalse();
        final IOException refusal = check.refusal();
        assertThat(refusal)
                .as("the refusal is kept, to be thrown in place of sshj's own")
                .isNotNull()
                .hasMessageContaining("192.0.2.7")
                .hasMessageContaining(SHOWN_FINGERPRINT);
    }

    @Test
    void acceptsThePinnedKeyAndRefusesNothing() throws Exception {
        final HostKeyCheck check =
                new HostKeyCheck(
                        "192.0.2.7", HostKey.pinned("ssh-ed25519 " + SHOWN + " a comment"));

        assertThat(check.verify("192.0.2.7", 22, shown()))
                .as("the key the machine was made with is accepted")
                .isTrue();
        assertThat(check.refusal()).as("nothing was refused").isNull();
    }

    @Test
    void asksForThePinnedKeysAlgorithmSoTheServerOffersThatKey() {
        // A server holding host keys of several types shows the one of the algorithm both sides
        // prefer, so the client asks for the pinned key's type first.
        final HostKeyCheck check =
                new HostKeyCheck("192.0.2.7", HostKey.pinned("ssh-ed25519 " + SHOWN));

        assertThat(check.findExistingAlgorithms("192.0.2.7", 22))
                .as("the algorithm asked for first")
                .containsExactly("ssh-ed25519");
    }

    @Test
    void acceptsAnyKeyOnlyWhereTheCallerSaidSo() throws Exception {
        final HostKeyCheck check =
                new HostKeyCheck("192.168.122.10", HostKey.any("a local virtual machine"));

        assertThat(check.verify("192.168.122.10", 22, shown()))
                .as("a caller that named its reason accepts what the machine shows")
                .isTrue();
    }
}
