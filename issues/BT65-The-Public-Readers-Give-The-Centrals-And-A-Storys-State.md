# BT65 — The Public Readers Give The Centrals And A Story's State

```json
{
  "$schema": "issue-1.json",
  "id": "BT65",
  "title": "The Public Readers Give The Centrals And A Story's State",
  "state": "Implementing",
  "size": "S",
  "priority": "Medium",
  "rank": 7,
  "blockedBy": [],
  "covers": "`porelo-stories`' public `Project` also gives each repository's central address from the same reading of `project.yml`, and the rule the stories index gives a story its state by is public, so porelo-planner clones every repository and shows the index's state without a second implementation."
}
```

**Why this size.** one more record component filled by the reading that is there, one private rule made public and
shared, tests through the public API, docs and a changelog entry.

**What must be true.** A program that links `porelo-stories` reads, through the public `Project`, the `central`
address `project.yml` gives the project (`project.central`) and each repository (`repositories.<name>.central`), as
written there, from the same reading that already checks those addresses. It also has, in public, the rule the stories index
gives a story its state by when its head leaves it to its issues, the one implementation the index uses. Asked for by
Agent Planner on 2026-11-10 for porelo-planner's PL03, which clones every repository of a project and shows each
story's state on its board, and may implement neither a second time.

## Plan

- **`Project`:** a third component `Map<String, String> centrals`, each repository's central address by name, the own
  one first, with `central(String name)` beside `repositories()`. The compact constructor refuses centrals whose
  names differ from the repositories', and copies them.
- **The reading:** `repository(...)` already reads and checks each `central` (or a not yet renamed `upstream`); it
  keeps the address it checked, as written, beside the name it derives. No new refusal.
- **Red first:** a test from outside the package reads the centrals of a project with two repositories, one given by
  `upstream`; it does not compile before.
- **The story's state:** `StoriesIndex.computed` (the earliest open issue state in board order; else the first
  rejected one when all are rejected; else the workflow's first completed state; the stated one when there are no
  issues) moves to a public `Workflow.storyState(String stated, List<String> issueStates)`, which the index calls; its
  refusal stays "workflow.yml has no completed state". Red first from outside the package, one case per branch.
- **Docs:** the stories library's page names the centrals and the state rule; a changelog entry.

## Acceptance

- The test from outside the package, seen failing to compile before and passing after.
- `./mvnw verify` green.
