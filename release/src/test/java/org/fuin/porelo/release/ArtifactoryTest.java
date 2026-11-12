package org.fuin.porelo.release;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.HttpServer;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.zip.GZIPOutputStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("integration")
class ArtifactoryTest {

    private static final String TOKEN = "test-token-not-a-secret";

    private HttpServer server;

    /** What each request asked: method, the raw path and query, and its Authorization header. */
    private final List<String> requests = new CopyOnWriteArrayList<>();

    /** Answers by "METHOD raw-path-and-query": status and body. */
    private final Map<String, Map.Entry<Integer, byte[]>> answers = new ConcurrentHashMap<>();

    private Artifactory artifactory;

    @BeforeEach
    void serve() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext(
                "/",
                exchange -> {
                    final URI uri = exchange.getRequestURI();
                    final String key =
                            exchange.getRequestMethod()
                                    + " "
                                    + uri.getRawPath()
                                    + (uri.getRawQuery() == null ? "" : "?" + uri.getRawQuery());
                    requests.add(
                            key + " " + exchange.getRequestHeaders().getFirst("Authorization"));
                    exchange.getRequestBody().readAllBytes();
                    final Map.Entry<Integer, byte[]> answer =
                            answers.getOrDefault(key, Map.entry(404, new byte[0]));
                    exchange.sendResponseHeaders(
                            answer.getKey(),
                            answer.getValue().length == 0 ? -1 : answer.getValue().length);
                    if (answer.getValue().length > 0) {
                        exchange.getResponseBody().write(answer.getValue());
                    }
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

    private void answer(String key, int status, String body) {
        answers.put(key, Map.entry(status, body.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void readsAListingAsArtifactoryWritesItWithTheTokenAsItsHeaderAlone() throws IOException {
        answer(
                "GET /artifactory/api/storage/porelo-dist-rpm/snapshots",
                200,
                """
                {"repo":"porelo-dist-rpm","path":"/snapshots","children":[
                  {"uri":"/repodata","folder":true},
                  {"uri":"/porelo-0.5.0~snapshot.289-1.x86_64.rpm","folder":false}]}
                """);

        assertThat(artifactory.children("porelo-dist-rpm", "snapshots"))
                .as("both entries, as folder and file")
                .contains(
                        List.of(
                                new Artifactory.Child("repodata", true),
                                new Artifactory.Child(
                                        "porelo-0.5.0~snapshot.289-1.x86_64.rpm", false)));
        assertThat(requests)
                .as("the token goes as a bearer header, never in the address")
                .containsExactly(
                        "GET /artifactory/api/storage/porelo-dist-rpm/snapshots Bearer " + TOKEN);
    }

    @Test
    void readsWhenAFileWasStoredAndAddressesItsTildeEncoded() throws IOException {
        answer(
                "GET /artifactory/api/storage/porelo-dist-rpm/snapshots/porelo-0.5.0~snapshot.1-1.x86_64.rpm",
                200,
                "{\"created\":\"2026-10-17T04:58:30.623Z\",\"size\":\"5\"}");

        assertThat(
                        artifactory.created(
                                "porelo-dist-rpm",
                                "snapshots/porelo-0.5.0~snapshot.1-1.x86_64.rpm"))
                .as("Artifactory's created")
                .isEqualTo(Instant.parse("2026-10-17T04:58:30.623Z"));
    }

    @Test
    void aDeleteRefusedOrOfNothingIsAFailureNeverAnAnswer() {
        answer("DELETE /artifactory/porelo-dist-deb/prune-probe/run-1.txt", 403, "");

        assertThatThrownBy(() -> artifactory.delete("porelo-dist-deb", "prune-probe/run-1.txt"))
                .as("403 is not deleted")
                .isInstanceOf(IOException.class)
                .hasMessageContaining("HTTP 403")
                .hasMessageNotContaining(TOKEN);
        assertThatThrownBy(() -> artifactory.delete("porelo-dist-deb", "prune-probe/run-2.txt"))
                .as("404 deleted nothing either")
                .isInstanceOf(IOException.class)
                .hasMessageContaining("HTTP 404");
    }

    @Test
    void aFileWithNoPropertiesHasNoneButAMissingFileIsAFailure() throws IOException {
        answer(
                "GET /artifactory/api/storage/porelo-dist-deb/pool/a.deb",
                200,
                "{\"created\":\"2026-10-17T04:58:30Z\"}");

        assertThat(artifactory.properties("porelo-dist-deb", "pool/a.deb")).as("none").isEmpty();
        assertThatThrownBy(() -> artifactory.properties("porelo-dist-deb", "pool/gone.deb"))
                .as("a file that is not there has no channel to read")
                .isInstanceOf(IOException.class);
    }

    @Test
    void readsTheRpmPrimaryMetadataThroughItsIndex() throws IOException {
        answer(
                "GET /artifactory/porelo-dist-rpm/snapshots/repodata/repomd.xml",
                200,
                """
                <repomd><data type="other"><location href="repodata/o-other.xml.gz"/></data>
                <data type="primary">
                  <location href="repodata/p-primary.xml.gz"/>
                </data></repomd>
                """);
        final ByteArrayOutputStream packed = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(packed)) {
            gzip.write("<location href=\"porelo-1.rpm\"/>".getBytes(StandardCharsets.UTF_8));
        }
        answers.put(
                "GET /artifactory/porelo-dist-rpm/snapshots/repodata/p-primary.xml.gz",
                Map.entry(200, packed.toByteArray()));

        assertThat(artifactory.rpmPrimary("porelo-dist-rpm", "snapshots"))
                .as("the primary metadata, unpacked")
                .contains("<location href=\"porelo-1.rpm\"/>");
    }

    @Test
    void theCommandRefusesToStartWithoutItsTokenOrWithTheWrongAddress() {
        final ByteArrayOutputStream err = new ByteArrayOutputStream();
        final PrintStream errors = new PrintStream(err, true, StandardCharsets.UTF_8);
        final PrintStream out =
                new PrintStream(new ByteArrayOutputStream(), true, StandardCharsets.UTF_8);
        final String[] args = {"prune-snapshots", "--probe", "run-1"};

        assertThat(
                        Main.run(
                                args,
                                out,
                                errors,
                                (address, headers) -> java.util.Optional.empty(),
                                Map.of("JF_URL", "https://example.org"),
                                (tree, manifest, lockfile, work) -> lockfile))
                .as("no token")
                .isEqualTo(Stop.UNANSWERED);
        assertThat(
                        Main.run(
                                args,
                                out,
                                errors,
                                (address, headers) -> java.util.Optional.empty(),
                                Map.of(
                                        "JF_URL",
                                        "https://example.org/artifactory",
                                        "JF_ACCESS_TOKEN",
                                        TOKEN),
                                (tree, manifest, lockfile, work) -> lockfile))
                .as("the platform's address is wanted, not Artifactory's")
                .isEqualTo(Stop.UNANSWERED);
        assertThat(err.toString(StandardCharsets.UTF_8))
                .as("each says why, never the token")
                .contains("JF_ACCESS_TOKEN is not set")
                .contains("without /artifactory")
                .doesNotContain(TOKEN);
        assertThat(requests).as("nothing was asked of any server").isEmpty();
    }

    @Test
    void aListingOrPropertiesInAnotherShapeAreUnreadableNotEmpty() {
        answer("GET /artifactory/api/storage/porelo-dist-deb/pool/main/p", 200, "{}");
        answer(
                "GET /artifactory/api/storage/porelo-dist-deb/pool/a.deb?properties",
                200,
                "{\"uri\":\"x\"}");

        assertThatThrownBy(() -> artifactory.children("porelo-dist-deb", "pool/main/p"))
                .as("a listing without children is not an empty folder")
                .isInstanceOf(IOException.class);
        assertThatThrownBy(() -> artifactory.properties("porelo-dist-deb", "pool/a.deb"))
                .as("an answer without properties is not a file without any")
                .isInstanceOf(IOException.class);
    }

    @Test
    void theCommandRefusesATokenItCouldOnlyLeakAPlainAddressAndAProbeOutsideItsFolder() {
        final ByteArrayOutputStream err = new ByteArrayOutputStream();
        final PrintStream errors = new PrintStream(err, true, StandardCharsets.UTF_8);
        final PrintStream out =
                new PrintStream(new ByteArrayOutputStream(), true, StandardCharsets.UTF_8);
        final String local = "http://127.0.0.1:" + server.getAddress().getPort();

        assertThat(
                        run(
                                new String[] {"prune-snapshots", "--probe", "run-1"},
                                Map.of(
                                        "JF_URL",
                                        "https://example.org",
                                        "JF_ACCESS_TOKEN",
                                        TOKEN + "\r\n"),
                                out,
                                errors))
                .as("a token with a line break")
                .isEqualTo(Stop.UNANSWERED);
        assertThat(
                        run(
                                new String[] {"prune-snapshots", "--probe", "run-1"},
                                Map.of("JF_URL", local, "JF_ACCESS_TOKEN", TOKEN),
                                out,
                                errors))
                .as("a token over plain http")
                .isEqualTo(Stop.UNANSWERED);
        assertThat(
                        run(
                                new String[] {
                                    "prune-snapshots", "--probe", "../pool/main/p/porelo/x"
                                },
                                Map.of("JF_URL", "https://example.org", "JF_ACCESS_TOKEN", TOKEN),
                                out,
                                errors))
                .as("a probe name that leaves its folder")
                .isNotZero();
        assertThat(err.toString(StandardCharsets.UTF_8))
                .as("refused as an argument, before any address is tried")
                .contains("usage: prune-snapshots");
        assertThat(err.toString(StandardCharsets.UTF_8))
                .as("never the token")
                .doesNotContain(TOKEN);
        assertThat(requests).as("nothing was asked of any server").isEmpty();
    }

    private static int run(
            String[] args, Map<String, String> env, PrintStream out, PrintStream err) {
        return Main.run(
                args,
                out,
                err,
                (address, headers) -> java.util.Optional.empty(),
                env,
                (tree, manifest, lockfile, work) -> lockfile);
    }
}
