# BT71 — The Work Format Handed To The Planner

```json
{
  "$schema": "issue-1.json",
  "id": "BT71",
  "title": "The Work Format Handed To The Planner",
  "state": "Idea",
  "size": "-",
  "priority": "High",
  "rank": 25,
  "blockedBy": [],
  "covers": "Once porelo-planner publishes the work format and its checks, porelo-buildtools drops them: the porelo-stories module, the `stories`, `issues`, `stage`, `check-commits` and `check-pushes` commands with Stage, StageTokens, IssueLinks and Who, and `stats/`; every repository's workflows call the planner's tool instead."
}
```

**Why.** Part of ST320 (porelo-project). The planner reads stories and issues and is to own their format. Two copies of the rules would
drift.

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
- The Coordinator's `tools.classpath` holds porelo-release and porelo-stories.

**What stays here.** `check-citations` stays with the CI checks: 16 repositories' workflows and the Coordinator's
prepush call it, beside `check-shared` and `check-actions`. Where it reads an issue id or link, it reads it through the
planner's published library.

**What must be true.**
1. The planner's issue for the work format is done, and its tool is published under a fixed coordinate.
2. Each workflow above calls the planner's tool at a pinned version, and each repository's run is green on it.
3. porelo-buildtools drops the module, the commands, the classes and `stats/`. The release tool and its classpath no
   longer carry porelo-stories.
4. The Coordinator's `tools.classpath` and the agents' local commands name the planner's tool. Machines states the
   classpath change in the room.

It is blocked by the planner's issue for taking the work format over, which is filed with ST320 (porelo-project).
