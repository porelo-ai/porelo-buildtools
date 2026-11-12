package org.fuin.porelo.release;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * What {@link Artifactory#overHttp} sends for the {@code artifactory} command, and how it takes the
 * answers, against a loopback server.
 */
@Tag("integration")
class ArtifactoryUploadTest {

    private static final String TOKEN = "test-token-not-a-secret";

    @TempDir Path dir;

    private HttpServer server;

    /** Each request: method, raw path, its Authorization and checksum headers. */
    private final List<String> requests = new CopyOnWriteArrayList<>();

    private final List<byte[]> bodies = new CopyOnWriteArrayList<>();

    /** The statuses answered in turn; 200 once they are spent. */
    private final Deque<Integer> statuses = new ArrayDeque<>();

    /** Runs while a request is answered, before its status is sent. */
    private volatile Runnable during = () -> {};

    private Artifactory artifactory;

    @BeforeEach
    void serve() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext(
                "/",
                exchange -> {
                    final URI uri = exchange.getRequestURI();
                    final var headers = exchange.getRequestHeaders();
                    requests.add(
                            exchange.getRequestMethod()
                                    + " "
                                    + uri.getRawPath()
                                    + " auth="
                                    + headers.getFirst("Authorization")
                                    + " sha1="
                                    + headers.getFirst("X-Checksum-Sha1")
                                    + " sha256="
                                    + headers.getFirst("X-Checksum-Sha256"));
                    bodies.add(exchange.getRequestBody().readAllBytes());
                    during.run();
                    final Integer status;
                    synchronized (statuses) {
                        status = statuses.poll();
                    }
                    exchange.sendResponseHeaders(status == null ? 200 : status, -1);
                    exchange.close();
                });
        server.start();
        artifactory =
                Artifactory.overHttp(
                        URI.create("http://127.0.0.1:" + server.getAddress().getPort()), TOKEN);
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    @Test
    void uploadsTheFileWithBothChecksumsItsPropertiesAndTheToken() throws Exception {
        final Path file = Files.writeString(dir.resolve("porelo-mcp_0.1.0_amd64.deb"), "a package");

        artifactory.upload(
                "porelo-dist-deb/pool/main/p/porelo-mcp/porelo-mcp_0.1.0_amd64.deb",
                file,
                "deb.distribution=snapshots;deb.component=main;deb.architecture=amd64");

        assertThat(requests)
                .as("one PUT, the properties as matrix parameters")
                .containsExactly(
                        "PUT /artifactory/porelo-dist-deb/pool/main/p/porelo-mcp/porelo-mcp_0.1.0_amd64.deb"
                                + ";deb.distribution=snapshots;deb.component=main;deb.architecture=amd64"
                                + " auth=Bearer "
                                + TOKEN
                                + " sha1="
                                + digest(file, "SHA-1")
                                + " sha256="
                                + digest(file, "SHA-256"));
        assertThat(bodies.getFirst()).as("the file's bytes").isEqualTo(Files.readAllBytes(file));
    }

    @Test
    void aServerErrorIsUnansweredAndARefusalIsNot() throws Exception {
        final Path file = Files.writeString(dir.resolve("a.rpm"), "rpm");
        statuses.add(503);
        statuses.add(403);

        assertThatThrownBy(() -> artifactory.upload("porelo-dist-rpm/snapshots/a.rpm", file, ""))
                .as("worth asking again")
                .isInstanceOf(Artifactory.Unanswered.class)
                .hasMessageContaining("HTTP 503");
        assertThatThrownBy(() -> artifactory.call("GET", "/api/repositories"))
                .as("not mended by asking again")
                .isInstanceOf(IOException.class)
                .isNotInstanceOf(Artifactory.Unanswered.class)
                .hasMessageContaining("HTTP 403")
                .hasMessageNotContaining(TOKEN);
    }

    @Test
    void aFileChangedWhileItWasSentIsRefusedNotUnanswered() throws Exception {
        final Path file = Files.writeString(dir.resolve("a.rpm"), "a longer package");
        during =
                () -> {
                    try {
                        Files.writeString(file, "short");
                    } catch (IOException ex) {
                        throw new java.io.UncheckedIOException(ex);
                    }
                };
        statuses.add(503);

        assertThatThrownBy(() -> artifactory.upload("porelo-dist-rpm/snapshots/a.rpm", file, ""))
                .isInstanceOf(IOException.class)
                .isNotInstanceOf(Artifactory.Unanswered.class)
                .hasMessageContaining("changed while it was sent");
    }

    @Test
    void sendsNothingOutsideArtifactory() {
        assertThatThrownBy(() -> artifactory.call("GET", "/../access/api/v1/tokens"))
                .hasMessageContaining("not under /artifactory/");
        assertThat(requests).as("nothing asked").isEmpty();
    }

    private static String digest(Path file, String algorithm) throws Exception {
        return HexFormat.of()
                .formatHex(MessageDigest.getInstance(algorithm).digest(Files.readAllBytes(file)));
    }
}
