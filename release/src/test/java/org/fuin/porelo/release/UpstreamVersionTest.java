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
import java.util.HashMap;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class UpstreamVersionTest {

    private static final String STABLE = AgentRepository.CLAUDE_RELEASES + "/stable";

    @TempDir Path directory;

    private final ByteArrayOutputStream out = new ByteArrayOutputStream();

    private final ByteArrayOutputStream err = new ByteArrayOutputStream();

    private final Map<String, String> env = new HashMap<>();

    @Test
    void aNewerVersionOnThePomsChannelIsAnUpdate() {
        final AgentRepository claude =
                AgentRepository.claude(directory, "2.1.236", "1.0.0-SNAPSHOT");

        assertThat(answer(claude, new FakeWeb().serve(STABLE, "2.1.267\n"), null, null))
                .as(report())
                .isEqualTo(0);
        assertThat(stdout())
                .isEqualTo(
                        """
                pinned=2.1.236
                upstream=2.1.267
                source=channel stable
                update=yes
                major=same
                """);
    }

    @Test
    void theChannelOnTheCommandLineWinsOverThePoms() {
        final AgentRepository claude =
                AgentRepository.claude(directory, "2.1.236", "1.0.0-SNAPSHOT");
        final FakeWeb web =
                new FakeWeb().serve(AgentRepository.CLAUDE_RELEASES + "/latest", "2.1.267");

        assertThat(answer(claude, web, "latest", null)).as(report()).isEqualTo(0);
        assertThat(stdout()).contains("source=channel latest");
    }

    @Test
    void anOlderVersionFromAPointerIsSaidToBeARollback() {
        final AgentRepository claude =
                AgentRepository.claude(directory, "2.1.267", "1.0.0-SNAPSHOT");

        assertThat(answer(claude, new FakeWeb().serve(STABLE, "2.1.236"), null, null))
                .as(report())
                .isEqualTo(0);
        assertThat(stdout()).contains("update=rollback");
    }

    @Test
    void aVersionNamedByHandIsAnsweredWithoutAskingUpstream() {
        final AgentRepository claude =
                AgentRepository.claude(directory, "2.1.267", "1.0.0-SNAPSHOT");

        assertThat(answer(claude, FakeWeb.untouchable(), null, "2.1.236"))
                .as(report())
                .isEqualTo(0);
        assertThat(stdout()).contains("source=a version named by hand").contains("update=yes");
    }

    @Test
    void aNamedVersionThatIsNotAVersionIsUnanswered() {
        final AgentRepository claude =
                AgentRepository.claude(directory, "2.1.267", "1.0.0-SNAPSHOT");

        assertThat(answer(claude, FakeWeb.untouchable(), null, "latest")).as(report()).isEqualTo(2);
        assertThat(stdout()).isEmpty();
    }

    @Test
    void aPointerServingSomethingOtherThanAVersionIsUnanswered() {
        final AgentRepository claude =
                AgentRepository.claude(directory, "2.1.236", "1.0.0-SNAPSHOT");

        assertThat(answer(claude, new FakeWeb().serve(STABLE, "<html>moved</html>"), null, null))
                .as(report())
                .isEqualTo(2);
        assertThat(stdout()).as("no answer may reach a workflow").isEmpty();
        assertThat(stderr()).contains("did not answer with a version");
    }

    @Test
    void anUnreachablePointerIsUnansweredNotUpToDate() {
        final AgentRepository claude =
                AgentRepository.claude(directory, "2.1.236", "1.0.0-SNAPSHOT");

        assertThat(answer(claude, new FakeWeb().fail(STABLE, "connection refused"), null, null))
                .as(report())
                .isEqualTo(2);
        assertThat(stdout()).doesNotContain("update=no");
    }

    @Test
    void aMissingPointerIsUnansweredNotUpToDate() {
        final AgentRepository claude =
                AgentRepository.claude(directory, "2.1.236", "1.0.0-SNAPSHOT");

        assertThat(answer(claude, new FakeWeb(), null, null)).as(report()).isEqualTo(2);
    }

    @Test
    void readsTheDistTagFromTheNpmRegistry() {
        final AgentRepository pi = AgentRepository.pi(directory, "0.85.0");
        final FakeWeb web =
                new FakeWeb()
                        .serve(
                                AgentRepository.NPM_REGISTRY,
                                "{\"dist-tags\":{\"latest\":\"0.85.1\",\"legacy-node20\":\"0.70.0\"}}");

        assertThat(answer(pi, web, null, null)).as(report()).isEqualTo(0);
        assertThat(stdout())
                .contains("upstream=0.85.1")
                .contains("source=dist-tag latest")
                .contains("update=yes");
    }

    @Test
    void aDistTagTheRegistryDoesNotHaveIsUnansweredAndNamesTheOnesItHas() {
        final AgentRepository pi = AgentRepository.pi(directory, "0.85.0");
        final FakeWeb web =
                new FakeWeb()
                        .serve(
                                AgentRepository.NPM_REGISTRY,
                                "{\"dist-tags\":{\"latest\":\"0.85.1\"}}");

        assertThat(answer(pi, web, "next", null)).as(report()).isEqualTo(2);
        assertThat(stderr()).contains("no dist-tag 'next'").contains("latest");
    }

    @Test
    void readsTheNewestReleaseWithoutItsLeadingV() {
        final AgentRepository omp = AgentRepository.omp(directory, "18.1.13");
        final FakeWeb web =
                new FakeWeb().serve(AgentRepository.OMP_LATEST, "{\"tag_name\":\"v18.1.16\"}");

        assertThat(answer(omp, web, null, null)).as(report()).isEqualTo(0);
        assertThat(stdout()).contains("upstream=18.1.16").contains("source=newest release");
    }

    @Test
    void sendsTheGithubTokenWhenThereIsOne() {
        final AgentRepository omp = AgentRepository.omp(directory, "18.1.13");
        final FakeWeb web =
                new FakeWeb().serve(AgentRepository.OMP_LATEST, "{\"tag_name\":\"v18.1.16\"}");
        env.put("GITHUB_TOKEN", "t0ken");

        answer(omp, web, null, null);

        assertThat(web.headers)
                .singleElement()
                .satisfies(sent -> assertThat(sent).containsEntry("Authorization", "Bearer t0ken"));
    }

    @Test
    void neverSendsTheGithubTokenToAnyOtherHost() {
        final AgentRepository omp = AgentRepository.omp(directory, "18.1.13");
        final String elsewhere = "https://api.github.example/repos/omp/releases/latest";
        omp.write("pom.xml", omp.read("pom.xml").replace(AgentRepository.OMP_LATEST, elsewhere));
        final FakeWeb web = new FakeWeb().serve(elsewhere, "{\"tag_name\":\"v18.1.16\"}");
        env.put("GITHUB_TOKEN", "t0ken");

        assertThat(answer(omp, web, null, null)).as(report()).isEqualTo(0);

        assertThat(web.headers)
                .singleElement()
                .satisfies(sent -> assertThat(sent).doesNotContainKey("Authorization"));
    }

    @Test
    void theNewestReleaseTakesNoChannel() {
        final AgentRepository omp = AgentRepository.omp(directory, "18.1.13");

        assertThat(answer(omp, FakeWeb.untouchable(), "stable", null)).as(report()).isEqualTo(2);
    }

    @Test
    void saysWhenTheMajorVersionMoved() {
        final AgentRepository omp = AgentRepository.omp(directory, "18.1.13");

        answer(
                omp,
                new FakeWeb().serve(AgentRepository.OMP_LATEST, "{\"tag_name\":\"v19.0.0\"}"),
                null,
                null);

        assertThat(stdout()).contains("major=moved");
    }

    @Test
    void appendsTheAnswerToGithubOutput() throws IOException {
        final AgentRepository claude =
                AgentRepository.claude(directory, "2.1.236", "1.0.0-SNAPSHOT");
        final Path output = Files.writeString(directory.resolve("github-output"), "earlier=kept\n");
        env.put("GITHUB_OUTPUT", output.toString());

        answer(claude, new FakeWeb().serve(STABLE, "2.1.267"), null, null);

        assertThat(Files.readString(output))
                .startsWith("earlier=kept\npinned=2.1.236\n")
                .contains("update=yes\n");
    }

    @Test
    void aPomThatPinsNothingIsUnansweredNotUpToDate() {
        final AgentRepository claude =
                AgentRepository.claude(directory, "2.1.236", "1.0.0-SNAPSHOT");
        claude.write(
                "pom.xml",
                claude.read("pom.xml")
                        .replace("<agent.cli.version>2.1.236</agent.cli.version>", ""));

        assertThat(answer(claude, FakeWeb.untouchable(), null, null)).as(report()).isEqualTo(2);
    }

    @Test
    void aReleaseYoungerThanTheAgeAskedForIsWaitedForNotTaken() {
        final AgentRepository claude =
                AgentRepository.claude(directory, "2.1.236", "1.0.0-SNAPSHOT");
        final FakeWeb web =
                new FakeWeb()
                        .serve(STABLE, "2.1.267")
                        .serve(
                                AgentRepository.CLAUDE_RELEASES + "/2.1.267/manifest.json",
                                "{\"buildDate\":\"2026-09-26T10:00:00Z\"}");

        assertThat(aged(claude, web, "3d")).as(report()).isEqualTo(0);
        assertThat(stdout())
                .isEqualTo(
                        """
                pinned=2.1.236
                upstream=2.1.267
                source=channel stable
                update=no
                major=same
                published=2026-09-26T10:00:00Z
                waiting=2026-09-29T10:00:00Z
                """);
    }

    @Test
    void aReleaseOldEnoughIsAnUpdateAndSaysWhenItWasPublished() {
        final AgentRepository claude =
                AgentRepository.claude(directory, "2.1.236", "1.0.0-SNAPSHOT");
        final FakeWeb web =
                new FakeWeb()
                        .serve(STABLE, "2.1.267")
                        .serve(
                                AgentRepository.CLAUDE_RELEASES + "/2.1.267/manifest.json",
                                "{\"buildDate\":\"2026-09-25T10:00:00Z\"}");

        assertThat(aged(claude, web, "3d")).as(report()).isEqualTo(0);
        assertThat(stdout())
                .contains("update=yes\n")
                .contains("published=2026-09-25T10:00:00Z")
                .doesNotContain("waiting=");
    }

    @Test
    void thePomsAgeAppliesWhenTheCommandLineNamesNone() {
        final AgentRepository claude =
                AgentRepository.claude(directory, "2.1.236", "1.0.0-SNAPSHOT");
        claude.write(
                "pom.xml",
                claude.read("pom.xml")
                        .replace(
                                "</properties>",
                                "    <porelo.release.min-age>3d</porelo.release.min-age>\n    </properties>"));
        final FakeWeb web =
                new FakeWeb()
                        .serve(STABLE, "2.1.267")
                        .serve(
                                AgentRepository.CLAUDE_RELEASES + "/2.1.267/manifest.json",
                                "{\"buildDate\":\"2026-09-27T10:00:00Z\"}");

        assertThat(aged(claude, web, null)).as(report()).isEqualTo(0);
        assertThat(stdout()).contains("update=no\n").contains("waiting=2026-09-30T10:00:00Z");
    }

    @Test
    void aVersionNamedByHandWaitsForNothing() {
        final AgentRepository claude =
                AgentRepository.claude(directory, "2.1.236", "1.0.0-SNAPSHOT");

        assertThat(
                        new UpstreamVersion(
                                        stream(out), stream(err), FakeWeb.untouchable(), env, NOW)
                                .answer(claude.pom(), null, "2.1.267", null, "3d"))
                .as(report())
                .isEqualTo(0);
        assertThat(stdout()).contains("update=yes\n").doesNotContain("published=");
    }

    @Test
    void aReleaseWithoutAReadableDateIsUnansweredNotOldEnough() {
        final AgentRepository pi = AgentRepository.pi(directory, "0.85.0");
        final FakeWeb web =
                new FakeWeb()
                        .serve(
                                AgentRepository.NPM_REGISTRY,
                                "{\"dist-tags\":{\"latest\":\"0.86.0\"},\"time\":{}}");

        assertThat(aged(pi, web, "3d")).as(report()).isEqualTo(Stop.UNANSWERED);
        assertThat(stdout()).isEmpty();
        assertThat(stderr()).contains("gives no date");
    }

    @Test
    void readsTheDateOfAnNpmReleaseAndOfANewestGithubRelease() {
        final AgentRepository pi = AgentRepository.pi(directory, "0.85.0");
        assertThat(
                        aged(
                                pi,
                                new FakeWeb()
                                        .serve(
                                                AgentRepository.NPM_REGISTRY,
                                                "{\"dist-tags\":{\"latest\":\"0.86.0\"},\"time\":{\"0.86.0\":\"2026-09-20T08:00:00.123Z\"}}"),
                                "3d"))
                .as(report())
                .isEqualTo(0);
        assertThat(stdout())
                .contains("update=yes\n")
                .contains("published=2026-09-20T08:00:00.123Z");

        out.reset();
        final AgentRepository omp = AgentRepository.omp(directory.resolve("omp"), "18.1.13");
        assertThat(
                        aged(
                                omp,
                                new FakeWeb()
                                        .serve(
                                                AgentRepository.OMP_LATEST,
                                                "{\"tag_name\":\"v18.1.16\",\"published_at\":\"2026-09-27T12:00:00Z\"}")
                                        .serve(
                                                AgentRepository.OMP_RELEASES,
                                                "[{\"tag_name\":\"v18.1.16\",\"published_at\":\"2026-09-27T12:00:00Z\"}]"),
                                "3d"))
                .as(report())
                .isEqualTo(0);
        assertThat(stdout()).contains("update=no\n").contains("waiting=2026-09-30T12:00:00Z");
    }

    @Test
    void takesTheNewestReleaseOldEnoughWhenNewerOnesAreTooYoung() {

        // Oh My Pi releases several times a day, so its newest release was never three days old and
        // the pin stood at 18.4.1 for three weeks (2026-10-18): the newest release that has stood
        // that long is what is taken, even when newer ones exist.
        final AgentRepository omp = AgentRepository.omp(directory, "18.4.1");
        final FakeWeb web =
                new FakeWeb()
                        .serve(
                                AgentRepository.OMP_LATEST,
                                "{\"tag_name\":\"v18.12.1\",\"published_at\":\"2026-09-28T09:00:00Z\"}")
                        .serve(
                                AgentRepository.OMP_RELEASES,
                                "[{\"tag_name\":\"v18.12.1\",\"published_at\":\"2026-09-28T09:00:00Z\"},"
                                        + "{\"tag_name\":\"v18.13.0-rc.1\",\"prerelease\":true,"
                                        + "\"published_at\":\"2026-09-20T09:00:00Z\"},"
                                        + "{\"tag_name\":\"v18.11.3\",\"published_at\":\"2026-09-26T09:00:00Z\"},"
                                        + "{\"tag_name\":\"v18.10.4\",\"published_at\":\"2026-09-24T09:00:00Z\"},"
                                        + "{\"tag_name\":\"v18.9.0\",\"published_at\":\"2026-09-20T09:00:00Z\"}]");

        assertThat(aged(omp, web, "3d")).as(report()).isEqualTo(0);
        assertThat(stdout())
                .as("the answer for a young newest release over an older one old enough")
                .contains("upstream=18.10.4\n")
                .contains("update=yes\n")
                .contains("published=2026-09-24T09:00:00Z")
                .contains("newest=18.12.1\n");
    }

    @Test
    void takesTheNewestNpmVersionOldEnoughWhenTheDistTagIsTooYoung() {
        final AgentRepository pi = AgentRepository.pi(directory, "0.85.0");
        final FakeWeb web =
                new FakeWeb()
                        .serve(
                                AgentRepository.NPM_REGISTRY,
                                "{\"dist-tags\":{\"latest\":\"0.87.0\"},\"time\":{"
                                        + "\"created\":\"2026-01-01T00:00:00Z\","
                                        + "\"0.87.0\":\"2026-09-28T08:00:00Z\","
                                        + "\"0.88.0-beta.1\":\"2026-09-01T08:00:00Z\","
                                        + "\"0.86.0\":\"2026-09-22T08:00:00Z\","
                                        + "\"0.85.0\":\"2026-09-10T08:00:00Z\"}}");

        assertThat(aged(pi, web, "3d")).as(report()).isEqualTo(0);
        assertThat(stdout())
                .as("the answer for a young dist-tag over an older version old enough")
                .contains("upstream=0.86.0\n")
                .contains("update=yes\n")
                .contains("newest=0.87.0\n");
    }

    @Test
    void waitsWhenNoReleaseNewerThanThePinIsOldEnough() {
        final AgentRepository omp = AgentRepository.omp(directory, "18.10.4");
        final FakeWeb web =
                new FakeWeb()
                        .serve(
                                AgentRepository.OMP_LATEST,
                                "{\"tag_name\":\"v18.12.1\",\"published_at\":\"2026-09-28T09:00:00Z\"}")
                        .serve(
                                AgentRepository.OMP_RELEASES,
                                "[{\"tag_name\":\"v18.12.1\",\"published_at\":\"2026-09-28T09:00:00Z\"},"
                                        + "{\"tag_name\":\"v18.10.4\",\"published_at\":\"2026-09-24T09:00:00Z\"}]");

        assertThat(aged(omp, web, "3d")).as(report()).isEqualTo(0);
        assertThat(stdout())
                .as("the answer when nothing newer than the pin has stood long enough")
                .contains("update=no\n")
                .contains("waiting=2026-10-01T09:00:00Z");
    }

    @Test
    void anAgeThatCannotBeReadIsUnanswered() {
        final AgentRepository claude =
                AgentRepository.claude(directory, "2.1.236", "1.0.0-SNAPSHOT");

        assertThat(aged(claude, new FakeWeb().serve(STABLE, "2.1.267"), "three days"))
                .as(report())
                .isEqualTo(Stop.UNANSWERED);
        assertThat(stderr()).contains("3d or 72h");
    }

    /** Noon on 2026-09-28. */
    private static final Clock NOW =
            Clock.fixed(Instant.parse("2026-09-28T12:00:00Z"), ZoneOffset.UTC);

    private int aged(AgentRepository repository, Web web, @Nullable String minAge) {
        return new UpstreamVersion(stream(out), stream(err), web, env, NOW)
                .answer(repository.pom(), null, null, null, minAge);
    }

    private static PrintStream stream(ByteArrayOutputStream target) {
        return new PrintStream(target, true, StandardCharsets.UTF_8);
    }

    private int answer(
            AgentRepository repository, Web web, @Nullable String channel, @Nullable String named) {
        return new UpstreamVersion(
                        new PrintStream(out, true, StandardCharsets.UTF_8),
                        new PrintStream(err, true, StandardCharsets.UTF_8),
                        web,
                        env)
                .answer(repository.pom(), channel, named);
    }

    private String stdout() {
        return out.toString(StandardCharsets.UTF_8);
    }

    private String stderr() {
        return err.toString(StandardCharsets.UTF_8);
    }

    private String report() {
        return "stdout:%n%s%nstderr:%n%s".formatted(stdout(), stderr());
    }
}
