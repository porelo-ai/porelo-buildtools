# BT46 — The Plan Pages Read Stories

**Status:** Idea.

**What must be true.** `porelo-release plan render` and `plan check` read a release as a set of stories in place of
sprints: each story with its name, what a user gains, the person's test, its issues across the repositories and the
date the person tested it, its state derived as its least advanced issue's, so the person reads on the plan page which
stories stand where, and `plan check` refuses a story naming an issue that does not exist and a release naming a
story that does not exist.

## Why

The project replaces sprints by stories: one function of value to a user, with a test the person runs at its end and
every issue it needs. A sprint's functions already are that, but they sit inside sprints, which are periods, and have
no number or state of their own. The plan pages and `plan check` are built here, so they change with it.

## The shape

- **The story's data is read from the project's `stories/` index**, one row per story (`ST<n>`, name, gain, test,
  issues as `repository#ID`, tested date), as the issue indexes are read today; a story's own file is prose.
  `plan/<release>/plan.yml` names its stories (`stories: [ST1, …]`) in place of `sprints:`.
- **A story's state** is the least advanced state of its issues, in the indexes' order of states; a tested date moves
  it past Finished to tested. `plan check` refuses a story with no issue and no tested date, an issue no index holds,
  a story no index holds, a Finished story without a tested date, and a wait (`plan/after.yml`) on a story that comes
  later or not at all.
- **Order without sprints:** the stories follow the plan's own list, and a story never stands before one it waits on;
  the "Sprint progress" picture becomes one bar per story's state.
- **A plan whose functions are all tested keeps its sprints as its record**, so the reader takes either form and
  renders the old one as today; a plan with both is refused.
- Touches `release`: `Plan`, `PlanCheck`, `PlanRender`, `PlanTemplate`, the templates `plan.jte`, `progress.jte`,
  `sprint.jte` (to `story.jte`), `CheckCitations`' recognition of a plan file, `PlanCommandsTest` and the fixtures
  under `src/test/resources/plans`, `doc/index.md` and a changelog entry.

## Open questions

- Is the `stories/README.md` table the one place a story's data is read from, its file being prose (one form, as the
  issues have)? Recommended: yes; the other choice, a YAML block in each story's file, keeps the data in two forms.
- Do the old plans that are all tested stay with sprints, read by a second path, or are they converted once to
  stories? Recommended: they stay, as the operator decided for porelo-project; the second path is small and is
  dropped when they are retired.

## To be checked

- The story index's exact columns, with the coordinator, before the reader is written.
