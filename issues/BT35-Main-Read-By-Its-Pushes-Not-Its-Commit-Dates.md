# BT35 — Main Read By Its Pushes, Not Its Commit Dates

**Status:** Analyzed.
**Size:** M - one command in `porelo-release` with its tests, the scheduled step it replaces in `shared-rules.yml` and
its guard test, then every repository with a workflow takes the template again, as with
[BT17](README.md#bt17).

**What must be true.** Every commit that reaches `main` is held to the shared rule on commit messages once, however long
before its push it was committed. Today the scheduled step *Main's commits of the last day* picks its range by committer
date (`git rev-list --before='26 hours ago'`), so a commit made more than 26 hours before a push the Build skips
(`[skip ci]`, documents only, `.github/` only) meets neither that step nor the push step, whose next range starts above
it.

## Plan

- **The day's pushes, not the day's commit dates.** GitHub's repository activity
  (`GET /repos/{owner}/{repo}/activity?ref=refs/heads/main&time_period=week`) names each push to `main` with its time,
  `before` and `after`. Measured on porelo-host with a token holding only *Actions* and *Metadata* read: `200`, five
  pushes with their two commits each, `34c08b9..919cf03` at 05:01Z the newest. A push of several commits shows as one
  entry (`20670fd..4337e1f` holds `bd8c8fe`, `4b9de92` and `4337e1f`).
- **`porelo-release check-pushes --since <hours>`**, a new class `CheckPushes` beside `CheckCommits`:
  - reads `GITHUB_API_URL`, `GITHUB_REPOSITORY` and `GITHUB_TOKEN` from the environment, never from an argument;
  - takes every page through `Web` (the `Link` header's `next`);
  - for each push younger than `--since`, runs `CheckCommits` over `before..after` against the checkout. That is the
    same rule as on the push and the pull request, its exemptions included;
  - prints each push it read with its range. Any fault fails the step, and so do an unreachable API, a page it cannot
    read, or a `before`/`after` the checkout does not hold. An empty day passes and says so.
- **The scheduled step** in `.github/workflows/shared-rules.yml` calls `check-pushes --since 26` in place of the
  `rev-list` range, with `GITHUB_TOKEN` in its `env:` and `contents: read` as today. The checkout keeps
  `fetch-depth: 0`.
- **The template** goes into `~/.porelo/handover/porelo-buildtools/<commit>/template.md`. Each agent takes it into its
  repositories, as with [BT17](README.md#bt17), and each keeps its own way to put `porelo-release` on the classpath.
- **Docs:** `doc/index.md` names `check-pushes` beside `check-commits`; changelog under *Added*.
- **The test that goes red first:** `CheckPushesTest.refusesAnOldCommitInADaysPush`. It uses a temporary repository
  (`GIT_CONFIG_GLOBAL=/dev/null`) whose commit with a requirement number is dated three days back, and a `FakeWeb`
  activity page naming its push an hour ago. Red against today's date-based range, which reads nothing there.

## Acceptance

- `CheckPushesTest`, each test seen red first:
  - an old commit in a push of the day is refused;
  - a clean day passes;
  - a push older than `--since` is not read;
  - a second page is read;
  - an API error, a missing token, and a `before` the checkout lacks each fail and name what they asked;
  - the token appears in no output.
- `WorkflowCommitsTest` holds the scheduled step's new call and its `env:` binding, and is seen red against the old
  step.
- Run here with the real API, read only, over porelo-buildtools' last day: each push is named with its range, and the
  result is the same as `check-commits` over those ranges by hand.

## To be checked

- Whether the forge's copy runs the scheduled step at all, and if so what Forgejo offers in place of GitHub's
  activity (`/repos/{owner}/{repo}/activities/feeds`). Until that is read, `check-pushes` refuses an API it does not
  know rather than passing.
- That the workflow's own `GITHUB_TOKEN` with `contents: read` reads the activity, as the measured token with
  *Metadata* read did.
- How far back the activity reaches, and whether a missed scheduled run (GitHub drops some under load) needs
  `--since` wider than 26 hours. Today a missed day is not read either.
