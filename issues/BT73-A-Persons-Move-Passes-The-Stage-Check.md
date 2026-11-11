# BT73 — A Person's Move Passes The Stage Check

```json
{
  "$schema": "issue-1.json",
  "id": "BT73",
  "title": "A Person's Move Passes The Stage Check",
  "state": "Review",
  "size": "XS",
  "priority": "High",
  "rank": 27,
  "blockedBy": [],
  "covers": "`Stage.validate` takes a person's move on the board, `actor: person` with `stage: move`, `size` null or a size, `wall_min` and `vm_min` null, and the median leaves every move out; `stage begin` still refuses a move."
}
```

**Why.** It is part of ST320 (porelo-project). The planner writes a stats line when a person moves an issue on the
board. Today's `check-commits` reads every `stats/*.jsonl` through `Stage.records`, which takes only `agent` and
`subagent` lines. The planner's first move line would turn a repository's Build red until the planner's own commit
checks replace `check-commits`.

**What must be true.**
- A closed line with `actor: person` and `stage: move` passes. Its `size` may be null, and its `wall_min` and `vm_min`
  must be null.
- `person` passes with no other stage, and `move` with no other actor.
- The median and p80 add no minutes and no sample for a move.
- `stage begin <issue> move` is refused: a move is never a stage an agent opens.

The cross-checks against the issue's head come with the planner's commit checks, not here. Nothing else in `Stage`
changes; the rest of the format is handed over as it is ([BT71](README.md#bt71)).

**Review (2026-11-11).** On main, so Review by the operator's order of 15:44Z. Its stage lines were recorded afterwards: start from the session's first work on it, end at its commit, tokens not read (`no-session`).
