# BT40 — Work May Wait On Work Begun

```json
{
  "$schema": "issue-1.json",
  "id": "BT40",
  "title": "Work May Wait On Work Begun",
  "state": "Review",
  "size": "S",
  "priority": "Medium",
  "rank": 19,
  "blockedBy": [],
  "covers": "`plan check` lets work wait for an issue that is Implementing, Rework, Review or Finished, and refuses only a wait for one not yet begun or Rejected."
}
```

**Why this size.** one rule of `plan check`, its test and its documentation.

**What must be true.** An issue that is Implementing, Rework, Review or Finished may wait for one that is Implementing,
Rework, Review or Finished. Only a wait for one not yet begun, Idea, Accepted, Analyzed, Estimated or Backlog, is
refused, as is a wait for one Rejected.

## Plan

- **The contract** (operator's decision, relayed by the Coordinator in the room, 2026-10-29): agents build against
  local results and contracts stated in words, so work starts once what it waits for has begun, not once it is
  published. The refusal still names both issues and both states.
- **`PlanCheck.checkStarted`:** the waited issue's state must be one of Implementing, Rework, Review or Finished, in
  `after.yml` and in a *Blocked by* cell alike. Every other rule on waits stays: a later sprint, a cycle, a covered
  requirement, an issue planned in no release that is not Review, Finished or Rejected.
- **Test first** in `PlanCommandsTest`: a started issue waiting for one in each state; refused for Idea to Backlog and
  Rejected, accepted for Implementing, Rework, Review and Finished. Red before the change.
- `doc/index.md`'s list of refusals and a changelog entry.

## Acceptance

- The unit test above, seen red first, and the module's build green.
- `plan check` on porelo-project's plan no longer refuses its waits for issues that are Implementing, among them a
  homeserver's and a host's issue waiting for Porelo's own work on messages.
