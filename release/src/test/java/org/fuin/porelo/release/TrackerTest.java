package org.fuin.porelo.release;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TrackerTest {

    private static final String TRACKED =
            "project:\n  name: \"porelo\"\ntracker:\n  kind: github\n  project: porelo-ai/7\n";

    private static final String MARKDOWN = "project:\n  name: \"porelo\"\n";

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
            assertThatThrownBy(() -> Tracker.of(below, web(Optional.of(MARKDOWN))))
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

        assertThat(Tracker.of(fixture, web(Optional.of(TRACKED))).github())
                .as("the fixture's own file, not the one above it")
                .isFalse();
        assertThat(Tracker.of(directory, web(Optional.of(MARKDOWN))).github())
                .as("the directory's own file")
                .isTrue();
        assertThat(asked).as("nothing read over the network").isEmpty();
    }

    // Prevents a repository without a project file of its own from being judged without one.
    @Test
    void readsTheProjectsFileOnGitHubWhereNoneStandsAbove() throws IOException, Stop {
        final Path repository = Files.createDirectories(directory.resolve("repository/issues"));

        assertThat(Tracker.of(repository, web(Optional.of(TRACKED))).github())
                .as("the tracker porelo-project's file names")
                .isTrue();
        assertThat(asked).as("porelo-project's file on main").containsExactly(Tracker.PROJECT);
    }

    // Prevents an unreachable project file from being taken for either answer.
    @Test
    void cannotAnswerWhenTheProjectsFileCannotBeRead() {
        final Path repository = directory.resolve("repository");
        assertThatThrownBy(() -> Tracker.of(repository, web(Optional.empty())))
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
                                        }))
                .as("no connection")
                .isInstanceOfSatisfying(
                        Stop.class,
                        stop -> assertThat(stop.code()).as("unanswered").isEqualTo(Stop.UNANSWERED))
                .hasMessageContaining("connection refused");
    }

    private Web web(Optional<String> answer) {
        return (address, headers) -> {
            asked.add(address);
            return answer.map(text -> text.getBytes(StandardCharsets.UTF_8));
        };
    }
}
