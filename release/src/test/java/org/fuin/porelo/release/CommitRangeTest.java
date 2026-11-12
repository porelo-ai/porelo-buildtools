package org.fuin.porelo.release;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.function.Predicate;
import org.junit.jupiter.api.Test;

class CommitRangeTest {

    private static final String BEFORE = "a".repeat(40);

    private static final String SHA = "b".repeat(40);

    private static final String BASE = "c".repeat(40);

    private static final String HEAD = "d".repeat(40);

    private static final Predicate<String> HELD = hash -> true;

    private static final Predicate<String> NOT_HELD = hash -> false;

    private static GitHubEvent push(String ref, String refType, String before) {
        return new GitHubEvent(
                "push", ref, refType, SHA, "porelo-ai/x", "https://github.com", before, null);
    }

    private static GitHubEvent pull(
            String headRepository, String association, String author, String server) {
        return new GitHubEvent(
                "pull_request",
                "refs/pull/1/merge",
                "branch",
                SHA,
                "porelo-ai/x",
                server,
                null,
                new GitHubEvent.PullRequest(BASE, HEAD, headRepository, association, author));
    }

    // Prevents a push that names no start from being read as an empty range.
    @Test
    void refusesAPushWithoutItsStart() {
        assertThatThrownBy(() -> CommitRange.decide(push("refs/heads/main", "branch", ""), HELD))
                .hasMessage("the push names no commit it started from");
    }

    // Prevents a tag's push from reading every commit since the branch it was cut from.
    @Test
    void readsATagsOwnCommitAlone() {
        assertThat(CommitRange.decide(push("refs/tags/v1", "tag", BEFORE), NOT_HELD))
                .isEqualTo(new CommitRange(SHA + "~1.." + SHA, false));
    }

    // Prevents a push from reading more or less than it brought.
    @Test
    void readsAPushFromItsStart() {
        assertThat(CommitRange.decide(push("refs/heads/main", "branch", BEFORE), HELD))
                .isEqualTo(new CommitRange(BEFORE + ".." + SHA, false));
    }

    // Prevents main's history being rewritten past the check unseen.
    @Test
    void refusesMainMovedFromACommitTheCheckoutDoesNotHold() {
        assertThatThrownBy(
                        () ->
                                CommitRange.decide(
                                        push("refs/heads/main", "branch", BEFORE), NOT_HELD))
                .hasMessage("main moved from " + BEFORE + ", a commit this checkout does not hold");
    }

    // Prevents a forced push of a task's branch from having no range at all.
    @Test
    void readsABranchWhoseStartIsGoneFromMain() {
        assertThat(CommitRange.decide(push("refs/heads/task", "branch", BEFORE), NOT_HELD))
                .isEqualTo(new CommitRange("origin/main.." + SHA, false));
    }

    // Prevents a field from the event from reaching git as an option or a revision expression.
    @Test
    void refusesAStartThatIsNoFullHash() {
        assertThatThrownBy(
                        () ->
                                CommitRange.decide(
                                        push("refs/heads/task", "branch", "--output=/tmp/x"), HELD))
                .hasMessage("the push's start is no full commit hash: --output=/tmp/x");
    }

    // Prevents a pull request of the repository by its owner or a member from being held to the
    // sign-off.
    @Test
    void takesAnOwnersAndAMembersPullRequestFromInside() {
        assertThat(
                        CommitRange.decide(
                                        pull("porelo-ai/x", "OWNER", "jane", "https://github.com"),
                                        NOT_HELD)
                                .outside())
                .isFalse();
        assertThat(
                        CommitRange.decide(
                                        pull("porelo-ai/x", "MEMBER", "jane", "https://github.com"),
                                        NOT_HELD)
                                .outside())
                .isFalse();
    }

    // Prevents a contributor's pull request from passing without the sign-off.
    @Test
    void takesAContributorsPullRequestFromOutside() {
        assertThat(
                        CommitRange.decide(
                                pull("porelo-ai/x", "CONTRIBUTOR", "jane", "https://github.com"),
                                NOT_HELD))
                .isEqualTo(new CommitRange(BASE + ".." + HEAD, true));
    }

    // Prevents the repository's own workflow's pull requests from being held to the sign-off.
    @Test
    void takesTheActionsBotsPullRequestFromInside() {
        assertThat(
                        CommitRange.decide(
                                        pull(
                                                "porelo-ai/x",
                                                "CONTRIBUTOR",
                                                "github-actions[bot]",
                                                "https://github.com"),
                                        NOT_HELD)
                                .outside())
                .isFalse();
    }

    // Prevents a fork's pull request from counting as inside, whoever opens it.
    @Test
    void takesAForksPullRequestFromOutsideEvenByAnOwner() {
        assertThat(
                        CommitRange.decide(
                                        pull(
                                                "someone/x",
                                                "OWNER",
                                                "github-actions[bot]",
                                                "https://forge.example"),
                                        NOT_HELD)
                                .outside())
                .isTrue();
    }

    // Prevents the host's forge, which names no association, from holding its own pull requests to
    // the sign-off.
    @Test
    void takesAPullRequestOfTheRepositoryOnAnotherServerFromInside() {
        assertThat(
                        CommitRange.decide(
                                        pull("porelo-ai/x", "", "jane", "https://forge.example"),
                                        NOT_HELD)
                                .outside())
                .isFalse();
    }

    // Prevents a scheduled run from checking a range nobody named.
    @Test
    void refusesAnEventThatIsNeitherAPushNorAPullRequest() {
        assertThatThrownBy(
                        () ->
                                CommitRange.decide(
                                        new GitHubEvent(
                                                "schedule",
                                                "refs/heads/main",
                                                "branch",
                                                SHA,
                                                "porelo-ai/x",
                                                "https://github.com",
                                                null,
                                                null),
                                        HELD))
                .hasMessage(
                        "a schedule event has no commits of its own; check-commits --event takes a"
                                + " push or a pull request");
    }
}
