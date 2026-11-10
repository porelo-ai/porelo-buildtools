# BT60 — A Story Takes The State It Is Given

```json
{
  "$schema": "issue-1.json",
  "id": "BT60",
  "title": "A Story Takes The State It Is Given",
  "state": "Review",
  "size": "S",
  "priority": "Medium",
  "rank": 4,
  "blockedBy": [],
  "covers": "A story whose head states a state has it, whatever its issues; `stories index` and `stories check` warn where the issues give another, and a story without one keeps the automatic rule."
}
```

**Why this size.** the story-2 schema and its check allow `state` beside issues, `stories index` and `stories check` keep
it and warn of a mismatch; the docs say the rule.

**What must be true.** A story whose head states a state has that state, whatever its issues' states are, as
[BT46](README.md#bt46)'s decision of 2026-11-08 says: a story's state is written, not derived. Where the issues give
another state by the automatic rule, `stories index` and `stories check` say so as a warning, never a refusal. A story
without a stated state keeps today's automatic rule. Part of ST309 "A story takes the state the operator gives it".

## Why

Today `StorySchema` refuses `state` beside issues ("state is not allowed beside issues"), and `StoriesIndex` takes the
earliest open issue state in board order. The operator cannot put ST25 into Backlog while its issues are Accepted.

## Plan

- **The schema.** `story-2.json` allows `state` beside issues: still required when `issues` is empty, optional beside
  them. `StorySchema` drops the refusal.
- **The state.** For a story-2 head, a stated state is the story's state. Without one, the automatic rule stays:
  the earliest open issue state in `workflow.yml` order, else the first completed state, else Rejected. A legacy
  story-1 head keeps today's migration (its state beside issues is removed, with the NOTE naming both states).
- **The warning.** When a stated state beside issues differs from what the issues give, both commands print one line
  on standard output and still succeed:
  `WARN  ST25: stated Backlog, its issues give Accepted (porelo-omp#OM18 Accepted, ...)`.
- **The tested day.** A stated completed story needs its `tested` day, with issues or without; a tested day stays
  refused on a story that is not completed.
- **Docs:** `doc/index.md`'s story rules and the schema's descriptions say the stated state and the warning.
- **Red first:** a head with a state beside issues is accepted and keeps that state in the story's text and the index;
  a mismatch prints the warning and exits 0; a head without a state keeps the computed state; a stated Finished story
  beside issues without a tested day is refused.

## Acceptance

- ST25 with `"state": "Backlog"` beside its issues: `stories index` writes it as Backlog, prints the warning naming
  its issues' state, and `stories check` passes with the same warning.
- A story without a stated state is written exactly as today.

## Checked

- On a copy of porelo-project's main with every repository's main beside it, ST25 given `"state": "Backlog"`:
  `stories index` wrote it as Backlog with `WARN  ST25: stated Backlog, its issues give Accepted (...)`, naming each
  issue with its state, and `stories check` passed with the same warning. The copy's other faults (five stories' texts
  and the index behind their issues' states) stand on main before this change as well; `stories index` clears them.
- The old test that refused a state beside issues in a new head now asserts the state kept and the warning.
