package org.fuin.porelo.release;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Refuses provider names and server types outside their implementation. */
final class CheckProviders {

    private static final Pattern PROVIDER =
            Pattern.compile(
                    "\\bhetzner\\b|\\bhcloud\\b|HETZNER_API|HETZNER_SSH|HETZNER_KEY"
                            + "|(?<!#)\\b(cpx|ccx|cax)\\d{2}\\b|(?<!#)\\bcx[2-5]\\d\\b",
                    Pattern.CASE_INSENSITIVE);

    private static final Set<String> EXEMPT =
            Set.of(
                    "machines/src/main/java/org/fuin/porelo/machines/Hetzner.java",
                    "machines/src/main/java/org/fuin/porelo/machines/CentralSetup.java",
                    "machines/src/main/java/org/fuin/porelo/machines/Api.java",
                    "machines/src/main/java/org/fuin/porelo/machines/Fingerprint.java",
                    "machines/src/test/java/org/fuin/porelo/machines/HetznerTest.java",
                    "machines/src/test/java/org/fuin/porelo/machines/StubApi.java",
                    "machines/src/test/java/org/fuin/porelo/machines/FingerprintTest.java",
                    "machines/src/test/java/org/fuin/porelo/machines/HostKeyPairTest.java",
                    "release/src/main/java/org/fuin/porelo/release/CheckProviders.java",
                    "release/src/test/java/org/fuin/porelo/release/CheckProvidersTest.java",
                    "CHANGELOG.md");

    private final PrintStream out;

    private final PrintStream err;

    /**
     * Constructor.
     *
     * @param out where success goes
     * @param err where findings and failed reads go
     */
    CheckProviders(PrintStream out, PrintStream err) {
        this.out = out;
        this.err = err;
    }

    /**
     * Checks git-tracked text outside the provider implementation and historical documents.
     *
     * @param root the repository root
     * @return 0 when no reference is found, {@link Stop#REFUSED} for a provider reference, or
     *     {@link Stop#UNANSWERED} when git or a file cannot be read
     */
    int check(Path root) {
        int read = 0;
        int found = 0;
        try {
            for (final Path file : CheckCitations.committed(root)) {
                final String name = root.relativize(file).toString().replace('\\', '/');
                if (EXEMPT.contains(name)
                        || name.startsWith("issues/")
                        || name.startsWith("changelog/")) {
                    continue;
                }
                final Optional<String> text = text(file);
                if (text.isEmpty()) {
                    continue;
                }
                read++;
                final String[] lines = text.get().split("\\R", -1);
                for (int line = 0; line < lines.length; line++) {
                    final Matcher match = PROVIDER.matcher(lines[line]);
                    while (match.find()) {
                        err.println(name + ":" + (line + 1) + ": " + match.group());
                        found++;
                    }
                }
            }
        } catch (IOException ex) {
            err.println(
                    "could not check git-tracked text files under "
                            + root
                            + ": "
                            + ex.getMessage());
            return Stop.UNANSWERED;
        }
        if (found != 0) {
            err.println(
                    "Expected no provider names or server types outside their implementation; found "
                            + found
                            + " match(es).");
            return Stop.REFUSED;
        }
        out.println(
                "OK    providers in "
                        + root
                        + ": "
                        + read
                        + " text file(s) read, no provider names or server types outside their implementation");
        return 0;
    }

    private static Optional<String> text(Path file) throws IOException {
        final byte[] bytes = Files.readAllBytes(file);
        for (final byte value : bytes) {
            if (value == 0) {
                return Optional.empty();
            }
        }
        try {
            return Optional.of(
                    StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes)).toString());
        } catch (CharacterCodingException ex) {
            return Optional.empty();
        }
    }
}
