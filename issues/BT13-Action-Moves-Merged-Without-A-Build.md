# BT13 — Action Moves Merged Without A Build

**Status:** Implementing. Built in porelo-buildtools; the other repositories take the template, and a real
Dependabot pull request is its proof.
**Size:** S - one light workflow every repository takes (the push filter is already in everywhere), first in this
repository, then taken over by each agent, and the operator's required-check setting listed.

**What must be true.** A Dependabot pull request that moves an action gets a light check that rents nothing and
publishes nothing, and the operator merges it once that check passes, with no Build. A push that changes only `.github/`
starts no Build.

## Why

The operator decided on 2026-10-18, in the Shared Area `1b87b5ddd8799c95`: "Dependabot moves only the actions, weekly,
in one group, after three days; the operator merges each, as code owner of `.github/`, once the pull request's light
check (no rented machine) passes", and "A push that changes only `.github/` starts no Build; the next push with content
builds with it."

Each repository's Build rents machines on a push to `main`. Merging an action's pull request was such a push, so a
one-line pin move cost a Build with its legs.

**No auto-merge, decided 2026-10-18.** The organisation's ruleset on `main` (24845358, every repository) asks for the
code owner's review, and `CODEOWNERS` names the operator for `.github/`, which is all an action move touches. Letting
Dependabot's pull requests through without it (Dependabot as a bypass actor, or a bot's review the ruleset accepts)
would widen what may change `main` without the operator, which
[PJ28](https://github.com/porelo-ai/porelo-project/blob/main/issues/README.md#pj28) closed. So the moves stay the
operator's hand merge, and the ruleset stays as it is. The comments in each `dependabot.yml` and `doc/decisions.md`
saying "nothing merges it automatically" stay true.

## The shape

- **The push filter**, done: the Build's `push` trigger ignores `.github/**` (`paths-ignore`, or `'!.github/**'` where a
  Build filters with `paths`, since GitHub refuses both together), and `pull_request` keeps its paths. It went out
  with wave 1 on 2026-10-18 in every repository; the template takes over its wording.
- **A light pull-request check**, a workflow of its own that rents nothing and publishes nothing: `check-actions` and
  the workflow files' validity (each loads as YAML, names its triggers and jobs), with read permissions only. A
  repository whose Build already runs on `pull_request` with no renting job may name that job instead, as long as it
  stays light.
- **What the operator sets per repository**, written into
  [PJ28](https://github.com/porelo-ai/porelo-project/blob/main/issues/README.md#pj28)'s list: the light check required
  on `main`, and nothing else widened.

## Built here, 2026-10-18

- **`.github/workflows/workflows.yml`, "Workflow check":** on a pull request whose paths are `.github/**` only. It
  checks out without credentials, takes the pinned GraalVM, runs `check-actions` (here `-pl release
  exec:java@check-actions`, a new execution of `release/pom.xml`), and loads every workflow with PyYAML, which the
  runner image carries, refusing one that does not load or names no trigger or no job. `contents: read` only; no
  secret, no rented machine, nothing published; ten minutes at most.
- **The Build** ignores `.github/**` on a pull request too, so such a pull request runs the Workflow check alone.
- **Seen to refuse:** the YAML step against a file cut off mid-list and one with no `jobs:`, both named, exit 1; the
  step's text was run here as the workflow runs it. `check-actions` refuses an unpinned step, as `CheckActionsTest`
  holds.
- **For every other repository, the template:** the same file, with its own `check-actions` call in place of the
  `release` one (`./mvnw -B -q -s settings.xml -N exec:exec@check-actions` where the root pom has that execution, as
  `porelo`'s does), and `.github/**` added to its Build's `pull_request` `paths-ignore`.
- **The operator's setting:** "Workflow check" required on `main` in each repository's ruleset, beside what it asks
  today; nothing else widened.

## Acceptance

- [ ] A push to `main` that changes only `.github/` starts no Build in any repository; a push with content still does.
- [ ] A Dependabot pull request moving an action shows the light check green, with no Build and no rented machine, and
  is merged by the operator with no Build: seen once on a real pull request.
- [ ] The light check fails on a pull request whose workflow `check-actions` refuses, seen once.
- [ ] The operator's setting per repository is in
  [PJ28](https://github.com/porelo-ai/porelo-project/blob/main/issues/README.md#pj28)'s list.
- [ ] Every repository has taken the template; the agents name their commits.
