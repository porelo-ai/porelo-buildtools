# BT64 — Public Readers Of A Project And A Story

```json
{
  "$schema": "issue-1.json",
  "id": "BT64",
  "title": "Public Readers Of A Project And A Story",
  "state": "Review",
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

- **`Project`:** a public record with a public `read(String text, Set<String> ownPrefixes)` from the text of
  `project.yml`; the own repository's prefixes come as a value, since `project.yml` does not name them, so
  `Indexes` stays package-private and the text form throws no `IOException`. The path form reads the file, asks
  `Indexes` and goes through the same parsing. The record's `Path root`, read by nothing, goes.
- **`Workflow`:** a public `read(String text)` beside `read(Path)`, which reads the file and calls it.
- **The story reader:** a new public `Story` record (id, title, state, priority, issues, body) and
  `Story.read(String text, Workflow)`, modelled on `IssueHead.read(String, Workflow)`: it throws
  `IllegalArgumentException` in the words the index reports as faults. `Heads.read(Path, faults, Workflow)` is built
  on the same parsing, so the index and the reader are one parser; `Heads.Head` stays package-private, since it
  carries the file's path, Jackson's `JsonNode` and the raw lines.
- **Small and closed:** immutable values or records, no setters, javadoc on each public type; the library is
  LGPL-3.0-or-later, so others link it.
- **Red first:** a test in another package reads a project and a story through the public API alone; it does not
  compile while they are package-private.
- **Docs:** the stories library's page names the two readers.

## Acceptance

- The test from outside the package, seen failing to compile before and passing after.
- `./mvnw verify` green; the stories index's own tests unchanged and green.

## To be checked

- Built by a sub-agent of Agent Machines (claude-opus-5-5) from the plan; `Project.read` takes the own prefixes as a
  value, so `Indexes` stays package-private. Red first: the outside-package test failed to compile while the readers
  were package-private.
- A code reading by another model (sonnet) found: the heading check moved into the shared parsing with no test at the
  index; `Story.read`'s message wrapping the faults; public constructors taking invalid state; the body dropping the
  first line's indentation; invalid UTF-8 in `project.yml` turning from the index's refusal into an `IOException`.
  Each fixed with a test seen red first. `release/` still has its own `project.yml` reading in its plan command, not
  touched here.
- `./mvnw -B -s settings.xml clean verify` green: stories 123 (1 skipped, the native test), release 962, machines 263.
