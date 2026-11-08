package org.fuin.porelo.machines;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.fuin.porelo.pinned.PinnedRetry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Tests for {@link PinnedRetry} and its shell twin {@code .github/actions/pinned-jdk/pinned.sh}:
 * one rule for every pinned download.
 */
class PinnedRetryTest {

    private static final Path SCRIPT = Path.of("../.github/actions/pinned-jdk/pinned.sh");

    @TempDir Path dir;

    @Test
    void theShellPausesAsJavaDoes() throws IOException, InterruptedException {
        final List<String> expected = new ArrayList<>();
        for (final Duration pause : PinnedRetry.pauses()) {
            expected.add(Long.toString(pause.toSeconds()));
        }
        expected.add("none");
        expected.add("attempts " + PinnedRetry.attempts());

        final Ran ran =
                shell(
                        "for n in 1 2 3 4 5 6 7 8; do pinned_pause \"$n\" || echo none; done;"
                                + " echo \"attempts $pinned_attempts\"",
                        "exit 99");

        assertThat(ran.out().lines()).containsExactlyElementsOf(expected);
        assertThat(ran.exit()).isZero();
    }

    @Test
    void theShellKeepsTheDeadlineAsJavaDoes() throws IOException, InterruptedException {
        final List<String> expected = new ArrayList<>();
        final StringBuilder calls = new StringBuilder();
        for (int attempt = 0; attempt <= 8; attempt++) {
            for (final int elapsed : new int[] {-5, 0, 100, 400, 600, 780, 890, 899, 900, 1000}) {
                expected.add(
                        attempt
                                + " "
                                + elapsed
                                + " "
                                + PinnedRetry.pauseAfter(attempt, Duration.ofSeconds(elapsed))
                                        .map(pause -> Long.toString(pause.toSeconds()))
                                        .orElse("none"));
                calls.append("echo \"")
                        .append(attempt)
                        .append(' ')
                        .append(elapsed)
                        .append(" $(pinned_pause ")
                        .append(attempt)
                        .append(' ')
                        .append(elapsed)
                        .append(" || echo none)\"; ");
            }
        }

        final Ran ran = shell(calls.toString(), "exit 99");

        assertThat(ran.out().lines())
                .as("pinned_pause against pauseAfter")
                .containsExactlyElementsOf(expected);
        assertThat(ran.exit()).isZero();
    }

    @Test
    void theShellsClockCountsUpWhateverTheCallerDoesWithSeconds()
            throws IOException, InterruptedException {
        final Ran ran =
                shell(
                        "a=$(pinned_now); SECONDS=0; python3 -c 'import time; time.sleep(1.3)'; b=$(pinned_now);"
                                + " echo $((b - a))",
                        "exit 99");

        assertThat(ran.exit()).as(ran.out()).isZero();
        assertThat(Integer.parseInt(ran.out().strip()))
                .as("seconds counted over 1.3 s")
                .isBetween(1, 2);
    }

    @Test
    void theShellRunsACommandAgainOnlyUntilTheDeadline() throws IOException, InterruptedException {
        final Path clock = dir.resolve("clock");
        Files.writeString(clock, "0\n");

        // Each run hangs two minutes and fails; sleep moves the same clock.
        final Ran ran =
                shell(
                        "pinned_now() { cat "
                                + clock
                                + "; }; hangs() { echo $(($(cat "
                                + clock
                                + ") + 120)) > "
                                + clock
                                + "; return 3; }; pinned_retry 'the wrapper' hangs",
                        "exit 99",
                        "echo $(($(cat "
                                + clock
                                + ") + $1)) > "
                                + clock
                                + "; echo \"slept $1\" >&2");

        assertThat(ran.exit()).as(ran.out()).isEqualTo(1);
        assertThat(ran.out())
                .as("six runs, the last at the deadline")
                .contains("the wrapper failed (exit 3) on attempt 5 of 8; trying again in 75 s")
                .contains("given up after 6 of at most 8 attempts in 1020 s");
    }

    @Test
    void theShellRunsACommandWithItsOwnErrexitAndKeepsTheCallers()
            throws IOException, InterruptedException {
        final Path reached = dir.resolve("reached");

        // pinned_retry as a statement of its own: as a condition, bash ignores set -e all the way
        // down.
        final Ran ran =
                shell(
                        "install() { false; echo reached > "
                                + reached
                                + "; }; pinned_now() { echo 0; };"
                                + " set +e; pinned_retry install install; echo \"status $?\";"
                                + " case $- in *e*) echo 'errexit came on' ;; esac;"
                                + " set -e; pinned_retry ok true; case $- in *e*) echo 'errexit kept' ;; esac",
                        "exit 99");

        assertThat(reached).as("a failing command stops the function given").doesNotExist();
        assertThat(ran.out())
                .as(ran.out())
                .contains("install failed (exit 1) on attempt 1 of 8")
                .contains("status 1")
                .doesNotContain("errexit came on")
                .contains("errexit kept");
    }

    @Test
    void theShellStartsNoAttemptAfterFifteenMinutes() throws IOException, InterruptedException {
        final Path clock = dir.resolve("clock");
        Files.writeString(clock, "0\n");
        final Path started = dir.resolve("started");

        // Each attempt hangs two minutes before it answers 504; sleep moves the same clock.
        final Ran ran =
                shell(
                        "pinned_now() { cat "
                                + clock
                                + "; }; pinned_fetch https://example.invalid/tool "
                                + sha("pinned")
                                + " "
                                + dir.resolve("tool"),
                        "cat "
                                + clock
                                + " >> "
                                + started
                                + "; echo $(($(cat "
                                + clock
                                + ") + 120)) > "
                                + clock
                                + "; printf 504",
                        "echo $(($(cat "
                                + clock
                                + ") + $1)) > "
                                + clock
                                + "; echo \"slept $1\" >&2");

        assertThat(ran.exit()).as(ran.out()).isEqualTo(1);
        assertThat(Files.readString(started).lines())
                .as("no attempt starts after 900 s")
                .containsExactly("0", "135", "285", "465", "705", "900");
        assertThat(ran.out())
                .as("what it says when it gives up")
                .contains("given up after 6 of at most 8 attempts in 1020 s");
    }

    @Test
    void theShellKeepsAFileThatMatchesWithoutFetching() throws IOException, InterruptedException {
        final Path file = dir.resolve("tool");
        Files.writeString(file, "pinned");

        final Ran ran = fetch(file, "echo called >> " + dir.resolve("calls") + "; exit 99");

        assertThat(ran.exit()).isZero();
        assertThat(dir.resolve("calls")).doesNotExist();
        assertThat(ran.out()).contains("from the cache");
        assertThat(file).hasContent("pinned");
    }

    @Test
    void theShellFetchesAgainAFileThatDoesNotMatch() throws IOException, InterruptedException {
        final Path file = dir.resolve("tool");
        Files.writeString(file, "planted");

        final Ran ran = fetch(file, serving("pinned", "200", 0));

        assertThat(ran.exit()).isZero();
        assertThat(ran.out()).doesNotContain("from the cache");
        assertThat(file).hasContent("pinned");
    }

    @Test
    void theShellTakesNoLinkForACachedFile() throws IOException, InterruptedException {
        final Path target = dir.resolve("elsewhere");
        Files.writeString(target, "pinned");
        final Path file = dir.resolve("tool");
        Files.createSymbolicLink(file, target);

        final Ran ran = fetch(file, serving("pinned", "200", 0));

        assertThat(ran.exit()).isZero();
        assertThat(ran.out()).doesNotContain("from the cache");
        assertThat(Files.isSymbolicLink(file)).isFalse();
        assertThat(file).hasContent("pinned");
    }

    @Test
    void theShellTriesAgainOnA5xxAndPausesByTheRule() throws IOException, InterruptedException {
        final Path file = dir.resolve("tool");

        final Ran ran = fetch(file, failingFirst(2, "printf 'bad' > \"$out\"; printf 504"));

        assertThat(ran.exit()).isZero();
        assertThat(file).hasContent("pinned");
        assertThat(Files.readString(dir.resolve("n")).strip()).isEqualTo("3");
        assertThat(ran.out())
                .contains("HTTP 504 (curl exit 0) on attempt 1 of 8")
                .contains("slept 15")
                .contains("slept 30");
        assertNoPartLeft(file);
    }

    @Test
    void theShellTriesAgainWhenTheConnectionFails() throws IOException, InterruptedException {
        final Path file = dir.resolve("tool");

        final Ran ran = fetch(file, failingFirst(1, "printf 000; exit 7"));

        assertThat(ran.exit()).isZero();
        assertThat(file).hasContent("pinned");
        assertThat(ran.out())
                .contains("HTTP 000 (curl exit 7) on attempt 1 of 8")
                .contains("slept 15");
    }

    @Test
    void theShellGivesUpAfterEightAttempts() throws IOException, InterruptedException {
        final Path file = dir.resolve("tool");

        final Ran ran = fetch(file, failingFirst(99, "printf 503"));

        assertThat(ran.exit()).isEqualTo(1);
        assertThat(Files.readString(dir.resolve("n")).strip()).isEqualTo("8");
        assertThat(ran.out().lines().filter(line -> line.startsWith("slept"))).hasSize(7);
        assertThat(ran.out()).contains("given up after 8 of at most 8 attempts");
        assertThat(file).doesNotExist();
        assertNoPartLeft(file);
    }

    @Test
    void theShellFailsAtOnceOnWhatWaitingWillNotMend() throws IOException, InterruptedException {
        final Path file = dir.resolve("tool");

        final Ran refused = fetch(file, serving("", "404", 0));
        final Ran wrong = fetch(file, serving("other", "200", 0));
        final Ran certificate = fetch(file, serving("", "000", 60));
        final Ran full = fetch(file, serving("", "200", 23));

        assertThat(refused.out()).contains("HTTP 404").doesNotContain("slept");
        assertThat(wrong.out()).contains("not the pinned").doesNotContain("slept");
        assertThat(certificate.out()).contains("curl exit 60").doesNotContain("slept");
        assertThat(full.out()).contains("curl exit 23").doesNotContain("slept");
        for (final Ran ran : List.of(refused, wrong, certificate, full)) {
            assertThat(ran.exit()).as(ran.out()).isEqualTo(1);
        }
        assertThat(file).doesNotExist();
        assertNoPartLeft(file);
    }

    @Test
    void theShellAsksOnlyOverHttps() throws IOException, InterruptedException {
        final Ran ran =
                fetch(
                        dir.resolve("tool"),
                        "echo \"$*\" > " + dir.resolve("args") + "; printf 000; exit 1");

        assertThat(ran.exit()).isEqualTo(1);
        assertThat(Files.readString(dir.resolve("args")))
                .contains("--proto =https")
                .contains("--proto-redir =https");
    }

    @Test
    void theShellRunsACommandAgainByTheRule() throws IOException, InterruptedException {
        final Path count = dir.resolve("count");

        final Ran ran =
                shell(
                        "pinned_retry 'the wrapper' sh -c 'n=$(cat "
                                + count
                                + " 2>/dev/null || echo 0); echo $((n+1)) > "
                                + count
                                + "; [ $n -ge 2 ]' >/dev/null",
                        "exit 99");

        assertThat(ran.exit()).isZero();
        assertThat(Files.readString(count).strip()).isEqualTo("3");
        // Its words reach the log though the command's output is discarded.
        assertThat(ran.out())
                .contains("the wrapper failed (exit 1) on attempt 1 of 8")
                .contains("slept 15");
    }

    @Test
    void theShellKeepsEachCallsAttemptsApart() throws IOException, InterruptedException {
        final Path count = dir.resolve("count");
        Files.writeString(dir.resolve("tool"), "pinned");

        // A command run again that itself fetches must not reset the outer count.
        final Ran ran =
                shell(
                        "inner() { pinned_fetch https://example.invalid/tool "
                                + sha("pinned")
                                + " "
                                + dir.resolve("tool")
                                + "; n=$(cat "
                                + count
                                + " 2>/dev/null || echo 0); echo $((n+1)) > "
                                + count
                                + "; [ $n -ge 2 ]; }; pinned_retry outer inner",
                        "exit 99");

        assertThat(ran.exit()).isZero();
        assertThat(ran.out())
                .contains("outer failed (exit 1) on attempt 1 of 8")
                .contains("outer failed (exit 1) on attempt 2 of 8");
    }

    /** What a run of bash printed, standard error included, and how it ended. */
    private record Ran(String out, int exit) {}

    private Ran fetch(Path file, String curl) throws IOException, InterruptedException {
        Files.deleteIfExists(dir.resolve("n"));
        return shell(
                "pinned_fetch https://example.invalid/tool " + sha("pinned") + " " + file, curl);
    }

    /**
     * A stand-in curl that writes a body where -o says, prints a status and ends with an exit code.
     */
    private static String serving(String body, String status, int exit) {
        return "while [ $# -gt 0 ]; do [ \"$1\" = -o ] && out=$2; shift; done; printf '%s' '"
                + body
                + "' > \"$out\"; printf "
                + status
                + "; exit "
                + exit;
    }

    /** A stand-in curl that fails as told the first times, then serves the pinned file. */
    private String failingFirst(int times, String failure) {
        return "n=$(cat "
                + dir.resolve("n")
                + " 2>/dev/null || echo 0); n=$((n+1)); echo $n > "
                + dir.resolve("n")
                + "\nwhile [ $# -gt 0 ]; do [ \"$1\" = -o ] && out=$2; shift; done\n"
                + "if [ $n -le "
                + times
                + " ]; then "
                + failure
                + "; else printf pinned > \"$out\"; printf 200; fi";
    }

    private void assertNoPartLeft(Path file) throws IOException {
        try (var left = Files.list(file.getParent())) {
            assertThat(
                            left.filter(
                                    path ->
                                            path.getFileName()
                                                    .toString()
                                                    .startsWith(file.getFileName() + ".")))
                    .isEmpty();
        }
    }

    /** Runs the script's functions in bash with stand-ins for curl and sleep. */
    private Ran shell(String body, String curl) throws IOException, InterruptedException {
        return shell(body, curl, "echo \"slept $1\" >&2");
    }

    private Ran shell(String body, String curl, String sleep)
            throws IOException, InterruptedException {
        final Path bin = Files.createDirectories(dir.resolve("bin"));
        Files.writeString(bin.resolve("curl"), "#!/bin/sh\n" + curl + "\n");
        Files.writeString(bin.resolve("sleep"), "#!/bin/sh\n" + sleep + "\n");
        bin.resolve("curl").toFile().setExecutable(true);
        bin.resolve("sleep").toFile().setExecutable(true);
        final ProcessBuilder builder =
                new ProcessBuilder(
                                "bash",
                                "-c",
                                "set -eu; . \"$0\"; " + body,
                                SCRIPT.toAbsolutePath().toString())
                        .redirectErrorStream(true);
        builder.environment().put("PATH", bin + ":" + System.getenv("PATH"));
        final Process bash = builder.start();
        final String out = new String(bash.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertThat(bash.waitFor(60, TimeUnit.SECONDS)).isTrue();
        return new Ran(out, bash.exitValue());
    }

    private static String sha(String text) {
        try {
            return java.util.HexFormat.of()
                    .formatHex(
                            java.security.MessageDigest.getInstance("SHA-256")
                                    .digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
