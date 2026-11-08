package org.fuin.porelo.machines;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.HttpServer;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Tests for {@link PinnedJdk} and the {@code jdk} command: a JDK installed only when it is the
 * pinned one.
 */
class PinnedJdkTest {

    private static final String URL =
            "https://github.example/graalvm-community-jdk_linux-x64_bin.tar.gz";

    @TempDir Path dir;

    @Test
    void installsThePinnedArchiveWhenItsDigestMatches() throws IOException {
        final byte[] archive = archive("java", "native-image");

        final PinnedJdk.Installed installed =
                PinnedJdk.install(
                        contents(sha256(archive)),
                        dir.resolve("jdk"),
                        (address, into) -> into.write(archive));

        assertThat(installed.home().resolve("bin/java")).isExecutable();
        assertThat(installed.version()).isEqualTo("25.4.4.1.1");
        // The archive is gone; only the JDK stays.
        try (var left = Files.list(dir)) {
            assertThat(left).containsExactly(dir.resolve("jdk"));
        }
    }

    @Test
    void installsNothingWhenTheDigestDiffers() throws IOException {
        final byte[] archive = archive("java", "native-image");

        assertThatThrownBy(
                        () ->
                                PinnedJdk.install(
                                        contents("0".repeat(64)),
                                        dir.resolve("jdk"),
                                        (address, into) -> into.write(archive)))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("not the pinned")
                .hasMessageContaining("nothing was installed");
        assertThat(dir.resolve("jdk/bin")).doesNotExist();
    }

    @Test
    void refusesToInstallOverAnotherJdk() throws IOException {
        Files.createDirectories(dir.resolve("jdk/bin"));

        assertThatThrownBy(
                        () ->
                                PinnedJdk.install(
                                        contents("0".repeat(64)),
                                        dir.resolve("jdk"),
                                        (address, into) -> {
                                            throw new AssertionError(
                                                    "nothing may be fetched for a directory that is not empty");
                                        }))
                .hasMessageContaining("is not empty");
    }

    @Test
    void refusesAnArchiveThatIsNoGraalvmWithNativeImage() throws IOException {
        final byte[] archive = archive("java");

        assertThatThrownBy(
                        () ->
                                PinnedJdk.install(
                                        contents(sha256(archive)),
                                        dir.resolve("jdk"),
                                        (address, into) -> into.write(archive)))
                .hasMessageContaining("no bin/native-image");
    }

    @Test
    void makesItTheJdkOfEveryLaterStep() throws IOException {
        final Path env = dir.resolve("github-env");
        final Path path = dir.resolve("github-path");
        Files.writeString(env, "EARLIER=kept\n");

        PinnedJdk.exportTo(new PinnedJdk.Installed(dir.resolve("jdk"), "25.4.4.1.1"), env, path);

        assertThat(Files.readString(env))
                .isEqualTo(
                        "EARLIER=kept\nJAVA_HOME="
                                + dir.resolve("jdk")
                                + "\nGRAALVM_HOME="
                                + dir.resolve("jdk")
                                + "\n");
        assertThat(Files.readString(path)).isEqualTo(dir.resolve("jdk/bin") + "\n");
    }

    @Test
    void refusesToExportWhereNoGithubJobIsBeforeDownloadingAnything() throws IOException {
        final List<String> said = new ArrayList<>();

        assertThat(
                        Main.jdk(
                                new String[] {
                                    "jdk", "--github", "--into", dir.resolve("jdk").toString()
                                },
                                said::add,
                                Map.of(),
                                (address, into) -> {
                                    throw new AssertionError(
                                            "nothing may be fetched that could not be exported");
                                }))
                .isEqualTo(2);
        assertThat(said).singleElement().asString().contains("GITHUB_ENV");
    }

    @Test
    void needsADirectoryOutsideAGithubJob() throws IOException {
        final List<String> said = new ArrayList<>();

        assertThat(
                        Main.jdk(
                                new String[] {"jdk"},
                                said::add,
                                Map.of(),
                                (address, into) -> {
                                    throw new AssertionError(
                                            "nothing may be fetched without a place to put it");
                                }))
                .isEqualTo(2);
        assertThat(said).singleElement().asString().contains("--into");
    }

    @Test
    void triesAgainWhenTheServerCannotAnswerAndChecksOnlyTheLastAttempt() throws IOException {
        final byte[] archive = archive("java", "native-image");
        final AtomicInteger attempts = new AtomicInteger();
        final List<String> said = new ArrayList<>();

        final PinnedJdk.Installed installed =
                PinnedJdk.install(
                        contents(sha256(archive)),
                        dir.resolve("jdk"),
                        (address, into) -> {
                            if (attempts.incrementAndGet() < 3) {
                                // What broke off midway must not stay in the file or the digest.
                                into.write(archive, 0, 10);
                                throw new PinnedJdk.Unavailable("HTTP 504");
                            }
                            into.write(archive);
                        },
                        null,
                        time -> {},
                        said::add);

        assertThat(installed.home().resolve("bin/java")).isExecutable();
        assertThat(attempts).hasValue(3);
        assertThat(said).hasSize(2);
        assertThat(said.get(0)).contains("HTTP 504").contains("attempt 1 of 8");
    }

    @Test
    void givesUpAfterItsAttemptsAndLeavesNothing() throws IOException {
        final AtomicInteger attempts = new AtomicInteger();

        assertThatThrownBy(
                        () ->
                                PinnedJdk.install(
                                        contents("0".repeat(64)),
                                        dir.resolve("jdk"),
                                        (address, into) -> {
                                            attempts.incrementAndGet();
                                            throw new PinnedJdk.Unavailable("HTTP 504");
                                        },
                                        null,
                                        time -> {},
                                        message -> {}))
                .isInstanceOf(PinnedJdk.Unavailable.class)
                .hasMessageContaining("HTTP 504");
        assertThat(attempts).hasValue(8);
        assertThat(dir.resolve("jdk/bin")).doesNotExist();
        try (var left = Files.list(dir)) {
            assertThat(left).containsExactly(dir.resolve("jdk"));
        }
    }

    @Test
    void waitsLongerBeforeEachAttempt() throws IOException {
        final List<Duration> waited = new ArrayList<>();

        assertThatThrownBy(
                        () ->
                                PinnedJdk.install(
                                        contents("0".repeat(64)),
                                        dir.resolve("jdk"),
                                        (address, into) -> {
                                            throw new PinnedJdk.Unavailable("HTTP 504");
                                        },
                                        null,
                                        waited::add,
                                        message -> {}))
                .isInstanceOf(PinnedJdk.Unavailable.class);

        // The one rule of every pinned download: GitHub's 504s on 2026-11-08 lasted over ten
        // minutes.
        assertThat(waited).isEqualTo(PinnedRetry.pauses());
    }

    @Test
    void keepsTheCheckedArchiveInTheCacheByItsDigest() throws IOException {
        final byte[] archive = archive("java", "native-image");
        final Path cache = dir.resolve("cache");

        PinnedJdk.install(
                contents(sha256(archive)),
                dir.resolve("jdk"),
                (address, into) -> into.write(archive),
                cache,
                time -> {},
                message -> {});

        try (var kept = Files.list(cache)) {
            assertThat(kept).containsExactly(cache.resolve(sha256(archive) + ".tar.gz"));
        }
        assertThat(cache.resolve(sha256(archive) + ".tar.gz")).hasBinaryContent(archive);
    }

    @Test
    void takesTheCachedArchiveWithoutFetchingWhenItsDigestMatches() throws IOException {
        final byte[] archive = archive("java", "native-image");
        final Path cache = Files.createDirectories(dir.resolve("cache"));
        Files.write(cache.resolve(sha256(archive) + ".tar.gz"), archive);
        final List<String> said = new ArrayList<>();

        final PinnedJdk.Installed installed =
                PinnedJdk.install(
                        contents(sha256(archive)),
                        dir.resolve("jdk"),
                        (address, into) -> {
                            throw new AssertionError(
                                    "a cached archive that matches is not fetched");
                        },
                        cache,
                        time -> {},
                        said::add);

        assertThat(installed.home().resolve("bin/native-image")).isExecutable();
        assertThat(said).singleElement().asString().contains("from the cache");
    }

    @Test
    void fetchesAgainWhenTheCachedArchiveDoesNotMatchAndNeverUnpacksIt() throws IOException {
        final byte[] archive = archive("java", "native-image");
        final Path cache = Files.createDirectories(dir.resolve("cache"));
        final Path cached = cache.resolve(sha256(archive) + ".tar.gz");
        // Its own file would show in the JDK if it were unpacked before or despite its digest.
        Files.write(cached, archive("planted-by-the-cache"));
        final AtomicInteger attempts = new AtomicInteger();
        final List<String> said = new ArrayList<>();

        final PinnedJdk.Installed installed =
                PinnedJdk.install(
                        contents(sha256(archive)),
                        dir.resolve("jdk"),
                        (address, into) -> {
                            attempts.incrementAndGet();
                            into.write(archive);
                        },
                        cache,
                        time -> {},
                        said::add);

        assertThat(attempts).hasValue(1);
        assertThat(installed.home().resolve("bin/native-image")).isExecutable();
        assertThat(installed.home().resolve("bin/planted-by-the-cache")).doesNotExist();
        assertThat(cached).hasBinaryContent(archive);
        assertThat(said).anySatisfy(line -> assertThat(line).contains("did not match"));
    }

    @Test
    void putsNothingInTheCacheThatDoesNotMatch() throws IOException {
        final Path cache = dir.resolve("cache");

        assertThatThrownBy(
                        () ->
                                PinnedJdk.install(
                                        contents("0".repeat(64)),
                                        dir.resolve("jdk"),
                                        (address, into) -> into.write(1),
                                        cache,
                                        time -> {},
                                        message -> {}))
                .hasMessageContaining("not the pinned");
        try (var kept = Files.list(cache)) {
            assertThat(kept).isEmpty();
        }
    }

    @Test
    void printsThePinnedDigestAloneForACacheKey() throws IOException {
        final java.io.PrintStream before = System.out;
        final ByteArrayOutputStream printed = new ByteArrayOutputStream();
        System.setOut(new java.io.PrintStream(printed, true, StandardCharsets.UTF_8));
        final int code;
        try {
            code =
                    Main.jdk(
                            new String[] {"jdk", "--print-sha256"},
                            message -> {
                                throw new AssertionError(message);
                            },
                            Map.of(),
                            (address, into) -> {
                                throw new AssertionError("printing the digest fetches nothing");
                            });
        } finally {
            System.setOut(before);
        }

        assertThat(code).isZero();
        assertThat(printed.toString(StandardCharsets.UTF_8))
                .isEqualTo(Snapshots.Contents.pinned().graalvmSha256() + "\n");
    }

    @Test
    void consultsTheCacheItIsGiven() throws IOException {
        final Path cache = Files.createDirectories(dir.resolve("cache"));
        final Path cached = cache.resolve(Snapshots.Contents.pinned().graalvmSha256() + ".tar.gz");
        Files.writeString(cached, "not the pinned archive");
        final AtomicInteger attempts = new AtomicInteger();

        assertThatThrownBy(
                        () ->
                                Main.jdk(
                                        new String[] {
                                            "jdk",
                                            "--into",
                                            dir.resolve("jdk").toString(),
                                            "--cache",
                                            cache.toString()
                                        },
                                        message -> {},
                                        Map.of(),
                                        (address, into) -> {
                                            attempts.incrementAndGet();
                                            throw new IOException("HTTP 404");
                                        }))
                .hasMessageContaining("HTTP 404");
        assertThat(attempts).hasValue(1);
        assertThat(cached).doesNotExist();
    }

    @Test
    void refusesAnEmptyCacheDirectory() throws IOException {
        final List<String> said = new ArrayList<>();

        assertThat(
                        Main.jdk(
                                new String[] {
                                    "jdk", "--into", dir.resolve("jdk").toString(), "--cache", " "
                                },
                                said::add,
                                Map.of(),
                                (address, into) -> {
                                    throw new AssertionError(
                                            "nothing is fetched for a refused call");
                                }))
                .isEqualTo(2);
        assertThat(said).singleElement().asString().contains("--cache");
    }

    @Test
    void refusesACacheOptionWithoutADirectory() throws IOException {
        final List<String> said = new ArrayList<>();

        assertThat(
                        Main.jdk(
                                new String[] {
                                    "jdk", "--into", dir.resolve("jdk").toString(), "--cache"
                                },
                                said::add,
                                Map.of(),
                                (address, into) -> {
                                    throw new AssertionError(
                                            "nothing is fetched for a refused call");
                                }))
                .isEqualTo(2);
        assertThat(said).singleElement().asString().contains("--cache");
    }

    @Test
    void doesNotTryAgainForADownloadThatIsRefused() throws IOException {
        final AtomicInteger attempts = new AtomicInteger();

        assertThatThrownBy(
                        () ->
                                PinnedJdk.install(
                                        contents("0".repeat(64)),
                                        dir.resolve("jdk"),
                                        (address, into) -> {
                                            attempts.incrementAndGet();
                                            throw new IOException("HTTP 404");
                                        },
                                        null,
                                        time -> {},
                                        message -> {}))
                .hasMessageContaining("HTTP 404");
        assertThat(attempts).hasValue(1);
    }

    @Test
    void doesNotTryAgainForAWrongDigest() throws IOException {
        final AtomicInteger attempts = new AtomicInteger();

        assertThatThrownBy(
                        () ->
                                PinnedJdk.install(
                                        contents("0".repeat(64)),
                                        dir.resolve("jdk"),
                                        (address, into) -> {
                                            attempts.incrementAndGet();
                                            into.write(1);
                                        },
                                        null,
                                        time -> {},
                                        message -> {}))
                .hasMessageContaining("not the pinned");
        assertThat(attempts).hasValue(1);
    }

    @Test
    void takesA5xxAsUnavailableAlsoBehindARedirect() throws IOException {
        final HttpServer server = server();
        server.createContext(
                "/redirect",
                exchange -> {
                    exchange.getResponseHeaders().add("Location", "/gateway");
                    exchange.sendResponseHeaders(302, -1);
                    exchange.close();
                });
        server.createContext("/gateway", exchange -> answer(exchange, 504));
        server.start();
        try {
            assertThatThrownBy(() -> fetch(server, "/redirect", new ByteArrayOutputStream()))
                    .isInstanceOf(PinnedJdk.Unavailable.class)
                    .hasMessageContaining("HTTP 504");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void takesA404AsRefused() throws IOException {
        final HttpServer server = server();
        server.createContext("/gone", exchange -> answer(exchange, 404));
        server.start();
        try {
            assertThatThrownBy(() -> fetch(server, "/gone", new ByteArrayOutputStream()))
                    .isInstanceOf(IOException.class)
                    .isNotInstanceOf(PinnedJdk.Unavailable.class)
                    .hasMessageContaining("HTTP 404");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void takesAnAnswerBeyondThe5xxAsRefused() throws IOException {
        final HttpServer server = server();
        server.createContext("/odd", exchange -> answer(exchange, 600));
        server.start();
        try {
            assertThatThrownBy(() -> fetch(server, "/odd", new ByteArrayOutputStream()))
                    .isInstanceOf(IOException.class)
                    .isNotInstanceOf(PinnedJdk.Unavailable.class)
                    .hasMessageContaining("HTTP 600");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void startsEachAttemptWithAnEmptyFile() throws IOException {
        final byte[] archive = archive("java", "native-image");
        final AtomicInteger attempts = new AtomicInteger();

        PinnedJdk.install(
                contents(sha256(archive)),
                dir.resolve("jdk"),
                (address, into) -> {
                    if (attempts.incrementAndGet() == 1) {
                        into.write(archive);
                        throw new PinnedJdk.Unavailable("broke off");
                    }
                    // Longer than what follows, this attempt's rest would stay behind its end.
                    try (var files = Files.list(dir)) {
                        assertThat(
                                        files.filter(
                                                        file ->
                                                                file.getFileName()
                                                                        .toString()
                                                                        .startsWith("graalvm-"))
                                                .findFirst()
                                                .orElseThrow())
                                .isEmptyFile();
                    }
                    into.write(archive);
                },
                null,
                time -> {},
                message -> {});

        assertThat(attempts).hasValue(2);
    }

    @Test
    void takesARefusedConnectionAsUnavailable() throws IOException {
        final int port;
        try (ServerSocket free = new ServerSocket(0)) {
            port = free.getLocalPort();
        }

        assertThatThrownBy(
                        () ->
                                PinnedJdk.fetch(
                                        HttpClient.newHttpClient(),
                                        URI.create("http://127.0.0.1:" + port + "/"),
                                        new ByteArrayOutputStream()))
                .isInstanceOf(PinnedJdk.Unavailable.class);
    }

    @Test
    void takesABodyThatBreaksOffAsUnavailable() throws IOException {
        final HttpServer server = server();
        server.createContext(
                "/short",
                exchange -> {
                    exchange.sendResponseHeaders(200, 1000);
                    exchange.getResponseBody().write(new byte[10]);
                    exchange.getResponseBody().flush();
                    exchange.close();
                });
        server.start();
        try {
            assertThatThrownBy(() -> fetch(server, "/short", new ByteArrayOutputStream()))
                    .isInstanceOf(PinnedJdk.Unavailable.class);
        } finally {
            server.stop(0);
        }
    }

    @Test
    void takesAWriteThatFailsAsNothingToTryAgain() throws IOException {
        final HttpServer server = server();
        server.createContext(
                "/whole",
                exchange -> {
                    exchange.sendResponseHeaders(200, 10);
                    exchange.getResponseBody().write(new byte[10]);
                    exchange.close();
                });
        server.start();
        try {
            assertThatThrownBy(
                            () ->
                                    fetch(
                                            server,
                                            "/whole",
                                            new OutputStream() {
                                                @Override
                                                public void write(int value) throws IOException {
                                                    throw new IOException("disk full");
                                                }
                                            }))
                    .isInstanceOf(IOException.class)
                    .isNotInstanceOf(PinnedJdk.Unavailable.class)
                    .hasMessageContaining("disk full");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void writesWhatTheServerAnswers() throws IOException {
        final HttpServer server = server();
        server.createContext(
                "/whole",
                exchange -> {
                    exchange.sendResponseHeaders(200, 3);
                    exchange.getResponseBody().write(new byte[] {1, 2, 3});
                    exchange.close();
                });
        server.start();
        try {
            final ByteArrayOutputStream into = new ByteArrayOutputStream();
            fetch(server, "/whole", into);
            assertThat(into.toByteArray()).containsExactly(1, 2, 3);
        } finally {
            server.stop(0);
        }
    }

    private static HttpServer server() throws IOException {
        return HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    }

    private static void answer(com.sun.net.httpserver.HttpExchange exchange, int status)
            throws IOException {
        exchange.sendResponseHeaders(status, -1);
        exchange.close();
    }

    private static void fetch(HttpServer server, String path, OutputStream into)
            throws IOException {
        PinnedJdk.fetch(
                HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build(),
                URI.create("http://127.0.0.1:" + server.getAddress().getPort() + path),
                into);
    }

    private static Snapshots.Contents contents(String sha256) {
        return Snapshots.Contents.of(
                Map.of(
                                "graalvm.version",
                                "25.4.4.1.1",
                                "graalvm.url",
                                URL,
                                "graalvm.sha256",
                                sha256,
                                "image.ubuntu",
                                "docker.io/library/ubuntu:24.04",
                                "image.ubuntu.digest",
                                "sha256:" + "c".repeat(64),
                                "image.alpine",
                                "docker.io/library/alpine:3.20",
                                "image.alpine.digest",
                                "sha256:" + "d".repeat(64),
                                "image.registry",
                                "docker.io/library/registry:2",
                                "image.registry.digest",
                                "sha256:" + "e".repeat(64))
                        ::get);
    }

    /** A tarball shaped like GraalVM's: one top directory, the tools under bin/. */
    private byte[] archive(String... tools) throws IOException {
        final Path source = dir.resolve("source");
        final Path bin = Files.createDirectories(source.resolve("graalvm-community/bin"));
        for (final String tool : tools) {
            Files.writeString(bin.resolve(tool), "#!/bin/sh\n", StandardCharsets.UTF_8);
            Files.setPosixFilePermissions(
                    bin.resolve(tool), PosixFilePermissions.fromString("rwxr-xr-x"));
        }
        final Path archive = dir.resolve("graalvm.tar.gz");
        try {
            final Process tar =
                    new ProcessBuilder(
                                    "tar",
                                    "-czf",
                                    archive.toString(),
                                    "-C",
                                    source.toString(),
                                    "graalvm-community")
                            .inheritIO()
                            .start();
            assertThat(tar.waitFor()).isZero();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IOException(ex);
        }
        final byte[] bytes = Files.readAllBytes(archive);
        Files.delete(archive);
        deleteTree(source);
        return bytes;
    }

    private static void deleteTree(Path root) throws IOException {
        try (var walk = Files.walk(root)) {
            for (final Path path : walk.sorted(java.util.Comparator.reverseOrder()).toList()) {
                Files.delete(path);
            }
        }
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
