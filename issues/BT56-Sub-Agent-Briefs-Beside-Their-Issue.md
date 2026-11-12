# BT56 — Sub-Agent Briefs Beside Their Issue

```json
{
  "$schema": "issue-1.json",
  "id": "BT56",
  "title": "Sub-Agent Briefs Beside Their Issue",
  "state": "Analyzed",
  "size": "S",
  "priority": "Medium",
  "rank": 1,
  "blockedBy": [],
  "covers": "check-citations takes a sub-issue `<parent>.<n>` as it takes an issue (its number a link to its row `#<parent>.<n>`, its file dying with it) and drops the rules for sub-agent briefs as files in `<ID>.sub/`; the sub-issue rules of the work format go with it to the planner.",
  "openQuestions": 1
}
```

**Size S.** One module, `release`: `CheckCitations` and `IssueLinks` with their tests, a section of `doc/index.md` and a
changelog entry; no contract another repository builds against changes.

**What must be true.** A sub-issue `<parent>.<n>` is cited as an issue is: its number is a link to its row in the index
(`[B560.1](README.md#b560.1)`), a link of that shape is taken, and its file dies with it at Finished. No rule for
sub-agent briefs as files in `<ID>.sub/` is left, since a sub-agent's work is a sub-issue and its text the brief.

## Analysis

Read in porelo-buildtools 9d2f713 and porelo-planner main.

**Already built, outside this issue.** issue-1.json and `IssueSchema` take a sub-issue's id `<parent>.<n>`, its
`parent` and `role`; `IssuesIndex` checks that a parent is named only by a sub-issue, is its id before the dot, and
exists; its file name pattern takes the dot. porelo-planner reads sub-issues and gives each its parent's worker.

**Left here.**
- `IssueLinks`: `TO_A_ROW` (`#([a-z]{1,3}\d{2,3})$`), `NUMBER_TEXT` and `GITHUB` take no `.<n>`, so a link to a
  sub-issue's row is read as no issue link and its number as a bare number. Each takes an optional `\.[1-9]\d{0,2}`.
- `CheckCitations`: `DIES` and `ISSUE_FILE` take the sub-issue's file `<parent>.<n>-<Title>.md`; the `.sub/` brief
  pattern, its check and its report ("a sub-agent brief out of place") are removed with their tests.
- `doc/index.md`'s citation rules say the sub-issue's link form; the brief path goes. Changelog under *Changed*.

**Not here.** The rest of the earlier plan belongs to the work format, which [BT71](README.md#bt71) hands to the
planner: a parent Finished only when each sub-issue is Finished or Rejected, a story naming a sub-issue refused, a
sub-issue's stages in its parent's `stats/<parent>.jsonl`. This repository does not build them.

## Plan

1. `IssueLinksTest`: a page linking `[B560.1](README.md#b560.1)` with that anchor in the index is clean; a bare
   `B560.1` outside the issues is reported as a number; red first.
2. `CheckCitationsTest`: a lasting page linking into `B560.1-Words.md` is refused as a link into an issue's file;
   red first. The `.sub/` tests go with the rule.
3. The patterns widened, the brief rule removed; `doc/index.md` and the changelog.

## Acceptance

- The two red-first tests seen red, then green; the module's suite and the full build pass.
- `check-citations` on this repository and on porelo-planner's main, which holds sub-issues, is clean.

## Open questions

- **Build it here at all?** check-citations is to be dropped as a rule under ST321 (porelo-project). If that comes
  soon, this issue can be rejected and its part left to whatever replaces check-citations; if check-citations stays a
  while, a sub-issue's link is refused or misread until this is built. My proposal: build it (S), since sub-issues are
  in use now.
