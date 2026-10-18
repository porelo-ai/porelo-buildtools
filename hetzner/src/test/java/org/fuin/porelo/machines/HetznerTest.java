package org.fuin.porelo.machines;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/** Tests for {@link Hetzner}, against an API that answers what the test tells it to. */
class HetznerTest {

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
}
