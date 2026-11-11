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
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.fuin.porelo.pinned.PinnedRetry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Tests for {@link PinnedJdk} and the {@code jdk} command: a JDK installed only when it is the
 * pinned one.
 */
class PinnedJdkTest {

    private static final String WINDOWS_URL =
            "https://github.example/graalvm-community-jdk_windows-x64_bin.zip";

    private static final String URL =
            "https://github.example/graalvm-community-jdk_linux-x64_bin.tar.gz";

    @TempDir Path dir;

    @Test
    void installsThePinnedWindowsZipOfTheSameReleaseOnWindows() throws IOException {
        final byte[] zip = zip("graalvm-community/bin/java.exe", "graalvm-community/lib/modules");
        final java.util.List<URI> asked = new java.util.ArrayList<>();
        final byte[] full =
                zip(
                        "graalvm-community/bin/java.exe",
                        "graalvm-community/bin/native-image.cmd",
                        "graalvm-community/lib/modules");

        final PinnedJdk.Installed installed =
                PinnedJdk.install(
                        windows(sha256(full), sha256(zip)),
                        PinnedJdk.Platform.WINDOWS_X64,
                        dir.resolve("jdk"),
                        (address, into) -> {
                            asked.add(address);
                            into.write(full);
                        },
                        dir.resolve("cache"),
                        time -> {},
                        line -> {});

        assertThat(asked).as("the Windows zip is fetched").containsExactly(URI.create(WINDOWS_URL));
        assertThat(installed.home().resolve("bin/java.exe"))
                .as("unpacked without its top directory")
                .isRegularFile();
        assertThat(installed.home().resolve("lib/modules")).isRegularFile();
        assertThat(dir.resolve("cache").resolve(sha256(full) + ".zip"))
                .as("kept by its digest as a zip")
                .isRegularFile();
    }

    @Test
    void aWindowsZipWhoseDigestDiffersInstallsNothing() throws IOException {
        final byte[] full =
                zip("graalvm-community/bin/java.exe", "graalvm-community/bin/native-image.cmd");

        assertThatThrownBy(
                        () ->
                                PinnedJdk.install(
                                        windows("0".repeat(64), sha256(full)),
                                        PinnedJdk.Platform.WINDOWS_X64,
                                        dir.resolve("jdk"),
                                        (address, into) -> into.write(full),
                                        null,
                                        time -> {},
                                        line -> {}))
                .as("the Linux digest does not pass the Windows zip")
                .isInstanceOf(IOException.class)
                .hasMessageContaining("not the pinned " + "0".repeat(64));
        try (var left = Files.list(dir.resolve("jdk"))) {
            assertThat(left).as("nothing unpacked").isEmpty();
        }
    }

    @Test
    void aZipEntryThatLeavesTheJdksDirectoryIsRefused() throws IOException {
        final byte[] evil =
                zip(
                        "graalvm-community/bin/java.exe",
                        "graalvm-community/../../outside",
                        "graalvm-community/bin/native-image.cmd");

        assertThatThrownBy(
                        () ->
                                PinnedJdk.install(
                                        windows(sha256(evil), sha256(evil)),
                                        PinnedJdk.Platform.WINDOWS_X64,
                                        dir.resolve("home/jdk"),
                                        (address, into) -> into.write(evil),
                                        null,
                                        time -> {},
                                        line -> {}))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("which leaves");
        assertThat(dir.resolve("outside")).as("nothing written outside").doesNotExist();
        assertThat(dir.resolve("home/outside")).as("nothing written outside").doesNotExist();
        assertThat(dir.resolve("home/jdk/bin/java.exe"))
                .as("judged whole before the first entry is written")
                .doesNotExist();
    }

    @Test
    void aZipEntryNamingADriveIsRefusedOnEverySystem() throws IOException {
        // On Windows 'C:/x' is another drive; on Linux it would pass as a name.
        final byte[] drive =
                zip(
                        "graalvm-community/bin/java.exe",
                        "graalvm-community/C:/outside",
                        "graalvm-community/bin/native-image.cmd");

        assertThatThrownBy(
                        () ->
                                PinnedJdk.install(
                                        windows(sha256(drive), sha256(drive)),
                                        PinnedJdk.Platform.WINDOWS_X64,
                                        dir.resolve("jdk"),
                                        (address, into) -> into.write(drive),
                                        null,
                                        time -> {},
                                        line -> {}))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("which no system reads alike");
        try (var left = Files.list(dir.resolve("jdk"))) {
            assertThat(left).as("nothing unpacked").isEmpty();
        }
    }

    @Test
    void aZipNamingOneFileTwiceIsRefusedBeforeAnythingIsWritten() throws IOException {
        // The second would fail half way and leave a JDK of two minds behind.
        final byte[] twice =
                zip(
                        "graalvm-community/bin/java.exe",
                        "graalvm-community/bin/native-image.cmd",
                        "other/bin/java.exe");

        assertThatThrownBy(
                        () ->
                                PinnedJdk.install(
                                        windows(sha256(twice), sha256(twice)),
                                        PinnedJdk.Platform.WINDOWS_X64,
                                        dir.resolve("jdk"),
                                        (address, into) -> into.write(twice),
                                        null,
                                        time -> {},
                                        line -> {}))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("twice; nothing was unpacked");
        try (var left = Files.list(dir.resolve("jdk"))) {
            assertThat(left).as("nothing unpacked").isEmpty();
        }
    }

    @Test
    void theCommandPrintsTheWindowsZipsDigestOnWindows() throws IOException {
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
                            },
                            PinnedJdk.Platform.WINDOWS_X64);
        } finally {
            System.setOut(before);
        }

        assertThat(code).isZero();
        assertThat(printed.toString(StandardCharsets.UTF_8).strip())
                .as("a cache key on Windows names the zip, not the Linux archive")
                .isEqualTo(Snapshots.Contents.pinned().graalvmWindowsSha256())
                .isNotEqualTo(Snapshots.Contents.pinned().graalvmSha256());
    }

    @Test
    void aWindowsZipWithoutNativeImageIsNoJdk() throws IOException {
        final byte[] zip = zip("graalvm-community/bin/java.exe");

        assertThatThrownBy(
                        () ->
                                PinnedJdk.install(
                                        windows(sha256(zip), sha256(zip)),
                                        PinnedJdk.Platform.WINDOWS_X64,
                                        dir.resolve("jdk"),
                                        (address, into) -> into.write(zip),
                                        null,
                                        time -> {},
                                        line -> {}))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("no bin/native-image.cmd");
    }

    @Test
    void theRunningSystemPicksTheArchiveAndAnyOtherIsRefused() throws IOException {
        assertThat(PinnedJdk.Platform.of("Linux", "amd64")).isEqualTo(PinnedJdk.Platform.LINUX_X64);
        assertThat(PinnedJdk.Platform.of("Windows Server 2025", "amd64"))
                .isEqualTo(PinnedJdk.Platform.WINDOWS_X64);
        for (final String[] other :
                new String[][] {
                    {"Linux", "aarch64"}, {"Mac OS X", "x86_64"}, {"Windows 11", "aarch64"}
                }) {
            assertThatThrownBy(() -> PinnedJdk.Platform.of(other[0], other[1]))
                    .as("%s on %s gets no JDK it cannot run", other[0], other[1])
                    .isInstanceOf(IOException.class)
                    .hasMessageContaining("no GraalVM is pinned for");
        }
    }

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
    void startsNoAttemptAfterFifteenMinutesWithTheAttemptsTimeCounted() throws IOException {
        // Each attempt hangs two minutes before it fails, as GitHub's did on 2026-11-08.
        final Instant[] clock = {Instant.parse("2026-11-08T11:10:00Z")};
        final Instant first = clock[0];
        final List<Instant> started = new ArrayList<>();
        final List<Duration> waited = new ArrayList<>();
        final PinnedJdk.Pause pause =
                new PinnedJdk.Pause() {
                    @Override
                    public void of(Duration time) {
                        waited.add(time);
                        clock[0] = clock[0].plus(time);
                    }

                    @Override
                    public Instant now() {
                        return clock[0];
                    }
                };

        assertThatThrownBy(
                        () ->
                                PinnedJdk.install(
                                        contents("0".repeat(64)),
                                        dir.resolve("jdk"),
                                        (address, into) -> {
                                            started.add(clock[0]);
                                            clock[0] = clock[0].plus(Duration.ofMinutes(2));
                                            throw new PinnedJdk.Unavailable("HTTP 504");
                                        },
                                        null,
                                        pause,
                                        message -> {}))
                .isInstanceOf(PinnedJdk.Unavailable.class)
                .hasMessageContaining("HTTP 504")
                .hasMessageContaining("given up after 6 of at most 8 attempts in 1020 s");

        // Five attempts of 2 min and pauses of 15, 30, 60 and 120 s end at 13 min 45 s; the pause
        // of
        // 240 s is cut to 75 s, so the sixth starts at the deadline, and none after it.
        assertThat(started)
                .as("six attempts, none after the deadline")
                .hasSize(6)
                .allSatisfy(at -> assertThat(at).isBeforeOrEqualTo(first.plus(PinnedRetry.BUDGET)));
        assertThat(started.get(5))
                .as("the last at the deadline")
                .isEqualTo(first.plus(PinnedRetry.BUDGET));
        assertThat(waited)
                .as("the rule's pauses, the last cut")
                .containsExactly(
                        Duration.ofSeconds(15),
                        Duration.ofSeconds(30),
                        Duration.ofSeconds(60),
                        Duration.ofSeconds(120),
                        Duration.ofSeconds(75));
    }

    @Test
    void keepsTheDeadlineOnAClockThatCountsUp() throws InterruptedException {
        final PinnedJdk.Pause pause = time -> {};

        final Instant before = pause.now();
        Thread.sleep(20);

        assertThat(Duration.between(before, pause.now()))
                .as("the default clock moves, or the deadline would never come")
                .isGreaterThanOrEqualTo(Duration.ofMillis(20));
    }

    @Test
    void cutsThePauseSoTheNextAttemptStartsByTheDeadline() throws IOException {
        final Instant[] clock = {Instant.parse("2026-11-08T11:10:00Z")};
        final List<Duration> waited = new ArrayList<>();
        final AtomicInteger attempts = new AtomicInteger();
        final PinnedJdk.Pause pause =
                new PinnedJdk.Pause() {
                    @Override
                    public void of(Duration time) {
                        waited.add(time);
                        clock[0] = clock[0].plus(time);
                    }

                    @Override
                    public Instant now() {
                        return clock[0];
                    }
                };

        assertThatThrownBy(
                        () ->
                                PinnedJdk.install(
                                        contents("0".repeat(64)),
                                        dir.resolve("jdk"),
                                        (address, into) -> {
                                            // The first hangs 14 min 50 s.
                                            if (attempts.incrementAndGet() == 1) {
                                                clock[0] = clock[0].plus(Duration.ofSeconds(890));
                                            }
                                            throw new PinnedJdk.Unavailable("HTTP 504");
                                        },
                                        null,
                                        pause,
                                        message -> {}))
                .isInstanceOf(PinnedJdk.Unavailable.class);

        assertThat(waited)
                .as("the pause cut to what is left")
                .containsExactly(Duration.ofSeconds(10));
        assertThat(attempts).as("one more attempt, at the deadline").hasValue(2);
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

    @Test
    void sendsTheTokenToItsOriginAndNeverAlongARedirect() throws IOException {
        final HttpServer github = server();
        final HttpServer elsewhere = server();
        final List<String> seen = new java.util.concurrent.CopyOnWriteArrayList<>();
        github.createContext(
                "/asked",
                exchange -> {
                    seen.add("asked " + authorization(exchange));
                    redirect(exchange, "/again");
                });
        github.createContext(
                "/again",
                exchange -> {
                    seen.add("again " + authorization(exchange));
                    redirect(exchange, origin(elsewhere) + "/signed");
                });
        elsewhere.createContext(
                "/signed",
                exchange -> {
                    seen.add("signed " + authorization(exchange));
                    final byte[] body = "archive".getBytes(StandardCharsets.UTF_8);
                    exchange.sendResponseHeaders(200, body.length);
                    exchange.getResponseBody().write(body);
                    exchange.close();
                });
        github.start();
        elsewhere.start();
        try {
            final ByteArrayOutputStream into = new ByteArrayOutputStream();
            PinnedJdk.fetch(
                    noRedirects(),
                    URI.create(origin(github) + "/asked"),
                    new PinnedJdk.Credential(URI.create(origin(github)), TOKEN),
                    into);

            assertThat(into.toString(StandardCharsets.UTF_8))
                    .as("the archive")
                    .isEqualTo("archive");
            assertThat(seen)
                    .as(
                            "the token with the address asked for; not along a redirect, not even back to its own origin")
                    .containsExactly("asked Bearer " + TOKEN, "again none", "signed none");
        } finally {
            github.stop(0);
            elsewhere.stop(0);
        }
    }

    @Test
    void sendsTheTokenNowhereButItsOrigin() throws IOException {
        final HttpServer github = server();
        final HttpServer elsewhere = server();
        final List<String> seen = new java.util.concurrent.CopyOnWriteArrayList<>();
        elsewhere.createContext(
                "/archive",
                exchange -> {
                    seen.add(authorization(exchange));
                    answer(exchange, 200);
                });
        elsewhere.start();
        try {
            PinnedJdk.fetch(
                    noRedirects(),
                    URI.create(origin(elsewhere) + "/archive"),
                    new PinnedJdk.Credential(URI.create(origin(github)), TOKEN),
                    new ByteArrayOutputStream());

            assertThat(seen).as("another port is another origin").containsExactly("none");
        } finally {
            elsewhere.stop(0);
        }
    }

    @Test
    void takesA401OrA403AsRefusedNotUnavailable() throws IOException {
        for (final int status : new int[] {401, 403}) {
            final HttpServer server = server();
            server.createContext("/refused", exchange -> answer(exchange, status));
            server.start();
            try {
                assertThatThrownBy(
                                () ->
                                        PinnedJdk.fetch(
                                                noRedirects(),
                                                URI.create(origin(server) + "/refused"),
                                                new PinnedJdk.Credential(
                                                        URI.create(origin(server)), TOKEN),
                                                new ByteArrayOutputStream()))
                        .as("HTTP " + status + " is not asked again")
                        .isInstanceOf(IOException.class)
                        .isNotInstanceOf(PinnedJdk.Unavailable.class)
                        .hasMessageContaining("HTTP " + status)
                        .hasMessageNotContaining(TOKEN);
            } finally {
                server.stop(0);
            }
        }
    }

    @Test
    void followsNoEndlessRedirects() throws IOException {
        final HttpServer server = server();
        server.createContext("/loop", exchange -> redirect(exchange, "/loop"));
        server.start();
        try {
            assertThatThrownBy(
                            () ->
                                    PinnedJdk.fetch(
                                            noRedirects(),
                                            URI.create(origin(server) + "/loop"),
                                            null,
                                            new ByteArrayOutputStream()))
                    .hasMessageContaining("redirected more than 10 times");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void takesTheJobsTokenOnlyOnGithubAndOnlyOfATokensForm() {
        final Map<String, String> github =
                Map.of("GITHUB_SERVER_URL", "https://github.com", PinnedJdk.TOKEN_VARIABLE, TOKEN);

        assertThat(PinnedJdk.githubToken(github))
                .as("on github.com")
                .isEqualTo(new PinnedJdk.Credential(URI.create("https://github.com"), TOKEN));
        assertThat(
                        PinnedJdk.githubToken(
                                Map.of(
                                        "GITHUB_SERVER_URL",
                                        "http://127.0.0.1:3000",
                                        PinnedJdk.TOKEN_VARIABLE,
                                        TOKEN)))
                .as("a host's forge: its token is not GitHub's")
                .isNull();
        assertThat(PinnedJdk.githubToken(Map.of(PinnedJdk.TOKEN_VARIABLE, TOKEN)))
                .as("no server named")
                .isNull();
        assertThat(
                        PinnedJdk.githubToken(
                                Map.of(
                                        "GITHUB_SERVER_URL",
                                        "https://github.com/",
                                        PinnedJdk.TOKEN_VARIABLE,
                                        TOKEN)))
                .as("exactly https://github.com")
                .isNull();
        assertThat(
                        PinnedJdk.githubToken(
                                Map.of(
                                        "GITHUB_SERVER_URL",
                                        "https://github.com",
                                        PinnedJdk.TOKEN_VARIABLE,
                                        "abc\r\nX-Injected: 1")))
                .as("not of a token's form")
                .isNull();
        assertThat(PinnedJdk.githubToken(Map.of("GITHUB_SERVER_URL", "https://github.com")))
                .as("no token given")
                .isNull();
        assertThat(String.valueOf(PinnedJdk.githubToken(github)))
                .as("never told")
                .doesNotContain(TOKEN);
    }

    @Test
    void sendsTheTokenToGithubComAloneByItsWholeOrigin() {
        assertThat(
                        new PinnedJdk.Credential(URI.create("https://github.com"), TOKEN)
                                .isFor(
                                        URI.create(
                                                "https://github.com/graalvm/releases/download/x.tar.gz")))
                .as("github.com's release download")
                .isTrue();
        assertThat(
                        new PinnedJdk.Credential(URI.create("https://github.com"), TOKEN)
                                .isFor(URI.create("https://github.com.evil.example/x.tar.gz")))
                .as("a host that only begins like it")
                .isFalse();
        assertThat(
                        new PinnedJdk.Credential(URI.create("https://github.com"), TOKEN)
                                .isFor(URI.create("http://github.com/x.tar.gz")))
                .as("not over http")
                .isFalse();
        assertThat(
                        new PinnedJdk.Credential(URI.create("https://github.com"), TOKEN)
                                .isFor(URI.create("https://api.github.com/x")))
                .as("not another host of GitHub's")
                .isFalse();
    }

    @Test
    void namesWhereARedirectLedByItsHostAloneNeverItsSignature() throws IOException {
        final HttpServer github = server();
        final HttpServer signed = server();
        github.createContext(
                "/asked",
                exchange -> redirect(exchange, origin(signed) + "/object?X-Amz-Signature=s3cret"));
        signed.createContext("/object", exchange -> answer(exchange, 504));
        github.start();
        signed.start();
        try {
            assertThatThrownBy(
                            () ->
                                    PinnedJdk.fetch(
                                            noRedirects(),
                                            URI.create(origin(github) + "/asked"),
                                            null,
                                            new ByteArrayOutputStream()))
                    .isInstanceOf(PinnedJdk.Unavailable.class)
                    .hasMessageContaining(
                            origin(github) + "/asked (redirected to " + origin(signed) + ")")
                    .hasMessageNotContaining("s3cret");
        } finally {
            github.stop(0);
            signed.stop(0);
        }
    }

    @Test
    void followsTenRedirectsAndNoMore() throws IOException {
        final HttpServer server = server();
        server.createContext(
                "/r",
                exchange -> {
                    final int left = Integer.parseInt(exchange.getRequestURI().getQuery());
                    if (left == 0) {
                        answer(exchange, 200);
                    } else {
                        redirect(exchange, "/r?" + (left - 1));
                    }
                });
        server.start();
        try {
            PinnedJdk.fetch(
                    noRedirects(),
                    URI.create(origin(server) + "/r?10"),
                    null,
                    new ByteArrayOutputStream());
            assertThatThrownBy(
                            () ->
                                    PinnedJdk.fetch(
                                            noRedirects(),
                                            URI.create(origin(server) + "/r?11"),
                                            null,
                                            new ByteArrayOutputStream()))
                    .hasMessageContaining("redirected more than 10 times");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void followsARedirectOnlyToAnAddressOverHttpsOnceOnHttps() throws IOException {
        final URI https = URI.create("https://github.com/x");

        assertThat(PinnedJdk.redirected(https, "HTTPS://objects.example/y", "x"))
                .as("a scheme in capitals is the same")
                .hasHost("objects.example");
        assertThat(PinnedJdk.redirected(https, "/y", "x"))
                .as("relative")
                .hasToString("https://github.com/y");
        assertThatThrownBy(() -> PinnedJdk.redirected(https, "http://objects.example/y", "x"))
                .as("from https to http")
                .hasMessageContaining("not https");
        assertThatThrownBy(() -> PinnedJdk.redirected(https, "ftp://objects.example/y", "x"))
                .hasMessageContaining("not https");
        assertThatThrownBy(() -> PinnedJdk.redirected(https, "https://a b/ y", "x"))
                .as("no address")
                .isInstanceOf(IOException.class)
                .hasMessageContaining("redirected to an address that is none");
        assertThatThrownBy(() -> PinnedJdk.redirected(https, "https:///path", "x"))
                .as("no host")
                .isInstanceOf(IOException.class);
    }

    @Test
    void theDownloadsClientFollowsNoRedirectItself() {
        assertThat(PinnedJdk.client().followRedirects())
                .as("a client that follows would take the token along a same-origin redirect")
                .isEqualTo(HttpClient.Redirect.NEVER);
    }

    @Test
    void takesNoTokenOfAnotherFormHoweverLong() {
        for (final String wrong :
                List.of(
                        "ghs_" + "A".repeat(30) + "\"",
                        "ghs_" + "A".repeat(30) + "\nurl = https://evil.example",
                        "ghs_" + "A".repeat(30) + " x",
                        "A".repeat(256))) {
            assertThat(
                            PinnedJdk.githubToken(
                                    Map.of(
                                            "GITHUB_SERVER_URL",
                                            "https://github.com",
                                            PinnedJdk.TOKEN_VARIABLE,
                                            wrong)))
                    .as(wrong)
                    .isNull();
        }
    }

    private static final String TOKEN = "ghs_" + "A".repeat(36);

    private static HttpClient noRedirects() {
        return HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();
    }

    private static String origin(HttpServer server) {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    private static String authorization(com.sun.net.httpserver.HttpExchange exchange) {
        final String value = exchange.getRequestHeaders().getFirst("Authorization");
        return value == null ? "none" : value;
    }

    private static void redirect(com.sun.net.httpserver.HttpExchange exchange, String to)
            throws IOException {
        exchange.getResponseHeaders().add("Location", to);
        exchange.sendResponseHeaders(302, -1);
        exchange.close();
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

    // More pairs than Map.of takes.
    private static Map<String, String> pairs(String... pairs) {
        final Map<String, String> map = new java.util.LinkedHashMap<>();
        for (int at = 0; at < pairs.length; at += 2) {
            map.put(pairs[at], pairs[at + 1]);
        }
        return Map.copyOf(map);
    }

    // The Windows zip pinned with one digest, the Linux archive with another.
    private static Snapshots.Contents windows(String windows, String linux) {
        final Map<String, String> values = new java.util.HashMap<>();
        for (final String key :
                new String[] {
                    "graalvm.version",
                    "graalvm.url",
                    "graalvm.windows.url",
                    "image.ubuntu",
                    "image.ubuntu.digest",
                    "image.alpine",
                    "image.alpine.digest",
                    "image.registry",
                    "image.registry.digest"
                }) {
            values.put(key, contentsValue(key));
        }
        values.put("graalvm.sha256", linux);
        values.put("graalvm.windows.sha256", windows);
        return Snapshots.Contents.of(values::get);
    }

    private static String contentsValue(String key) {
        return switch (key) {
            case "graalvm.version" -> "25.4.4.1.1";
            case "graalvm.url" -> URL;
            case "graalvm.windows.url" -> WINDOWS_URL;
            case "image.ubuntu" -> "docker.io/library/ubuntu:24.04";
            case "image.ubuntu.digest" -> "sha256:" + "c".repeat(64);
            case "image.alpine" -> "docker.io/library/alpine:3.20";
            case "image.alpine.digest" -> "sha256:" + "d".repeat(64);
            case "image.registry" -> "docker.io/library/registry:2";
            case "image.registry.digest" -> "sha256:" + "e".repeat(64);
            default -> throw new IllegalArgumentException(key);
        };
    }

    /** A zip shaped like GraalVM's for Windows, its entries named as given, each a small file. */
    private static byte[] zip(String... entries) throws IOException {
        final java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
        try (java.util.zip.ZipOutputStream zip = new java.util.zip.ZipOutputStream(bytes)) {
            for (final String entry : entries) {
                zip.putNextEntry(new java.util.zip.ZipEntry(entry));
                zip.write(entry.getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        }
        return bytes.toByteArray();
    }

    private static Snapshots.Contents contents(String sha256) {
        return Snapshots.Contents.of(
                pairs(
                                "graalvm.version",
                                "25.4.4.1.1",
                                "graalvm.url",
                                URL,
                                "graalvm.sha256",
                                sha256,
                                "graalvm.windows.url",
                                WINDOWS_URL,
                                "graalvm.windows.sha256",
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
