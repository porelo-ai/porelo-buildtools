package org.fuin.porelo.machines;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import java.io.IOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.junit.jupiter.api.Test;

/** Tests how a remote command's two streams are read. */
class SshOutputTest {

    @Test
    void aCommandThatWritesMuchToStandardErrorBeforeItEndsIsReadToItsEnd() throws Exception {

        // Its output was read to the end before its errors were touched: a command blocked writing
        // more errors than a channel holds never closed its output, and the call waited for ever.
        final PipedInputStream out = new PipedInputStream(1024);
        final PipedInputStream err = new PipedInputStream(1024);
        final PipedOutputStream toOut = new PipedOutputStream(out);
        final PipedOutputStream toErr = new PipedOutputStream(err);
        final String errors = "e".repeat(64 * 1024);
        final Thread command =
                Thread.ofPlatform()
                        .daemon()
                        .start(
                                () -> {
                                    try (toOut;
                                            toErr) {
                                        toErr.write(errors.getBytes(StandardCharsets.UTF_8));
                                        toOut.write("done".getBytes(StandardCharsets.UTF_8));
                                    } catch (final IOException ex) {
                                        throw new java.io.UncheckedIOException(ex);
                                    }
                                });

        final String[] read =
                assertTimeoutPreemptively(Duration.ofSeconds(10), () -> Ssh.readBoth(out, err));

        assertThat(read[0]).isEqualTo("done");
        assertThat(read[1]).isEqualTo(errors);
        command.join(5_000);
    }
}
