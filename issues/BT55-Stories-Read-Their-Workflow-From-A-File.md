# BT55 — Stories Read Their Workflow From A File

**Status:** Backlog on the operator's word of 2026-11-09.
**Size:** L - grown from M by the operator's decisions of 2026-11-09. The states written in five classes of
`porelo-release` give way to one file: stories and every repository's issue index read it. A story's state is
computed from its issues. A second schema version.

**What must be true.** The states of work, their order on the board and their phases come from one file,
`stories/workflow.yml` in porelo-project, not from `porelo-release`'s code. Stories and every repository's issue index
check their states against it. A state added there (for instance *In analysis*, *Under estimation*, *Reworking*,
*Finishing*) is valid everywhere and gets its section in the file's order, with no new release. A state the file lacks
is refused, naming the file. A story's state is computed from its issues' states, not kept by hand beside them. A
project without the file is refused.

## Decided by the operator (2026-11-09)

1. **One file for everything.** `stories/workflow.yml` holds the states for stories and for every repository's issue
   index alike.
2. **Computed, not checked.** A story's state is computed from its issues. This replaces the rule that a story in
   Review needs its issues in Review, Finished or Rejected.
3. **No default.** A project without `stories/workflow.yml` is refused; there is no built-in fallback.
4. **The story-state rule.** A story stands in the least advanced state among its open issues, by the file's order.
   It is Finished when all its issues are closed and not all are Rejected, and Rejected when all are Rejected. A story
   without issues keeps its own early state, set by hand.
5. **Other repositories carry a copy.** Each repository keeps a byte-identical copy of `stories/workflow.yml`, checked
   against porelo-project's as the shared blocks are; nothing is fetched.
6. **Measured states.** The file marks the states that demand a stage record with `measured: true`.

## Plan

Read in porelo-buildtools 7016154: `Stories.java`, `IssueLinks.java` (`STATES`, the index's state headings),
`CheckCommits.java` (the stage records' states), `PlanCheck.java`, `PlanTemplate.java`, `Stage.java` and
`schemas/story-1.json`.

- **The file**, as settled with the coordinator:

      states:            # in board order
        - {name: Idea, closed: false}
        - {name: Implementing, closed: false, measured: true}
        - {name: Finished, closed: true, measured: true}
        - ...
      phases:
        - {name: <phase>, states: [<state>, ...]}

  Each name is one line and unique; `measured` is optional and false when left out; every phase's states are among
  `states`. Anything else is refused, naming the line.
- **One reader, `Workflow`**, in [BT54](README.md#bt54)'s module when that lands first, otherwise in `porelo-release`
  and moved with it. Every state check and every ordered list of states reads it:
  - the stories index's sections (`Stories.SHOWN`);
  - an issue index's state headings (`IssueLinks.STATES`);
  - the plan's groups (`PlanCheck`, `PlanTemplate`);
  - the stage records' states (`Stage`, and `CheckCommits`' set of states that need a stage record).

  Where code needs a state's meaning, it reads it from the file, not from the name: `closed` for the story rule and
  the plan, `measured` for the states whose move needs a stage record (decision 6).
- **The copy in each repository** is `issues/workflow.yml`, beside the index it governs. `check-shared` gains a
  comparison of that copy with porelo-project's `stories/workflow.yml`, given as a path or read from a sibling
  checkout, as it compares the Shared Area; a repository's own checks read only its copy (decision 5).
- **The story's state, computed** by decision 4. `story-2.json` keeps `state` in the head only for a story without
  issues, and refuses it beside issues. A `story-1` head's `state` beside issues is read only to report a difference
  once, then dropped by the index command.
- **Tests that go red first:**
  - `WorkflowTest.addedStateGetsItsSection`: a fixture file with *Reworking* between *Rework* and *Finished* gives
    that section in that place, in a story index and in an issue index. Red against the built-in lists.
  - `refusesAStateTheFileLacks`, in both indexes;
  - `refusesAPhaseNamingAnUnknownState`;
  - `missingFileIsRefused`;
  - `storyStateIsComputedFromItsIssues`, over each case of decision 4;
  - `aMeasuredStateNeedsAStageRecord`: a commit moving an issue into a state marked `measured` without a stage record
    is refused, and one into an unmarked state is not;
  - `aCopyThatDiffersIsRefused`: `check-shared` refuses an `issues/workflow.yml` that is not byte-identical.
- **Docs:** `doc/index.md` names the file, its format and the rule; changelog under *Changed*.

## Acceptance

- The red-first tests above, seen red, then green.
- porelo-project's stories and every repository's issue index, checked with a `workflow.yml` holding today's ten
  states, give unchanged indexes and the same faults as today.
- With the four new states added to the file, a story whose issues stand *In analysis* shows in that section, and an
  issue index with a `## Reworking` heading passes.
