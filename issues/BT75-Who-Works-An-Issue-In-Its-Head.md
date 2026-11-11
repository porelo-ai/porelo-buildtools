# BT75 — Who Works An Issue In Its Head

```json
{
  "$schema": "issue-1.json",
  "id": "BT75",
  "title": "Who Works An Issue In Its Head",
  "state": "Review",
  "size": "XS",
  "priority": "High",
  "rank": 28,
  "blockedBy": [],
  "covers": "An issue head may say who works it, `\"worker\": \"Agent\"` or `\"Manual\"`: issue-1.json takes the field and refuses any other value, and IssueHead reads it and writes it last, after the optional fields it writes today."
}
```

**Why.** porelo-planner's PL46 lets a person say per issue whether an agent works it, and keeps that in the issue's
head. issue-1.json has `"additionalProperties": false`, so without this the planner's first head with `worker` turns
every repository's `issues check` red. Agreed in the room on 2026-11-11 (15:29Z): the name, the values and the place,
built at once by the operator's order.

**What must be true.**
- issue-1.json has an optional `worker`, one of `Agent` and `Manual`. Any other value is refused.
- `IssueHead` gets a last component `@Nullable String worker`. It is read from the head and null when absent, and
  written after `openQuestions` when set.
- The 13-argument constructor stays as an overload with `worker` null, so no caller of the published library breaks.
- The library gives no default. That an absent `worker` reads as Agent is the planner's own reading.

## Plan

Red first: a schema test that takes both values and refuses another, and a head test that reads `worker` and writes
it back in its place. Then the schema, the record, the reader and the writer. Run the stories and release modules'
tests in the JVM; the release module has no native build.

## Built

- issue-1.json and the hand-written `IssueSchema` both take `worker`, `Agent` or `Manual`. The schema test holds
  them to each other and refuses `agent` and `Person`.
- `IssueHead` has the last component `worker`. It is read from the head, null when absent, and written after
  `openQuestions`. The 13-argument constructor stays as an overload.
- `IssueHeadWorkerTest` and the schema test's new cases were seen red first.
- The stories and release modules' tests pass in the JVM. Only release builds on stories, and it has no native build.
