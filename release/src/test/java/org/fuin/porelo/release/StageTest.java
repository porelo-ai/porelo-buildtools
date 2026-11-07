package org.fuin.porelo.release;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.fuin.porelo.json.Json;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class StageTest {
    @TempDir Path directory;
    private final ByteArrayOutputStream out = new ByteArrayOutputStream();
    private final ByteArrayOutputStream err = new ByteArrayOutputStream();
    private String time = "2026-01-01T10:00:00Z";

    @BeforeEach
    void preparesAnIsolatedRepository() throws Exception {
        git("init", "-q", "-b", "main");
        write(
                "issues/EX12-Sample.md",
                "# Sample\n\n**Status:** Implementing.\n**Size:** M - sample.\n");
        write("stats/seed.csv", "size,minutes\nXS,8\nS,27\nM,53\nL,80\n");
    }

    @Test
    void refusesASecondOwnStageWithoutLosingTheFirst() throws Exception {
        assertThat(run("begin", "EX12", "implement")).as(errors()).isZero();
        assertThat(run("begin", "EX12", "review"))
                .as("overlapping own work is double counted")
                .isEqualTo(2);
        assertThat(errors()).as("the refusal explains the open stage").contains("open");
        time = "2026-01-01T10:04:00Z";
        assertThat(run("end", "EX12", "implement")).as(errors()).isZero();
        assertThat(records()).as("the first stage survives").hasSize(1);
        assertThat(record().get("stage"))
                .as("the refused begin cannot replace work")
                .isEqualTo("implement");
    }

    @Test
    void appendsExactlyOneClosedStageWithOnlyTheClaudeWindowTokens() throws Exception {
        final Path log = fixture("claude.jsonl", "opaque.data");
        assertThat(run("begin", "EX12", "implement", "--session", log.toString()))
                .as(errors())
                .isZero();
        time = "2026-01-01T10:04:00Z";
        assertThat(run("end", "EX12", "implement")).as(errors()).isZero();
        assertThat(records()).as("one end appends one JSON line").hasSize(1);
        final Map<?, ?> record = record();
        assertThat(record.get("claude_input_tokens"))
                .as("outside-window usage is excluded")
                .isEqualTo(17.0);
        assertThat(record.get("claude_output_tokens")).as("both responses count").isEqualTo(7.0);
        assertThat(record.get("claude_cache_read_tokens"))
                .as("cache reads are separate")
                .isEqualTo(28.0);
        assertThat(record.get("claude_cache_write_tokens"))
                .as("cache writes are separate")
                .isEqualTo(3.0);
        assertThat(record.get("claude_messages"))
                .as("only assistant usage entries count")
                .isEqualTo(2.0);
        assertThat(record.get("wall_min"))
                .as("the injected clock measures elapsed minutes")
                .isEqualTo(4.0);
        assertThat(record.get("end")).as("open stages never enter tracked stats").isEqualTo(time);
        assertThat(records().getFirst())
                .as("records contain no session path")
                .doesNotContain(log.toString());
        assertThat(run("end", "EX12", "implement")).as("retry cannot double count").isEqualTo(2);
        assertThat(records()).as("repeated end leaves one line").hasSize(1);
    }

    @Test
    void countsTheCodexSessionOnceBesideAnOpenOwnStage() throws Exception {
        final Path log = fixture("codex.jsonl", "opaque.data");
        assertThat(run("begin", "EX12", "implement")).as(errors()).isZero();
        assertThat(
                        run(
                                "begin",
                                "EX12",
                                "review",
                                "--actor",
                                "subagent",
                                "--session",
                                log.toString(),
                                "--model",
                                "codex"))
                .as(errors())
                .isZero();
        time = "2026-01-01T10:04:00Z";
        assertThat(run("end", "EX12", "review", "--actor", "subagent", "--session", log.toString()))
                .as(errors())
                .isZero();
        final Map<?, ?> record = record();
        assertThat(record.get("actor"))
                .as("subagents do not consume the own stage")
                .isEqualTo("subagent");
        assertThat(record.get("codex_input_tokens"))
                .as("cumulative totals must not be summed twice")
                .isEqualTo(160.0);
        assertThat(record.get("codex_cached_tokens"))
                .as("cached input is preserved")
                .isEqualTo(50.0);
        assertThat(record.get("codex_output_tokens")).as("output is preserved").isEqualTo(35.0);
        assertThat(record.get("codex_reasoning_tokens"))
                .as("reasoning is preserved")
                .isEqualTo(15.0);
        assertThat(run("end", "EX12", "implement"))
                .as("own stage remains independently open")
                .isZero();
        assertThat(records()).as("both actors are recorded separately").hasSize(2);
    }

    @ParameterizedTest
    @ValueSource(strings = {"agy.json", "agy-token-fields.json"})
    void detectsAgyUsageByContent(String resource) throws Exception {
        final Path log = fixture(resource, "opaque.data");
        assertThat(
                        run(
                                "begin",
                                "EX12",
                                "review",
                                "--actor",
                                "subagent",
                                "--session",
                                log.toString(),
                                "--model",
                                "agy"))
                .as(errors())
                .isZero();
        time = "2026-01-01T10:04:00Z";
        assertThat(run("end", "EX12", "review", "--actor", "subagent", "--session", log.toString()))
                .as(errors())
                .isZero();
        assertThat(record().get("agy_input_tokens")).as("agy input is not guessed").isEqualTo(12.0);
        assertThat(record().get("agy_cache_read_tokens"))
                .as("agy cache reads are preserved")
                .isEqualTo(5.0);
        assertThat(record().get("agy_output_tokens")).as("agy output is preserved").isEqualTo(8.0);
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "{\"usage\":{\"input\":12,\"output\":8}}",
                "{\"usage\":{\"input\":12,\"cacheRead\":5,\"output\":8,\"input_tokens\":12}}"
            })
    void refusesIncompleteOrMixedAgyUsageWithoutInventingTokens(String content) throws Exception {
        final Path log = write("incomplete.data", content);
        assertThat(
                        run(
                                "begin",
                                "EX12",
                                "review",
                                "--actor",
                                "subagent",
                                "--session",
                                log.toString(),
                                "--model",
                                "agy"))
                .as(errors())
                .isZero();
        time = "2026-01-01T10:04:00Z";
        assertThat(run("end", "EX12", "review", "--actor", "subagent", "--session", log.toString()))
                .as("ambiguous or absent usage must not be replaced with guessed counts")
                .isEqualTo(2);
        assertThat(Files.exists(directory.resolve("stats/EX12.jsonl")))
                .as("invalid usage produces no committed measurement")
                .isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"../EX12", "ex12", "EX1", "EX1234", "ABCD12"})
    void refusesAnInvalidIssueBeforeWritingAnything(String issue) throws Exception {
        assertThat(run("begin", issue, "implement"))
                .as("untrusted issue must not become a path")
                .isEqualTo(2);
        assertThat(Files.exists(directory.resolve(".git/porelo-stage")))
                .as("invalid input creates no state")
                .isFalse();
        assertThat(Files.exists(directory.resolve("stats/EX12.jsonl")))
                .as("invalid input creates no stats")
                .isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"unknown", "two words", "../vm", "VM"})
    void refusesAnInvalidWaitReasonBeforeWritingAnything(String reason) throws Exception {
        assertThat(run("begin", "EX12", "wait", "--wait", reason))
                .as("invalid reasons must not enter records")
                .isEqualTo(2);
        assertThat(Files.exists(directory.resolve(".git/porelo-stage")))
                .as("invalid input creates no state")
                .isFalse();
    }

    @Test
    void leavesUnknownTokensNullAndNumbersRepeatedStages() throws Exception {
        for (int n = 1; n <= 2; n++) {
            assertThat(run("begin", "EX12", "review")).as(errors()).isZero();
            assertThat(run("end", "EX12", "review")).as(errors()).isZero();
            assertThat(record().get("n")).as("repeated work is counted").isEqualTo((double) n);
            assertThat(record().get("claude_input_tokens"))
                    .as("missing sessions never invent usage")
                    .isNull();
            assertThat(record().get("note"))
                    .as("missing usage is explicit")
                    .isEqualTo("no-session");
        }
    }

    @Test
    void refusesMalformedUsageWithoutClosingOrWritingTheStage() throws Exception {
        final Path log =
                write(
                        "bad.data",
                        "{\"type\":\"assistant\",\"timestamp\":\"2026-01-01T10:01:00Z\",\"message\":{\"usage\":{\"input_tokens\":-1}}}\n");
        assertThat(run("begin", "EX12", "test", "--session", log.toString())).as(errors()).isZero();
        time = "2026-01-01T10:04:00Z";
        assertThat(run("end", "EX12", "test")).as("corrupt usage fails closed").isEqualTo(2);
        assertThat(Files.exists(directory.resolve("stats/EX12.jsonl")))
                .as("no false measurements are published")
                .isFalse();
    }

    @Test
    void takesTheMedianOfTheSeedAndFinishedOwnWorkOnly() throws Exception {
        write(
                "stats/EX13.jsonl",
                measured("EX13", "plan", "agent", 10, "Implementing")
                        + measured("EX13", "implement", "agent", 30, "Implementing")
                        + measured("EX13", "wait", "agent", 100, "Implementing")
                        + measured("EX13", "review", "subagent", 100, "Implementing")
                        + measured("EX13", "handover", "agent", 5, "Finished"));
        write(
                "stats/EX14.jsonl",
                measured("EX14", "analyze", "agent", 200, "Implementing")
                        + measured("EX14", "implement", "agent", 50, "Finished"));
        write("stats/EX15.jsonl", measured("EX15", "implement", "agent", 1000, "Review"));
        assertThat(run("median", "M")).as(errors()).isZero();
        assertThat(out.toString(StandardCharsets.UTF_8).strip())
                .as(
                        "median of 45, 50, 53 excludes waits, analysis, subagents and unfinished"
                                + " issues")
                .isEqualTo("50");
    }

    @Test
    void readsTheOwnSessionFromTheEnvironmentWithoutRecordingItsPath() throws Exception {
        final Path log = fixture("claude.jsonl", "environment.data");
        final Map<String, String> environment = Map.of("PORELO_STAGE_CLAUDE_LOG", log.toString());
        assertThat(runAt(directory, environment, "begin", "EX12", "test")).as(errors()).isZero();
        time = "2026-01-01T10:04:00Z";
        assertThat(runAt(directory, Map.of(), "end", "EX12", "test"))
                .as("end retains begin's source even when the environment changes")
                .isZero();
        assertThat(record().get("claude_input_tokens"))
                .as("the supplied environment is honored")
                .isEqualTo(17.0);
        assertThat(records().getFirst())
                .as("the source stays private")
                .doesNotContain(log.toString());
    }

    @Test
    void keepsTwoSubagentSessionsSeparateAndRefusesReusingAnOpenSession() throws Exception {
        final Path first = fixture("codex.jsonl", "first.data");
        final Path second = fixture("agy.json", "second.data");
        assertThat(
                        run(
                                "begin",
                                "EX12",
                                "review",
                                "--actor",
                                "subagent",
                                "--session",
                                first.toString(),
                                "--model",
                                "codex"))
                .as(errors())
                .isZero();
        assertThat(
                        run(
                                "begin",
                                "EX12",
                                "review",
                                "--actor",
                                "subagent",
                                "--session",
                                first.toString(),
                                "--model",
                                "codex"))
                .as("one open stage per session prevents duplicate accounting")
                .isEqualTo(2);
        assertThat(
                        run(
                                "begin",
                                "EX12",
                                "review",
                                "--actor",
                                "subagent",
                                "--session",
                                second.toString(),
                                "--model",
                                "agy"))
                .as("another subagent may run concurrently")
                .isZero();
        time = "2026-01-01T10:04:00Z";
        assertThat(
                        run(
                                "end",
                                "EX12",
                                "review",
                                "--actor",
                                "subagent",
                                "--session",
                                second.toString()))
                .as(errors())
                .isZero();
        assertThat(record().get("n")).as("open sessions reserve distinct ordinals").isEqualTo(2.0);
        assertThat(record().get("codex_input_tokens"))
                .as("the other session is not mixed in")
                .isNull();
        assertThat(
                        run(
                                "end",
                                "EX12",
                                "review",
                                "--actor",
                                "subagent",
                                "--session",
                                first.toString()))
                .as(errors())
                .isZero();
        assertThat(record().get("n")).as("each session retains its own ordinal").isEqualTo(1.0);
        assertThat(record().get("agy_input_tokens"))
                .as("the other session is not mixed in")
                .isNull();
    }

    @Test
    void storesWorktreeStateInItsGitDirectoryAndStatsInItsOwnTree() throws Exception {
        git("add", "issues", "stats/seed.csv");
        git(
                "-c",
                "user.name=Example",
                "-c",
                "user.email=example@example.org",
                "commit",
                "-q",
                "-m",
                "Prepare work");
        final Path linked = directory.resolve("linked");
        git("worktree", "add", "--detach", linked.toString(), "HEAD");
        assertThat(runAt(linked, Map.of(), "begin", "EX12", "implement")).as(errors()).isZero();
        assertThat(Files.exists(linked.resolve("stats/EX12.jsonl")))
                .as("open stages never enter the tree")
                .isFalse();
        assertThat(
                        Files.isRegularFile(
                                directory.resolve(".git/worktrees/linked/porelo-stage/agent.json")))
                .as("a linked worktree's .git is a file, not the storage directory")
                .isTrue();
        time = "2026-01-01T10:04:00Z";
        assertThat(runAt(linked, Map.of(), "end", "EX12", "implement")).as(errors()).isZero();
        assertThat(Files.readAllLines(linked.resolve("stats/EX12.jsonl")))
                .as("the linked tree receives the closed stage")
                .hasSize(1);
        assertThat(Files.exists(directory.resolve("stats/EX12.jsonl")))
                .as("another worktree's measurements are untouched")
                .isFalse();
    }

    @Test
    void recordsTheEstimateAndTheExplicitResultAndStatus() throws Exception {
        assertThat(run("begin", "EX12", "estimate", "--model", "example-model", "--effort", "high"))
                .as(errors())
                .isZero();
        time = "2026-01-01T10:04:00Z";
        assertThat(
                        run(
                                "end",
                                "EX12",
                                "estimate",
                                "--result",
                                "returned",
                                "--status-after",
                                "Review"))
                .as(errors())
                .isZero();
        assertThat(record().get("estimate_min"))
                .as("an estimate is measured from its size seed")
                .isEqualTo(53.0);
        assertThat(record().get("result"))
                .as("returns are retained rather than reported as done")
                .isEqualTo("returned");
        assertThat(record().get("status_before"))
                .as("begin's status is preserved")
                .isEqualTo("Implementing");
        assertThat(record().get("status_after"))
                .as("end records the supplied state")
                .isEqualTo("Review");
    }

    @Test
    void closesWithAnExplicitStatusAfterTheIssueFileWasRemoved() throws Exception {
        assertThat(run("begin", "EX12", "handover")).as(errors()).isZero();
        Files.delete(directory.resolve("issues/EX12-Sample.md"));
        time = "2026-01-01T10:04:00Z";
        assertThat(run("end", "EX12", "handover", "--status-after", "Finished"))
                .as("finishing an issue removes its file but must not strand its stage")
                .isZero();
        assertThat(record().get("status_after"))
                .as("finished work remains available to the median")
                .isEqualTo("Finished");
    }

    @Test
    void refusesAConflictingRecordWithoutAStartInsteadOfCrashing() throws Exception {
        assertThat(run("begin", "EX12", "implement")).as(errors()).isZero();
        final String malformed =
                "{\"n\":1,\"stage\":\"implement\",\"actor\":\"agent\",\"end\":\"2026-01-01T10:00:00Z\"}\n";
        write("stats/EX12.jsonl", malformed);
        time = "2026-01-01T10:04:00Z";
        assertThat(run("end", "EX12", "implement"))
                .as("conflicting stored data must refuse rather than crash or duplicate work")
                .isEqualTo(2);
        assertThat(Files.readString(directory.resolve("stats/EX12.jsonl")))
                .as("the conflicting record is not overwritten")
                .isEqualTo(malformed);
        assertThat(Files.isRegularFile(directory.resolve(".git/porelo-stage/agent.json")))
                .as("failed end preserves the open stage for repair")
                .isTrue();
    }

    private static String measured(
            String issue, String stage, String actor, int minutes, String status) {
        return "{\"issue\":\""
                + issue
                + "\",\"stage\":\""
                + stage
                + "\",\"actor\":\""
                + actor
                + "\",\"size\":\"M\",\"wall_min\":"
                + minutes
                + ",\"status_after\":\""
                + status
                + "\",\"end\":\"2026-01-01T10:04:00Z\"}\n";
    }

    private int run(String... arguments) {
        return runAt(directory, Map.of(), arguments);
    }

    private int runAt(Path root, Map<String, String> environment, String... arguments) {
        return new Stage(
                        root,
                        Clock.fixed(Instant.parse(time), ZoneOffset.UTC),
                        new PrintStream(out, true, StandardCharsets.UTF_8),
                        new PrintStream(err, true, StandardCharsets.UTF_8),
                        environment)
                .run(List.of(arguments));
    }

    private Path fixture(String resource, String name) throws Exception {
        final Path path = directory.resolve(name);
        try (var input = StageTest.class.getResourceAsStream("/stages/" + resource)) {
            if (input == null) {
                throw new IOException("Missing fixture " + resource);
            }
            Files.copy(input, path);
        }
        return path;
    }

    private Path write(String name, String text) throws IOException {
        final Path path = directory.resolve(name);
        Files.createDirectories(path.getParent());
        return Files.writeString(path, text);
    }

    private List<String> records() throws IOException {
        return Files.readAllLines(directory.resolve("stats/EX12.jsonl"));
    }

    private Map<?, ?> record() throws IOException {
        return (Map<?, ?>) Json.parse(records().getLast());
    }

    private String errors() {
        return err.toString(StandardCharsets.UTF_8);
    }

    private void git(String... arguments) throws Exception {
        final var command = new java.util.ArrayList<>(List.of("git", "-C", directory.toString()));
        command.addAll(List.of(arguments));
        final ProcessBuilder builder = new ProcessBuilder(command).redirectErrorStream(true);
        builder.environment().put("GIT_CONFIG_GLOBAL", "/dev/null");
        builder.environment().put("GIT_CONFIG_NOSYSTEM", "1");
        final Process process = builder.start();
        final String output =
                new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertThat(process.waitFor()).as(output).isZero();
    }
}
