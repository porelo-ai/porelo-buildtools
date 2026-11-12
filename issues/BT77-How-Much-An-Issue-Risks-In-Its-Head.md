# BT77 — How Much An Issue Risks In Its Head

```json
{
  "$schema": "issue-1.json",
  "id": "BT77",
  "title": "How Much An Issue Risks In Its Head",
  "state": "Review",
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

## Analysis (2026-11-11, read in the code)

The same seams as `worker`, one place each:
- `stories/.../schemas/issue-1.json`: a `risk` property, `enum` `harm`, `ordinary` and `small`.
- `IssueSchema`: `risk` in `PROPERTIES` and `ENUMS`, and in the enum `case`.
- `IssueHead`: a 15th component `@Nullable String risk`. The 14-argument constructor (with `worker`) becomes an
  overload with `risk` null, beside the 13-argument one. The reader takes `text(data, "risk")`, and the writer adds
  `risk` after `worker`.
- The two callers of the constructor in main code (`IssueHead`'s reader and `IssuesIndex`'s conversion) need no
  change. The conversion keeps the 13-argument overload, since an index row carries no risk.
- Tests: a new head test class, named in `citations-exempt.txt` beside `IssueHeadWorkerTest` because it writes sample
  heads, and the schema test's cases. Red first.
- `doc/index.md`'s list of optional fields, and one `added` changelog entry.

**Size XS, 8 min at the measured median.** It has the shape of [BT75](README.md#bt75), whose implement stage took 2.2 minutes plus a
fix, here avoided by naming the test in the exemption list and rerunning the citation self-check after the commit.

**For the operator to decide.** Nothing. The values and where the field goes are the operator's decision, through the
Planner.
