# BT96 — Velocity Leaves Git With The Issues

```json
{
  "$schema": "issue-1.json",
  "id": "BT96",
  "title": "Velocity Leaves Git With The Issues",
  "state": "Analyzed",
  "size": "M",
  "priority": "High",
  "rank": 2,
  "blockedBy": [],
  "covers": "`porelo-release stage` and the stage records go: no `stats/`, no `seed.csv`, no stage rule in `check-commits`, since every project keeps its issues and their measurements in a tracker. Built after the carry-over has read the Markdown store."
}
```

**Why this size.** It removes one command other repositories call (`stage`), its two classes and the stage rule of
`check-commits`. That makes it a contract change with documentation, though the work is mostly removal.

**What must be true.** Porelo keeps issues only in a tracker, and a task's measurements are fields of its item:
size, start, ready, minutes and the stages the median needs. The planner computes each size's median from the
finished items, and the starting value per size stands once in `project.yml`. So these tools record, read and ask
for no measurement in Git, and a project that drops `stats/` stays green.

## Plan

It is built only after the carry-over has read the Markdown store once, as a step of its own.

- **`stage` goes:** `Stage.java` and `StageTokens.java`, the `stage` case and its usage line in `Main`, and the
  command's name in `Main`'s list of commands. Their tests go with them: `StageTest`, `StageFixtures` and the
  `stages` test resources.
- **`check-commits` reads no stage record:** `stageFaults`, the `NOTE no stats/seed.csv` line, the delete-guard on
  `stats/` and the measured-state rule that needs `stats/<ISSUE>.jsonl` all go from `CheckCommits`. So does the
  `Stage.status` and workflow reading they use. A commit that deletes `stats/` passes as any other deletion does. The
  matching cases go from `CheckCommitsTest`.
- **`check-citations`** loses its exemption for `stats/*.jsonl`, since no such file is kept.
- **Documentation:** the sections on stage records, `stats/seed.csv` and `stage median` go from `doc/index.md`, and
  one line says where the measurements live. They are the tracker's item fields, and the starting value per
  repository and size is `velocity.seed` in `project.yml`. Both are read by the planner, whose `porelo-planner
  velocity median|p80 <size> --project <dir>` answers what `stage median` and `stage p80` did. One changelog entry under `[Unreleased]`, *Removed*.
- **This repository's own `stats/`** goes in the same commit.

Red first: a `CheckCommitsTest` case where a commit adding an issue in a measured state, with no `stats/` beside it,
passes. Today it is refused. A `MainTest` case where `stage` is answered as an unknown command, exit 2. Each is seen
red against today's code.

## Acceptance

- The two tests above, seen red first, and the full build green.
- A scratch clone of a repository that still has `stats/` and an issue in a measured state: deleting `stats/` passes
  `check-commits`, and `porelo-release stage median M` answers as an unknown command.
