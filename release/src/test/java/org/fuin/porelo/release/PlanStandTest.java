package org.fuin.porelo.release;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Prevents render dates from drifting with wall time or a checkout's file timestamps. */
class PlanStandTest {
    @TempDir Path directory;

    @Test
    void manyForgeIssueFilesCostOneCommitRequestPerRepository() throws Exception {
        final Path plan = directory.resolve("plan.yml");
        Files.writeString(plan, "plan");
        final FakeWeb web = new FakeWeb();
        final var sources = new java.util.ArrayList<java.net.URI>();
        for (final String repository : List.of("porelo-project", "porelo-sample")) {
            for (final String path :
                    List.of(
                            "issues/README.md",
                            "issues/base/README.md",
                            "issues/SA01-One.md",
                            "issues/SA02-Two.md")) {
                sources.add(
                        java.net.URI.create(
                                "https://raw.githubusercontent.com/porelo-ai/"
                                        + repository
                                        + "/main/"
                                        + path));
            }
            web.serve(
                    "https://api.github.com/repos/porelo-ai/"
                            + repository
                            + "/commits?sha=main&path=issues&per_page=1",
                    "[{\"commit\":{\"committer\":{\"date\":\"2026-10-23T11:00:00Z\"}}}]");
        }
        assertThat(PlanStand.read(plan, sources, web))
                .as("the issues directory commit covers every issue source")
                .startsWith("2026-10-23T11:00:00Z");
        assertThat(web.asked.stream().map(Object::toString).toList())
                .as("four sources per repository must not exhaust the forge rate limit")
                .containsExactlyInAnyOrder(
                        "https://api.github.com/repos/porelo-ai/porelo-project/commits?sha=main&path=issues&per_page=1",
                        "https://api.github.com/repos/porelo-ai/porelo-sample/commits?sha=main&path=issues&per_page=1");
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {" garbage", " []"})
    void forgeCommitMetadataMustEndAfterItsJsonValue(String suffix) throws Exception {
        final Path plan = directory.resolve("plan.yml");
        Files.writeString(plan, "plan");
        final FakeWeb web = new FakeWeb();
        web.serve(
                "https://api.github.com/repos/porelo-ai/porelo-sample/commits?sha=main&path=issues&per_page=1",
                "[{\"commit\":{\"committer\":{\"date\":\"2026-10-23T11:00:00Z\"}}}]" + suffix);
        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () ->
                                PlanStand.read(
                                        plan,
                                        List.of(
                                                java.net.URI.create(
                                                        "https://raw.githubusercontent.com/porelo-ai/porelo-sample/main/issues/README.md")),
                                        web))
                .as("an unreadable suffix cannot supply a trusted Stand timestamp")
                .isInstanceOf(java.io.IOException.class);
    }

    @Test
    void localInputsUseTheirLatestGitCommitRatherThanFileTimes() throws Exception {
        final Path plan = Path.of("src/test/resources/plans/0.6/plan.yml").toAbsolutePath();
        final Path index =
                Path.of("src/test/resources/plans/checkouts/porelo-project/issues/README.md")
                        .toAbsolutePath();
        final var dates = new java.util.ArrayList<Instant>();
        for (final Path path : List.of(plan, index)) {
            final ProcessBuilder command =
                    new ProcessBuilder(
                            "git",
                            "-C",
                            path.getParent().toString(),
                            "log",
                            "-1",
                            "--format=%cI",
                            "--",
                            path.getFileName().toString());
            command.environment().put("GIT_CONFIG_GLOBAL", "/dev/null");
            final Process process = command.start();
            final String date =
                    new String(
                                    process.getInputStream().readAllBytes(),
                                    java.nio.charset.StandardCharsets.UTF_8)
                            .strip();
            assertThat(process.waitFor()).as("fixture commit history is readable").isZero();
            dates.add(Instant.parse(date));
        }
        final Path undated = directory.resolve("untracked.yml");
        Files.writeString(undated, "untracked");
        Files.setLastModifiedTime(undated, java.nio.file.attribute.FileTime.from(Instant.EPOCH));
        final String stand =
                PlanStand.read(
                        plan, List.of(index.toUri(), undated.toUri()), FakeWeb.untouchable());
        assertThat(stand)
                .as("the latest committed input dates the page; untracked inputs are explicit")
                .startsWith(dates.stream().max(Comparator.naturalOrder()).orElseThrow().toString())
                .contains("no commit time for untracked.yml", "no file-time fallback");
    }
}
