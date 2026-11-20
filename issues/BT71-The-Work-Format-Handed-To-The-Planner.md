# BT71 — The Work Format Handed To The Planner

```json
{
  "$schema": "issue-1.json",
  "id": "BT71",
  "title": "The Work Format Handed To The Planner",
  "state": "Backlog",
  "size": "S",
  "priority": "Medium",
  "rank": 25,
  "blockedBy": [],
  "covers": "Once porelo-planner publishes the commit checks, porelo-buildtools drops the `check-commits` and `check-pushes` commands with CheckCommits, CheckPushes and Who; every repository's workflows run the planner's native executable, downloaded from Artifactory pinned by version and sha256, instead."
}
```

**Why this size.** Only the two commit checks outlive the move to GitHub Issues; the rest of the work format goes at
the cut-over with [BT92](README.md#bt92). Deletion of three classes and their tests, and two workflows moved to a
pinned download: S.

**What is handed over.** The checks that hold commits to the shared rules, which stay after the cut-over:
`check-commits` (a subject of at most 72 characters, only trailers below it, no issue number, nobody named as having
asked for or made the change) and `check-pushes` (the same rule over each push to `main`), both as they stand after
the cut-over. The `issues`, `stories` and `stage` commands, `stats/`, `check-commits`' stage rule and the
porelo-stories library are no part of this issue: they go at the cut-over, and nothing of them is handed to the
planner.

**Who calls what today.**
- **`check-commits`.** The build and shared-rules workflows of porelo, porelo-adapters, porelo-ecosystems,
  porelo-forge-clients, porelo-frontend, porelo-intellij, porelo-mcp and porelo-planner call it, mostly as
  `exec:exec@release -Drelease.args="check-commits …"`. porelo-project calls it from `.github/scripts/commits-check.sh`.
  The Coordinator's local tools call it too.
- **`check-pushes`.** Only porelo-buildtools' own `shared-rules.yml` calls it.

**What must be true.**
1. The planner's native executable is published to Artifactory's generic repository `files`, at
   `files/porelo-planner/<version>/porelo-planner-<version>-linux-x86_64`, on every push to the planner's `main`
   under an immutable dev version; nothing of it goes to Maven Central. No planner release and no buildtools release
   is needed for it.
2. Each workflow above downloads such a dev version pinned by version and sha256 and runs it, and each repository's run
   is green on it.
3. porelo-buildtools drops `check-commits` and `check-pushes` with CheckCommits, CheckPushes and Who.
4. The Coordinator's `tools.classpath` and the agents' local commands name the planner's executable. Machines states
   the change in the room.

It is blocked by the planner's issue for taking the commit checks over and by the planner's publishing of the
executable on every `main` push. The plan below is made again once that lands.

## Plan

1. **The commands removed.** `check-commits` and `check-pushes` go from `Main`, with CheckCommits, CheckPushes and
   Who, and their tests. A test shows each removed command answers as unknown, red first.
2. **Own workflows.** `build.yml`'s `check-commits` step and `shared-rules.yml`'s `check-commits` and `check-pushes`
   steps download a dev version of the planner's executable by the rule of pinned downloads (version and sha256)
   and run it.
3. **Docs and changelog.** The sections of `doc/index.md` on these commands go, with one `removed` entry.
4. **The classpath.** The change to the Coordinator's `tools.classpath` is stated in the room; the Coordinator changes
   their own tools.

Other repositories' workflows are their owners' changes; this issue removes the code once they all run the planner's
executable, so it waits for them.

## Acceptance

- Each removed command answers as unknown; the full build passes; `check-shared`, `check-citations` and
  `check-actions` stay green.
- This repository's own workflows run the planner's executable at its pinned dev version and sha256 and are green on
  it.
