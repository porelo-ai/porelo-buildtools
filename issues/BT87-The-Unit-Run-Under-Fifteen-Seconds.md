# BT87 — The Unit Run Under Fifteen Seconds

```json
{
  "$schema": "issue-1.json",
  "id": "BT87",
  "title": "The Unit Run Under Fifteen Seconds",
  "state": "Backlog",
  "size": "S",
  "priority": "Medium",
  "rank": 5,
  "blockedBy": [],
  "covers": "`./mvnw test` takes under 15 s of test time, the last acceptance point the test pyramid's fixes did not reach: the slowest unit classes, which load a whole tree or a class graph per test, share it or shrink it."
}
```

**Why this size.** Four classes hold about a third of the time; each needs its fixture made once per class or made
smaller, then a measure on a quiet host.

**What must be true.** The unit run's test time, summed from Surefire's reports of a clean run, is under 15 s on a host
not loaded by other builds. No test leaves the unit run to get there.

**Why.** [BT57](README.md#bt57) split the unit tests from the integration tests and took the sleeps, servers and
processes out of the unit run, but its run summed 26 s on 2026-11-12 at a load of about 40 on 16 cores. The slowest
were `IssueSourceTest` (3.6 s, an ArchUnit import of the whole package in one test), `MergeTreeBillTest` (2.9 s, a large
tree), `WorkflowTest` (2.2 s), `StoriesIndexTest` and `IssuesIndexTest` (1.5 s each, real index fixtures). How much of
the 26 s was the load is part of the measure.
