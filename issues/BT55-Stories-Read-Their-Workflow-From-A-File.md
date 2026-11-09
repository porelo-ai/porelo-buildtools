# BT55 — Stories Read Their Workflow From A File

**Status:** Review.
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
5. **Each repository keeps its own signed copy** (operator, 2026-11-09 16:45 Berlin, replacing a checked comparison).
   The project admin places the file once in each repository and signs it, as with `project.yml`. A repository's checks
   read only its own copy: `stories/workflow.yml` in porelo-project, `issues/workflow.yml` elsewhere. Nothing compares
   the copies and nothing is fetched. No agent writes or changes a copy; a change goes to the operator to sign.
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
- **The copy in each repository** is `issues/workflow.yml`, beside the index it governs, placed and signed by the
  operator before this issue lands (decision 5). The tools read it; they never compare it with another repository's.
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
  - `ownIndexRequiresItsWorkflowAndKnownStates`: a repository's own checks refuse a missing `issues/workflow.yml`,
    naming it, and an unknown state section in its index.
- **Docs:** `doc/index.md` names the file, its format and the rule; changelog under *Changed*.

## Acceptance

- The red-first tests above, seen red, then green.
- porelo-project's stories and every repository's issue index, checked with the operator's 15-state file in each
  repository, keep every issue row and story row; a story's state changes only where decision 4 computes another, each
  named once by a NOTE line.
- Every repository's signed copy is on its main before this issue is pushed (done 2026-11-09, porelo-buildtools
  30fc1f6).
- With the four new states added to the file, a story whose issues stand *In analysis* shows in that section, and an
  issue index with a `## Reworking` heading passes.

## As built

- **`Workflow`** (public, `org.fuin.porelo.stories`):
  - It reads the file strictly and names the file and line of any fault.
  - Its API: `read(Path)`, `states()`, `closed`, `measured`, `rejected`, `phases()` and `order`.
  - Rejected is named in one place only, `rejected`: the one closed state that is not done work. The story rule, the
    tested-date rule and the stage medians use it.
- **No built-in state lists are left.** These all read the file:
  - the stories index's sections;
  - every issue index;
  - the plan's groups and legend;
  - the stage states;
  - `check-commits`' set needing a stage record (the states marked `measured`).
- **One index parser.** `IssueLinks` takes every row, link and anchor from `IssueIndexes.read`.
  - A heading followed by an issue table is a state section and must be a state of the file.
  - A heading named like a state is a state section without a table too.
  - Other headings are prose.
  - An unknown state section, with rows or without, is a citation fault naming the file and line.
- **The story's state is computed** by decision 4. `story-2.json` refuses `state` beside issues. The index command
  migrates a `story-1` head by exactly two lines: `state` out, a leading `$schema` in. It writes a NOTE where the stated
  and the computed state differ, and a second run writes nothing.
- **The copies follow decision 5.** Each repository's checks read only their own signed copy, and a missing or
  invalid copy is refused, naming it. `check-shared` has no workflow comparison. The operator placed the copies on
  every main first (porelo-buildtools 30fc1f6), and this commit does not touch them.
- **Checked:**
  - The whole Build with `-Pnative`.
  - On a copy of porelo-project with the signed file: 289 heads migrated by two lines each, and every row kept.
    - Computed states differ for 15 stories: ST2, ST5, ST12, ST26, ST42, ST105, ST118, ST145, ST193, ST194, ST196,
      ST201, ST290, ST292 and ST294.
    - Prose headings in porelo, porelo-pi and porelo-claude-code pass.
  - The document checks pass.
- **Read** once by agy (16:04 Berlin; 4 blocking). Its fixes were read once more (16:32; 3 blocking), against the rule
  of one reading. All seven findings are fixed, each with a test seen red first.
