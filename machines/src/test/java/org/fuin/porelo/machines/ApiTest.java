package org.fuin.porelo.machines;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** Tests for {@link Api}. */
class ApiTest {

    @Tag("integration")
    @Test
    void readsEveryPageAndNotOnlyTheFirst() throws IOException {
        // The failure this prevents costs money: the API pages at 25, and a sweep that reads one
        // page finds fewer leaked servers than exist and then reports that it cleaned up.
        try (StubApi stub =
                        new StubApi()
                                .answering(
                                        "/servers?page=1&per_page=50",
                                        """
                    {"servers":[{"id":1}],"meta":{"pagination":{"next_page":2}}}""")
                                .answering(
                                        "/servers?page=2&per_page=50",
                                        """
                    {"servers":[{"id":2}],"meta":{"pagination":{"next_page":3}}}""")
                                .answering(
                                        "/servers?page=3&per_page=50",
                                        """
                    {"servers":[{"id":3}],"meta":{"pagination":{"next_page":null}}}""");
                Api api = new Api("t", stub.base())) {
            final List<Map<String, Object>> all = api.all("/servers", "servers");
            assertThat(all).hasSize(3);
            assertThat(all.stream().map(each -> Values.id(each.get("id"))))
                    .containsExactly(1L, 2L, 3L);
        }
    }

    @Tag("integration")
    @Test
    void keepsAnExistingQueryWhenItAddsThePage() throws IOException {
        try (StubApi stub =
                        new StubApi()
                                .answering(
                                        "/servers?label_selector=porelo=ci&page=1&per_page=50",
                                        """
                    {"servers":[{"id":7}],"meta":{"pagination":{"next_page":null}}}""");
                Api api = new Api("t", stub.base())) {
            assertThat(api.all("/servers?label_selector=porelo%3Dci", "servers")).hasSize(1);
        }
    }

    @Tag("integration")
    @Test
    void waitsOutARateLimitRatherThanFailing() throws IOException {
        final AtomicInteger calls = new AtomicInteger();
        final MovingClock clock = new MovingClock();
        try (StubApi stub =
                        new StubApi()
                                .answering(
                                        "/servers",
                                        exchange -> {
                                            if (calls.incrementAndGet() == 1) {
                                                exchange.getResponseHeaders()
                                                        .add("Retry-After", "1");
                                                return new StubApi.Answer(
                                                        429,
                                                        "{\"error\":{\"code\":\"rate_limit_exceeded\"}}");
                                            }
                                            return new StubApi.Answer(200, "{\"servers\":[]}");
                                        });
                Api api = new Api("t", stub.base(), clock)) {
            assertThat(api.get("/servers")).containsKey("servers");
            assertThat(calls.get()).isEqualTo(2);
            assertThat(clock.sleeps)
                    .as("as long as Retry-After says")
                    .containsExactly(Duration.ofSeconds(1));
        }
    }

    // Prevents one brief outage of the provider's API from turning a cleanup red.
    @Tag("integration")
    @Test
    void triesAReadAgainThroughAShortOutage() throws IOException {
        final AtomicInteger calls = new AtomicInteger();
        final MovingClock clock = new MovingClock();
        try (StubApi stub =
                        new StubApi()
                                .answering(
                                        "/servers",
                                        exchange ->
                                                calls.incrementAndGet() <= 2
                                                        ? new StubApi.Answer(503, "")
                                                        : new StubApi.Answer(
                                                                200, "{\"servers\":[]}"));
                Api api = new Api("t", stub.base(), clock)) {
            assertThat(api.get("/servers")).containsKey("servers");
            assertThat(calls.get()).as("two 503s, then the answer").isEqualTo(3);
            assertThat(clock.sleeps)
                    .as("a pause before each try again")
                    .containsExactly(Duration.ofSeconds(5), Duration.ofSeconds(5));
        }
    }

    // Prevents an outage that outlasts the patience from being waited out forever or hidden.
    @Tag("integration")
    @Test
    void failsADeleteLoudWithTheProvidersAnswerOnceItsPatienceIsSpent() throws IOException {
        final AtomicInteger calls = new AtomicInteger();
        final MovingClock clock = new MovingClock();
        try (StubApi stub =
                        new StubApi()
                                .answering(
                                        "/servers/7",
                                        exchange -> {
                                            calls.incrementAndGet();
                                            return new StubApi.Answer(
                                                    503,
                                                    "{\"error\":{\"code\":\"unavailable\","
                                                            + "\"message\":\"service unavailable\"}}");
                                        });
                Api api = new Api("t", stub.base(), clock)) {
            assertThatThrownBy(() -> api.delete("/servers/7"))
                    .isInstanceOf(Api.ApiException.class)
                    .hasMessageContaining("503")
                    .hasMessageContaining("service unavailable");
            assertThat(clock.sleeps.stream().reduce(Duration.ZERO, Duration::plus))
                    .as("no longer than its patience")
                    .isEqualTo(Duration.ofSeconds(60));
            assertThat(calls.get()).isEqualTo(13);
        }
    }

    // Prevents an outage's tries from spending the rate limit's own budget.
    @Tag("integration")
    @Test
    void stillWaitsOutARateLimitAfterAnOutage() throws IOException {
        final AtomicInteger calls = new AtomicInteger();
        final MovingClock clock = new MovingClock();
        try (StubApi stub =
                        new StubApi()
                                .answering(
                                        "/servers",
                                        exchange -> {
                                            final int call = calls.incrementAndGet();
                                            if (call <= 6) {
                                                return new StubApi.Answer(503, "");
                                            }
                                            if (call == 7) {
                                                exchange.getResponseHeaders()
                                                        .add("Retry-After", "1");
                                                return new StubApi.Answer(
                                                        429,
                                                        "{\"error\":{\"code\":\"rate_limit_exceeded\"}}");
                                            }
                                            return new StubApi.Answer(200, "{\"servers\":[]}");
                                        });
                Api api = new Api("t", stub.base(), clock)) {
            assertThat(api.get("/servers")).containsKey("servers");
            assertThat(calls.get()).isEqualTo(8);
        }
    }

    // Prevents a create whose answer was lost from being sent twice and renting two machines.
    @Tag("integration")
    @Test
    void neverSendsACreateAgain() throws IOException {
        final AtomicInteger calls = new AtomicInteger();
        final MovingClock clock = new MovingClock();
        try (StubApi stub =
                        new StubApi()
                                .answering(
                                        "/servers",
                                        exchange -> {
                                            calls.incrementAndGet();
                                            return new StubApi.Answer(503, "");
                                        });
                Api api = new Api("t", stub.base(), clock)) {
            assertThatThrownBy(() -> api.post("/servers", java.util.Map.of("name", "x")))
                    .isInstanceOf(Api.ApiException.class)
                    .hasMessageContaining("503");
            assertThat(calls.get()).isEqualTo(1);
            assertThat(clock.sleeps).isEmpty();
        }
    }

    // Prevents a call that cannot connect from failing at the first refused connection.
    @Test
    void triesAReadAgainWhenItCannotConnectThenSaysWhy() {
        final MovingClock clock = new MovingClock();
        try (Api api = new Api("t", "http://127.0.0.1:9/v1", clock)) {
            assertThatThrownBy(() -> api.get("/servers"))
                    .isInstanceOf(java.io.IOException.class)
                    .hasMessageContaining("GET /servers")
                    .hasMessageContaining("13 tries in 60 s");
            assertThat(clock.sleeps).hasSize(12);
        }
    }

    @Tag("integration")
    @Test
    void carriesTheApisOwnCodeSoOneRefusalCanBeRetriedAndTheRestNotBe() throws IOException {
        try (StubApi stub =
                        new StubApi()
                                .answering(
                                        "/servers",
                                        exchange -> {
                                            return new StubApi.Answer(
                                                    403,
                                                    "{\"error\":{\"code\":\"resource_limit_exceeded\","
                                                            + "\"message\":\"project limit reached\"}}");
                                        });
                Api api = new Api("t", stub.base())) {
            assertThatThrownBy(() -> api.get("/servers"))
                    .isInstanceOf(Api.ApiException.class)
                    .hasMessageContaining("project limit reached")
                    .extracting(ex -> ((Api.ApiException) ex).code())
                    .isEqualTo("resource_limit_exceeded");
        }
    }

    @Tag("integration")
    @Test
    void saysWhatArrivedWhenTheAnswerIsNotJson() throws IOException {
        try (StubApi stub =
                        new StubApi()
                                .answering(
                                        "/servers",
                                        exchange ->
                                                new StubApi.Answer(200, "<html>gateway</html>"));
                Api api = new Api("t", stub.base())) {
            assertThatThrownBy(() -> api.get("/servers"))
                    .isInstanceOf(IOException.class)
                    .hasMessageContaining("not JSON")
                    .hasMessageContaining("gateway");
        }
    }

    @Test
    void refusesToStartWithoutAToken() {
        assertThatThrownBy(() -> new Api(" "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("never an argument");
    }
}
