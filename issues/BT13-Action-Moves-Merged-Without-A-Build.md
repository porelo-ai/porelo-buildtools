# BT13 — Action Moves Merged Without A Build

**Status:** Backlog.
**Size:** M - three workflow pieces every repository takes (the push filter, a light pull-request check, an auto-merge
job), `check-actions` taught the one job that may write, a page each repository copies, and the operator's settings
listed; first in this repository, then taken over by each agent.

**What must be true.** A Dependabot pull request that moves an action by a patch or minor version is merged once a light
check passes, with no rented machine and no Build. A major move waits for the operator. A push that changes only
`.github/` starts no Build.

## Why

The operator decided on 2026-10-18, in the Shared Area `fef7d153a0bcb86d`: "Dependabot moves only the actions, weekly,
in one group, after three days; a patch or minor move is merged on its own once the pull request's light check (no
rented machine) passes, a major one by the operator", and "A push that changes only `.github/` starts no Build; the
next push with content builds with it."

Each repository's Build rents machines on a push to `main`. Merging an action's pull request was such a push, so a
one-line pin move cost a Build with its legs.

## The shape

- **The push filter.** The Build's `push` trigger ignores `.github/**` (`paths-ignore`, or `'!.github/**'` where a
  Build filters with `paths`, since GitHub refuses both together). `pull_request` keeps its paths. This part is
  already in as local commits in every repository, ahead of this issue; the template takes over their wording.
- **A light pull-request check**, a workflow of its own that rents nothing and publishes nothing: `check-actions` and
  the workflow files' validity (each loads as YAML, names its triggers and jobs). The operator makes it a required
  check. A repository whose Build already runs on `pull_request` with no renting job may name that job instead, as
  long as it stays light.
- **Auto-merge for Dependabot.** A workflow on `pull_request_target`, or on `pull_request` from `dependabot[bot]`, reads
  the update with `dependabot/fetch-metadata`, pinned by commit like every action. For a `github-actions` update whose
  `update-type` is `version-update:semver-patch` or `version-update:semver-minor`, it turns on GitHub's auto-merge
  (`gh pr merge --auto`). A major move gets a comment that it waits for the operator. The write permission it needs
  (`contents: write`, `pull-requests: write`) stays in that one job. `check-actions` names that job as the only one
  allowed to write in such a workflow, and refuses the same permission anywhere else.
- **What the operator sets per repository**, written into
  [PJ28](https://github.com/porelo-ai/porelo-project/blob/main/issues/README.md#pj28)'s list: "Allow auto-merge" on, the
  light check required on `main`, and nothing else widened.
- The comments in each `dependabot.yml` and any `doc/decisions.md` that say "nothing merges it automatically" change
  with the workflow, not before it.

## Acceptance

- [ ] A push to `main` that changes only `.github/` starts no Build in any repository; a push with content still does.
- [ ] A Dependabot pull request moving an action by a minor version is merged by itself once the light check passes,
  with no Build and no rented machine: seen once on a real pull request.
- [ ] A major move is left open with its comment.
- [ ] `check-actions` passes the auto-merge job's write permission and fails the same permission in any other job, each
  seen in a test.
- [ ] The operator's settings per repository are in
  [PJ28](https://github.com/porelo-ai/porelo-project/blob/main/issues/README.md#pj28)'s list.
- [ ] Every repository has taken the template; the agents name their commits.

## To be checked

- Whether `pull_request_target` or `pull_request` with the `dependabot[bot]` actor is the safer trigger. Dependabot's
  pull requests get a read-only token and no secrets on `pull_request`, so turning on auto-merge may need
  `pull_request_target`, which must then never check out the pull request's code.
- Whether GitHub's auto-merge waits for every required check when the merging job itself is a check of the same pull
  request.
- Whether a grouped Dependabot pull request that holds a patch move and a major move counts as major, and how
  `fetch-metadata` reports it.
