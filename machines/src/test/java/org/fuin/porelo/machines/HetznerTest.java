package org.fuin.porelo.machines;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import org.fuin.porelo.json.Json;
import org.junit.jupiter.api.Test;

/** Tests for {@link Hetzner}, against an API that answers what the test tells it to. */
class HetznerTest {

    @Test
    void anOldProviderCallUsesTheBuildKindAndKeepsItsExplicitTypes() throws IOException {
        // A heavy leg must keep its capacity when its old request is routed by kind.
        final Credential credential = Keys.generated();
        final Spec spec = new Spec("heavy", "ubuntu", List.of("ccx33"), "root", credential, false);
        assertThat(spec.kind())
                .as("the old request reaches acquisition by kind")
                .isEqualTo(Kind.AMD64_BUILD);
        assertThat(spec.providerOverride())
                .as("the old request keeps its provider override")
                .containsExactly("ccx33");
        final java.util.concurrent.atomic.AtomicReference<String> sent =
                new java.util.concurrent.atomic.AtomicReference<>();
        try (StubApi stub =
                new StubApi()
                        .answering(
                                "/images?type=snapshot&label_selector=porelo=ci,os=ubuntu&page=1&per_page=50",
                                """
                        {"images":[{"id":11,"description":"ubuntu","status":"available",
                        "created":"2026-09-01T00:00:00+00:00"}],"meta":{"pagination":{"next_page":null}}}
                        """)
                        .answering(
                                "/locations?page=1&per_page=50",
                                """
                        {"locations":[{"name":"nbg1","network_zone":"eu-central"}],"meta":{"pagination":{"next_page":null}}}
                        """)
                        .answering(
                                "/server_types?name=ccx33&page=1&per_page=50",
                                """
                        {"server_types":[{"locations":[{"name":"nbg1","available":true}]}],"meta":{"pagination":{"next_page":null}}}
                        """)
                        .answering(
                                "/ssh_keys?page=1&per_page=50",
                                "{\"ssh_keys\":[{\"id\":2,\"name\":\"ours\",\"fingerprint\":\""
                                        + Fingerprint.md5(credential)
                                        + "\"}],\"meta\":{\"pagination\":{\"next_page\":null}}}")
                        .answering(
                                "/servers",
                                exchange -> {
                                    try {
                                        sent.set(
                                                new String(
                                                        exchange.getRequestBody().readAllBytes(),
                                                        java.nio.charset.StandardCharsets.UTF_8));
                                    } catch (IOException ex) {
                                        throw new java.io.UncheckedIOException(ex);
                                    }
                                    return new StubApi.Answer(201, Acquisition.created());
                                })
                        .answering(
                                "/servers/77", "{\"action\":{\"id\":5,\"status\":\"success\"}}")) {
            try (Hetzner provider = Hetzner.against(stub.base(), "compatibility");
                    Lease lease = provider.acquire(spec)) {

                assertThat(
                                Values.object(org.fuin.porelo.json.Json.parse(sent.get()))
                                        .get("server_type"))
                        .as("the explicit type reaches the provider unchanged")
                        .isEqualTo("ccx33");
            }
        }
    }

    private static final String NOW = "2999-01-01T00:00:00+00:00";

    private static final String LONG_AGO = "2000-01-01T00:00:00+00:00";

    /** The sweep's now, so an expiry a minute either side of it decides the same on every run. */
    private static final Clock SWEEP_TIME =
            Clock.fixed(Instant.parse("2026-10-18T04:13:00Z"), ZoneOffset.UTC);

    /** A minute before {@link #SWEEP_TIME}. */
    private static final String JUST_EXPIRED = "20261018T0412Z";

    /** A minute after {@link #SWEEP_TIME}. */
    private static final String STILL_IN_USE = "20261018T0414Z";

    @Test
    void picksTheNewestSnapshotThatIsActuallyAvailable() throws IOException {
        try (StubApi stub =
                new StubApi()
                        .answering(
                                "/images?type=snapshot&label_selector=porelo=ci,os=ubuntu&page=1&per_page=50",
                                """
                {"images":[
                  {"id":1,"description":"old","status":"available","created":"2026-01-01T00:00:00+00:00"},
                  {"id":2,"description":"newest but still building","status":"creating","created":"2026-09-01T00:00:00+00:00"},
                  {"id":3,"description":"newest available","status":"available","created":"2026-06-01T00:00:00+00:00"}],
                 "meta":{"pagination":{"next_page":null}}}""")) {
            final Hetzner hetzner = Hetzner.against(stub.base(), "run-1");
            assertThat(Values.text(hetzner.newestSnapshot("ubuntu"), "description"))
                    .isEqualTo("newest available");
        }
    }

    @Test
    void saysWhichSnapshotsExistWhenTheOneAskedForDoesNot() throws IOException {
        try (StubApi stub =
                new StubApi()
                        .answering(
                                "/images?type=snapshot&label_selector=porelo=ci,os=debian&page=1&per_page=50",
                                "{\"images\":[],\"meta\":{\"pagination\":{\"next_page\":null}}}")
                        .answering(
                                "/images?type=snapshot&label_selector=porelo=ci&page=1&per_page=50",
                                """
                    {"images":[{"id":1,"labels":{"os":"ubuntu"}},{"id":2,"labels":{"os":"fedora"}}],
                     "meta":{"pagination":{"next_page":null}}}""")) {
            final Hetzner hetzner = Hetzner.against(stub.base(), "run-1");
            assertThatThrownBy(() -> hetzner.newestSnapshot("debian"))
                    .isInstanceOf(IOException.class)
                    .hasMessageContaining("No snapshot for 'debian'")
                    .hasMessageContaining("fedora")
                    .hasMessageContaining("ubuntu");
        }
    }

    @Test
    void picksALocationThatCanActuallyServeTheType() throws IOException {
        try (StubApi stub =
                new StubApi()
                        .answering(
                                "/server_types?name=cpx41&page=1&per_page=50",
                                "{\"server_types\":[{\"id\":9,\"locations\":[{\"name\":\"fsn1\",\"available\":false},{\"name\":\"ash\",\"available\":true},{\"name\":\"nbg1\",\"available\":true}]}],\"meta\":{\"pagination\":{\"next_page\":null}}}")
                        .answering(
                                "/locations?page=1&per_page=50",
                                """
                        {"locations":[{"name":"fsn1","network_zone":"eu-central"},{"name":"ash","network_zone":"us-east"},{"name":"nbg1","network_zone":"eu-central"}],"meta":{"pagination":{"next_page":null}}}""")) {
            // fsn1 offered zero server types on 2026-09-06 while nbg1 offered eighteen, and a
            // hard-coded location fails as "unsupported location for server type" - which reads
            // like a wrong type rather than a full datacentre. us-east has it and is not ours.
            assertThat(Hetzner.against(stub.base(), "run-1").placements(List.of("cpx41")))
                    .containsExactly(new Hetzner.Placement("cpx41", "nbg1"));
        }
    }

    @Test
    void keepsTheTypesThatAreOfferedInTheOrderTheyWereAskedFor() throws IOException {
        try (StubApi stub =
                new StubApi()
                        .answering(
                                "/locations?page=1&per_page=50",
                                """
                        {"locations":[{"name":"nbg1","network_zone":"eu-central"}],"meta":{"pagination":{"next_page":null}}}""")
                        .answering(
                                "/server_types?name=cx23&page=1&per_page=50",
                                "{\"server_types\":[{\"id\":23,\"locations\":[{\"name\":\"nbg1\",\"available\":false}]}],\"meta\":{\"pagination\":{\"next_page\":null}}}")
                        .answering(
                                "/server_types?name=cpx12&page=1&per_page=50",
                                "{\"server_types\":[{\"id\":12,\"locations\":[{\"name\":\"nbg1\",\"available\":false}]}],\"meta\":{\"pagination\":{\"next_page\":null}}}")
                        .answering(
                                "/server_types?name=cx33&page=1&per_page=50",
                                """
                    {"server_types":[{"id":33,"locations":[{"name":"nbg1","available":true}],"prices":[
                       {"location":"nbg1","price_hourly":{"gross":"0.01899000"}}]}],
                     "meta":{"pagination":{"next_page":null}}}""")) {
            // Only cx33 is offered here, so it is the only candidate - and a type nobody has is
            // dropped rather than ending the run.
            assertThat(
                            Hetzner.against(stub.base(), "run-1")
                                    .placements(List.of("cx23", "cx33", "cpx12")))
                    .containsExactly(new Hetzner.Placement("cx33", "nbg1"));
        }
    }

    @Test
    void saysWhatAnHourCostsToFourDecimals() {
        // The API answers with eight, which is noise; the reason for an ordered list is money, so
        // the number has to be readable in a log and checkable against a bill.
        assertThat(Hetzner.trimmed("0.01899000")).isEqualTo("0.0190");
        assertThat(Hetzner.trimmed("not a number")).isEqualTo("not a number");
    }

    @Test
    void saysWhereItLookedWhenNoLocationHasTheType() throws IOException {
        try (StubApi stub =
                new StubApi()
                        .answering(
                                "/server_types?name=cpx41&page=1&per_page=50",
                                "{\"server_types\":[{\"id\":9,\"locations\":[{\"name\":\"fsn1\",\"available\":false}]}],\"meta\":{\"pagination\":{\"next_page\":null}}}")
                        .answering(
                                "/locations?page=1&per_page=50",
                                """
                        {"locations":[{"name":"fsn1","network_zone":"eu-central"}],"meta":{"pagination":{"next_page":null}}}""")) {
            assertThatThrownBy(
                            () ->
                                    Hetzner.against(stub.base(), "run-1")
                                            .placements(List.of("cpx41")))
                    .isInstanceOf(IOException.class)
                    .hasMessageContaining("cpx41=not offered in fsn1");
        }
    }

    @Test
    void refusesAServerTypeThatDoesNotExist() throws IOException {
        try (StubApi stub =
                new StubApi()
                        .answering(
                                "/locations?page=1&per_page=50",
                                "{\"locations\":[],\"meta\":{\"pagination\":{\"next_page\":null}}}")
                        .answering(
                                "/server_types?name=nonsense&page=1&per_page=50",
                                "{\"server_types\":[],\"meta\":{\"pagination\":{\"next_page\":null}}}")) {
            assertThatThrownBy(
                            () ->
                                    Hetzner.against(stub.base(), "run-1")
                                            .placements(List.of("nonsense")))
                    .isInstanceOf(IOException.class)
                    .hasMessageContaining("no such type");
        }
    }

    @Test
    void deletesOnlyItsOwnRatherThanEverythingItFinds() throws IOException {
        final AtomicInteger deleted = new AtomicInteger();
        try (StubApi stub =
                new StubApi()
                        .answering(
                                "/servers?label_selector=porelo=ci&page=1&per_page=50",
                                """
                    {"servers":[
                      {"id":1,"name":"mine","labels":{"porelo":"ci","run":"run-1"},"created":"%s"},
                      {"id":2,"name":"another run's","labels":{"porelo":"ci","run":"run-2"},"created":"%s"}],
                     "meta":{"pagination":{"next_page":null}}}"""
                                        .formatted(NOW, NOW))
                        .answering(
                                "/servers/1",
                                exchange -> {
                                    deleted.incrementAndGet();
                                    return new StubApi.Answer(
                                            200, "{\"action\":{\"id\":5,\"status\":\"success\"}}");
                                })) {
            // Deleting by age instead would catch another run's server whenever that run is slower
            // than the window, and ssh dying part way through a build is undebuggable.
            assertThat(Hetzner.against(stub.base(), "run-1").deleteMine()).isEqualTo(1);
            assertThat(deleted.get()).isEqualTo(1);
            assertThat(stub.asked()).doesNotContain("DELETE /v1/servers/2");
        }
    }

    @Test
    void deletesTheRestOfARunsServersWhenOneCannotBeDeletedAndNamesIt() throws IOException {
        // A server left because another one refused bills until somebody notices.
        try (StubApi stub = twoServersTheFirstRefusing(NOW, "run-1")) {
            final Hetzner hetzner = Hetzner.against(stub.base(), "run-1");

            assertThatThrownBy(hetzner::deleteMine)
                    .as("the run's clean-up fails, naming the server it could not delete")
                    .isInstanceOf(IOException.class)
                    .hasMessageContaining("refused one");
            assertThat(stub.asked())
                    .as("the second server is deleted all the same")
                    .contains("DELETE /v1/servers/2");
        }
    }

    @Test
    void sweepsTheRestWhenOneServerCannotBeDeletedAndNamesIt() throws IOException {
        try (StubApi stub = twoServersTheFirstRefusing(LONG_AGO, "gone")) {
            final Hetzner hetzner = Hetzner.against(stub.base(), "run-1");

            assertThatThrownBy(() -> hetzner.sweep(false, false))
                    .as("the sweep fails, naming the server it could not delete")
                    .isInstanceOf(IOException.class)
                    .hasMessageContaining("refused one");
            assertThat(stub.asked())
                    .as("the second server is swept all the same")
                    .contains("DELETE /v1/servers/2");
        }
    }

    private static StubApi twoServersTheFirstRefusing(String created, String run)
            throws IOException {
        return new StubApi()
                .answering(
                        "/servers?label_selector=porelo=ci&page=1&per_page=50",
                        """
                    {"servers":[
                      {"id":1,"name":"refused one","labels":{"porelo":"ci","run":"%s"},"created":"%s"},
                      {"id":2,"name":"second one","labels":{"porelo":"ci","run":"%s"},"created":"%s"}],
                     "meta":{"pagination":{"next_page":null}}}"""
                                .formatted(run, created, run, created))
                .answering(
                        "/servers/1",
                        exchange ->
                                new StubApi.Answer(
                                        423,
                                        "{\"error\":{\"code\":\"locked\",\"message\":\"server is locked\"}}"))
                .answering("/servers/2", "{\"action\":{\"id\":5,\"status\":\"success\"}}");
    }

    @Test
    void sweepsWhatIsPastItsExpiryWhoeverLeftItAndSaysWhatItWouldDoFirst() throws IOException {
        // What a process killed between two statements leaves behind bills until it is swept.
        final AtomicInteger deleted = new AtomicInteger();
        try (StubApi stub =
                new StubApi()
                        .answering(
                                "/servers?label_selector=porelo=ci&page=1&per_page=50",
                                """
                    {"servers":[
                      {"id":1,"name":"left behind","labels":{"run":"gone","porelo-expires":"%s"},
                       "created":"%s"},
                      {"id":2,"name":"in use","labels":{"run":"live","porelo-expires":"%s"},
                       "created":"%s"}],
                     "meta":{"pagination":{"next_page":null}}}"""
                                        .formatted(JUST_EXPIRED, NOW, STILL_IN_USE, LONG_AGO))
                        .answering(
                                "/servers/1",
                                exchange -> {
                                    deleted.incrementAndGet();
                                    return new StubApi.Answer(
                                            200, "{\"action\":{\"id\":5,\"status\":\"success\"}}");
                                })) {
            final Hetzner hetzner = Hetzner.against(stub.base(), "run-1").at(SWEEP_TIME);
            assertThat(hetzner.sweep(false, true)).isEqualTo(1);
            assertThat(deleted.get()).as("a dry run must not delete").isZero();
            assertThat(hetzner.sweep(false, false)).isEqualTo(1);
            assertThat(deleted.get())
                    .as("the server a minute past its expiry is deleted")
                    .isEqualTo(1);
            assertThat(stub.asked())
                    .as("the server a minute within its time is not")
                    .doesNotContain("DELETE /v1/servers/2");
        }
    }

    @Test
    void sweepsAServerThatSaysNothingOfItsTimeHoweverYoung() throws IOException {
        // A porelo=ci server with no expiry was made by nothing that writes one: nobody can say
        // whose it is or how long it was meant for, so it bills until it is swept.
        try (StubApi stub =
                new StubApi()
                        .answering(
                                "/servers?label_selector=porelo=ci&page=1&per_page=50",
                                """
                    {"servers":[{"id":1,"name":"unattributed","labels":{"porelo":"ci"},"created":"%s"}],
                     "meta":{"pagination":{"next_page":null}}}"""
                                        .formatted(NOW))
                        .answering(
                                "/servers/1", "{\"action\":{\"id\":5,\"status\":\"success\"}}")) {
            assertThat(Hetzner.against(stub.base(), "run-1").at(SWEEP_TIME).sweep(false, false))
                    .isEqualTo(1);
            assertThat(stub.asked())
                    .as("a server created after the sweep's now but with no expiry is deleted")
                    .contains("DELETE /v1/servers/1");
        }
    }

    @Test
    void sweepsAServerWithinItsTimeOnlyWhenToldToIncludeThoseInUse() throws IOException {
        // Deleting a server in use ends somebody's run, so it takes a word of its own.
        try (StubApi stub = oneServerExpiring(STILL_IN_USE)) {
            final Hetzner hetzner = Hetzner.against(stub.base(), "run-1").at(SWEEP_TIME);
            assertThat(hetzner.sweep(false, false)).isZero();
            assertThat(stub.asked())
                    .as("without --including-in-use a server within its time is kept")
                    .doesNotContain("DELETE /v1/servers/1");

            assertThat(hetzner.sweep(true, false)).isEqualTo(1);
            assertThat(stub.asked()).as("with it, it is deleted").contains("DELETE /v1/servers/1");
        }
    }

    @Test
    void keepsAServerWhoseExpiryItCannotReadAndFailsNamingIt() throws IOException {
        // An expiry in a form this does not read was written by something newer or broken, not
        // left out; deleting the server may end a run in flight, and keeping it quietly bills.
        try (StubApi stub = oneServerExpiring("1760760780")) {
            final Hetzner hetzner = Hetzner.against(stub.base(), "run-1").at(SWEEP_TIME);
            assertThatThrownBy(() -> hetzner.sweep(false, false))
                    .as("the sweep fails, naming the server and what its label held")
                    .isInstanceOf(IOException.class)
                    .hasMessageContaining("expiring")
                    .hasMessageContaining("porelo-expires=1760760780");
            assertThat(stub.asked())
                    .as("the server is kept")
                    .doesNotContain("DELETE /v1/servers/1");
        }
    }

    private static StubApi oneServerExpiring(String expires) throws IOException {
        return new StubApi()
                .answering(
                        "/servers?label_selector=porelo=ci&page=1&per_page=50",
                        """
                    {"servers":[{"id":1,"name":"expiring","labels":{"porelo":"ci","run":"live",
                      "porelo-expires":"%s"},"created":"%s"}],
                     "meta":{"pagination":{"next_page":null}}}"""
                                .formatted(expires, LONG_AGO))
                .answering("/servers/1", "{\"action\":{\"id\":5,\"status\":\"success\"}}");
    }

    @Test
    void keepsAServerWithinItsTimeHoweverOldItIs() throws IOException {
        // A test that runs longer than an hour lost its machine to the hourly sweep while it was
        // still running; the time it was made for is what decides, not its age.
        try (StubApi stub =
                new StubApi()
                        .answering(
                                "/servers?label_selector=porelo=ci&page=1&per_page=50",
                                """
                    {"servers":[{"id":1,"name":"long test","labels":{"porelo":"ci","run":"live",
                      "porelo-expires":"29990101T0000Z"},"created":"%s"}],
                     "meta":{"pagination":{"next_page":null}}}"""
                                        .formatted(LONG_AGO))
                        .answering(
                                "/servers/1", "{\"action\":{\"id\":5,\"status\":\"success\"}}")) {
            Hetzner.against(stub.base(), "run-1").sweep(false, false);

            assertThat(stub.asked())
                    .as("a server older than an hour whose expiry lies ahead is kept")
                    .doesNotContain("DELETE /v1/servers/1");
        }
    }

    @Test
    void namesWhatItKeptSoZeroDeletionsIsNotTwoDifferentThings() throws IOException {
        // "0 to delete" covers a project holding three machines all in use and a project holding
        // none. The script this is being checked against says which, and so must this, or the two
        // cannot be compared on a real project at all.
        final java.io.ByteArrayOutputStream said = new java.io.ByteArrayOutputStream();
        final java.io.PrintStream was = System.out;
        try (StubApi stub =
                new StubApi()
                        .answering(
                                "/servers?label_selector=porelo=ci&page=1&per_page=50",
                                """
                    {"servers":[{"id":2,"name":"in use","labels":{"porelo-expires":"%s"},
                      "created":"%s"}],
                     "meta":{"pagination":{"next_page":null}}}"""
                                        .formatted(STILL_IN_USE, NOW))) {
            System.setOut(
                    new java.io.PrintStream(said, true, java.nio.charset.StandardCharsets.UTF_8));
            try {
                assertThat(Hetzner.against(stub.base(), "run-1").at(SWEEP_TIME).sweep(false, true))
                        .isZero();
            } finally {
                System.setOut(was);
            }
        }
        assertThat(said.toString(java.nio.charset.StandardCharsets.UTF_8))
                .contains("keeping in use")
                .contains("nothing to sweep");
    }

    @Test
    void matchesTheProjectsKeyByFingerprintRatherThanByName() throws IOException {
        final Credential credential = Keys.generated();
        final String fingerprint = Fingerprint.md5(credential);
        try (StubApi stub =
                new StubApi()
                        .answering(
                                "/ssh_keys?page=1&per_page=50",
                                """
                {"ssh_keys":[
                  {"id":1,"name":"somebody else's","fingerprint":"00:11:22"},
                  {"id":2,"name":"ours","fingerprint":"%s"}],
                 "meta":{"pagination":{"next_page":null}}}"""
                                        .formatted(fingerprint))) {
            // Naming it means keeping the secret and the registered key in step; when they drift
            // the server is created with a public key nobody holds, and that shows up as a
            // connection refused twenty lines later.
            assertThat(Hetzner.against(stub.base(), "run-1").keyMatching(credential)).isEqualTo(2L);
        }
    }

    @Test
    void saysWhatTheProjectHoldsWhenNoKeyMatches() throws IOException {
        final Credential credential = Keys.generated();
        try (StubApi stub =
                new StubApi()
                        .answering(
                                "/ssh_keys?page=1&per_page=50",
                                """
                {"ssh_keys":[{"id":1,"name":"somebody else's","fingerprint":"00:11:22"}],
                 "meta":{"pagination":{"next_page":null}}}""")) {
            assertThatThrownBy(() -> Hetzner.against(stub.base(), "run-1").keyMatching(credential))
                    .isInstanceOf(IOException.class)
                    .hasMessageContaining("somebody else's=00:11:22");
        }
    }

    @Test
    void findsServersOnEveryPage() throws IOException {
        try (StubApi stub =
                new StubApi()
                        .answering(
                                "/servers?label_selector=porelo=ci&page=1&per_page=50",
                                """
                    {"servers":[{"id":1,"name":"page one","labels":{},"created":"%s"}],
                     "meta":{"pagination":{"next_page":2}}}"""
                                        .formatted(NOW))
                        .answering(
                                "/servers?label_selector=porelo=ci&page=2&per_page=50",
                                """
                    {"servers":[{"id":2,"name":"page two","labels":{},"created":"%s"}],
                     "meta":{"pagination":{"next_page":null}}}"""
                                        .formatted(NOW))) {
            final List<Hetzner.Server> servers = Hetzner.against(stub.base(), "run-1").servers();
            assertThat(servers)
                    .extracting(Hetzner.Server::name)
                    .containsExactly("page one", "page two");
        }
    }

    @org.junit.jupiter.api.Nested
    class Acquisition {
        private static final Duration NO_WAIT = Duration.ofMillis(1);

        private static StubApi lookups() throws IOException {
            return new StubApi()
                    .answering(
                            "/images?type=snapshot&label_selector=porelo=ci,os=ubuntu&page=1&per_page=50",
                            """
                        {"images":[{"id":11,"description":"ubuntu","status":"available",
                          "created":"2026-09-01T00:00:00+00:00"}],
                         "meta":{"pagination":{"next_page":null}}}""")
                    .answering(
                            "/server_types?name=cpx41&page=1&per_page=50",
                            "{\"server_types\":[{\"id\":9,\"locations\":[{\"name\":\"nbg1\",\"available\":true}]}],\"meta\":{\"pagination\":{\"next_page\":null}}}")
                    .answering(
                            "/locations?page=1&per_page=50",
                            """
                        {"locations":[{"name":"nbg1","network_zone":"eu-central"}],"meta":{"pagination":{"next_page":null}}}""");
        }

        /**
         * The type these stubs answer for.
         *
         * <p>Named here rather than taken from {@link Spec#DEFAULT_TYPES}: a test that follows the
         * default breaks when the default moves for a reason that has nothing to do with it, which
         * is what happened when the images gained a 320 GB floor.
         */
        private static final String TYPE = "cpx41";

        private static Spec spec(Credential credential) {
            return Spec.of("porelo-ci-test", "ubuntu", "build", credential)
                    .tryingInOrder(List.of(TYPE));
        }

        @Test
        void waitsOutAFullProjectRatherThanFailingTheRun() throws IOException {
            final Credential credential = Keys.generated();
            final AtomicInteger attempts = new AtomicInteger();
            try (StubApi stub =
                    lookups()
                            .answering(
                                    "/ssh_keys?page=1&per_page=50",
                                    keys(Fingerprint.md5(credential)))
                            .answering(
                                    "/servers",
                                    exchange -> {
                                        if (attempts.incrementAndGet() < 3) {
                                            // Four repositories rent machines and nothing
                                            // coordinates
                                            // them, so overlapping runs collide. Failing here kills
                                            // a
                                            // run twenty minutes in, after it has already built
                                            // everything.
                                            return new StubApi.Answer(
                                                    403,
                                                    "{\"error\":{\"code\":"
                                                            + "\"resource_limit_exceeded\",\"message\":\"full\"}}");
                                        }
                                        return new StubApi.Answer(201, created());
                                    })
                            .answering(
                                    "/servers/77",
                                    exchange ->
                                            new StubApi.Answer(
                                                    200,
                                                    "{\"action\":{\"id\":5,\"status\":\"success\"}}"))) {
                final Hetzner hetzner = Hetzner.against(stub.base(), "run-1", NO_WAIT);
                try (Lease rental = hetzner.acquire(spec(credential))) {
                    assertThat(rental.address()).isEqualTo("1.2.3.4");
                }
                assertThat(attempts.get()).isEqualTo(3);
            }
        }

        @Test
        void createsTheServerWithAnIpv4AddressAndNoIpv6One() throws IOException {
            // The project may hold 16 primary IPs, and a server made with both kinds takes two: 8
            // servers filled it on
            // 2026-10-18 while the vCPUs stood at 20 of 32. Nothing reads the IPv6 address.
            final Credential credential = Keys.generated();
            final AtomicReference<String> sent = new AtomicReference<>();
            try (StubApi stub =
                    lookups()
                            .answering(
                                    "/ssh_keys?page=1&per_page=50",
                                    keys(Fingerprint.md5(credential)))
                            .answering(
                                    "/servers",
                                    exchange -> {
                                        try {
                                            sent.set(
                                                    new String(
                                                            exchange.getRequestBody()
                                                                    .readAllBytes(),
                                                            StandardCharsets.UTF_8));
                                        } catch (IOException ex) {
                                            throw new java.io.UncheckedIOException(ex);
                                        }
                                        return new StubApi.Answer(201, created());
                                    })
                            .answering(
                                    "/servers/77",
                                    exchange ->
                                            new StubApi.Answer(
                                                    200,
                                                    "{\"action\":{\"id\":5,\"status\":\"success\"}}"))) {
                try (Lease rental =
                        Hetzner.against(stub.base(), "run-1", NO_WAIT).acquire(spec(credential))) {
                    assertThat(rental.address()).isEqualTo("1.2.3.4");
                }
            }
            assertThat(Values.object(Values.object(Json.parse(sent.get())), "public_net"))
                    .as("the server create's public network, in %s", sent.get())
                    .isEqualTo(Map.of("enable_ipv4", Boolean.TRUE, "enable_ipv6", Boolean.FALSE));
        }

        @Test
        void labelsTheServerWithTheEndOfTheTimeItsJobMayHoldIt() throws IOException {
            // Without it the sweep of everyone's servers cannot tell one in use from one forgotten,
            // and either takes a long test's machine or leaves a leaked one billing.
            final Clock created =
                    Clock.fixed(Instant.parse("2026-10-18T04:13:27Z"), ZoneOffset.UTC);
            assertThat(labelsSent(hetzner -> hetzner.at(created)))
                    .as("by default an hour's job plus the margin, the seconds dropped")
                    .isEqualTo(
                            Map.of(
                                    "porelo",
                                    "ci",
                                    "run",
                                    "run-1",
                                    "porelo-expires",
                                    "20261018T0528Z"));
            assertThat(labelsSent(hetzner -> hetzner.at(created).forJobsOf(Duration.ofMinutes(90))))
                    .as("a job of 90 minutes, plus the margin")
                    .isEqualTo(
                            Map.of(
                                    "porelo",
                                    "ci",
                                    "run",
                                    "run-1",
                                    "porelo-expires",
                                    "20261018T0558Z"));
        }

        private static Map<String, Object> labelsSent(
                java.util.function.UnaryOperator<Hetzner> configured) throws IOException {
            final Credential credential = Keys.generated();
            final AtomicReference<String> sent = new AtomicReference<>();
            try (StubApi stub =
                    lookups()
                            .answering(
                                    "/ssh_keys?page=1&per_page=50",
                                    keys(Fingerprint.md5(credential)))
                            .answering(
                                    "/servers",
                                    exchange -> {
                                        try {
                                            sent.set(
                                                    new String(
                                                            exchange.getRequestBody()
                                                                    .readAllBytes(),
                                                            StandardCharsets.UTF_8));
                                        } catch (IOException ex) {
                                            throw new java.io.UncheckedIOException(ex);
                                        }
                                        return new StubApi.Answer(201, created());
                                    })
                            .answering(
                                    "/servers/77",
                                    exchange ->
                                            new StubApi.Answer(
                                                    200,
                                                    "{\"action\":{\"id\":5,\"status\":\"success\"}}"))) {
                try (Lease rental =
                        configured
                                .apply(Hetzner.against(stub.base(), "run-1", NO_WAIT))
                                .acquire(spec(credential))) {
                    assertThat(rental.address()).isEqualTo("1.2.3.4");
                }
            }
            return Values.object(Values.object(Json.parse(sent.get())), "labels");
        }

        @Test
        void createsTheServerWithAHostKeyMadeForItAndPinsThatKey() throws IOException {
            // Without it a connection accepts whatever answers at the address, and somebody on the
            // path
            // to the provider can take what a leg sends and answer it as passed.
            final Credential credential = Keys.generated();
            final AtomicReference<String> sent = new AtomicReference<>();
            final HostKey pinned;
            try (StubApi stub =
                    lookups()
                            .answering(
                                    "/ssh_keys?page=1&per_page=50",
                                    keys(Fingerprint.md5(credential)))
                            .answering(
                                    "/servers",
                                    exchange -> {
                                        try {
                                            sent.set(
                                                    new String(
                                                            exchange.getRequestBody()
                                                                    .readAllBytes(),
                                                            StandardCharsets.UTF_8));
                                        } catch (IOException ex) {
                                            throw new java.io.UncheckedIOException(ex);
                                        }
                                        return new StubApi.Answer(201, created());
                                    })
                            .answering(
                                    "/servers/77",
                                    exchange ->
                                            new StubApi.Answer(
                                                    200,
                                                    "{\"action\":{\"id\":5,\"status\":\"success\"}}"))) {
                try (Lease rental =
                        Hetzner.against(stub.base(), "run-1", NO_WAIT).acquire(spec(credential))) {
                    pinned = rental.hostKey();
                }
            }
            final String userData = Values.text(Values.object(Json.parse(sent.get())), "user_data");
            final String block =
                    userData.lines()
                            .filter(line -> line.startsWith("    "))
                            .map(String::strip)
                            .collect(java.util.stream.Collectors.joining("\n", "", "\n"));
            assertThat(pinned)
                    .as("the lease pins the key whose private half the server was created with")
                    .isEqualTo(HostKey.pinned(HostKeyPairTest.publicLineOf(block)));
        }

        @Test
        void givesUpOnAFullProjectOnlyAfterAnHour() throws IOException {
            final Credential credential = Keys.generated();
            final AtomicInteger attempts = new AtomicInteger();
            try (StubApi stub =
                    lookups()
                            .answering(
                                    "/ssh_keys?page=1&per_page=50",
                                    keys(Fingerprint.md5(credential)))
                            .answering(
                                    "/servers",
                                    exchange -> {
                                        attempts.incrementAndGet();
                                        return new StubApi.Answer(
                                                403,
                                                "{\"error\":{\"code\":"
                                                        + "\"resource_limit_exceeded\",\"message\":\"full\"}}");
                                    })) {
                assertThatThrownBy(
                                () ->
                                        Hetzner.against(stub.base(), "run-1", NO_WAIT)
                                                .acquire(spec(credential)))
                        .isInstanceOf(IOException.class)
                        .hasMessageContaining("after 60 minutes");
                // Acceptance legs run one after another, so a run queued behind others waits out
                // their
                // whole legs: every 30 seconds for an hour.
                assertThat(attempts.get()).isEqualTo(120);
            }
        }

        @Test
        void movesToTheNextTypeWhenOneWillNotTakeTheImage() throws IOException {
            final Credential credential = Keys.generated();
            final AtomicInteger creates = new AtomicInteger();
            try (StubApi stub =
                    new StubApi()
                            .answering(
                                    "/images?type=snapshot&label_selector=porelo=ci,os=ubuntu&page=1&per_page=50",
                                    """
                        {"images":[{"id":11,"description":"ubuntu","status":"available",
                          "created":"2026-09-01T00:00:00+00:00"}],
                         "meta":{"pagination":{"next_page":null}}}""")
                            .answering(
                                    "/server_types?name=cx23&page=1&per_page=50",
                                    "{\"server_types\":[{\"id\":23,\"locations\":[{\"name\":\"nbg1\",\"available\":true}]}],\"meta\":{\"pagination\":{\"next_page\":null}}}")
                            .answering(
                                    "/server_types?name=cpx42&page=1&per_page=50",
                                    "{\"server_types\":[{\"id\":42,\"locations\":[{\"name\":\"nbg1\",\"available\":true}]}],\"meta\":{\"pagination\":{\"next_page\":null}}}")
                            .answering(
                                    "/locations?page=1&per_page=50",
                                    """
                        {"locations":[{"name":"nbg1","network_zone":"eu-central"}],"meta":{"pagination":{"next_page":null}}}""")
                            .answering(
                                    "/ssh_keys?page=1&per_page=50",
                                    """
                        {"ssh_keys":[{"id":2,"name":"ours","fingerprint":"%s"}],
                         "meta":{"pagination":{"next_page":null}}}"""
                                            .formatted(Fingerprint.md5(credential)))
                            .answering(
                                    "/servers",
                                    exchange -> {
                                        if (creates.incrementAndGet() == 1) {
                                            // A snapshot can only be restored onto a disk at least
                                            // as
                                            // big as the one it was taken from. Measured against
                                            // the
                                            // real API, which answers 422.
                                            return new StubApi.Answer(
                                                    422,
                                                    "{\"error\":{\"code\":\"invalid_input\","
                                                            + "\"message\":\"image disk is bigger than server type disk\"}}");
                                        }
                                        return new StubApi.Answer(
                                                201,
                                                """
                            {"server":{"id":77,"name":"p",
                               "public_net":{"ipv4":{"ip":"1.2.3.4"}}},
                             "action":{"id":5,"status":"success"}}""");
                                    })
                            .answering(
                                    "/servers/77",
                                    exchange ->
                                            new StubApi.Answer(
                                                    200,
                                                    "{\"action\":{\"id\":6,\"status\":\"success\"}}"))) {
                final Hetzner hetzner = Hetzner.against(stub.base(), "run-1", NO_WAIT);
                try (Lease lease =
                        hetzner.acquire(
                                Spec.of("p", "ubuntu", "build", credential)
                                        .tryingInOrder(List.of("cx23", "cpx42")))) {
                    assertThat(lease.address()).isEqualTo("1.2.3.4");
                }
                assertThat(creates.get()).as("did not try the second type").isEqualTo(2);
            }
        }

        @Test
        void saysWhyNoTypeCouldHostTheImage() throws IOException {
            final Credential credential = Keys.generated();
            try (StubApi stub =
                    new StubApi()
                            .answering(
                                    "/images?type=snapshot&label_selector=porelo=ci,os=ubuntu&page=1&per_page=50",
                                    """
                        {"images":[{"id":11,"description":"ubuntu","status":"available",
                          "created":"2026-09-01T00:00:00+00:00"}],
                         "meta":{"pagination":{"next_page":null}}}""")
                            .answering(
                                    "/server_types?name=cx23&page=1&per_page=50",
                                    "{\"server_types\":[{\"id\":23,\"locations\":[{\"name\":\"nbg1\",\"available\":true}]}],\"meta\":{\"pagination\":{\"next_page\":null}}}")
                            .answering(
                                    "/locations?page=1&per_page=50",
                                    """
                        {"locations":[{"name":"nbg1","network_zone":"eu-central"}],"meta":{"pagination":{"next_page":null}}}""")
                            .answering(
                                    "/ssh_keys?page=1&per_page=50",
                                    """
                        {"ssh_keys":[{"id":2,"name":"ours","fingerprint":"%s"}],
                         "meta":{"pagination":{"next_page":null}}}"""
                                            .formatted(Fingerprint.md5(credential)))
                            .answering(
                                    "/servers",
                                    exchange ->
                                            new StubApi.Answer(
                                                    422,
                                                    "{\"error\":{\"code\":\"invalid_input\",\"message\":"
                                                            + "\"image disk is bigger than server type disk\"}}"))) {
                assertThatThrownBy(
                                () ->
                                        Hetzner.against(stub.base(), "run-1", NO_WAIT)
                                                .acquire(
                                                        Spec.of("p", "ubuntu", "build", credential)
                                                                .tryingInOrder(List.of("cx23"))))
                        .isInstanceOf(IOException.class)
                        .hasMessageContaining("could host")
                        .hasMessageContaining("smaller disk");
            }
        }

        @Test
        void givesUpOnAnythingWaitingCannotFix() throws IOException {
            final Credential credential = Keys.generated();
            final AtomicInteger attempts = new AtomicInteger();
            try (StubApi stub =
                    lookups()
                            .answering(
                                    "/ssh_keys?page=1&per_page=50",
                                    keys(Fingerprint.md5(credential)))
                            .answering(
                                    "/servers",
                                    exchange -> {
                                        attempts.incrementAndGet();
                                        return new StubApi.Answer(
                                                400,
                                                "{\"error\":{\"code\":\"invalid_input\","
                                                        + "\"message\":\"image is not available\"}}");
                                    })) {
                // Burning an hour before reporting a bad image would be worse than failing now.
                assertThatThrownBy(
                                () ->
                                        Hetzner.against(stub.base(), "run-1", NO_WAIT)
                                                .acquire(spec(credential)))
                        .isInstanceOf(IOException.class)
                        .hasMessageContaining("image is not available");
                assertThat(attempts.get())
                        .as("retried something that waiting cannot fix")
                        .isEqualTo(1);
            }
        }

        @Test
        void deletesTheServerEvenWhenTheWorkFailed() throws IOException {
            final Credential credential = Keys.generated();
            final AtomicInteger deleted = new AtomicInteger();
            try (StubApi stub =
                    lookups()
                            .answering(
                                    "/ssh_keys?page=1&per_page=50",
                                    keys(Fingerprint.md5(credential)))
                            .answering("/servers", exchange -> new StubApi.Answer(201, created()))
                            .answering(
                                    "/servers/77",
                                    exchange -> {
                                        deleted.incrementAndGet();
                                        return new StubApi.Answer(
                                                200,
                                                "{\"action\":{\"id\":5,\"status\":\"success\"}}");
                                    })) {
                final Hetzner hetzner = Hetzner.against(stub.base(), "run-1", NO_WAIT);
                assertThatThrownBy(
                                () -> {
                                    try (Lease rental = hetzner.acquire(spec(credential))) {
                                        throw new IllegalStateException(
                                                "the work failed on line three");
                                    }
                                })
                        .isInstanceOf(IllegalStateException.class);
                // The expensive mistake is a server that outlives a script which failed early.
                assertThat(deleted.get()).isEqualTo(1);
            }
        }

        @Test
        void keepsTheServerWhenAskedAndSaysItIsCostingMoney() throws IOException {
            final Credential credential = Keys.generated();
            final AtomicInteger deleted = new AtomicInteger();
            try (StubApi stub =
                    lookups()
                            .answering(
                                    "/ssh_keys?page=1&per_page=50",
                                    keys(Fingerprint.md5(credential)))
                            .answering("/servers", exchange -> new StubApi.Answer(201, created()))
                            .answering(
                                    "/servers/77",
                                    exchange -> {
                                        deleted.incrementAndGet();
                                        return new StubApi.Answer(
                                                200,
                                                "{\"action\":{\"id\":5,\"status\":\"success\"}}");
                                    })) {
                final Hetzner hetzner = Hetzner.against(stub.base(), "run-1", NO_WAIT);
                try (Lease rental = hetzner.acquire(spec(credential).kept())) {
                    assertThat(rental.address()).isEqualTo("1.2.3.4");
                }
                assertThat(deleted.get()).isZero();
            }
        }

        @Test
        void waitsForTheCreateToFinishBecauseCreatedIsNotReady() throws IOException {
            final Credential credential = Keys.generated();
            final AtomicInteger polls = new AtomicInteger();
            try (StubApi stub =
                    lookups()
                            .answering(
                                    "/ssh_keys?page=1&per_page=50",
                                    keys(Fingerprint.md5(credential)))
                            .answering(
                                    "/servers",
                                    exchange -> new StubApi.Answer(201, created("running")))
                            .answering(
                                    "/actions/5",
                                    exchange ->
                                            new StubApi.Answer(
                                                    200,
                                                    "{\"action\":{\"id\":5,\"status\":\""
                                                            + (polls.incrementAndGet() < 2
                                                                    ? "running"
                                                                    : "success")
                                                            + "\"}}"))
                            .answering(
                                    "/servers/77",
                                    exchange ->
                                            new StubApi.Answer(
                                                    200,
                                                    "{\"action\":{\"id\":6,\"status\":\"success\"}}"))) {
                try (Lease rental =
                        Hetzner.against(stub.base(), "run-1", NO_WAIT).acquire(spec(credential))) {
                    assertThat(rental.address()).isEqualTo("1.2.3.4");
                }
                assertThat(polls.get()).isGreaterThanOrEqualTo(2);
            }
        }

        @Test
        void failsWhenTheCreateActionItselfFails() throws IOException {
            final Credential credential = Keys.generated();
            try (StubApi stub =
                    lookups()
                            .answering(
                                    "/ssh_keys?page=1&per_page=50",
                                    keys(Fingerprint.md5(credential)))
                            .answering(
                                    "/servers",
                                    exchange -> new StubApi.Answer(201, created("error")))
                            .answering(
                                    "/servers/77",
                                    exchange ->
                                            new StubApi.Answer(
                                                    200,
                                                    "{\"action\":{\"id\":6,\"status\":\"success\"}}"))) {
                assertThatThrownBy(
                                () ->
                                        Hetzner.against(stub.base(), "run-1", NO_WAIT)
                                                .acquire(spec(credential)))
                        .isInstanceOf(IOException.class)
                        .hasMessageContaining("ended as 'error'");
                // The server exists and bills from the moment the API answered, whether or not it
                // ever
                // came up.
                assertThat(stub.asked()).contains("DELETE /v1/servers/77");
            }
        }

        @Test
        void deletesAServerItCouldNotReadAndKeepsWhyTheDeleteFailedToo() throws IOException {
            final Credential credential = Keys.generated();
            try (StubApi stub =
                    lookups()
                            .answering(
                                    "/ssh_keys?page=1&per_page=50",
                                    keys(Fingerprint.md5(credential)))
                            .answering(
                                    "/servers",
                                    exchange ->
                                            new StubApi.Answer(
                                                    201,
                                                    """
                        {"server":{"id":77,"name":"porelo-ci-test","public_net":{}},
                         "action":{"id":5,"status":"success"}}"""))
                            .answering(
                                    "/servers/77",
                                    exchange ->
                                            new StubApi.Answer(
                                                    500,
                                                    "{\"error\":{\"code\":\"server_error\",\"message\":\"try again\"}}"))) {
                assertThatThrownBy(
                                () ->
                                        Hetzner.against(stub.base(), "run-1", NO_WAIT)
                                                .acquire(spec(credential)))
                        .satisfies(
                                thrown ->
                                        assertThat(thrown.getSuppressed())
                                                .as("why the delete failed")
                                                .anySatisfy(
                                                        delete ->
                                                                assertThat(delete)
                                                                        .hasMessageContaining(
                                                                                "try again")));
                assertThat(stub.asked()).contains("DELETE /v1/servers/77");
            }
        }

        private static String keys(String fingerprint) {
            return """
            {"ssh_keys":[{"id":2,"name":"ours","fingerprint":"%s"}],
             "meta":{"pagination":{"next_page":null}}}"""
                    .formatted(fingerprint);
        }

        private static String created() {
            return created("success");
        }

        private static String created(String status) {
            return """
            {"server":{"id":77,"name":"porelo-ci-test",
               "public_net":{"ipv4":{"ip":"1.2.3.4"}}},
             "action":{"id":5,"status":"%s"}}"""
                    .formatted(status);
        }
    }

    @org.junit.jupiter.api.Nested
    class CommandLine {
        private static final Consumer<String> QUIET = message -> {};

        @Test
        void keepsAServerWithinItsTimeWhenAskedToActuallyDelete() throws IOException {
            // '--now' means "delete rather than say", not "delete everything". Ignoring the expiry
            // here
            // would take out the servers of every run in flight.
            try (StubApi stub = twoServers("live")) {
                assertThat(
                                Main.run(
                                        new String[] {"sweep", "--now"},
                                        QUIET,
                                        () -> Hetzner.against(stub.base(), "run-1").at(SWEEP_TIME)))
                        .isZero();
                assertThat(stub.asked()).contains("DELETE /v1/servers/1");
                assertThat(stub.asked()).doesNotContain("DELETE /v1/servers/2");
            }
        }

        @Test
        void deletesServersInUseOnlyWhenToldTo() throws IOException {
            try (StubApi stub = twoServers("live")) {
                assertThat(
                                Main.run(
                                        new String[] {"sweep", "--including-in-use", "--now"},
                                        QUIET,
                                        () -> Hetzner.against(stub.base(), "run-1").at(SWEEP_TIME)))
                        .isZero();
                assertThat(stub.asked())
                        .as("--including-in-use deletes the server within its time too")
                        .contains("DELETE /v1/servers/1", "DELETE /v1/servers/2");
            }
        }

        @Test
        void deletesTheRunsOwnServersWithinTheirTime() throws IOException {
            // A job clearing up after itself does so while its servers are still within their time;
            // keeping them until they expire would bill for the margin on every run.
            try (StubApi stub = twoServers("run-1")) {
                assertThat(
                                Main.run(
                                        new String[] {"sweep", "--mine"},
                                        QUIET,
                                        () -> Hetzner.against(stub.base(), "run-1").at(SWEEP_TIME)))
                        .isZero();
                assertThat(stub.asked())
                        .as("--mine deletes the run's server whose expiry lies ahead")
                        .contains("DELETE /v1/servers/2");
            }
        }

        @Test
        void deletesNothingUnlessToldTo() throws IOException {
            // Dry by default, and a non-zero exit so a scheduled run that found something it was
            // not
            // allowed to remove is visible rather than quietly green.
            try (StubApi stub = twoServers("live")) {
                assertThat(
                                Main.run(
                                        new String[] {"sweep"},
                                        QUIET,
                                        () -> Hetzner.against(stub.base(), "run-1").at(SWEEP_TIME)))
                        .isEqualTo(1);
                assertThat(stub.asked())
                        .doesNotContain("DELETE /v1/servers/1", "DELETE /v1/servers/2");
            }
        }

        /**
         * One server a minute past its expiry and one a minute within its time at {@link
         * #SWEEP_TIME}, both ninety minutes old.
         *
         * @param run The run the one within its time belongs to.
         * @return The stub.
         * @throws IOException If it cannot be started.
         */
        private static StubApi twoServers(String run) throws IOException {
            final String old = "2026-10-18T02:43:00+00:00";
            return new StubApi()
                    .answering(
                            "/servers?label_selector=porelo=ci&page=1&per_page=50",
                            """
                    {"servers":[
                      {"id":1,"name":"forgotten","labels":{"run":"gone",
                        "porelo-expires":"20261018T0412Z"},"created":"%s"},
                      {"id":2,"name":"in use","labels":{"run":"%s",
                        "porelo-expires":"20261018T0414Z"},"created":"%s"}],
                     "meta":{"pagination":{"next_page":null}}}"""
                                    .formatted(old, run, old))
                    .answering(
                            "/servers/1",
                            exchange ->
                                    new StubApi.Answer(
                                            200, "{\"action\":{\"id\":5,\"status\":\"success\"}}"))
                    .answering(
                            "/servers/2",
                            exchange ->
                                    new StubApi.Answer(
                                            200, "{\"action\":{\"id\":6,\"status\":\"success\"}}"));
        }
    }

    @org.junit.jupiter.api.Nested
    class CurrentSnapshots {
        @Test
        void willNotBuildAnImageOnAMachineTooSmallToBuildOn() {
            // cpx12 has one core and 2 GB and is often the only small type on offer, so the
            // availability fallback picked it - for a job that compiles six native images, one of
            // which
            // peaked at 2.32 GB resident.
            assertThat(CentralSetup.BUILD_TYPES).doesNotContain("cpx12");
            assertThat(CentralSetup.BUILD_TYPES).isNotEmpty();
        }

        @Test
        void takesTheNewestSnapshotAsCurrentOnlyWhenItWasMadeFromTheSameInputs()
                throws IOException {
            final String listing =
                    "/images?type=snapshot&label_selector=porelo=ci,os=ubuntu&page=1&per_page=50";
            try (StubApi stub =
                    new StubApi()
                            .answering(
                                    listing,
                                    "{\"images\":[{\"id\":3,\"status\":\"available\",\"created\":\"2026-10-17T00:00:00+00:00\","
                                            + "\"labels\":{\"porelo\":\"ci\",\"os\":\"ubuntu\",\"inputs\":\"0123456789abcdef\"}}],"
                                            + "\"meta\":{\"pagination\":{\"next_page\":null}}}")) {
                final Hetzner hetzner = Hetzner.against(stub.base(), "run-1");
                assertThat(Snapshots.current(hetzner, "ubuntu", "0123456789abcdef"))
                        .as("the same inputs")
                        .isTrue();
                assertThat(Snapshots.current(hetzner, "ubuntu", "fedcba9876543210"))
                        .as("other inputs")
                        .isFalse();
            }
        }

        @Test
        void takesASnapshotWithoutInputsOrNoneAtAllAsNotCurrent() throws IOException {
            final String listing =
                    "/images?type=snapshot&label_selector=porelo=ci,os=ubuntu&page=1&per_page=50";
            try (StubApi stub =
                    new StubApi()
                            .answering(
                                    listing,
                                    "{\"images\":[{\"id\":3,\"status\":\"available\",\"created\":\"2026-10-17T00:00:00+00:00\","
                                            + "\"labels\":{\"porelo\":\"ci\",\"os\":\"ubuntu\"}}],"
                                            + "\"meta\":{\"pagination\":{\"next_page\":null}}}")
                            .answering(
                                    "/images?type=snapshot&label_selector=porelo=ci,os=fedora&page=1&per_page=50",
                                    "{\"images\":[],\"meta\":{\"pagination\":{\"next_page\":null}}}")
                            .answering(
                                    "/images?type=snapshot&label_selector=porelo=ci&page=1&per_page=50",
                                    "{\"images\":[],\"meta\":{\"pagination\":{\"next_page\":null}}}")) {
                final Hetzner hetzner = Hetzner.against(stub.base(), "run-1");
                assertThat(Snapshots.current(hetzner, "ubuntu", "0123456789abcdef"))
                        .as("an image from before")
                        .isFalse();
                assertThat(Snapshots.current(hetzner, "fedora", "0123456789abcdef"))
                        .as("no image")
                        .isFalse();
            }
        }
    }

    @Test
    void previousTypeOrdersReachAcquisitionByKindWithTheirOverridesIntact() {
        // A compatibility adapter must neither lose capacity nor bypass the kind-based source.
        for (final List<String> types :
                List.of(
                        List.of("cpx12"),
                        Spec.DEFAULT_TYPES,
                        Snapshots.BUILD_TYPES,
                        List.of("ccx33"),
                        List.of("cx23", "cpx42"))) {
            final Kind expected =
                    types.equals(List.of("cpx12"))
                            ? Kind.AMD64
                            : types.equals(Snapshots.BUILD_TYPES)
                                    ? Kind.AMD64_SNAPSHOT
                                    : Kind.AMD64_BUILD;
            final Spec old = new Spec("old", "ubuntu", types, "root", Keys.generated(), false);
            final RecordingMachines source = new RecordingMachines();
            assertThatThrownBy(() -> source.acquire(old))
                    .as("the old request reaches the shared acquisition function")
                    .hasMessage("acquisition recorded");
            final Spec received = source.requested.getFirst();
            assertThat(received.kind()).as("%s maps to its capacity", types).isEqualTo(expected);
            assertThat(received.providerOverride())
                    .as("the explicit order survives routing")
                    .isEqualTo(types);
            assertThat(received.kept().serverTypes())
                    .as("keeping an old request preserves its override")
                    .isEqualTo(types);
        }
        assertThat(Spec.order(" ccx33, cpx42 "))
                .as("old comma-separated type orders still work")
                .containsExactly("ccx33", "cpx42");
        assertThat(Spec.order(" "))
                .as("old blank orders retain the old defaults")
                .isEqualTo(Spec.DEFAULT_TYPES);
        assertThatThrownBy(
                        () -> new Spec("bad", "ubuntu", List.of(), "root", Keys.generated(), false))
                .as("empty legacy orders must not start renting")
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void oldCommandLineTypesStillReachAcquisitionWithTheirOverrides() {
        // Existing workflows must keep the capacity they explicitly chose.
        for (final String command : List.of("snapshot", "acceptance", "lease")) {
            final RecordingMachines source = new RecordingMachines();
            final List<String> args =
                    new java.util.ArrayList<>(
                            List.of(command, "--os", "ubuntu", "--type", "ccx33"));
            if (command.equals("acceptance")) {
                args.addAll(List.of("--package", "porelo-agent", "--cucumber", "."));
            }
            assertThatThrownBy(
                            () ->
                                    Main.run(
                                            args.toArray(String[]::new),
                                            message -> {},
                                            Map.of(Main.OLD_SSH_KEY, "test key"),
                                            () -> source))
                    .as("%s retains --type and the previous key variable", command)
                    .hasMessage("acquisition recorded");
            final Spec request = source.requested.getFirst();
            assertThat(request.kind())
                    .as("%s routes its previous request by kind", command)
                    .isEqualTo(command.equals("snapshot") ? Kind.AMD64_SNAPSHOT : Kind.AMD64_BUILD);
            assertThat(request.providerOverride())
                    .as("%s preserves its explicit type", command)
                    .containsExactly("ccx33");
        }
    }

    @Test
    void kindsAndAccountCountsSelectAdequateProviderCapacity() throws IOException {
        // Small snapshot disks must not grow and several accounts must not share a tiny server.
        final List<List<String>> orders =
                List.of(
                        List.of("cpx12"),
                        List.of("cpx42", "cx43", "cpx32", "cx33"),
                        List.of("cpx42", "cx43", "cpx32", "cx33"),
                        List.of("cx23", "cpx22", "cx33"));
        final List<Kind> kinds =
                List.of(Kind.AMD64, Kind.AMD64, Kind.AMD64_BUILD, Kind.AMD64_SNAPSHOT);
        final List<Integer> accounts = List.of(1, 4, 1, 1);
        for (int at = 0; at < kinds.size(); at++) {
            final Kind kind = kinds.get(at);
            final int count = accounts.get(at);
            final List<String> types = orders.get(at);
            final Credential key = Keys.generated();
            final AtomicReference<String> created = new AtomicReference<>();
            try (StubApi stub =
                    new StubApi()
                            .answering(
                                    "/locations?page=1&per_page=50",
                                    """
                            {"locations":[{"name":"nbg1","network_zone":"eu-central"}],"meta":{"pagination":{"next_page":null}}}
                            """)
                            .answering(
                                    "/ssh_keys?page=1&per_page=50",
                                    Acquisition.keys(Fingerprint.md5(key)))
                            .answering(
                                    "/servers",
                                    exchange -> {
                                        try {
                                            created.set(
                                                    new String(
                                                            exchange.getRequestBody()
                                                                    .readAllBytes(),
                                                            StandardCharsets.UTF_8));
                                        } catch (IOException ex) {
                                            throw new java.io.UncheckedIOException(ex);
                                        }
                                        return new StubApi.Answer(201, Acquisition.created());
                                    })
                            .answering(
                                    "/servers/77",
                                    "{\"action\":{\"id\":5,\"status\":\"success\"}}")) {
                for (final String type : types) {
                    stub.answering(
                            "/server_types?name=" + type + "&page=1&per_page=50",
                            """
                            {"server_types":[{"locations":[{"name":"nbg1","available":true}]}],"meta":{"pagination":{"next_page":null}}}
                            """);
                }
                try (Machines source = Hetzner.against(stub.base(), "sizing");
                        Lease lease =
                                source.acquireFromStock(
                                        Spec.of("capacity", "ubuntu", kind, "root", key)
                                                .forAccounts(count),
                                        "ubuntu-26.04")) {
                    assertThat(Values.object(Json.parse(created.get())).get("server_type"))
                            .as("%s for %s accounts rents its preferred capacity", kind, count)
                            .isEqualTo(types.getFirst());
                    assertThat(
                                    stub.asked().stream()
                                            .filter(request -> request.contains("/server_types")))
                            .as("the full ordered fallback set is queried")
                            .containsExactlyElementsOf(
                                    types.stream()
                                            .map(
                                                    type ->
                                                            "GET /v1/server_types?name="
                                                                    + type
                                                                    + "&page=1&per_page=50")
                                            .toList());
                }
            }
        }
    }

    @Test
    void oldLegAndRentalOptionsReachTheKindBasedSourceWithoutLosingCapacity() {
        // Previous library callers must keep their explicit capacity and account count.
        final Credential key = Keys.generated();
        final AgentLeg.Options single =
                new AgentLeg.Options(
                        "ubuntu",
                        List.of("ccx33"),
                        "https://packages.invalid",
                        "porelo-agent",
                        null,
                        null,
                        false,
                        null);
        final AgentLeg.Options several =
                new AgentLeg.Options(
                        "ubuntu",
                        List.of("ccx33"),
                        "https://packages.invalid",
                        "porelo-agent",
                        null,
                        null,
                        false,
                        null,
                        4);
        for (final AgentLeg.Options options : List.of(single, several)) {
            final RecordingMachines source = new RecordingMachines();
            assertThatThrownBy(() -> AgentLeg.run(source, options, key))
                    .as("the old leg reaches the shared acquisition function")
                    .hasMessage("acquisition recorded");
            final Spec request = source.requested.getFirst();
            assertThat(request.kind())
                    .as("a heavy old leg still requests build capacity")
                    .isEqualTo(Kind.AMD64_BUILD);
            assertThat(request.accounts())
                    .as("the leg retains its account count")
                    .isEqualTo(options == single ? 1 : 4);
            assertThat(request.providerOverride())
                    .as("the old leg retains its explicit type")
                    .containsExactly("ccx33");
            assertThat(options.types())
                    .as("the previous accessor remains usable")
                    .containsExactly("ccx33");
        }
        final RecordingMachines source = new RecordingMachines();
        final Rental.Options rental =
                new Rental.Options(
                        "ubuntu", List.of("ccx33"), "https://packages.invalid", null, null);
        assertThatThrownBy(() -> Rental.run(source, rental, key))
                .as("the old rental reaches the shared acquisition function")
                .hasMessage("acquisition recorded");
        assertThat(source.requested.getFirst().kind())
                .as("the old rental retains build capacity")
                .isEqualTo(Kind.AMD64_BUILD);
        assertThat(source.requested.getFirst().providerOverride())
                .as("the old rental retains its explicit type")
                .containsExactly("ccx33");
        assertThat(rental.types())
                .as("the previous rental accessor remains usable")
                .containsExactly("ccx33");
    }
}
