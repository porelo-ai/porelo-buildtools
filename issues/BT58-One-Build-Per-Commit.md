# BT58 — One Build Per Commit

```json
{
  "$schema": "issue-1.json",
  "id": "BT58",
  "title": "One Build Per Commit",
  "state": "Review",
  "size": "S",
  "priority": "Medium",
  "rank": 5,
  "blockedBy": [],
  "covers": "A commit is built once: on a task branch by its pull request, on `main` by its push; the shared template says so and the shared check refuses a workflow that builds both."
}
```

**Why this size.** one rule in `check-actions` with its tests, and this repository's Build taking it; every other repository
takes it with its next `porelo-release`.

**What must be true.** A commit is built once: on a task's branch by its pull request alone, on `main` by its push
alone, so the two Builds of one commit never hold a host's runner slots side by side and a push's verdict is final after
one Build. The rule is part of the shared workflow template every repository takes, and the shared checks refuse a
build workflow that would build a branch's commit on its push and its pull request both.

## Acceptance

- A task branch with a pull request open gets one Build of its commit; `main` gets one Build of each push.
- A build workflow that runs on `push` for every branch and on `pull_request` as well is refused by the shared check,
  naming the file and the two triggers; one that keeps to the rule passes.
- The shared-rules workflow, which runs on a schedule and on pull requests, keeps running there.

## Measured

On the foo-org host's forge (Forgejo, porelo-ai-test's `foo-project/foo-1`), 2026-11-09, one signed task branch with
a pull request open and a workflow of one job:
- with this repository's triggers after the change (`push` on `main` and tags, `pull_request` on every branch): the
  branch's push built nothing, the pull request built its commit once;
- with the triggers before (`push` and `pull_request` on every branch): the next commit was built twice, by `push`
  and by `pull_request_sync`, one second apart, side by side on the host's runner.
Not measured: a push to `main`, since merging would have changed the test project. On GitHub the test identity's
token may neither open a pull request nor read Actions runs, so nothing was measured there.
