# BT37 — The Buildtools' Agent Works In A Porelo Task

```json
{
  "$schema": "issue-1.json",
  "id": "BT37",
  "title": "The Buildtools' Agent Works In A Porelo Task",
  "state": "Backlog",
  "size": "S",
  "priority": "Medium",
  "rank": 3,
  "blockedBy": [],
  "covers": "porelo-buildtools' agent, `Agent Buildtools`, in a Porelo task on the move's host: first `machines.yml` and the scheduled `check-pushes` held to GitHub, then the trial move with Forgejo's build publishing `main`'s snapshots to its registry."
}
```

**Why this size.** two scheduled workflows held to the central forge, with their workflow test, before the move; then the
trial move's steps, this issue worked to Review inside the task, and the way back.

**What must be true.** porelo-buildtools' own agent, `Agent Buildtools` in the project's room, works in a Porelo task on
the move's host as the project's procedure for an agent's move lays out.

## Plan

Read in today's `porelo.yml`, `settings.xml` and the six workflows, against the host's forge settings (`PORELO_PUBLISH`
`false`, `PORELO_DEPLOY_PROFILE` `forge-release`, `MAVEN_SNAPSHOTS_URL` and `PORELO_PACKAGES_READ_TOKEN` per project).

- **What already holds on a forge, read, not run:**
  - *Build and unit tests* runs on every push and pull request. It stops at once on a forge without
    `MAVEN_SNAPSHOTS_URL` or its read token, and its deploy dry run reaches no outside service.
  - *Snapshots* (the cloud images) is skipped where `PORELO_PUBLISH` is `false`.
  - *Publish* deploys `main`'s snapshots by `PORELO_DEPLOY_PROFILE`, and a tag's release needs `central-sonatype-release`
    and the release keys, which the host's forge has not.
  - `delete-runs.yml` and `prune-snapshots.yml` run only by hand.
- **What does not hold, to be changed here before the move:**
  - `machines.yml` runs daily on `schedule` on every forge. On the host's forge it would ask upstream and open its pull
    request with `gh`, which speaks to GitHub alone, so it fails every day. Its job runs only where
    `github.server_url` is `https://github.com`.
  - `shared-rules.yml`'s scheduled step *Main's pushes of the last day* (`check-pushes`) reads the repository's activity
    from GitHub's API with the run's token. On the host's forge that token is Forgejo's, and the read fails or reads
    nothing. That step, too, runs only on `https://github.com`; the forge's pushes reach GitHub through the gathered
    push, where it reads them.
  - `porelo.yml`'s comment says nothing reads it; Porelo builds a task's image from it today. The comment is corrected.
- **Red first:** a test beside `WorkflowCommitsTest` reads every workflow with a `schedule` trigger and fails where a
  job or step that speaks to GitHub (`gh`, `check-pushes`) is not held to `https://github.com`. It is red on today's two
  workflows.
- **The move,** in the procedure's order, once the guard is on `main`:
  - The repository is named in the host's `forge_repositories`, and the coordinator's switch in `project.yml`.
  - `porelo task start --repository porelo-buildtools --agent claude`, attended.
  - Inside the task: the skills fetched and checked, this issue set to Review, the forge's Build and *Shared rules
    check* read through the `forgejo` reader, both green, and *Publish* seen to deploy `main`'s snapshot into the
    forge's registry after the merge.
  - The move's check, red before, green after; then the way back.
- **Not in the task:** machine credentials, release keys, and the snapshots and Publish legs that rent machines.
  Neither Codex CLI nor Antigravity CLI is in the task, so the reading before the hand-over is done on the host, by a
  reader started apart from the task.

## Acceptance

- The workflow test, seen red on today's workflows and green after.
- On the host's forge: the Build and *Shared rules check* of the task's pull request green; no scheduled run of
  `machines.yml` or `check-pushes` red; `main`'s snapshot in the forge's registry after the merge.
- The move's check for this repository red before the start and green after, and this issue in Review in the commit
  the operator merges.

## To be checked

- **The repository alone moves.** `porelo-host`, kept by the same agent today, moves last of the builders, by an issue
  of that repository's own.
- That the forge's runner offers the label `ubuntu-26.04` the workflows name; it does so for porelo-message-sluice's
  workflows.
- Whether Forgejo runs a `schedule` trigger on a repository migrated from GitHub at all; the guard holds either way.

## Built here (2026-11-13)

- **The guard before the move.** `machines.yml`'s *Ask upstream* job and `shared-rules.yml`'s *Main's pushes of the
  last day* step now run only where `github.server_url` is `https://github.com`. The first opens its pull request with
  `gh`; the second reads the activity from GitHub's API. `ScheduledWorkflowsTest` reads every workflow with a
  `schedule` and fails on a step that runs `gh` or `check-pushes` without that hold. It was seen red on both before the
  change and is green after. `WorkflowCommitsTest` names the new condition.
- **`porelo.yml`'s comment** now says that Porelo builds a task's image from the file.
- **Not built: the move.** The trial move to the host, the issue worked inside the task and the way back need the
  operator and the host. So the issue stays in Backlog until then.
