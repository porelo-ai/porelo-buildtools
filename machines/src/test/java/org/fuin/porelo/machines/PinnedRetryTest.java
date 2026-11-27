package org.fuin.porelo.machines;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.fuin.porelo.pinned.PinnedRetry;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Tests for {@link PinnedRetry} and its shell twin {@code .github/actions/pinned-jdk/pinned.sh}:
 * one rule for every pinned download.
 */
@Tag("documents")
@Tag("integration")
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
        expected.add("first " + PinnedRetry.FIRST_SOURCE.toSeconds());
        expected.add("first attempts " + PinnedRetry.FIRST_SOURCE_ATTEMPTS);
        expected.add("mirror " + PinnedJdk.MIRROR);

        final Ran ran =
                shell(
                        "for n in 1 2 3 4 5 6 7 8; do pinned_pause \"$n\" || echo none; done;"
                                + " echo \"attempts $pinned_attempts\";"
                                + " echo \"first $pinned_first_deadline\";"
                                + " echo \"first attempts $pinned_first_attempts\";"
                                + " echo \"mirror $pinned_mirror\"",
                        "exit 99");

        assertThat(ran.out().lines()).containsExactlyElementsOf(expected);
        assertThat(ran.exit()).isZero();
    }

    @Test
    void theShellKeepsTheDeadlineAsJavaDoes() throws IOException, InterruptedException {
        final List<String> expected = new ArrayList<>();
        final StringBuilder calls = new StringBuilder();
        for (final Duration deadline :
                new Duration[] {PinnedRetry.FIRST_SOURCE, PinnedRetry.BUDGET}) {
            for (int attempt = 0; attempt <= 8; attempt++) {
                for (final int elapsed :
                        new int[] {-5, 0, 100, 400, 590, 599, 600, 780, 890, 899, 900, 1000}) {
                    expected.add(
                            attempt
                                    + " "
                                    + elapsed
                                    + " "
                                    + PinnedRetry.pauseAfter(
                                                    attempt, Duration.ofSeconds(elapsed), deadline)
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
                            .append(' ')
                            .append(deadline.toSeconds())
                            .append(" || echo none)\"; ");
                }
            }
        }
        // Without a deadline, the whole budget.
        for (final int elapsed : new int[] {600, 890}) {
            expected.add(
                    "default "
                            + elapsed
                            + " "
                            + PinnedRetry.pauseAfter(5, Duration.ofSeconds(elapsed))
                                    .map(pause -> Long.toString(pause.toSeconds()))
                                    .orElse("none"));
            calls.append("echo \"default ")
                    .append(elapsed)
                    .append(" $(pinned_pause 5 ")
                    .append(elapsed)
                    .append(" || echo none)\"; ");
        }

        final Ran ran = shell(calls.toString(), "exit 99");

        assertThat(ran.out().lines())
                .as("pinned_pause against pauseAfter")
                .containsExactlyElementsOf(expected);
        assertThat(ran.exit()).isZero();
    }

    // Prevents a deadline the caller's own SECONDS moves: the clock is the kernel's uptime, which
    // only counts up, read without waiting for it to move.
    @Test
    void theShellsClockCountsUpWhateverTheCallerDoesWithSeconds()
            throws IOException, InterruptedException {
        final Ran ran =
                shell(
                        "a=$(pinned_now); SECONDS=100000; b=$(pinned_now); SECONDS=-100000;"
                                + " c=$(pinned_now); read -r up _ </proc/uptime;"
                                + " echo \"$a $b $c ${up%%.*}\"",
                        "exit 99");

        assertThat(ran.exit()).as(ran.out()).isZero();
        final long[] seen =
                java.util.Arrays.stream(ran.out().strip().split(" "))
                        .mapToLong(Long::parseLong)
                        .toArray();
        assertThat(seen[1] - seen[0])
                .as("SECONDS set forward does not move the clock")
                .isBetween(0L, 1L);
        assertThat(seen[2] - seen[1])
                .as("SECONDS set back does not move it back")
                .isBetween(0L, 1L);
        assertThat(seen[3] - seen[2]).as("the clock is the uptime's").isBetween(0L, 1L);
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

    @Test
    void theShellSendsTheJobsTokenOnItsStandardInputToGithubAloneAndNotAlongTheRedirect()
            throws IOException, InterruptedException {
        final Ran ran =
                githubFetch(
                        "https://github.com/graalvm/x.tar.gz",
                        Map.of(
                                "GITHUB_SERVER_URL",
                                "https://github.com",
                                "PORELO_PINNED_GITHUB_TOKEN",
                                TOKEN));

        assertThat(ran.exit()).as(ran.out()).isEqualTo(0);
        assertThat(Files.readString(dir.resolve("calls")))
                .as(
                        "the address asked for with the token on stdin, following nothing; the signed one without it")
                .isEqualTo(
                        "-K - stdin header = \"Authorization: Bearer "
                                + TOKEN
                                + "\" url https://github.com/graalvm/x.tar.gz\n"
                                + "-L stdin  url https://objects.example/signed\n");
        assertThat(ran.out()).as("never told").doesNotContain(TOKEN);
    }

    @Test
    void theShellSendsNoTokenOffGithubOrOutOfForm() throws IOException, InterruptedException {
        final Map<String, Map<String, String>> cases =
                Map.of(
                        "https://github.com/x.tar.gz",
                        Map.of(
                                "GITHUB_SERVER_URL",
                                "http://127.0.0.1:3000",
                                "PORELO_PINNED_GITHUB_TOKEN",
                                TOKEN),
                        "https://github.com.example/x.tar.gz",
                        Map.of(
                                "GITHUB_SERVER_URL",
                                "https://github.com",
                                "PORELO_PINNED_GITHUB_TOKEN",
                                TOKEN),
                        "https://example.invalid/x.tar.gz",
                        Map.of(
                                "GITHUB_SERVER_URL",
                                "https://github.com",
                                "PORELO_PINNED_GITHUB_TOKEN",
                                TOKEN),
                        "https://github.com/y.tar.gz",
                        Map.of(
                                "GITHUB_SERVER_URL",
                                "https://github.com",
                                "PORELO_PINNED_GITHUB_TOKEN",
                                "short\"x"),
                        "https://github.com/z.tar.gz",
                        Map.of(
                                "GITHUB_SERVER_URL",
                                "https://github.com",
                                "PORELO_PINNED_GITHUB_TOKEN",
                                TOKEN + "\nurl = \"https://evil.example/x"),
                        "https://github.com/w.tar.gz",
                        Map.of(
                                "GITHUB_SERVER_URL",
                                "https://github.com",
                                "PORELO_PINNED_GITHUB_TOKEN",
                                TOKEN + "\""));
        for (final var entry : cases.entrySet()) {
            Files.deleteIfExists(dir.resolve("calls"));
            Files.deleteIfExists(dir.resolve("tool"));
            final Ran ran = githubFetch(entry.getKey(), entry.getValue());

            assertThat(Files.readString(dir.resolve("calls")))
                    .as(
                            entry.getKey()
                                    + " "
                                    + entry.getValue().get("GITHUB_SERVER_URL")
                                    + ": "
                                    + ran.out())
                    .startsWith("-L stdin  url " + entry.getKey())
                    .doesNotContain("Authorization");
        }
    }

    @Test
    void theShellFailsAtOnceWhenTheTokenIsRefused() throws IOException, InterruptedException {
        Files.deleteIfExists(dir.resolve("n"));
        final Ran ran =
                shell(
                        "pinned_fetch https://github.com/x.tar.gz "
                                + sha("pinned")
                                + " "
                                + dir.resolve("tool"),
                        "cat >/dev/null; printf '403 '",
                        "echo \"slept $1\" >&2",
                        Map.of(
                                "GITHUB_SERVER_URL",
                                "https://github.com",
                                "PORELO_PINNED_GITHUB_TOKEN",
                                TOKEN));

        assertThat(ran.exit()).isEqualTo(1);
        assertThat(ran.out())
                .contains("answered HTTP 403")
                .doesNotContain("slept")
                .doesNotContain(TOKEN);
    }

    private static final String MIRROR = "https://mirror.example/files";

    /** Where the stand-in mirror keeps the pinned file. */
    private static final String MIRRORED = MIRROR + "/pinned/" + sha("pinned") + "/tool";

    // Prevents a Build that goes red while the organization's Artifactory holds the file: the first
    // source gives way after three attempts, and the mirror's file is taken once it matches.
    @Test
    void theShellTakesTheFileFromTheMirrorWhenTheFirstSourceAnswers503ThreeTimes()
            throws IOException, InterruptedException {
        final Path clock = dir.resolve("clock");
        Files.writeString(clock, "0\n");

        // Each attempt at the first source hangs two minutes before it answers 503.
        final Ran ran =
                mirrored(
                        "pinned_now() { cat " + clock + "; }; " + fetchTool(),
                        clock,
                        "echo $(($(cat " + clock + ") + 120)) > " + clock + "; printf 503",
                        "printf pinned > \"$out\"; printf 200");

        assertThat(ran.exit()).as(ran.out()).isZero();
        assertThat(dir.resolve("tool")).as("the mirror's file, checked").hasContent("pinned");
        final List<String> calls = Files.readAllLines(dir.resolve("calls"));
        assertThat(calls)
                .as("the first source three times, then the mirror at once")
                .containsExactly(
                        "0 https://example.invalid/tool",
                        "135 https://example.invalid/tool",
                        "285 https://example.invalid/tool",
                        "405 " + MIRRORED);
        assertThat(ran.out())
                .as("the source that answered is named")
                .contains("taken from " + MIRRORED)
                .doesNotContain("::error::");
        assertNoPartLeft(dir.resolve("tool"));
    }

    // Prevents a mirror that serves something else from being taken for the pinned file.
    @Test
    void theShellRefusesAFileFromTheMirrorThatDoesNotMatch()
            throws IOException, InterruptedException {
        final Path clock = dir.resolve("clock");
        Files.writeString(clock, "0\n");

        // A 404 at the first source, as a moved release asset answers.
        final Ran ran =
                mirrored(fetchTool(), clock, "printf 404", "printf planted > \"$out\"; printf 200");

        assertThat(ran.exit()).as(ran.out()).isEqualTo(1);
        assertThat(Files.readAllLines(dir.resolve("calls")))
                .as("the first source once, the mirror once")
                .containsExactly("0 https://example.invalid/tool", "0 " + MIRRORED);
        assertThat(ran.out()).contains(MIRRORED + " has the digest of something else");
        assertThat(dir.resolve("tool")).doesNotExist();
        assertNoPartLeft(dir.resolve("tool"));
    }

    // Prevents a Build from waiting on with nothing to take when neither source holds the file.
    @Test
    void theShellFailsNamingBothSourcesWhenTheMirrorLacksTheFileToo()
            throws IOException, InterruptedException {
        final Path clock = dir.resolve("clock");
        Files.writeString(clock, "0\n");

        final Ran ran = mirrored(fetchTool(), clock, "printf 404", "printf 404");

        assertThat(ran.exit()).as(ran.out()).isEqualTo(1);
        assertThat(Files.readAllLines(dir.resolve("calls")))
                .as("each source once, since a 404 does not mend by asking again")
                .containsExactly("0 https://example.invalid/tool", "0 " + MIRRORED);
        assertThat(ran.out())
                .as("both answers named")
                .contains("https://example.invalid/tool answered HTTP 404")
                .contains(MIRRORED + " answered HTTP 404");
        assertThat(dir.resolve("tool")).as("nothing taken").doesNotExist();
        assertNoPartLeft(dir.resolve("tool"));
    }

    // Prevents one hanging attempt from running far past its source's deadline: curl is told how
    // long the deadline leaves, and at least 30 s.
    @Test
    void theShellBoundsEachAttemptByWhatItsSourcesDeadlineLeaves()
            throws IOException, InterruptedException {
        final Path clock = dir.resolve("clock");
        Files.writeString(clock, "0\n");
        final Path limits = dir.resolve("limits");

        final Ran ran =
                shell(
                        "pinned_now() { cat " + clock + "; }; " + fetchTool(),
                        "for a; do last=$a; done\n"
                                + "while [ $# -gt 0 ]; do case $1 in -o) out=$2 ;; --max-time)"
                                + " echo \"$(cat "
                                + clock
                                + ") $2\" >> "
                                + limits
                                + " ;; esac; shift; done\n"
                                + "case $last in "
                                + MIRROR
                                + "/*) printf pinned > \"$out\"; printf 200 ;;"
                                + " *) echo $(($(cat "
                                + clock
                                + ") + 120)) > "
                                + clock
                                + "; printf 503 ;; esac",
                        "echo $(($(cat " + clock + ") + $1)) > " + clock,
                        Map.of("PORELO_PINNED_MIRROR", MIRROR));

        assertThat(ran.exit()).as(ran.out()).isZero();
        assertThat(Files.readAllLines(limits))
                .as("the first source's attempts end by 600 s, the mirror's by 900 s")
                .containsExactly("0 600", "135 465", "285 315", "405 495");
    }

    // Prevents a Build that goes red while the mirror holds the file when the first source serves
    // another one, as zlib.net did to GitHub's runner; the mirror's copy is taken only by the
    // digest.
    @Test
    void theShellTakesTheMirrorsFileAtOnceOnAWrongDigestFromTheFirstSource()
            throws IOException, InterruptedException {
        final Path clock = dir.resolve("clock");
        Files.writeString(clock, "0\n");

        final Ran ran =
                mirrored(
                        fetchTool(),
                        clock,
                        "printf planted > \"$out\"; printf 200",
                        "printf pinned > \"$out\"; printf 200");

        assertThat(ran.exit()).as(ran.out()).isZero();
        assertThat(Files.readAllLines(dir.resolve("calls")))
                .as("the first source once, then the mirror")
                .containsExactly("0 https://example.invalid/tool", "0 " + MIRRORED);
        assertThat(ran.out())
                .contains("https://example.invalid/tool has the digest")
                .contains("taken from " + MIRRORED);
        assertThat(dir.resolve("tool")).as("the mirror's file, checked").hasContent("pinned");
    }

    // Prevents the job's token from going to the mirror, which is no GitHub address.
    @Test
    void theShellSendsTheJobsTokenNeverToTheMirror() throws IOException, InterruptedException {
        final Path calls = dir.resolve("calls");

        final Ran ran =
                shell(
                        "pinned_fetch https://github.com/x/tool "
                                + sha("pinned")
                                + " "
                                + dir.resolve("tool"),
                        "in=; case \" $* \" in *' -K - '*) in=$(cat) ;; esac\n"
                                + "for a; do last=$a; done\n"
                                + "printf '%s stdin %s\\n' \"$last\" \"$in\" >> "
                                + calls
                                + "\nwhile [ $# -gt 0 ]; do [ \"$1\" = -o ] && out=$2; shift; done\n"
                                + "case $last in "
                                + MIRROR
                                + "/*) printf pinned > \"$out\"; printf 200 ;; *) printf '404 ' ;; esac",
                        "echo \"slept $1\" >&2",
                        Map.of(
                                "GITHUB_SERVER_URL",
                                "https://github.com",
                                "PORELO_PINNED_GITHUB_TOKEN",
                                TOKEN,
                                "PORELO_PINNED_MIRROR",
                                MIRROR));

        assertThat(ran.exit()).as(ran.out()).isZero();
        assertThat(Files.readAllLines(calls))
                .as("the token to github.com, the mirror without it")
                .containsExactly(
                        "https://github.com/x/tool stdin header = \"Authorization: Bearer "
                                + TOKEN
                                + "\"",
                        MIRROR + "/pinned/" + sha("pinned") + "/tool stdin ");
        assertThat(ran.out()).doesNotContain(TOKEN);
    }

    // Prevents a mirror set to something other than an https address from being asked.
    @Test
    void theShellRefusesAMirrorThatIsNoHttpsAddress() throws IOException, InterruptedException {
        for (final String mirror :
                List.of(
                        "http://mirror.example/files",
                        "https://mirror.example/a b",
                        "https://user:secret@mirror.example/files",
                        "https://mirror.example/files?token=secret",
                        "https://mirror.example/files#secret",
                        "mirror.example")) {
            Files.deleteIfExists(dir.resolve("calls"));
            final Ran ran =
                    shell(
                            fetchTool(),
                            "echo called >> " + dir.resolve("calls") + "; exit 99",
                            "echo \"slept $1\" >&2",
                            Map.of("PORELO_PINNED_MIRROR", mirror));

            assertThat(ran.exit()).as(mirror + ": " + ran.out()).isEqualTo(1);
            assertThat(ran.out())
                    .as(mirror)
                    .contains("PORELO_PINNED_MIRROR")
                    .doesNotContain("secret");
            assertThat(dir.resolve("calls")).as(mirror + ": nothing fetched").doesNotExist();
        }
    }

    // Prevents a mirror that a caller turned off from being asked all the same.
    @Test
    void theShellAsksNoMirrorWhenItIsSetEmptyAndTheOrganizationsOneWhenItIsUnset()
            throws IOException, InterruptedException {
        final Path calls = dir.resolve("calls");
        final String curl = "for a; do last=$a; done; echo \"$last\" >> " + calls + "; printf 404";

        final Ran off = shell(fetchTool(), curl);
        final List<String> offCalls = Files.readAllLines(calls);
        Files.delete(calls);
        final Ran unset = shell("unset PORELO_PINNED_MIRROR; " + fetchTool(), curl);

        assertThat(off.exit()).isEqualTo(1);
        assertThat(offCalls).as("no mirror").containsExactly("https://example.invalid/tool");
        assertThat(unset.exit()).isEqualTo(1);
        assertThat(Files.readAllLines(calls))
                .as("the organization's Artifactory by the file's digest and name")
                .containsExactly(
                        "https://example.invalid/tool",
                        PinnedJdk.MIRROR + "/pinned/" + sha("pinned") + "/tool");
    }

    private String fetchTool() {
        return "pinned_fetch https://example.invalid/tool "
                + sha("pinned")
                + " "
                + dir.resolve("tool");
    }

    /**
     * Fetches with {@link #MIRROR} as the mirror and a stand-in curl that writes each call, with
     * the clock's time, to {@code calls}, and answers as the first source or the mirror is told.
     */
    private Ran mirrored(String body, Path clock, String first, String mirror)
            throws IOException, InterruptedException {
        return shell(
                body,
                "for a; do last=$a; done\n"
                        + "echo \"$(cat "
                        + clock
                        + ") $last\" >> "
                        + dir.resolve("calls")
                        + "\nwhile [ $# -gt 0 ]; do [ \"$1\" = -o ] && out=$2; shift; done\n"
                        + "case $last in "
                        + MIRROR
                        + "/*) "
                        + mirror
                        + " ;; *) "
                        + first
                        + " ;; esac",
                "echo $(($(cat " + clock + ") + $1)) > " + clock + "; echo \"slept $1\" >&2",
                Map.of("PORELO_PINNED_MIRROR", MIRROR));
    }

    private static final String TOKEN = "ghs_" + "B".repeat(36);

    /**
     * Fetches with a stand-in curl that writes each call to {@code calls}: its mode, what came on
     * stdin, and the address. With -K it answers a redirect to a signed address, else the file.
     */
    private Ran githubFetch(String url, Map<String, String> env)
            throws IOException, InterruptedException {
        final Path calls = dir.resolve("calls");
        return shell(
                "pinned_fetch " + url + " " + sha("pinned") + " " + dir.resolve("tool"),
                "mode=; in=; case \" $* \" in *' -K - '*) mode='-K -'; in=$(cat) ;; esac\n"
                        + "case \" $* \" in *' -L '*) mode=\"${mode:+$mode }-L\" ;; esac\n"
                        + "for a; do last=$a; done\n"
                        + "printf '%s stdin %s url %s\\n' \"$mode\" \"$in\" \"$last\" >> "
                        + calls
                        + "\nwhile [ $# -gt 0 ]; do [ \"$1\" = -o ] && out=$2; shift; done\n"
                        + "if [ \"$mode\" = '-K -' ]; then printf '302 https://objects.example/signed'; "
                        + "else printf pinned > \"$out\"; printf 200; fi",
                "echo \"slept $1\" >&2",
                env);
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
        return shell(body, curl, sleep, Map.of());
    }

    private Ran shell(String body, String curl, String sleep, Map<String, String> env)
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
        builder.environment().remove("GITHUB_SERVER_URL");
        builder.environment().remove("PORELO_PINNED_GITHUB_TOKEN");
        // No mirror unless a test names one, so a fake curl never answers for the real one.
        builder.environment().put("PORELO_PINNED_MIRROR", "");
        builder.environment().putAll(env);
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
