# BT93 — Check Commits Tests Run Without Git

```json
{
  "$schema": "issue-1.json",
  "id": "BT93",
  "title": "Check Commits Tests Run Without Git",
  "state": "Analyzed",
  "size": "XS",
  "priority": "Medium",
  "rank": 1,
  "blockedBy": ["BT92"],
  "covers": "`CheckCommitsTest` holds `check-commits`' rules over an in-memory `CheckCommits.Git`; one integration test still runs real `git`, so the log format is proven against git itself."
}
```

**Why this size.** One test class, no contract, after the stage tests have gone at the cut-over: XS. It waits for
[BT92](README.md#bt92). Of the 24 tests in `CheckCommitsTest` that run real `git` today, 21 test the stage rule and
are deleted there. Building this first would move about 90 `git` calls onto a fake only to delete them.

**What must be true.** `check-commits` stays in this repository, so its tests follow the pyramid. A rule test builds
its commits in memory, not in a repository on disk. Only the test that `CheckCommits.Git.in` reads what git itself
writes runs git.

## Plan

- `release/src/test/java/org/fuin/porelo/release/CheckCommitsTest.java`: a `FakeGit` implementing
  `CheckCommits.Git` (`log` in `Git.FORMAT`, `count`, `parent`) from a list of commits written in the test.
- `checksThePushTheEventNames` and `givesNoAnswerWhenTheLogSplitsIntoMoreCommitsThanGitCounts` move onto it. Their
  `git init`, `commit` and `rev-parse` calls go.
- `readsWhatGitItselfWrites` stays tagged `integration` and runs real git. It is the one test that proves `Git.FORMAT`
  against git's own output.
- Red first: the two moved tests, run with no `git` on `PATH`, fail today.

## Acceptance

- `CheckCommitsTest` runs `git` in exactly one test, counted by a search for `git(` that is shown to find today's
  calls first.
- The unit tests of `CheckCommitsTest` pass with no `git` on `PATH`.
- The full build passes.
