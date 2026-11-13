# BT89 — The Citation Check And The Planner Read One Rule

```json
{
  "$schema": "issue-1.json",
  "id": "BT89",
  "title": "The Citation Check And The Planner Read One Rule",
  "state": "Review",
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

The planner's board shows 40 citation faults that the prepush gate let through: porelo-planner 28 and porelo-project
12.

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
- Over the two repositories at the commits the faults were read on, it names the planner's 40 faults, and no other.

## What is built

- **The index's dead links.** `check-citations` reads every page of its own `issues/`, the index among them, for the
  file of a settled issue, one whose row is left without its file: the number, a dash and a name ending in `.md`, as
  `StoreReader.mentions` reads it. Red first:
  `CheckCitationsTest.refusesALinkToTheDeletedFileOfAFinishedIssueInTheIndexToo`. Over porelo-planner at 09b665e it
  names the 28 places the board showed; at 012bd3d, none.
- **The plain numbers and the story files** were no gap in the check: on its main it names porelo-project's 12, the
  seven plain numbers and the five story files. They passed because the prepush gate does not run `check-citations`.
  `CheckCitationsTest.refusesALinkToAStorysFileFromAnIssue` holds the story file's case, green before and after.
- **A story's index row** was refused: a link from an issue to `../stories/README.md#st<n>`, the form the planner asks
  for, read as a row that is not there, since only the pages under `issues/` gave anchors. The stories' index gives its
  rows' anchors too. Red first: `CheckCitationsTest.acceptsALinkToAStorysIndexRowFromAnIssue`. A story of one digit,
  `ST9`, is no issue number to the check at all, so its file link passes where `ST11`'s does not.
- The guide's words under *What `check-citations` reads* and the class comment say the rule.

