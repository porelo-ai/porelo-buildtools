# BT36 — A Forge Pull Request From The Same Repository Counts As Inside

**Status:** Backlog, set by the operator on 2026-10-27.
**Size:** S - one condition and one environment binding in the template's pull-request step, its cases in
`WorkflowCommitsTest`, and every repository with the template takes it, as with [BT34](README.md#bt34).

**What must be true.** On the host's forge, a pull request from a branch of the same repository is checked as one from
inside: its commits need no `Signed-off-by:`, and the shared rule on commit messages still holds for them. A pull
request from another repository still needs its author's sign-off on every commit. On GitHub nothing changes: inside
is `OWNER` or `MEMBER`, or the repository's own `github-actions[bot]`.

## What was measured

Sprint 5's rented host, 2026-10-27, porelo-buildtools pull requests 1 and 2 on the forge, both opened by
`agent-machines` from a branch of `porelo/porelo-buildtools`:

- *Shared rules check* failed on both: `check-commits … --outside` asked each commit for a `Signed-off-by:` naming its
  author, which the shared rule leaves out of our own commits.
- `GET /api/v1/repos/porelo/porelo-buildtools/pulls/1` on the forge has no `author_association` field at all (Core,
  07:15Z). The step's `PR_ASSOCIATION` is therefore empty, and only the bot case could make a forge pull request
  inside. So every pull request on the forge is checked as one from outside, whoever opens it.
- On the forge, `github.server_url` is the runner's address of the forge, `https://10.73.0.1:3000`. The checkout of the
  same job fetched `https://10.73.0.1:3000/porelo/porelo-buildtools` and removed
  `http.https://10.73.0.1:3000/.extraheader`.
- The merge is not blocked by the red check, since the forge's `main` requires no status check, but every forge pull
  request shows it red.

## Plan

- **Telling the forge apart:** by `github.server_url`, which is `https://github.com` on GitHub and the forge's own
  address on the host. It is measured, as above, and is part of the context on both. `FORGEJO_ACTIONS` and
  `GITEA_ACTIONS` would be the runner's own variables, but neither is measured in a job yet.
- **The step**, *The pull request's commit messages* in `.github/workflows/shared-rules.yml`:
  - gains `PR_SERVER: ${{ github.server_url }}` in its `env:`;
  - inside the existing same-repository branch (`"$PR_REPOSITORY" = "$GITHUB_REPOSITORY"`), a pull request is inside
    when `PR_SERVER` is not `https://github.com`.

  Only a holder of write access can push a branch to the repository there, and the forge's `main` still takes a
  merge only with the operator's approval (porelo-host's
  [HO31](https://github.com/porelo-ai/porelo-host/blob/main/issues/README.md#ho31)). A comment in the step says so.
- **`WorkflowCommitsTest`** runs the step's own script, as for [BT34](README.md#bt34). It also checks the binding
  `PR_SERVER` = `${{ github.server_url }}`, and these cases:
  - a forge pull request from the same repository, with an empty association, is checked without `--outside`;
  - a forge pull request from another repository keeps `--outside`;
  - a GitHub pull request from the same repository by a `CONTRIBUTOR` keeps `--outside`, so the rule does not leak
    to GitHub.

  Each is seen red against today's step.
- **The template** goes to `~/.porelo/handover/porelo-buildtools/<commit>/template.md`. Each agent takes it into its
  repositories and their own copy of the test, as with [BT17](README.md#bt17). Those are porelo, porelo-adapters,
  porelo-agy, porelo-ai.github.io, porelo-buildtools, porelo-claude-code, porelo-codex, porelo-frontend, porelo-host,
  porelo-intellij, porelo-message-matrix, porelo-message-sluice, porelo-omp and porelo-pi. porelo-message-sluice
  holds it in `filter/app/src/test/java/org/fuin/porelo/msgsluice/filter/WorkflowCommitsTest.java`, porelo-frontend in
  `test/workflow_commits_test.dart`.
- **Docs:** `doc/index.md`, under *What `check-commits` reads*, says how a forge pull request counts; changelog under
  *Changed*.

## Acceptance

- `WorkflowCommitsTest`'s new cases, each seen red first.
- On a rented host with the forge, a pull request from a branch of the same repository passes *Shared rules check*
  without a sign-off.

## To be checked

- That a forge pull request from a fork reports the fork as `head.repo.full_name`, so it stays outside. Today the
  organization is private and has no forks.
