package org.fuin.porelo.machines;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

/**
 * A throwaway key, made once a run.
 *
 * <p>Generated rather than checked in: a private key in a repository is a finding in every scanner
 * that looks at one, and explaining that this particular one is harmless costs more than making a
 * new one each run. One per run, not per test: an RSA key takes a tenth of a second or more to
 * make, and no test needs two that differ.
 */
final class Keys {

    /** Made when first asked for. */
    private static final class Made {
        static final Credential KEY = make();
    }

    private Keys() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Returns this run's key, in the format an ssh client reads.
     *
     * @return The key.
     */
    static Credential generated() {
        return Made.KEY;
    }

    private static Credential make() {
        final KeyPairGenerator generator;
        try {
            generator = KeyPairGenerator.getInstance("RSA");
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("no RSA to make a test key with", ex);
        }
        generator.initialize(2048);
        final KeyPair pair = generator.generateKeyPair();
        final String encoded =
                Base64.getMimeEncoder(64, new byte[] {'\n'})
                        .encodeToString(pair.getPrivate().getEncoded());
        return new Credential.InMemory(
                "-----BEGIN PRIVATE KEY-----\n" + encoded + "\n-----END PRIVATE KEY-----");
    }
}
