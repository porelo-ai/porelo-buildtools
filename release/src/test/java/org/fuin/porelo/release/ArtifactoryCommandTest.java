package org.fuin.porelo.release;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The {@code artifactory} command over an Artifactory held in memory; what {@link
 * Artifactory#overHttp} sends for it, {@link ArtifactoryUploadTest} proves.
 */
class ArtifactoryCommandTest {

    private static final String TOKEN = "test-token-not-a-secret";

    @TempDir Path dir;

    private final FakeArtifactory artifactory = new FakeArtifactory();

    private final ByteArrayOutputStream out = new ByteArrayOutputStream();

    private final ByteArrayOutputStream err = new ByteArrayOutputStream();

    private Instant clock = Instant.parse("2026-11-08T12:00:00Z");

    private final List<Duration> waited = new ArrayList<>();

    /** Records each upload and call, fails as told in turn, and answers {@link #body}. */
    private static final class FakeArtifactory implements Artifactory {

        final List<String> asked = new ArrayList<>();

        final List<byte[]> sent = new ArrayList<>();

        final Deque<IOException> failures = new ArrayDeque<>();

        String body = "";

        private void fail() throws IOException {
            final IOException failure = failures.poll();
            if (failure != null) {
                throw failure;
            }
        }

        @Override
        public void upload(String target, Path file, String properties) throws IOException {
            asked.add("upload " + target + " " + file.getFileName() + " [" + properties + "]");
            sent.add(Files.readAllBytes(file));
            fail();
        }

        @Override
        public byte[] call(String method, String path) throws IOException {
            asked.add("call " + method + " " + path);
            fail();
            return body.getBytes(StandardCharsets.UTF_8);
        }

        @Override
        public Optional<List<Child>> children(String repository, String folder) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Instant created(String repository, String path) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Map<String, List<String>> properties(String repository, String path) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void put(String repository, String path, byte[] body) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void delete(String repository, String path) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<String> debianPackages(String repository, String distribution) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<String> rpmPrimary(String repository, String folder) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void recalculateDebian(String repository) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void recalculateRpm(String repository, String folder) {
            throw new UnsupportedOperationException();
        }
    }

    private static Artifactory.Unanswered unanswered(String status) {
        return new Artifactory.Unanswered("PUT x: HTTP " + status);
    }

    @Test
    void putsAFileUnderItsOwnNameWithItsProperties() throws Exception {
        final Path file = Files.writeString(dir.resolve("porelo-mcp_0.1.0_amd64.deb"), "a package");

        final int code =
                run(
                        "put",
                        file.toString(),
                        "porelo-dist-deb/pool/main/p/porelo-mcp/",
                        "deb.distribution=snapshots;deb.component=main;deb.architecture=amd64");

        assertThat(code).as(said()).isZero();
        assertThat(artifactory.asked)
                .as("one upload to the file's own name, with the properties")
                .containsExactly(
                        "upload porelo-dist-deb/pool/main/p/porelo-mcp/porelo-mcp_0.1.0_amd64.deb"
                                + " porelo-mcp_0.1.0_amd64.deb"
                                + " [deb.distribution=snapshots;deb.component=main;deb.architecture=amd64]");
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
        assertThat(artifactory.asked)
                .as("the target as it was named, no properties")
                .containsExactly("upload porelo-dist-deb/setup/porelo-setup-latest.sh setup.sh []");
    }

    @Test
    void asksAgainWhatWasUnansweredByThePinnedRule() throws Exception {
        final Path file = Files.writeString(dir.resolve("a.rpm"), "rpm");
        artifactory.failures.add(unanswered("503"));
        artifactory.failures.add(unanswered("502"));

        assertThat(run("put", file.toString(), "porelo-dist-rpm/snapshots/")).as(said()).isZero();

        assertThat(artifactory.asked).as("two unanswered, then stored").hasSize(3);
        assertThat(artifactory.sent)
                .as("the whole file each time")
                .allSatisfy(sent -> assertThat(sent).isEqualTo(Files.readAllBytes(file)));
        assertThat(waited)
                .as("the rule's first pauses")
                .containsExactly(Duration.ofSeconds(15), Duration.ofSeconds(30));
        assertThat(stderr())
                .as("each attempt said")
                .contains("HTTP 503 on attempt 1 of 8; trying again in 15 s");
    }

    @Test
    void asksTheRestInterfaceAgainWhenUnanswered() throws Exception {
        artifactory.failures.add(unanswered("502"));
        artifactory.body = "{}";

        assertThat(run("api", "GET", "/api/repositories")).as(said()).isZero();
        assertThat(artifactory.asked).as("asked again").hasSize(2);
        assertThat(waited).containsExactly(Duration.ofSeconds(15));
    }

    @Test
    void refusesAFileChangedWhileItWasSent() throws Exception {
        final Path file = Files.writeString(dir.resolve("a.rpm"), "a longer package");
        artifactory.failures.add(new IOException("PUT x: " + file + " changed while it was sent"));

        assertThat(run("put", file.toString(), "porelo-dist-rpm/snapshots/"))
                .as(said())
                .isEqualTo(Stop.REFUSED);
        assertThat(artifactory.asked).as("not asked again with other bytes").hasSize(1);
        assertThat(stderr()).contains("changed while it was sent");
    }

    @Test
    void refusesAFileWhoseNameNoTargetHolds() throws Exception {
        final Path file = Files.writeString(dir.resolve("a;deb.distribution=x.rpm"), "rpm");

        assertThat(run("put", file.toString(), "porelo-dist-rpm/snapshots/"))
                .isEqualTo(Stop.REFUSED);
        assertThat(artifactory.asked).as("nothing asked").isEmpty();
        assertThat(stderr()).contains("is no target");
    }

    @Test
    void takesAnInterruptionAsUnansweredNotRefused() {
        artifactory.failures.add(unanswered("503"));
        final int code =
                new ArtifactoryCommand(
                                artifactory,
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
            artifactory.failures.add(unanswered("504"));
        }

        assertThat(run("put", file.toString(), "porelo-dist-rpm/snapshots/"))
                .as(said())
                .isEqualTo(Stop.UNANSWERED);
        assertThat(artifactory.asked).as("eight attempts at most").hasSize(8);
        assertThat(stderr())
                .as("why it stopped")
                .contains("given up after 8 of at most 8 attempts");
    }

    @Test
    void failsAtOnceOnWhatAskingAgainWillNotMend() throws Exception {
        final Path file = Files.writeString(dir.resolve("a.rpm"), "rpm");
        artifactory.failures.add(new IOException("PUT x: HTTP 403"));

        assertThat(run("put", file.toString(), "porelo-dist-rpm/snapshots/"))
                .as(said())
                .isEqualTo(Stop.REFUSED);
        assertThat(artifactory.asked).as("asked once").hasSize(1);
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
        assertThat(artifactory.asked).as("nothing asked").isEmpty();
        assertThat(stderr()).as("the file named").contains("no file '" + dir.resolve("none.rpm"));
    }

    @Test
    void printsWhatTheRestInterfaceAnswers() {
        artifactory.body = "{\"packageType\":\"rpm\"}";

        assertThat(run("api", "GET", "/api/repositories/porelo-dist-rpm")).as(said()).isZero();
        assertThat(stdout()).as("the answer, as it came").isEqualTo(artifactory.body);
        assertThat(artifactory.asked).containsExactly("call GET /api/repositories/porelo-dist-rpm");
    }

    @Test
    void takesA404OfTheRestInterfaceAsRefused() {
        artifactory.failures.add(new IOException("DELETE x: HTTP 404"));

        assertThat(run("api", "DELETE", "/porelo-dist-test/smoke.txt")).isEqualTo(Stop.REFUSED);
        assertThat(stderr()).as("the answer named").contains("HTTP 404");
    }

    @Test
    void pingsAndWantsOk() {
        artifactory.body = "OK\n";
        assertThat(run("ping")).as(said()).isZero();

        artifactory.body = "Unauthorized";
        assertThat(run("ping")).as("an answer that is not OK").isEqualTo(Stop.REFUSED);
        assertThat(artifactory.asked)
                .as("both asked the ping")
                .containsOnly("call GET /api/system/ping")
                .hasSize(2);
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
                        artifactory,
                        new PrintStream(out, true, StandardCharsets.UTF_8),
                        new PrintStream(err, true, StandardCharsets.UTF_8),
                        pause)
                .run(List.of(args));
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
