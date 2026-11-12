package org.fuin.porelo.release;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.ConnectException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("integration")
class WebTest {

    private HttpServer server;

    private final List<String> authorizationAtTarget = new CopyOnWriteArrayList<>();

    /** Statuses /flaky answers in turn; 200 "at last" once they are spent. */
    private final Deque<Integer> statuses = new ConcurrentLinkedDeque<>();

    private final List<Duration> waited = new ArrayList<>();

    private final List<String> said = new ArrayList<>();

    private volatile Instant clock = Instant.parse("2026-11-08T12:00:00Z");

    /** How long the clock moves while /flaky answers. */
    private volatile Duration slowness = Duration.ZERO;

    @BeforeEach
    void serve() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext(
                "/moved",
                exchange -> {
                    // Another host name for the same server, so the redirect crosses hosts as a
                    // hostile one would.
                    exchange.getResponseHeaders()
                            .add(
                                    "Location",
                                    "http://localhost:"
                                            + server.getAddress().getPort()
                                            + "/target");
                    exchange.sendResponseHeaders(302, -1);
                    exchange.close();
                });
        server.createContext(
                "/target",
                exchange -> {
                    final String authorization =
                            exchange.getRequestHeaders().getFirst("Authorization");
                    authorizationAtTarget.add(authorization == null ? "none" : authorization);
                    final byte[] body = "arrived".getBytes(StandardCharsets.UTF_8);
                    exchange.sendResponseHeaders(200, body.length);
                    exchange.getResponseBody().write(body);
                    exchange.close();
                });
        server.createContext(
                "/flaky",
                exchange -> {
                    clock = clock.plus(slowness);
                    final Integer status = statuses.poll();
                    final byte[] body = "at last".getBytes(StandardCharsets.UTF_8);
                    if (status == null) {
                        exchange.sendResponseHeaders(200, body.length);
                        exchange.getResponseBody().write(body);
                    } else {
                        exchange.sendResponseHeaders(status, -1);
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
    void followsARedirectWhenNoCredentialIsSent() throws IOException {
        assertThat(Web.overHttp().read(address("/moved")))
                .hasValueSatisfying(
                        body ->
                                assertThat(new String(body, StandardCharsets.UTF_8))
                                        .isEqualTo("arrived"));
    }

    @Test
    void aRedirectToAnotherHostDoesNotCarryTheCredential() throws IOException {
        Web.overHttp().read(address("/moved"), Map.of("Authorization", "Bearer t0ken"));

        assertThat(authorizationAtTarget)
                .as(
                        "the JDK drops Authorization when a redirect changes host; if this"
                                + " fails, the client now forwards a token to wherever it is sent")
                .containsExactly("none");
    }

    @Test
    void anUnreachableAddressIsNamedWithTheKindOfFailure() throws IOException {
        final URI closed;
        try (ServerSocket socket = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))) {
            closed = URI.create("http://127.0.0.1:" + socket.getLocalPort() + "/index");
        }

        assertThatThrownBy(() -> quick().read(closed))
                .as(
                        "the JDK's ConnectException has no message and names no address: both are named")
                .isInstanceOf(IOException.class)
                .hasMessageStartingWith(
                        closed + ": ConnectException; given up after 8 of at most 8")
                .cause()
                .as("the last attempt's failure, kept")
                .isInstanceOf(Web.Unanswered.class)
                .cause()
                .isInstanceOf(ConnectException.class);
        assertThat(said).as("each attempt said").hasSize(7);
    }

    @Test
    void readsAgainAfterA5xxByThePinnedRule() throws IOException {
        statuses.add(503);
        statuses.add(502);

        assertThat(quick().read(address("/flaky")))
                .as("read once the server answers")
                .hasValueSatisfying(
                        body ->
                                assertThat(new String(body, StandardCharsets.UTF_8))
                                        .isEqualTo("at last"));
        assertThat(waited)
                .as("the rule's first pauses")
                .containsExactly(Duration.ofSeconds(15), Duration.ofSeconds(30));
        assertThat(said.getFirst())
                .as("the attempt said")
                .contains("HTTP 503 on attempt 1 of 8; trying again in 15 s");
    }

    @Test
    void givesUpAtTheDeadline() {
        for (int i = 0; i < 20; i++) {
            statuses.add(504);
        }

        assertThatThrownBy(() -> quick().read(address("/flaky")))
                .as("eight attempts at most")
                .hasMessageContaining("HTTP 504; given up after 8 of at most 8 attempts");
    }

    @Test
    void countsTheAttemptsOwnTimeAgainstTheDeadline() {
        for (int i = 0; i < 20; i++) {
            statuses.add(504);
        }
        slowness = Duration.ofSeconds(200);

        assertThatThrownBy(() -> quick().read(address("/flaky")))
                .as("200 s an attempt: the fourth ends past 15 min, so no fifth")
                .hasMessageContaining("given up after 4 of at most 8 attempts in 905 s");
    }

    @Test
    void readsNotAgainWhatAskingAgainWillNotMend() throws IOException {
        statuses.add(403);

        assertThatThrownBy(() -> quick().read(address("/flaky")))
                .as("a 4xx fails at once")
                .hasMessageEndingWith(": HTTP 403");
        statuses.add(404);
        assertThat(quick().read(address("/flaky"))).as("a 404 is not there, at once").isEmpty();
        assertThat(waited).as("no pause").isEmpty();
    }

    /** A reader whose pauses only move a clock of its own. */
    private Web quick() {
        return Web.overHttp(
                        HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build())
                .askedAgain(
                        new Web.Pause() {
                            @Override
                            public void of(Duration time) {
                                waited.add(time);
                                clock = clock.plus(time);
                            }

                            @Override
                            public Instant now() {
                                return clock;
                            }
                        },
                        said::add);
    }

    @Test
    void aFailureWithAMessageKeepsIt() {
        assertThat(Web.reason(new IOException("disk full")))
                .as("a message of its own is kept as it is")
                .isEqualTo("disk full");
        assertThat(Web.reason(new ConnectException()))
                .as("a failure with none is named by its kind")
                .isEqualTo("ConnectException");
    }

    private URI address(String path) {
        return URI.create("http://127.0.0.1:" + server.getAddress().getPort() + path);
    }
}
