# BT77 — How Much An Issue Risks In Its Head

```json
{
  "$schema": "issue-1.json",
  "id": "BT77",
  "title": "How Much An Issue Risks In Its Head",
  "state": "Accepted",
  "size": "XS",
  "priority": "High",
  "rank": 30,
  "blockedBy": [],
  "covers": "An issue head may say how much the issue risks, `\"risk\": \"harm\"`, `\"ordinary\"` or `\"small\"`: issue-1.json and the issues check take it and refuse any other value, and IssueHead reads it and writes it last, after `worker`."
}
```

**Why.** porelo-planner's PL66 ("Model And Effort Chosen By Phase And Risk") lets the work rules choose a task's model
and effort by the risk of its issue, and keeps that risk in the issue's head. issue-1.json has
`"additionalProperties": false`, so the planner writes `risk` only once this schema takes it. Asked by Agent Planner
on 2026-11-11 (17:00Z), from the operator's decision.

**What must be true.**
- issue-1.json and the hand-written `IssueSchema` have an optional `risk`, one of `harm`, `ordinary` and `small`.
  Any other value is refused.
- `IssueHead` gets a last component `@Nullable String risk`. It is read from the head, null when absent, and written
  after `worker` when set.
- The 13- and 14-argument constructors stay as overloads with `risk` null, so no caller of the published library
  breaks.
- The library gives no default. That an absent `risk` reads as `ordinary` is the planner's own reading.

## Plan

Built as [BT75](README.md#bt75) built `worker`. Red first: the schema test's cases (`harm`, `ordinary` and `small`
taken; `Harm` and `high` refused) and a head test that reads `risk` and writes it after `worker`. Then the schema,
`IssueSchema`'s enum and field list, the record, the reader, the writer and doc/index.md, with a changelog entry. Run
the stories and release modules' tests in the JVM, and CheckCitationsTest again after the commit. The snapshot
follows from the Build on main.
