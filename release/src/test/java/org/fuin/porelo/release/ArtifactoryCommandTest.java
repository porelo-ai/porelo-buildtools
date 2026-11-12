package org.fuin.porelo.release;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpServer;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@Tag("integration")
class ArtifactoryCommandTest {

    private static final String TOKEN = "test-token-not-a-secret";

    @TempDir Path dir;

    private HttpServer server;

    /** Each request: method, raw path, its Authorization and checksum headers, its body. */
    private final List<String> requests = new CopyOnWriteArrayList<>();

    private final List<byte[]> bodies = new CopyOnWriteArrayList<>();

    /** The statuses answered in turn; 200 with {@link #body} once they are spent. */
    private final Deque<Integer> statuses = new ArrayDeque<>();

    private volatile String body = "";

    private final ByteArrayOutputStream out = new ByteArrayOutputStream();

    private final ByteArrayOutputStream err = new ByteArrayOutputStream();

    private Instant clock = Instant.parse("2026-11-08T12:00:00Z");

    private final List<Duration> waited = new ArrayList<>();

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
                                    + (uri.getRawQuery() == null ? "" : "?" + uri.getRawQuery())
                                    + " auth="
                                    + headers.getFirst("Authorization")
                                    + " sha1="
                                    + headers.getFirst("X-Checksum-Sha1")
                                    + " sha256="
                                    + headers.getFirst("X-Checksum-Sha256"));
                    bodies.add(exchange.getRequestBody().readAllBytes());
                    final Integer status;
                    synchronized (statuses) {
                        status = statuses.poll();
                    }
                    final byte[] answer = body.getBytes(StandardCharsets.UTF_8);
                    if (status != null) {
                        exchange.sendResponseHeaders(status, -1);
                    } else {
                        exchange.sendResponseHeaders(200, answer.length == 0 ? -1 : answer.length);
                        if (answer.length > 0) {
                            exchange.getResponseBody().write(answer);
                        }
                    }
                    exchange.close();
                });
        server.start();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    @Test
    void putsAFileUnderItsOwnNameWithBothChecksumsAndItsProperties() throws Exception {
        final Path file = Files.writeString(dir.resolve("porelo-mcp_0.1.0_amd64.deb"), "a package");

        final int code =
                run(
                        "put",
                        file.toString(),
                        "porelo-dist-deb/pool/main/p/porelo-mcp/",
                        "deb.distribution=snapshots;deb.component=main;deb.architecture=amd64");

        assertThat(code).as(said()).isZero();
        assertThat(requests)
                .as("one PUT to the file's own name, the properties as matrix parameters")
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
        assertThat(stdout())
                .as("what was put")
                .contains(
                        "  put   porelo-dist-deb/pool/main/p/porelo-mcp/porelo-mcp_0.1.0_amd64.deb");
    }

    @Test
    void putsAFileUnderTheNameGiven() throws Exception {
        final Path file = Files.writeString(dir.resolve("setup.sh"), "#!/bin/sh\n");

        assertThat(run("put", file.toString(), "porelo-dist-deb/setup/porelo-setup-latest.sh"))
                .as(said())
                .isZero();
        assertThat(requests.getFirst())
                .as("the target as it was named, no properties")
                .startsWith("PUT /artifactory/porelo-dist-deb/setup/porelo-setup-latest.sh auth=");
    }

    @Test
    void asksAgainOnA5xxByThePinnedRule() throws Exception {
        final Path file = Files.writeString(dir.resolve("a.rpm"), "rpm");
        statuses.add(503);
        statuses.add(502);

        assertThat(run("put", file.toString(), "porelo-dist-rpm/snapshots/")).as(said()).isZero();

        assertThat(requests).as("two answers of 5xx, then stored").hasSize(3);
        assertThat(bodies)
                .as("the whole file each time")
                .allSatisfy(sent -> assertThat(sent).isEqualTo(Files.readAllBytes(file)));
        assertThat(requests.stream().map(r -> r.substring(r.indexOf(" sha1="))).distinct())
                .as("the same checksums each time")
                .containsExactly(
                        " sha1=" + digest(file, "SHA-1") + " sha256=" + digest(file, "SHA-256"));
        assertThat(waited)
                .as("the rule's first pauses")
                .containsExactly(Duration.ofSeconds(15), Duration.ofSeconds(30));
        assertThat(stderr())
                .as("each attempt said")
                .contains("HTTP 503 on attempt 1 of 8; trying again in 15 s");
    }

    @Test
    void asksTheRestInterfaceAgainOnA5xx() throws Exception {
        statuses.add(502);
        body = "{}";

        assertThat(run("api", "GET", "/api/repositories")).as(said()).isZero();
        assertThat(requests).as("asked again").hasSize(2);
        assertThat(waited).containsExactly(Duration.ofSeconds(15));
    }

    @Test
    void refusesAFileChangedWhileItWasSent() throws Exception {
        final Path file = Files.writeString(dir.resolve("a.rpm"), "a longer package");
        server.removeContext("/");
        server.createContext(
                "/",
                exchange -> {
                    exchange.getRequestBody().readAllBytes();
                    requests.add(exchange.getRequestMethod());
                    Files.writeString(file, "short");
                    exchange.sendResponseHeaders(503, -1);
                    exchange.close();
                });

        assertThat(run("put", file.toString(), "porelo-dist-rpm/snapshots/"))
                .as(said())
                .isEqualTo(Stop.REFUSED);
        assertThat(requests).as("not asked again with other bytes").hasSize(1);
        assertThat(stderr()).contains("changed while it was sent");
    }

    @Test
    void refusesAFileWhoseNameNoTargetHolds() throws Exception {
        final Path file = Files.writeString(dir.resolve("a;deb.distribution=x.rpm"), "rpm");

        assertThat(run("put", file.toString(), "porelo-dist-rpm/snapshots/"))
                .isEqualTo(Stop.REFUSED);
        assertThat(requests).as("nothing asked").isEmpty();
        assertThat(stderr()).contains("is no target");
    }

    @Test
    void takesAnInterruptionAsUnansweredNotRefused() throws Exception {
        statuses.add(503);
        final int code =
                new ArtifactoryCommand(
                                Artifactory.overHttp(
                                        URI.create(
                                                "http://127.0.0.1:"
                                                        + server.getAddress().getPort()),
                                        TOKEN),
                                new PrintStream(out, true, StandardCharsets.UTF_8),
                                new PrintStream(err, true, StandardCharsets.UTF_8),
                                time -> {
                                    throw new InterruptedException("stopped");
                                })
                        .run(List.of("ping"));
        final boolean interrupted = Thread.interrupted();

        assertThat(code).as(said()).isEqualTo(Stop.UNANSWERED);
        assertThat(interrupted).as("the interruption kept").isTrue();
    }

    @Test
    void sendsNothingOutsideArtifactory() {
        final Artifactory artifactory =
                Artifactory.overHttp(
                        URI.create("http://127.0.0.1:" + server.getAddress().getPort()), TOKEN);

        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> artifactory.call("GET", "/../access/api/v1/tokens"))
                .hasMessageContaining("not under /artifactory/");
        assertThat(requests).as("nothing asked").isEmpty();
    }

    @Test
    void asksAgainOnlyWhatMayBeAnsweredNextTime() {
        assertThat(Web.mayAnswerNextTime(new java.net.ConnectException()))
                .as("no connection")
                .isTrue();
        assertThat(Web.mayAnswerNextTime(new java.net.http.HttpTimeoutException("timed out")))
                .as("a timeout")
                .isTrue();
        assertThat(
                        Web.mayAnswerNextTime(
                                new javax.net.ssl.SSLHandshakeException(
                                        "PKIX",
                                        new java.security.cert.CertificateException("untrusted"))))
                .as("a refused certificate")
                .isFalse();
        assertThat(
                        Web.mayAnswerNextTime(
                                new IOException(
                                        "wrapped", new java.nio.file.NoSuchFileException("a.rpm"))))
                .as("a file gone, wrapped as the client does")
                .isFalse();
        assertThat(
                        Web.mayAnswerNextTime(
                                new IOException(
                                        "wrapped",
                                        new java.io.UncheckedIOException(new IOException("EIO")))))
                .as("a file that could not be read")
                .isFalse();
    }

    @Test
    void givesUpAtTheDeadline() throws Exception {
        final Path file = Files.writeString(dir.resolve("a.rpm"), "rpm");
        for (int i = 0; i < 20; i++) {
            statuses.add(504);
        }

        assertThat(run("put", file.toString(), "porelo-dist-rpm/snapshots/"))
                .as(said())
                .isEqualTo(Stop.UNANSWERED);
        assertThat(requests).as("eight attempts at most").hasSize(8);
        assertThat(stderr())
                .as("why it stopped")
                .contains("given up after 8 of at most 8 attempts");
    }

    @Test
    void failsAtOnceOnWhatAskingAgainWillNotMend() throws Exception {
        final Path file = Files.writeString(dir.resolve("a.rpm"), "rpm");
        statuses.add(403);

        assertThat(run("put", file.toString(), "porelo-dist-rpm/snapshots/"))
                .as(said())
                .isEqualTo(Stop.REFUSED);
        assertThat(requests).as("asked once").hasSize(1);
        assertThat(waited).as("no pause").isEmpty();
        assertThat(stderr()).as("the answer named").contains("HTTP 403");
    }

    @Test
    void refusesAMissingFileOrALinkBeforeAskingAnything() throws Exception {
        final Path target = Files.writeString(dir.resolve("real.rpm"), "rpm");
        final Path link = Files.createSymbolicLink(dir.resolve("link.rpm"), target);

        assertThat(run("put", dir.resolve("none.rpm").toString(), "porelo-dist-rpm/snapshots/"))
                .isEqualTo(Stop.REFUSED);
        assertThat(run("put", link.toString(), "porelo-dist-rpm/snapshots/"))
                .isEqualTo(Stop.REFUSED);
        assertThat(requests).as("nothing asked").isEmpty();
        assertThat(stderr()).as("the file named").contains("no file '" + dir.resolve("none.rpm"));
    }

    @Test
    void printsWhatTheRestInterfaceAnswers() throws Exception {
        body = "{\"packageType\":\"rpm\"}";

        assertThat(run("api", "GET", "/api/repositories/porelo-dist-rpm")).as(said()).isZero();
        assertThat(stdout()).as("the answer, as it came").isEqualTo(body);
        assertThat(requests.getFirst())
                .as("the path under /artifactory, with the token")
                .startsWith(
                        "GET /artifactory/api/repositories/porelo-dist-rpm auth=Bearer " + TOKEN);
    }

    @Test
    void takesA404OfTheRestInterfaceAsRefused() throws Exception {
        statuses.add(404);

        assertThat(run("api", "DELETE", "/porelo-dist-test/smoke.txt")).isEqualTo(Stop.REFUSED);
        assertThat(stderr()).as("the answer named").contains("HTTP 404");
    }

    @Test
    void pingsAndWantsOk() throws Exception {
        body = "OK\n";
        assertThat(run("ping")).as(said()).isZero();

        body = "Unauthorized";
        assertThat(run("ping")).as("an answer that is not OK").isEqualTo(Stop.REFUSED);
        assertThat(requests)
                .as("both asked the ping")
                .allMatch(r -> r.startsWith("GET /artifactory/api/system/ping "));
    }

    @Test
    void takesOnlyTheThreeCallsInTheirForm() {
        for (final List<String> wrong :
                List.of(
                        List.<String>of(),
                        List.of("get"),
                        List.of("put", "a.rpm"),
                        List.of("put", "a.rpm", "/porelo-dist-rpm/x"),
                        List.of("put", "a.rpm", "porelo-dist-rpm"),
                        List.of("put", "a.rpm", "porelo-dist-rpm/../other/x"),
                        List.of("put", "a.rpm", "porelo-dist-rpm//x"),
                        List.of("put", "a.rpm", "porelo-dist-rpm/x", "novalue"),
                        List.of("put", "a.rpm", "porelo-dist-rpm/x", "a=b?c"),
                        List.of("put", "a.rpm", "porelo-dist-rpm/x", "k=a|b"),
                        List.of("put", "a.rpm", "porelo-dist-rpm/x", "k=a b"),
                        List.of("put", "a.rpm", "porelo-dist-rpm/x;deb.distribution=snapshots"),
                        List.of("put", "a.rpm", "porelo-dist-rpm/a b"),
                        List.of("put", "a.rpm", "porelo-dist-rpm/%2e%2e/x"),
                        List.of("put", "a.rpm", "porelo-dist-rpm/./x"),
                        List.of("api", "GET", "/../access/api/v1/tokens"),
                        List.of("api", "GET", "/api/%2e%2e/x"),
                        List.of("api", "GET", "/api/%2E./x"),
                        List.of("api", "GET", "/api/a%2fb"),
                        List.of("api", "GET", "/api/./x"),
                        List.of("api", "GET", "/x//y"),
                        List.of("api", "GET", "/api/{x}"),
                        List.of("api", "GET", "/api/%ZZ"),
                        List.of("api", "GET", "/api/x#y"),
                        List.of("api", "HEAD", "/x"),
                        List.of("api", "GET", "x"),
                        List.of("api", "GET", "//elsewhere.example/x"),
                        List.of("ping", "again"))) {
            assertThat(ArtifactoryCommand.fits(wrong)).as(wrong.toString()).isFalse();
        }
        assertThat(
                        ArtifactoryCommand.fits(
                                List.of("api", "PUT", "/api/storage/a/b?properties=smoke=1")))
                .as("a query in a call")
                .isTrue();
        assertThat(
                        ArtifactoryCommand.fits(
                                List.of(
                                        "put",
                                        "a.deb",
                                        "porelo-dist-deb/pool/p/porelo_0.5.0~snapshot.3+b1_amd64.deb")))
                .as("a Debian package's name")
                .isTrue();
        assertThat(ArtifactoryCommand.fits(List.of("api", "GET", "/api/search/aql%20x/")))
                .as("an escape that is no dot or slash")
                .isTrue();
    }

    @Test
    void refusesWithoutTheSettingsAndNeverSaysTheToken() {
        final Web unused =
                (address, headers) -> {
                    throw new AssertionError("artifactory reads nothing over Web");
                };
        final ByteArrayOutputStream said = new ByteArrayOutputStream();
        final PrintStream stream = new PrintStream(said, true, StandardCharsets.UTF_8);

        final int noUrl =
                Main.run(
                        new String[] {"artifactory", "ping"},
                        stream,
                        stream,
                        unused,
                        Map.of("JF_ACCESS_TOKEN", TOKEN),
                        (tree, manifest, lockfile, work) -> lockfile);
        final int plain =
                Main.run(
                        new String[] {"artifactory", "ping"},
                        stream,
                        stream,
                        unused,
                        Map.of("JF_URL", "http://example.invalid", "JF_ACCESS_TOKEN", TOKEN),
                        (tree, manifest, lockfile, work) -> lockfile);
        final int noToken =
                Main.run(
                        new String[] {"artifactory", "ping"},
                        stream,
                        stream,
                        unused,
                        Map.of("JF_URL", "https://example.invalid"),
                        (tree, manifest, lockfile, work) -> lockfile);
        final int usage =
                Main.run(
                        new String[] {"artifactory", "put"},
                        stream,
                        stream,
                        unused,
                        Map.of("JF_URL", "https://example.invalid", "JF_ACCESS_TOKEN", TOKEN),
                        (tree, manifest, lockfile, work) -> lockfile);

        assertThat(List.of(noUrl, plain, noToken))
                .as(said.toString(StandardCharsets.UTF_8))
                .containsOnly(Stop.UNANSWERED);
        assertThat(usage).as("a call out of its form").isEqualTo(2);
        assertThat(said.toString(StandardCharsets.UTF_8))
                .as("what was said")
                .contains("JF_URL must be the platform's https address")
                .contains("JF_ACCESS_TOKEN is not set")
                .contains("usage: artifactory put")
                .doesNotContain(TOKEN);
    }

    private int run(String... args) {
        final ArtifactoryCommand.Pause pause =
                new ArtifactoryCommand.Pause() {
                    @Override
                    public void of(Duration time) {
                        waited.add(time);
                        clock = clock.plus(time);
                    }

                    @Override
                    public Instant now() {
                        return clock;
                    }
                };
        return new ArtifactoryCommand(
                        Artifactory.overHttp(
                                URI.create("http://127.0.0.1:" + server.getAddress().getPort()),
                                TOKEN),
                        new PrintStream(out, true, StandardCharsets.UTF_8),
                        new PrintStream(err, true, StandardCharsets.UTF_8),
                        pause)
                .run(List.of(args));
    }

    private static String digest(Path file, String algorithm) throws Exception {
        return HexFormat.of()
                .formatHex(MessageDigest.getInstance(algorithm).digest(Files.readAllBytes(file)));
    }

    private String stdout() {
        return out.toString(StandardCharsets.UTF_8);
    }

    private String stderr() {
        return err.toString(StandardCharsets.UTF_8);
    }

    private String said() {
        return stdout() + stderr();
    }
}
