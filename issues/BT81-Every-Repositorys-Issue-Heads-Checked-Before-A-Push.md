# BT81 — Every Repository's Issue Heads Checked Before A Push

```json
{
  "$schema": "issue-1.json",
  "id": "BT81",
  "title": "Every Repository's Issue Heads Checked Before A Push",
  "state": "Backlog",
  "size": "-",
  "priority": "High",
  "rank": 1,
  "blockedBy": [],
  "covers": "Every repository's checks and the coordinator's prepush run porelo-release's issue-head check against issue-1.json, so a head the planner's board refuses (no size, a rank of null) is refused before it reaches main."
}
```

**What must be true.** An issue head that issue-1.json refuses never reaches `main` of any repository: each
repository's light checks and the coordinator's prepush refuse it, naming the file and the field.

**Why.** Heads on `main` without a size and with a rank of `null` were refused by the planner's board, but nothing
refused them before the push.

**Found while filing.** `porelo-release issues check <directory>` already validates every head against issue-1.json:
on a copy of this repository's issues, a head without `size` and one with `"rank": null` are each refused, exit 1,
naming the file and the field (measured 2026-11-12). No workflow runs it, and agents run it only by hand. So the work
is mostly to run it everywhere, as `check-issues` beside `check-citations` or under its present name.

**Read on each repository's `main`, not its working tree (2026-11-12).** A trial on today's checkouts gave porelo 344
faults, mostly "no head right below the heading", and stopped porelo-frontend with "could not read issues:
.../stories/workflow.yml". Both came from working trees far from `main`: porelo's on a branch of 2026-11-10, before
its heads were converted, and porelo-frontend's 247 commits behind, before its `issues/workflow.yml` copy. On
`origin/main` (porelo 1d9997fb7, porelo-frontend at its fetched tip) every one of porelo's 372 issue files has a head,
and each repository gives exactly one fault, a real one: an index row out of the heads' order ("the index is never
edited by hand, run porelo-release issues index issues"). So the gate runs on the pushed commit, as a workflow's
checkout and the prepush's `git archive` of the commit do, never on a working tree.

- **The lookup's message.** With no `issues/workflow.yml`, the check looks for porelo-project's `stories/workflow.yml`
  beside the issues and names that path; for a repository that is not porelo-project, it should name the missing
  `issues/workflow.yml` instead.

## To be checked

- Whether the check is named `check-issues`, an alias of `issues check issues`, or keeps its present name; and how
  it moves with the work format's hand-over to the planner.
- Which repositories' workflows add the step: each repository's owner changes its own workflow.
