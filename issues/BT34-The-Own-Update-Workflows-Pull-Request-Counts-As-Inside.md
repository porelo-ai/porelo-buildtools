# BT34 — The Own Update Workflow's Pull Request Counts As Inside

**Status:** Implementing.
**Size:** XS - one condition in the template's pull-request step and its tests; built with
[BT17](README.md#bt17)'s template change.

**What must be true.** A pull request that a repository's own workflow opens as `github-actions[bot]`, from a branch
of the same repository, is checked as one from inside: its commits need no `Signed-off-by:`, and the shared rule on
commit messages still holds for them. A pull request from another repository, or by anyone not a member, still needs
its author's sign-off on every commit.

## Decided

- **Inside, not a sign-off in each update workflow:** such a branch can only be pushed by a holder of write access to
  the repository, so its pull request is not from outside. Found on porelo-claude-code #8 (`update/claude-code-…`),
  whose step *The pull request's commit messages* refused `030e350` for a missing `Signed-off-by:`, since GitHub
  reports `author_association` `CONTRIBUTOR` for `github-actions[bot]`.

## Built

- `shared-rules.yml`'s step *The pull request's commit messages* takes the pull request's author
  (`github.event.pull_request.user.login`) through its environment. A pull request from the same repository is from
  inside when its author is `OWNER` or `MEMBER`, or when `github-actions[bot]` opened it.
- `WorkflowCommitsTest` runs the step's own script:
  - the own workflow's pull request (`CONTRIBUTOR`, `github-actions[bot]`, same repository) is checked without
    `--outside`;
  - the bot's pull request from another repository, a contributor's from this one, and a member's from another all
    keep `--outside`.

  Seen red without the condition, and with the condition outside the same-repository check.

## Acceptance

- In every repository that takes the template, a pull request its own workflow opens passes the commit-message step
  without a sign-off, and one from another repository is still refused without it.
