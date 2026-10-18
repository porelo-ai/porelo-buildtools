package org.fuin.porelo.release;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link ProvenLegs}: a Build on {@code main} takes its legs from a run that proved the
 * same tree, rather than renting machines for it again.
 */
class ProvenLegsTest {

    private static final String REPO = "porelo-ai/porelo";

    private static final String TREE = "aaaa1111";

    private static final String RUNS =
            "https://api.github.com/repos/porelo-ai/porelo/actions/runs?per_page=100&created=%3E%3D2026-10-17T12:00:00Z";

    private static final Clock NOW =
            Clock.fixed(Instant.parse("2026-10-18T12:00:00Z"), ZoneOffset.UTC);

    private static final List<List<String>> LEGS = List.of(List.of("Leg - ubuntu", "Leg - fedora"));

    private final ByteArrayOutputStream out = new ByteArrayOutputStream();

    private final ByteArrayOutputStream err = new ByteArrayOutputStream();

    private final Map<String, String> env = new HashMap<>(Map.of("GITHUB_RUN_ID", "900"));

    private static String run(long id, String event, String tree, String created, String repo) {
        return "{\"id\":"
                + id
                + ",\"event\":\""
                + event
                + "\",\"created_at\":\""
                + created
                + "\",\"head_commit\":{\"tree_id\":\""
                + tree
                + "\"},\"repository\":{\"full_name\":\""
                + REPO
                + "\"},\"head_repository\":{\"full_name\":\""
                + repo
                + "\"}}";
    }

    private static String runs(String... runs) {
        return "{\"workflow_runs\":[" + String.join(",", runs) + "]}";
    }

    private static String jobs(String... nameAndConclusion) {
        final StringBuilder all = new StringBuilder("{\"jobs\":[");
        for (int at = 0; at < nameAndConclusion.length; at += 2) {
            all.append(at == 0 ? "" : ",")
                    .append("{\"name\":\"")
                    .append(nameAndConclusion[at])
                    .append("\",\"conclusion\":\"")
                    .append(nameAndConclusion[at + 1])
                    .append("\"}");
        }
        return all.append("]}").toString();
    }

    private static String jobsOf(long id) {
        return "https://api.github.com/repos/porelo-ai/porelo/actions/runs/"
                + id
                + "/jobs?per_page=100";
    }

    private int ask(FakeWeb web) {
        return new ProvenLegs(
                        new PrintStream(out, true, StandardCharsets.UTF_8),
                        new PrintStream(err, true, StandardCharsets.UTF_8),
                        web,
                        env,
                        NOW)
                .find(REPO, TREE, LEGS, "24h");
    }

    private String stdout() {
        return out.toString(StandardCharsets.UTF_8);
    }

    @Test
    void takesTheLegsOfARunOfTheSameTreeWhoseLegsAreGreen() {
        final FakeWeb web =
                new FakeWeb()
                        .serve(RUNS, runs(run(800, "push", TREE, "2026-10-18T06:00:00Z", REPO)))
                        .serve(
                                jobsOf(800),
                                jobs(
                                        "Build and unit tests",
                                        "success",
                                        "Leg - ubuntu",
                                        "success",
                                        "Leg - fedora",
                                        "success",
                                        "Publish",
                                        "failure"));

        assertThat(ask(web)).as(err.toString(StandardCharsets.UTF_8)).isZero();
        assertThat(stdout())
                .as("the answer for a run of the same tree with both legs green")
                .contains("proven=yes\n")
                .contains("run=800\n")
                .contains("legs taken from run 800, tree aaaa1111, 6h 0m old");
    }

    @Test
    void runsTheLegsWhenNoRunHadTheSameTree() {
        final FakeWeb web =
                new FakeWeb()
                        .serve(
                                RUNS,
                                runs(run(800, "push", "bbbb2222", "2026-10-18T06:00:00Z", REPO)))
                        .serve(
                                jobsOf(800),
                                jobs("Leg - ubuntu", "success", "Leg - fedora", "success"));

        assertThat(ask(web)).isZero();
        assertThat(stdout()).as("the answer for another tree").contains("proven=no\n");
    }

    @Test
    void runsTheLegsWhenOneOfThemWasNotGreen() {
        final FakeWeb web =
                new FakeWeb()
                        .serve(RUNS, runs(run(800, "push", TREE, "2026-10-18T06:00:00Z", REPO)))
                        .serve(
                                jobsOf(800),
                                jobs("Leg - ubuntu", "success", "Leg - fedora", "failure"));

        assertThat(ask(web)).isZero();
        assertThat(stdout()).as("the answer for a red fedora leg").contains("proven=no\n");
    }

    @Test
    void runsTheLegsWhenTheProvingRunIsOlderThanTheAge() {
        // The window the API is asked for is the bound, and the run's own date is held to it too.
        final FakeWeb web =
                new FakeWeb()
                        .serve(RUNS, runs(run(800, "push", TREE, "2026-10-17T11:00:00Z", REPO)))
                        .serve(
                                jobsOf(800),
                                jobs("Leg - ubuntu", "success", "Leg - fedora", "success"));

        assertThat(ask(web)).isZero();
        assertThat(stdout()).as("the answer for a run 25 h old").contains("proven=no\n");
    }

    @Test
    void neverTakesARunOfAPullRequestOrOfAFork() {
        final FakeWeb web =
                new FakeWeb()
                        .serve(
                                RUNS,
                                runs(
                                        run(
                                                801,
                                                "pull_request",
                                                TREE,
                                                "2026-10-18T06:00:00Z",
                                                REPO),
                                        run(
                                                802,
                                                "push",
                                                TREE,
                                                "2026-10-18T06:00:00Z",
                                                "someone/porelo")))
                        .serve(
                                jobsOf(801),
                                jobs("Leg - ubuntu", "success", "Leg - fedora", "success"))
                        .serve(
                                jobsOf(802),
                                jobs("Leg - ubuntu", "success", "Leg - fedora", "success"));

        assertThat(ask(web)).isZero();
        assertThat(stdout())
                .as("the answer for a pull request's and a fork's run")
                .contains("proven=no\n");
        assertThat(web.asked)
                .as("what was read")
                .noneMatch(address -> address.toString().contains("/runs/80"));
    }

    @Test
    void neverTakesItsOwnRun() {
        env.put("GITHUB_RUN_ID", "800");
        final FakeWeb web =
                new FakeWeb()
                        .serve(RUNS, runs(run(800, "push", TREE, "2026-10-18T06:00:00Z", REPO)))
                        .serve(
                                jobsOf(800),
                                jobs("Leg - ubuntu", "success", "Leg - fedora", "success"));

        assertThat(ask(web)).isZero();
        assertThat(stdout())
                .as("the answer when the only match is this run")
                .contains("proven=no\n");
    }

    @Test
    void aTagAlwaysRunsItsLegs() {
        env.put("GITHUB_REF_TYPE", "tag");

        assertThat(
                        new ProvenLegs(
                                        new PrintStream(out, true, StandardCharsets.UTF_8),
                                        new PrintStream(err, true, StandardCharsets.UTF_8),
                                        FakeWeb.untouchable(),
                                        env,
                                        NOW)
                                .find(REPO, TREE, LEGS, "24h"))
                .isZero();
        assertThat(stdout()).as("the answer on a tag").contains("proven=no\n").contains("tag");
    }

    @Test
    void takesAnUpdateRunWhoseOneJobProvedTheSameScenarios() {
        // An adapter's update run checks both legs inside one job, which fails on any red suite.
        final FakeWeb web =
                new FakeWeb()
                        .serve(RUNS, runs(run(803, "schedule", TREE, "2026-10-18T05:17:00Z", REPO)))
                        .serve(jobsOf(803), jobs("Follow upstream", "success"));

        assertThat(
                        new ProvenLegs(
                                        new PrintStream(out, true, StandardCharsets.UTF_8),
                                        new PrintStream(err, true, StandardCharsets.UTF_8),
                                        web,
                                        env,
                                        NOW)
                                .find(
                                        REPO,
                                        TREE,
                                        List.of(
                                                List.of(
                                                        "Acceptance - ubuntu",
                                                        "Acceptance - fedora"),
                                                List.of("Follow upstream")),
                                        "24h"))
                .isZero();
        assertThat(stdout())
                .as("the answer for an update run")
                .contains("proven=yes\n")
                .contains("run=803\n");
    }
}
