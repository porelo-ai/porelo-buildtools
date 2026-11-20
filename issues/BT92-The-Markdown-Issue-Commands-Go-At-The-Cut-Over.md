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
  "covers": "Until the day stories and issues move to GitHub Issues, `issues index`, `issues check`, `stories index`, `stories check` and the issue-link rules of `check-citations` keep working unchanged; on that day they go, with their workflow step, and no check of the GitHub side takes their place here."
}
```

**Why this size.** Mostly removal, but in two modules (porelo-release and the published porelo-stories library), one
workflow step, the documents and a changelog entry, and a library other programs read: M.

**What must be true.** Projects move from Markdown issues to GitHub Issues on one cut-over day, never gradually. Until
that day every repository's `issues/` and porelo-project's `stories/` are checked exactly as today. After it, nothing
in porelo-buildtools reads, writes or checks a Markdown issue or story, so no check fails on a repository whose
`issues/` is gone.

- **Until the cut-over, nothing here changes.** `issues index|check|convert`, `stories index|check|cut`, the
  `issue-1.json` and `story-*.json` schemas, and `check-citations`' rules for issue links and issue files keep their
  behavior. No change is needed for the time between.
- **At the cut-over, these go:**
  - the `issues` and `stories` commands with `Issues`, `Stories` and `IssueLinks`;
  - `check-citations`' rules that read `issues/` and its index;
  - the shared-rules step "The issue heads";
  - this repository's own `issues/` and `stories/` test fixtures, documents and changelog sources.
- **The porelo-stories library goes only once nothing reads it.** Today the planner's git store reads it besides
  porelo-release. A release of the library stays published for as long as a reader pins it.
- **No GitHub check takes their place here.** The heads' rules (allowed states, sizes, priorities, ids) become the
  values of the GitHub project's single-select fields and the planner's own refusals when it writes. A CI step in a
  repository could only check GitHub with a token the Build does not have and should not have.

## Plan

- `release/src/main/java/org/fuin/porelo/release/Main.java`: drop `case "issues"` and `case "stories"` and their usage
  lines; delete `Issues.java`, `Stories.java`, `IssueLinks.java` and their tests (`IssuesTest`, `StoriesTest`,
  `StoriesIndexTest`, `IssueIndexFixturesTest`, `src/test/resources/issue-indexes/`, `issue-index-fingerprints.json`).
- `CheckCitations.java`: keep the rule that no code or shipped text cites an issue number, if it stays (see *Open
  questions*). Drop the reading of the index and of the issues' own pages, which needs `issues/`.
- `.github/workflows/shared-rules.yml`: drop "The issue heads". Change the `documents` profile's tests that read this
  repository's `issues/` the same way.
- `stories/` module and its `<module>`: removed in a later step, once its last reader has moved off it; until then it
  stays built and published unchanged.
- `doc/index.md`, the README's command list and a changelog entry under *Removed*.
- Red first: a test that `porelo-release issues check issues` ends with the usage refusal fails today.

## Acceptance

- `./mvnw verify` and `-Pdocuments test` pass with `issues/` and `stories/` deleted from a copy of this repository.
  The same `check-citations .` that passes there passes on a repository that never had `issues/`.
- `porelo-release issues …` and `porelo-release stories …` are refused as unknown commands.
- Before the cut-over: nothing of this is merged, and every check on `main` still passes against the Markdown issues.

## Open questions

- An earlier Backlog issue here, [BT71](README.md#bt71), hands the same `issues` and `stories` commands and the
  porelo-stories library to the planner. With the move to GitHub Issues that hand-over goes away. Should that issue shrink
  to `check-commits`, `check-pushes` and `stage`, or be rejected for the parts this issue removes?
- Do the stage records (`stats/`, `stage`, and `check-commits`' rule on open stages) go at the same cut-over, with the
  footprint then kept in the GitHub issue's body? Or do they stay in git?
- Does the rule that code and shipped text never cite an issue number stay after the cut-over, now held against
  GitHub numbers (`#123`) and the carried-over old ids? Or does it go with the Markdown issues?

## To be checked

- With the planner's agent: whether its git store and its carry-over read porelo-stories after the cut-over, and up
  to which version. That decides when the library and its module go.
- Every caller of `issues check` and `stories check` outside this repository: workflows, prepush hooks and the
  coordinator's local scripts, found by a search shown to find the one in porelo-host's `shared-rules.yml`.
