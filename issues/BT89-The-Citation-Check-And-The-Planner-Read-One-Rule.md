# BT89 — The Citation Check And The Planner Read One Rule

```json
{
  "$schema": "issue-1.json",
  "id": "BT89",
  "title": "The Citation Check And The Planner Read One Rule",
  "state": "Backlog",
  "size": "M",
  "priority": "Medium",
  "rank": 32,
  "blockedBy": [],
  "covers": "`check-citations` refuses what porelo-planner's reader counts as a board fault: an index row linking an issue file that was deleted, an issue number in issue text that is not a link to its index row, and a link to a story's file instead of its index row; the two read one rule."
}
```

**Why this size.** Three gaps in one check, each needing a red-first test. The second gap's cause is not read yet, and
the rule has to match a reader in another repository: one module, its tests, the guide's words, and a reading of
`StoreReader` and `IssueLinks` in porelo-planner.

Filed 2026-11-13 from the Coordinator's finding. The planner reported 40 board faults that the prepush gate had let
through, read live at 09:41 UTC: porelo-planner 28 and porelo-project 12. The list is in the handover
`board-faults-2026-11-13.md`.

**What must be true.** A repository whose prepush `check-citations` passes shows no citation fault on the planner's
board. These are the three gaps:

- **The index is exempt.** `CheckCitations.linksToFilesThatDie` skips every issue page, the index among them ("an
  index names files because that is what an index does"). So 28 Finished rows in porelo-planner's `issues/README.md`
  still link `PLnn-....md`, a file deleted when the issue finished. The planner's reader flags them as "links to the
  deleted file of PLnn". The index's links are checked like any other: a row links a file that is there.
- **Plain numbers pass the unlinked rule.** Seven `PJnn` in porelo-project issue texts are not links to their index
  row: PJ104:83, PJ109:106, and PJ91:154, 161, 163, 169 and 170. They passed although the check has an `unlinked`
  rule. Why they passed is to be read first; the reader flags them as "is not a link to its index row".
- **A story's file passes.** PJ116 to PJ120 link `../stories/ST<n>-....md` on line 21 instead of the story's index row
  `../stories/README.md#st<n>`. The reader says "links to <target>, not to its index row".

## Plan

- Read porelo-planner's `StoreReader` (the `mentions` search, around 355-359) and `stories/IssueLinks` (around 203 and
  220) at its `origin/main`, and write their rule into the check, not a second one.
- Red first, one test for each gap, in `CheckCitationsTest`. The index's dead links come first, as they are most of the
  faults.
- Run the check over porelo-planner and porelo-project at their `origin/main`. It must report the same 40 places the
  planner reports, and nothing it does not.
- The guide's words on citations, and the class comment that exempts the index.

## Acceptance

- `check-citations` fails on each of the three kinds, and a test fails without each fix.
- Over the two repositories as they stood on 2026-11-13, it names the planner's 40 faults, and no other.
