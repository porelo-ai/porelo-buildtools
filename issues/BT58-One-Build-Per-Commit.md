# BT58 — One Build Per Commit

**Status:** Implementing.
**Size:** S - one rule in `check-actions` with its tests, and this repository's Build taking it; every other repository
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
