# BT92 — The Markdown Issue Commands Go At The Cut-Over

```json
{
  "$schema": "issue-1.json",
  "id": "BT92",
  "title": "The Markdown Issue Commands Go At The Cut-Over",
  "state": "Accepted",
  "size": "M",
  "priority": "High",
  "rank": 1,
  "blockedBy": [],
  "covers": "Until the day stories and issues move to GitHub Issues, the `issues`, `stories` and `stage` commands, `stats/`, check-commits' stage rule and the issue-link rules of `check-citations` keep working unchanged; on that day they go with the porelo-stories library and their workflow step, the rule against citing an issue number stays for the old ids and the GitHub numbers, and no check of the GitHub side takes their place here."
}
```

**Why this size.** Mostly removal, but in two modules (porelo-release and the porelo-stories library), the stage
records, one workflow step, the documents and a changelog entry, plus the issue-number rule moved off `issues/` in two
checks: M.

**What must be true.** Projects move from Markdown issues to GitHub Issues on one cut-over day, never gradually. Until
that day every repository's `issues/`, `stats/` and porelo-project's `stories/` are checked exactly as today. After it,
nothing in porelo-buildtools reads, writes or checks a Markdown issue, a story or a stage record, so no check fails on
a repository whose `issues/` and `stats/` are gone.

- **Until the cut-over, nothing here changes.** `issues index|check|convert`, `stories index|check|cut`, `stage`, the
  `issue-1.json` and `story-*.json` schemas, `check-commits`' stage rule and `check-citations`' rules for issue links
  and issue files keep their behavior. On the cut-over day the carry-over writes a new key, `issue_floor`, into
  `project.yml`, and the checks that read `project.yml` until then accept it.
- **At the cut-over, these go:**
  - the `issues`, `stories` and `stage` commands with `Issues`, `Stories`, `IssueLinks`, `ProjectFile`, `Stage` and
    `StageTokens`;
  - the stage records: this repository's `stats/` and `check-commits`' rule that a tree's stage records are closed
    and that an issue in a measured state has its stats. The footprint and the analysis result then stand in the
    GitHub issue's body;
  - the porelo-stories library and its module: no program but porelo-release reads it, porelo-planner included;
  - `check-citations`' rules that read `issues/` and its index;
  - the shared-rules step "The issue heads";
  - this repository's own `issues/` and `stories/` test fixtures, documents and changelog sources.
- **The rule that code and shipped text never cite an issue number stays**, for the carried-over old ids and the new
  GitHub numbers alike. `check-commits`' rule against an issue number in a commit message stays the same way.
  Neither reads its prefixes from `issues/` any more: today both stop unanswered when `issues/` holds no prefix, which
  after the cut-over would fail every repository's Build.
- **No GitHub check takes their place here.** The heads' rules (allowed states, sizes, priorities, ids) become the
  values of the GitHub project's single-select fields and the planner's own refusals when it writes. A CI step in a
  repository could only check GitHub with a token the Build does not have and should not have.

## Plan

- `release/src/main/java/org/fuin/porelo/release/Main.java`: drop `case "issues"`, `case "stories"` and
  `case "stage"` and their usage lines; delete `Issues.java`, `Stories.java`, `IssueLinks.java`, `ProjectFile.java`,
  `Stage.java`, `StageTokens.java` and their tests (`IssuesTest`, `StoriesTest`, `StoriesIndexTest`,
  `IssueIndexFixturesTest`, the stage tests, `src/test/resources/issue-indexes/`, `issue-index-fingerprints.json`).
- `CheckCommits.java`: drop `stageFaults` and the `stats/seed.csv` note. The issue-number rule takes a number by its
  shape, `[A-Z]{1,3}\d{2,3}`, as `check-citations`' rule outside the issues already does, with the same exemptions,
  plus a GitHub issue reference (`#<n>`, `…/issues/<n>`); the `Prefixes` reader goes.
- `CheckCitations.java`: drop the reading of the index and of the issues' own pages, `issuePrefixes` and the import
  of porelo-stories' `IssueHead`. Keep the number rule outside the issues, extended to a GitHub issue reference.
- The `stories` module and its `<module>` in the parent pom go. Its published releases stay where they are.
- `.github/workflows/shared-rules.yml`: drop "The issue heads". Change the `documents` profile's tests that read this
  repository's `issues/` the same way. Delete `stats/`.
- `doc/index.md`, the README's command list and a changelog entry under *Removed*, and one under *Changed* for the
  number rules.
- Red first: a test that `porelo-release issues check issues` ends with the usage refusal fails today, and a test
  that `check-commits` passes on a tree without `issues/` and refuses `HO12` and `#12` in a subject fails today.

## Acceptance

- `./mvnw verify` and `-Pdocuments test` pass with `issues/`, `stories/` and `stats/` deleted from a copy of this
  repository. The same `check-citations .` and `check-commits` that pass there pass on a repository that never had
  `issues/`.
- `check-citations` and `check-commits` refuse an old id (`HO12`) and a GitHub number (`#12`) in code, shipped text
  and a commit subject, and the GitHub number's shape is shown not to hit any of today's repositories' committed
  files outside their exemptions.
- `porelo-release issues …`, `porelo-release stories …` and `porelo-release stage …` are refused as unknown commands.
- Before the cut-over: nothing of this is merged, and every check on `main` still passes against the Markdown issues.

## To be checked

- Every caller of `issues check`, `stories check` and `stage` outside this repository: workflows, prepush hooks and
  the coordinator's local scripts, found by a search shown to find the one in porelo-host's `shared-rules.yml`.
- The shape of a GitHub number that hits no CSS color, shell comment or Markdown heading anchor in today's
  repositories, measured over all of them before it is fixed.
