package org.fuin.porelo.release;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class TrackerTest {

    private static final String TRACKED =
            "project:\n  name: \"porelo\"\ntracker:\n  kind: github\n  project: porelo-ai/7\n";

    private static final String MARKDOWN = "project:\n  name: \"porelo\"\n";

    private static final Tracker.Origin NO_ORIGIN = at -> Optional.empty();

    private static final String PORELO_HOST = "https://github.com/porelo-ai/porelo-host.git";

    @TempDir Path directory;

    private final List<URI> asked = new ArrayList<>();

    // Prevents a Build after the cut-over from failing on issues/ that are no longer kept.
    @Test
    void readsGitHubIssuesFromTheTrackersKind() throws Stop {
        assertThat(Tracker.read(TRACKED, "project.yml").github())
                .as("kind github, in porelo's shape")
                .isTrue();
    }

    // Prevents a project that still keeps Markdown issues from losing its checks.
    @Test
    void readsMarkdownIssuesWhereNoTrackerIsNamed() throws Stop {
        assertThat(Tracker.read(MARKDOWN, "project.yml").github())
                .as("no tracker: the Markdown issues, as before")
                .isFalse();
    }

    // Prevents a tracker this does not know from passing checks it cannot replace.
    @Test
    void refusesAnotherKindOrATrackerOfAnotherShape() {
        for (final String text :
                List.of(
                        "project: {}\ntracker:\n  kind: jira\n",
                        "project: {}\ntracker: github\n",
                        "project: {}\ntracker:\n  project: porelo-ai/7\n",
                        "project: {}\ntracker:\n  kind: GitHub\n",
                        "project: {}\ntracker:\n  kind: 123\n")) {
            assertThatThrownBy(() -> Tracker.read(text, "project.yml"))
                    .as(text)
                    .isInstanceOfSatisfying(
                            Stop.class,
                            stop -> assertThat(stop.code()).as("refused").isEqualTo(Stop.REFUSED))
                    .hasMessageContaining("project.yml: tracker.kind");
        }
    }

    // Prevents a broken project file from being read as one without a tracker.
    @Test
    void refusesAFileThatIsNoMapping() {
        for (final String text : List.of("- a\n- b\n", "a: [\n", "a: 1\n---\nb: 2\n")) {
            assertThatThrownBy(() -> Tracker.read(text, "project.yml"))
                    .as(text)
                    .isInstanceOfSatisfying(
                            Stop.class,
                            stop -> assertThat(stop.code()).as("refused").isEqualTo(Stop.REFUSED));
        }
    }

    // Prevents a project file that cannot be read from being skipped for one further up or the
    // project's on GitHub.
    @Test
    void cannotAnswerWhenTheNearestProjectFileIsNoFile() throws IOException {
        Files.writeString(directory.resolve("project.yml"), TRACKED, StandardCharsets.UTF_8);
        final Path folder = Files.createDirectories(directory.resolve("a/project.yml"));
        final Path dangling = Files.createDirectories(directory.resolve("b"));
        Files.createSymbolicLink(dangling.resolve("project.yml"), directory.resolve("missing"));

        for (final Path below : List.of(folder.getParent(), dangling)) {
            assertThatThrownBy(() -> Tracker.of(below, web(Optional.of(MARKDOWN)), NO_ORIGIN))
                    .as(below.toString())
                    .isInstanceOfSatisfying(
                            Stop.class,
                            stop ->
                                    assertThat(stop.code())
                                            .as("unanswered")
                                            .isEqualTo(Stop.UNANSWERED))
                    .hasMessageContaining(below.resolve("project.yml").toString());
        }
        assertThat(asked).as("nothing read over the network").isEmpty();
    }

    // Prevents a fixture or the project's own repository from being judged by another project.
    @Test
    void takesTheNearestProjectFileAboveAndAsksNoServer() throws IOException, Stop {
        Files.writeString(directory.resolve("project.yml"), TRACKED, StandardCharsets.UTF_8);
        final Path fixture = Files.createDirectories(directory.resolve("fixture/issues"));
        Files.writeString(
                directory.resolve("fixture/project.yml"), MARKDOWN, StandardCharsets.UTF_8);

        assertThat(Tracker.of(fixture, web(Optional.of(TRACKED)), NO_ORIGIN).github())
                .as("the fixture's own file, not the one above it")
                .isFalse();
        assertThat(Tracker.of(directory, web(Optional.of(MARKDOWN)), NO_ORIGIN).github())
                .as("the directory's own file")
                .isTrue();
        assertThat(asked).as("nothing read over the network").isEmpty();
    }

    // Prevents a repository without a project file of its own from being judged without one.
    @ParameterizedTest
    @ValueSource(
            strings = {
                "https://github.com/porelo-ai/porelo-host",
                "https://github.com/porelo-ai/porelo-host.git",
                "git@github.com:porelo-ai/porelo-host.git",
                "ssh://git@github.com/Porelo-AI/porelo-host.git/"
            })
    void readsTheProjectsFileOnGitHubForACheckoutOfTheProject(String origin)
            throws IOException, Stop {
        final Path repository = Files.createDirectories(directory.resolve("repository/issues"));

        assertThat(
                        Tracker.of(repository, web(Optional.of(TRACKED)), at -> Optional.of(origin))
                                .github())
                .as("the tracker porelo-project's file names")
                .isTrue();
        assertThat(asked).as("porelo-project's file on main").containsExactly(Tracker.PROJECT);
    }

    // Prevents a test's fixture from being judged by the project's live tracker.
    @ParameterizedTest
    @ValueSource(
            strings = {
                "",
                "https://github.com/octo-org/project.git",
                "https://github.com/porelo-ai-test/porelo-host.git",
                "https://github.com/porelo-ai/porelo-host/tree/main",
                "/home/someone/porelo-ai/porelo-host"
            })
    void takesMarkdownIssuesWhereNoProjectFileStandsAboveAnotherDirectory(String origin)
            throws IOException, Stop {
        final Path fixture = Files.createDirectories(directory.resolve("fixture/issues"));

        assertThat(
                        Tracker.of(
                                        fixture,
                                        web(Optional.of(TRACKED)),
                                        at ->
                                                origin.isEmpty()
                                                        ? Optional.empty()
                                                        : Optional.of(origin))
                                .github())
                .as("Markdown issues under the fixture's issues/")
                .isFalse();
        assertThat(asked).as("nothing read over the network").isEmpty();
    }

    // Prevents a directory whose checkout cannot be told from being taken for either answer.
    @Test
    void cannotAnswerWhenTheOriginCannotBeTold() {
        final Path repository = directory.resolve("repository");
        assertThatThrownBy(
                        () ->
                                Tracker.of(
                                        repository,
                                        web(Optional.of(TRACKED)),
                                        at -> {
                                            throw new IOException("detected dubious ownership");
                                        }))
                .isInstanceOfSatisfying(
                        Stop.class,
                        stop -> assertThat(stop.code()).as("unanswered").isEqualTo(Stop.UNANSWERED))
                .hasMessageContaining("dubious ownership");
        assertThat(asked).as("nothing read over the network").isEmpty();
    }

    // Prevents a directory outside a checkout, or in a checkout without origin, from passing for
    // one of the project's repositories, and a rewrite or a hook's repository from deciding.
    @Test
    void readsTheOriginOfTheCheckoutAboveFromGit() throws IOException, InterruptedException {
        final Path outside = Files.createDirectories(directory.resolve("outside/issues"));
        assertThat(git().of(outside)).as("no checkout").isEmpty();

        final Path checkout = Files.createDirectories(directory.resolve("checkout"));
        git(checkout, "init", "-q");
        final Path below = Files.createDirectories(checkout.resolve("issues"));
        assertThat(git().of(below)).as("a checkout without origin").isEmpty();

        git(checkout, "remote", "add", "origin", PORELO_HOST);
        assertThat(git().of(below)).as("the checkout's origin").contains(PORELO_HOST);
        assertThat(git().of(below.resolve("missing/deeper")))
                .as("a directory not yet made, from the nearest one above it")
                .contains(PORELO_HOST);
        assertThat(
                        git(Map.of(
                                        "GIT_CONFIG_COUNT", "1",
                                        "GIT_CONFIG_KEY_0", "url.https://mirror.example/.insteadOf",
                                        "GIT_CONFIG_VALUE_0", "https://github.com/"))
                                .of(below))
                .as("the address as the checkout holds it, no rewrite applied")
                .contains(PORELO_HOST);

        final Path other = Files.createDirectories(directory.resolve("other"));
        git(other, "init", "-q");
        assertThat(git(Map.of("GIT_DIR", checkout.resolve(".git").toString())).of(other))
                .as("a hook's GIT_DIR, not the directory's own checkout")
                .isEmpty();
    }

    // Prevents a checkout git cannot read from being taken for no checkout at all.
    @Test
    void cannotTellTheOriginOfABrokenCheckout() throws IOException {
        final Path broken = Files.createDirectories(directory.resolve("broken"));
        Files.writeString(broken.resolve(".git"), "gitdir: " + directory.resolve("missing") + "\n");

        assertThatThrownBy(() -> git().of(broken))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("rev-parse")
                .hasMessageContaining("128");
    }

    // Prevents an unreachable project file from being taken for either answer.
    @Test
    void cannotAnswerWhenTheProjectsFileCannotBeRead() {
        final Path repository = directory.resolve("repository");
        assertThatThrownBy(
                        () ->
                                Tracker.of(
                                        repository,
                                        web(Optional.empty()),
                                        at -> Optional.of(PORELO_HOST)))
                .as("a 404")
                .isInstanceOfSatisfying(
                        Stop.class,
                        stop -> assertThat(stop.code()).as("unanswered").isEqualTo(Stop.UNANSWERED))
                .hasMessageContaining("404");
        assertThatThrownBy(
                        () ->
                                Tracker.of(
                                        repository,
                                        (address, headers) -> {
                                            throw new IOException("connection refused");
                                        },
                                        at -> Optional.of(PORELO_HOST)))
                .as("no connection")
                .isInstanceOfSatisfying(
                        Stop.class,
                        stop -> assertThat(stop.code()).as("unanswered").isEqualTo(Stop.UNANSWERED))
                .hasMessageContaining("connection refused");
    }

    private Tracker.Origin git() {
        return git(Map.of());
    }

    // git as the test's own: no global or system config, and no checkout above the temporary
    // directory taken for the one under test.
    private Tracker.Origin git(Map<String, String> more) {
        final Map<String, String> environment = new HashMap<>(more);
        environment.put("GIT_CONFIG_GLOBAL", "/dev/null");
        environment.put("GIT_CONFIG_NOSYSTEM", "1");
        environment.put("GIT_CEILING_DIRECTORIES", directory.toString());
        return Tracker.Origin.git(environment);
    }

    private Web web(Optional<String> answer) {
        return (address, headers) -> {
            asked.add(address);
            return answer.map(text -> text.getBytes(StandardCharsets.UTF_8));
        };
    }

    private static void git(Path in, String... arguments) throws IOException, InterruptedException {
        final List<String> command = new ArrayList<>(List.of("git", "-C", in.toString()));
        command.addAll(List.of(arguments));
        final ProcessBuilder builder = new ProcessBuilder(command).redirectErrorStream(true);
        builder.environment().put("GIT_CONFIG_GLOBAL", "/dev/null");
        builder.environment().put("GIT_CONFIG_NOSYSTEM", "1");
        final Process process = builder.start();
        final String output = new String(process.getInputStream().readAllBytes(), UTF_8);
        assertThat(process.waitFor()).as("git " + command + ": " + output).isZero();
    }
}
