# BT62 — An Issue's Data In A Head

```json
{
  "$schema": "issue-1.json",
  "id": "BT62",
  "title": "An Issue's Data In A Head",
  "state": "Review",
  "size": "M",
  "priority": "Medium",
  "rank": 2,
  "blockedBy": [],
  "covers": "An issue's data in a JSON head by `issue-1.json`; `porelo-release issues index`, `check` and `convert`; the stories library, `stage` and check-commits read the head."
}
```

**Why this size.** a schema, three `porelo-release issues` commands, the stories library and two readers of today's prose
lines learn the head; docs, the shared rule text and the document checks follow.

**What must be true.** An issue's data stands in a JSON head right below its title, checked by `issue-1.json`, and the
issue index is generated from the heads, as a story's is from its head ([BT46](README.md#bt46)'s pattern).
porelo-buildtools' part of porelo-project's PJ105 (plan in its 88f1d9d), in ST311; built on the operator's order of
2026-11-10.

## Plan

- **`issue-1.json`** beside `story-2.json`: `$schema`, `id` (`<prefix><number>`, or `<parent>.<n>` for a sub-issue),
  `title`, `state` (a state of `issues/workflow.yml`), `size` (`XS`..`XL` or `-`), `priority` (`Highest`..`Lowest`),
  `rank` (an integer, the order within a priority), `blockedBy` (ids of the same repository), `covers` (the index
  line); optional `parent` and `role` ([BT56](README.md#bt56)'s sub-issue), `severity`, `kind`, `openQuestions`.
- **`porelo-release issues index`:** writes each directory's `README.md` from its files' heads: one table per state
  in the workflow's order, rows by priority, then rank. The intro, "What was decided along the way" and the rows of
  Finished and Rejected, whose files are gone, are kept from the file.
- **`porelo-release issues check`:** each head by the schema, the heading equal to the head's title, every
  `blockedBy` and `parent` known, the index equal to its generation; a hand-edited index is refused, naming the
  first differing line.
- **`porelo-release issues convert`:** for each file without a head, reads its `**Status:**`, `**Size:**`,
  `**Blocked by:**` and `**Priority:**` lines and its index row; where they agree it writes the head and drops those
  lines, where not it lists every difference and writes nothing for that file. A second run leaves nothing to do.
- **Readers of today's lines:** `porelo-release stage` (`Stage.java`, status and size) and check-commits' stats rule
  read the head where a file has one, the line otherwise. The stories library's `IssueIndexes` reads heads where a
  file has one, the index rows else.
- **check-citations** takes the head's `blockedBy` and `parent` ids as data, not as bare citations.
- **The shared rule text:** "an issue's data stands in its head; the index is never edited by hand"; the shared
  workflows' document checks call `issues check`.
- **Docs and changelog**; **red first** for each command and each reader, on fixtures of this repository and of
  porelo's sets.
- **The proof:** porelo-buildtools converts its own issues first, once the snapshot is published.

## Acceptance

- `issues convert` on a copy of this repository lists its differences, writes nothing for those files, and after the
  fixes writes every head; `issues index` then gives an index `issues check` accepts.
- `stories check` in porelo-project passes over a repository converted and one not.
- `stage begin` and check-commits read a converted issue's state and size.

## For the Shared Area

The rule sentence, word for word:

> An issue's data stands in its head; the index is never edited by hand.

The step for the shared workflows' document check, after the one for check-citations, in the same form:

```yaml
            - name: The issues' heads and their index
              run: '"$JAVA_HOME/bin/java" -cp "$(cat "$RUNNER_TEMP/tools-cp.txt")" org.fuin.porelo.release.Main issues check issues'
```

In a repository whose issues have no heads yet the step fails and names each file, so a repository adds it once
its issues are converted.

## To be checked

- The plan was not read by another model before the build; another model's reading of the code stood in for it.
