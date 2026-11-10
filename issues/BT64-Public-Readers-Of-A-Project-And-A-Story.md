# BT64 — Public Readers Of A Project And A Story

```json
{
  "$schema": "issue-1.json",
  "id": "BT64",
  "title": "Public Readers Of A Project And A Story",
  "state": "Backlog",
  "size": "M",
  "priority": "Medium",
  "rank": 6,
  "blockedBy": [],
  "covers": "`porelo-stories` reads a project's repositories from the text of `project.yml` and a story from its file's text through public readers, the very code the stories index uses, so porelo-planner reads its board without a second parser."
}
```

**Why this size.** a public story record and a text entry point over the parsing `Heads` does today, a text form of
`Project.read` with its own-prefix lookup made public or dropped, a throwing contract in the index's words, a test
from outside the package, docs and a changelog entry; no second parser, but new code around the one there is.

**What must be true.** A program that links `porelo-stories` reads which repositories a project names, from the text
of its `project.yml`, and a story's id, title, state, priority, issues and body, from its file's text and the
`Workflow`, through public types alone. Both are the code `porelo-release stories index` uses, and refuse in the
index's words. Asked for by Agent Planner on 2026-11-10 for porelo-planner's PL03 (the board read from Git), whose
plan forbids a second parser. Agent Planner builds it for Agent Machines after PL11.

## Plan

- **`Project`:** public, with a public `read(String text, Indexes indexes)` from the text of `project.yml`. Today
  `Project.read(Path, Indexes)` walks up to `project.yml` and asks the package-private `Indexes` for the own
  repository's prefixes: `Indexes` becomes a public functional interface, or the text form takes the prefixes as a
  value; the path form stays for the index and calls the text form. The record's `Path root` is not part of the
  text form's answer.
- **The story reader:** a new public `Story` record (id, title, state, priority, issues, body) and
  `Story.read(String text, Workflow)`, modelled on `IssueHead.read(String, Workflow)`: it throws
  `IllegalArgumentException` in the words the index reports as faults. `Heads.read(Path, faults, Workflow)` is built
  on the same parsing, so the index and the reader are one parser; `Heads.Head` stays package-private, since it
  carries the file's path, Jackson's `JsonNode` and the raw lines.
- **Small and closed:** immutable values or records, no setters, javadoc on each public type; the library is
  LGPL-3.0-or-later (BT61), so others link it.
- **Red first:** a test in another package reads a project and a story through the public API alone; it does not
  compile while they are package-private.
- **Docs:** the stories library's page names the two readers.

## Acceptance

- The test from outside the package, seen failing to compile before and passing after.
- `./mvnw verify` green; the stories index's own tests unchanged and green.
