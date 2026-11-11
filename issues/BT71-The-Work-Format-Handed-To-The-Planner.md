# BT71 — The Work Format Handed To The Planner

```json
{
  "$schema": "issue-1.json",
  "id": "BT71",
  "title": "The Work Format Handed To The Planner",
  "state": "Backlog",
  "size": "L",
  "priority": "High",
  "rank": 25,
  "blockedBy": [],
  "covers": "Once porelo-planner publishes the work format and its checks, porelo-buildtools drops them: the porelo-stories module, the `stories`, `issues`, `stage`, `check-commits` and `check-pushes` commands with Stage, StageTokens, IssueLinks and Who, and `stats/`; every repository's workflows call the planner's tool instead."
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

## Analysis (2026-11-11, the operator's order of 15:44Z)

**Aligned with the planner's PL59 and PL60.** PL59 took `issues index`, `issues check` and `stories index` when it was filed. PL60
takes `check-commits`, `check-pushes` and the stage records' check. Three of today's commands appear in neither:
- `stories check` (porelo-project's `stories-check.sh` calls it);
- `stories cut` (release notes) and `issues convert` (a one-time conversion);
- `stage begin|end|median|p80`, which agents run locally to write their own lines.

Until those three have a home, porelo-buildtools cannot drop the module or the commands. It is a question for the
operator below; my proposal is that PL59 takes `stories check` and `stories cut`, PL60 takes `stage`, and
`issues convert` is dropped.

**What porelo-buildtools does, once the planner's tool is published and every caller is on it:**
1. **check-citations onto the planner's library.** It is the one command that stays. Today it reads issue ids, rows,
   links and story files through porelo-stories (`IssueHead`, `IssueIndexes`, `Workflow`) and `IssueLinks`.
   It changes to read them through the planner's published library at a pinned version. Its tests stay and must
   pass unchanged. This is the real work of the issue.
2. **The commands removed.** `stories`, `issues`, `stage`, `check-commits` and `check-pushes` go from `Main`, with
   Stage, StageTokens, IssueLinks (what check-citations no longer needs of it), Who, CheckCommits, CheckPushes,
   Stories, Issues and ProjectFile, and their tests. That is about 3,000 lines of main code and their tests.
   A test shows each removed command answers as unknown, red first.
3. **The porelo-stories module removed.** About 3,600 lines of main code and 3,100 of tests. It goes from the parent
   pom, and the release tool and its classpath no longer carry it.
4. **Own workflows.** `build.yml`'s `check-commits` step and `shared-rules.yml`'s `check-commits` and `check-pushes`
   steps call the planner's tool at a pinned version.
5. **Docs and changelog.** The sections of `doc/index.md` on these commands go, with one `removed` entry.
6. **The classpath.** I state the change to the Coordinator's `tools.classpath` in the room. The Coordinator changes
   their own tools.

Other repositories' workflows are their owners' changes (PL59 and PL60 say each repository changes only the artifact
its build runs). This issue only removes the code once they are all moved, so it waits for them, not the reverse.

**Size L, 80 min at the measured median (XS 8, S 27, M 53, L 80; the medians are still the seeds).**
- Steps 2, 3 and 5 are mostly deletion, about 25 min with their red-first test and a full JVM verify.
- Step 1 depends on how close the planner's library is to porelo-stories' public readers; about 35 min if it keeps
  `IssueIndexes.read` and `Workflow.read` as they are.
- Steps 4 and 6, and the run on main, take about 20 min.

It could be split into "check-citations reads the planner's library" (M) and "the work format removed" (S). I propose
one issue unless the operator wants two.

**Order.** It is blocked by PL59 and PL60 being published, and by every caller listed above being on the planner's
tool. The first step that can start early is step 1, as soon as the planner publishes a snapshot of its library.

**Aligned with the planner (15:55Z).**
- PL59 publishes `porelo-planner-work` to Maven Central and takes `stories check`. Its public readers stay public for
  check-citations: `Workflow.read`, `IssueHead`, `IssueIndexes.read`, `IssueLinks`, `Project.read` and `Story.read`.
  Step 1 reads through those. That makes it likely the smaller end of its 35 min.
- PL60, which PL59 blocks, takes `check-commits`, `check-pushes` and the stage commands.
- The planner's texts that name `porelo-release` stay until every repository runs the planner's tool: three refusal
  texts and the stories index heading "Written by `porelo-release stories index`". check-citations' story
  recognition reads that heading, so step 1 keeps accepting it.
- Until PL60 is done, `Stage.validate` keeps taking a person's move ([BT73](README.md#bt73)). Step 2 removes Stage only after PL60.

**For the operator to decide.**
1. **`stories cut`, `stage` and `issues convert`.** `stories check` is settled: PL59 takes it. The planner put the
   other three to the operator as its questions 1–3, with the same proposals as mine: `stories cut` and `stage` move,
   and `convert` is dropped. One answer covers both issues.
2. **`stats/` is data, not code.** The covers line says porelo-buildtools drops `stats/`. That directory is this
   repository's own measured history, the same kind of file every repository keeps and the planner reads. I propose
   it stays, and only the code that writes and checks it moves. The covers line would then lose "and `stats/`".
3. **check-citations depends on the planner.** check-citations runs in 16 repositories' CI. Reading through the
   planner's library makes porelo-release depend on a porelo-planner artifact. The alternative is a small own reader
   of index rows and links in check-citations: a second copy that can drift, which ST320 wants to avoid. I propose
   the dependency, pinned to a release.
