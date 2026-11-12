# BT71 — The Work Format Handed To The Planner

```json
{
  "$schema": "issue-1.json",
  "id": "BT71",
  "title": "The Work Format Handed To The Planner",
  "state": "Backlog",
  "size": "M",
  "priority": "High",
  "rank": 25,
  "blockedBy": [],
  "covers": "Once porelo-planner publishes the work format and its checks, porelo-buildtools drops them: the porelo-stories module, the `stories`, `issues`, `stage`, `check-commits` and `check-pushes` commands with Stage, StageTokens, IssueLinks and Who, and `stats/`; every repository's workflows run the planner's native executable, downloaded from Artifactory pinned by version and sha256, instead."
}
```

**Why.** Part of ST320 (porelo-project). The planner reads stories and issues and is to own their format. Two copies of the rules would
drift.

**Interim only.** What is handed over is the format as it stands: the agent writes its own stage line with
`stage begin|end` and reads its tokens from its session file. That is the interim source of the numbers; the lasting
one is porelod's per-task record (ST24 in porelo-project, porelo's token counting per task). Nothing new is built into
`Stage` here, the planner's move line included: the planner builds that on its side.

**Who calls what today.**
- **`check-commits`.** The build and shared-rules workflows of porelo, porelo-adapters, porelo-ecosystems,
  porelo-forge-clients, porelo-frontend, porelo-intellij, porelo-mcp and porelo-planner call it, mostly as
  `exec:exec@release -Drelease.args="check-commits …"`. porelo-project calls it from `.github/scripts/commits-check.sh`.
  The Coordinator's local tools call it too.
- **`check-pushes`.** Only porelo-buildtools' own `shared-rules.yml` calls it.
- **`stories check`.** porelo-project's `stories-check.sh` calls it.
- **`issues index` and `issues check`.** Agents run them locally before a push. No workflow calls them.
- **`stage`.** Agents run it locally. No workflow calls it.
- **porelo-stories as a library.** porelo-release uses it, and so does porelo-planner's `planner/store/git`.

**Not part of this issue.** `check-citations` stays with the CI checks until its rules are dropped under ST321
(porelo-project), which [BT74](README.md#bt74) writes down first; this issue does not change it, and the porelo-stories
module goes only once nothing here reads it.

**What must be true.**
1. The planner's native executable is published to Artifactory's generic repository `files`, at
   `files/porelo-planner/<version>/porelo-planner-<version>-linux-x86_64`, releases only; nothing of it goes to Maven
   Central.
2. Each workflow above downloads it pinned by version and sha256 and runs it, and each repository's run is green on it.
3. porelo-buildtools drops the commands, the classes and `stats/`: `stories` (`check`, `index` and `cut`), `issues`
   (`index`, `check` and `convert`), `stage`, `check-commits` and `check-pushes`. `stories cut` moves to the planner,
   `issues convert` is dropped, and `stage` goes with `stats/`, since the velocity is kept in the planner's store.
4. The Coordinator's `tools.classpath` and the agents' local commands name the planner's executable. Machines states
   the change in the room.

It is blocked by the planner's issue for taking the work format over, which is filed with ST320 (porelo-project).

## Plan

**Size M, 53 min at the measured median of M.** Mostly deletion: the commands, their classes and tests, `stats/`, the
docs' sections, and the own workflows moved to the download.

1. **The commands removed.** `stories`, `issues`, `stage`, `check-commits` and `check-pushes` go from `Main`, with
   Stage, StageTokens, Who, CheckCommits, CheckPushes, Stories, Issues and ProjectFile, and their tests. A test shows
   each removed command answers as unknown, red first.
2. **`stats/` removed**, with the code that writes and checks it.
3. **The porelo-stories module removed** once `check-citations` no longer reads it; until then it stays, and only the
   commands go.
4. **Own workflows.** `build.yml`'s `check-commits` step and `shared-rules.yml`'s `check-commits` and `check-pushes`
   steps download the planner's executable by the rule of pinned downloads (version and sha256) and run it.
5. **Docs and changelog.** The sections of `doc/index.md` on these commands go, with one `removed` entry.
6. **The classpath.** The change to the Coordinator's `tools.classpath` is stated in the room; the Coordinator changes
   their own tools.

Other repositories' workflows are their owners' changes; this issue removes the code once they all run the planner's
executable, so it waits for them.

## Acceptance

- Each removed command answers as unknown; the full build passes; `check-shared`, `check-citations` and
  `check-actions` stay green.
- This repository's own workflows run the planner's executable at its pinned version and sha256 and are green on it.
