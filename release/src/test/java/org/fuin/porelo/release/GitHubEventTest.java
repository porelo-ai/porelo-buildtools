package org.fuin.porelo.release;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class GitHubEventTest {

    private static Map<String, String> runner(String name) {
        final Map<String, String> environment = new HashMap<>();
        environment.put("GITHUB_EVENT_NAME", name);
        environment.put("GITHUB_REF", "refs/heads/main");
        environment.put("GITHUB_REF_TYPE", "branch");
        environment.put("GITHUB_SHA", "b".repeat(40));
        environment.put("GITHUB_REPOSITORY", "porelo-ai/x");
        environment.put("GITHUB_SERVER_URL", "https://github.com");
        return environment;
    }

    // Prevents a push's start from being lost between the event file and the range.
    @Test
    void readsAPushsStartFromTheEventFile() throws IOException {
        final GitHubEvent event =
                GitHubEvent.read(runner("push"), "{\"before\": \"" + "a".repeat(40) + "\"}");

        assertThat(event.before()).isEqualTo("a".repeat(40));
        assertThat(event.ref()).isEqualTo("refs/heads/main");
        assertThat(event.pullRequest()).isNull();
    }

    // Prevents a pull request's fields from being taken from anywhere but the event file.
    @Test
    void readsAPullRequestsFields() throws IOException {
        final GitHubEvent event =
                GitHubEvent.read(
                        runner("pull_request"),
                        """
                        {"pull_request": {
                          "base": {"sha": "%s"},
                          "head": {"sha": "%s", "repo": {"full_name": "someone/x"}},
                          "author_association": "CONTRIBUTOR",
                          "user": {"login": "jane"}}}
                        """
                                .formatted("c".repeat(40), "d".repeat(40)));

        assertThat(event.pullRequest())
                .isEqualTo(
                        new GitHubEvent.PullRequest(
                                "c".repeat(40),
                                "d".repeat(40),
                                "someone/x",
                                "CONTRIBUTOR",
                                "jane"));
    }

    // Prevents Forgejo's pull request, which names no association, from being refused.
    @Test
    void takesAPullRequestWithoutAnAssociationAsEmpty() throws IOException {
        final GitHubEvent event =
                GitHubEvent.read(
                        runner("pull_request"),
                        """
                        {"pull_request": {"base": {"sha": "c"}, "head": {"sha": "d",
                          "repo": {"full_name": "porelo-ai/x"}}, "user": {"login": "jane"}}}
                        """);

        assertThat(event.pullRequest()).isNotNull();
        assertThat(event.pullRequest().association()).isEmpty();
    }

    // Prevents a pull request whose head repository is gone from being read as this repository's.
    @Test
    void refusesAPullRequestWithoutItsHeadRepositoryNamingTheField() {
        assertThatThrownBy(
                        () ->
                                GitHubEvent.read(
                                        runner("pull_request"),
                                        """
                                        {"pull_request": {"base": {"sha": "c"},
                                          "head": {"sha": "d", "repo": null},
                                          "user": {"login": "jane"}}}
                                        """))
                .isInstanceOf(IOException.class)
                .hasMessage("the event file names no pull_request.head.repo.full_name");
    }

    // Prevents a step outside a workflow from checking a range made of empty fields.
    @Test
    void refusesAMissingVariableNamingIt() {
        final Map<String, String> environment = runner("push");
        environment.remove("GITHUB_SHA");

        assertThatThrownBy(() -> GitHubEvent.read(environment, "{}"))
                .isInstanceOf(IOException.class)
                .hasMessage("GITHUB_SHA is not set; run this in a workflow's step");
    }

    // Prevents an event file that is no JSON object from being read as an empty event.
    @Test
    void refusesAnEventFileThatIsNoObject() {
        assertThatThrownBy(() -> GitHubEvent.read(runner("push"), "[]"))
                .isInstanceOf(IOException.class)
                .hasMessage("the event file holds no JSON object");
    }
}
