# BT96 — Velocity Leaves Git With The Issues

```json
{
  "$schema": "issue-1.json",
  "id": "BT96",
  "title": "Velocity Leaves Git With The Issues",
  "state": "Analyzed",
  "size": "S",
  "priority": "High",
  "rank": 2,
  "blockedBy": [],
  "covers": "In a project whose `project.yml` names GitHub Issues, `stage median` and `stage p80` refuse instead of answering no number, `check-commits` refuses a commit that adds a stage record, and the documentation says the measurements live in the tracker's items."
}
```

**Why this size.** One module, `release/`, and its documentation: two refusals and a section, no contract another
repository reads.

**What must be true.** Once a project keeps its issues in GitHub Issues, nothing about an issue is kept in Git, its
measurements included. Those measurements are size, start, ready, minutes and the stages the median needs. They are
fields of the issue's item, set through the tracker's interface like its status. The median per size is computed from
the project's finished items, and the starting value per size stands once in `project.yml`. These tools then read and
write no `stats/` and ask for none, and they never answer as though they still could.

## Plan

What stands in the code already: with `tracker: {kind: github}` in the nearest `project.yml` (`Tracker`),
`check-commits` reads no stage record, a commit that deletes `stats/` passes, and `stage` passes through
`Main.unlessTracked`, printing that nothing is written. `issues check` and `stories check` behave the same way.
Three gaps remain:

- **`stage median` and `stage p80` refuse in a tracked project**, exit 2, naming the tracker's file and saying the
  median comes from the tracker's finished items. Today they pass through `unlessTracked` with exit 0 and an `OK`
  line where a number is expected, so a script that reads the number reads a sentence. `stage begin` and `end` keep
  passing: a workflow step left over from the Markdown issues stays green, and the line says nothing was written.
  In `Main`, the `stage` case tells the two reading verbs from the writing ones before `unlessTracked`.
- **`check-commits` refuses, in a tracked project, a commit that adds or changes `stats/seed.csv` or a
  `stats/<ISSUE>.jsonl`**, naming the file: a measurement written there would be a second copy beside the item, read
  by nobody. Deleting one keeps passing. This sits in `CheckCommits`' tracked branch, from `git.changed(hash)`, as
  `stageFaults` already reads a commit's changes.
- **`doc/index.md`, "Where the issues are kept"**: a tracked project keeps no `stats/`. Its measurements are fields of
  each item, and its starting value per size stands in `project.yml`, read by the planner. `stats/seed.csv`,
  `stage begin`/`end` and the stage rule of `check-commits` hold for the Markdown issues alone. One changelog entry
  under `[Unreleased]`.

Red first, as unit tests over a fixture `project.yml` with the tracker: `stage median M` exits 2 and prints no number
(`MainTest`); a commit adding `stats/HO1.jsonl` is refused and one deleting it passes (`CheckCommitsTest`). Each is
seen red against today's code.

A repository's own `stats/` and the Markdown issues go in one commit by its owner at the cut-over. Here that commit is
this repository's own, as for `issues/`, and it is not part of this issue.

## Acceptance

- The two unit tests above, seen red first, and the full build green.
- In a scratch clone with `tracker: {kind: github}`, `stage median M` exits 2 and names the tracker's file. A commit
  adding `stats/BT1.jsonl` is refused by `check-commits`, and one deleting `stats/` passes.

## To be checked

- The name of the starting-value key in `project.yml`, which porelo-project's schema owns. The documentation names it
  as the schema does once it stands.
