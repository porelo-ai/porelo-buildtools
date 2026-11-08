# BT46 — The Plan Pages Read Stories

**Status:** Review.
**Size:** M - an administrative tool the coordinator alone runs: the schema, the head reader and `stories check`; the
index and each story's readable part; the cut for a release's notes. Tests for what it decides, no more; one reading
at the end.

**What must be true.** A story's data stands in a JSON head at the end of its own file in porelo-project's `stories/`,
checked by a JSON schema held here. `porelo-release stories index` writes each story's readable part and
`stories/README.md` from the heads, each story in the state its head says; `stories check` refuses a head the schema
refuses, an issue no index holds, and a story or an index that differs from what `index` writes. `stories cut` lists,
for a release's notes, the stories tested since the last release.

## Why

The project replaces sprints by stories (porelo-project PJ104): one function of value to a user, with a test the
person runs at its end and every issue it needs. A story's data is held once, in a JSON head of its file, checked by a
schema, and what a person reads is made from it (PJ105, decided 2026-11-08); issues follow in the same form later.

Releases are no longer planned (the operator, 2026-11-08): the work follows the stories' priority and what each waits
on, and a release is a cut the person calls, holding every story tested since the last one. The release plans under
`plan/` stay as the record; nothing reads them for new work, and `plan render` and `plan check` are left as they are.

## A story's file

Decided by the operator (2026-11-08), so a person reads text, not JSON:

1. the heading `# <id> — <title>`;
2. the readable part, written by `stories index` from the head;
3. whatever a person writes by hand (notes, what it covers);
4. at the very end `## Data` and the head, fenced `json`.

````markdown
# ST1 — A Person Rents A Host And Gives It Back

**Priority:** 🔼 High · **State:** Review

**What a user gains.** A person works on a rented host as on the laptop and leaves nothing behind.

**The test.** Rent porelo-move-3, run a task to its pull request, give the host back.

**Issues:**

- [porelo-host#HO146](https://github.com/porelo-ai/porelo-host/blob/main/issues/README.md#ho146) (Review)

## Data

```json
{
  "id": "ST1",
  "title": "A Person Rents A Host And Gives It Back",
  "state": "Review",
  "priority": "High",
  "gain": "A person works on a rented host as on the laptop and leaves nothing behind.",
  "test": "Rent porelo-move-3, run a task to its pull request, give the host back.",
  "issues": ["porelo-host#HO146"],
  "tested": null
}
```
````

- **The head ends without doubt:** a line that is only three backticks cannot stand inside a JSON value, as a JSON
  string holds no raw line break. It is the file's last block, under `## Data`.
- **The heading repeats the head**, and the check refuses one that differs, so the forges show a title that cannot
  drift.
- **The readable part** runs from the heading to the last issue of the list (or `**Issues:** none yet`). A Finished
  story adds the day its test held: `**State:** Finished (<day>)`. Each issue links to its row in the index that holds
  it, never to its file, which goes when it is finished: porelo's issues stand in its sets' indexes.
- **The state is written**, as an issue's, by the same hands, and moves through the same states (the operator,
  2026-11-08): Idea, Accepted or Rejected, Analyzed, Estimated, Backlog, Implementing, Review, Rework or Finished. It is
  not derived from the issues. `stories check` refuses a story in Review while one of its issues is neither in Review,
  Finished nor Rejected, a Finished story without a `tested` day, and a `tested` day on a story that is not Finished. A
  story with no issue yet is not refused: 41 of the open functions had none.
- **`priority`** is Jira's: `Highest`, `High`, `Medium`, `Low` or `Lowest`, required, shown with Jira's icon (⏫ 🔼 🟰
  🔽 ⏬). Within a state, the index lists the higher first (the operator, 2026-11-08).
- **An issue** is named `<repository>#<number>` as the repository's index writes it, a leading zero included (`P08`),
  and `porelo-ai.github.io` among the repositories.
- **Not chosen** for the head: an HTML comment, as `--` cannot stand inside one; YAML front matter, as a YAML reader
  takes what is not JSON and the forges render nested lists poorly.

## The shape

- **The schema** `story-1.json` is published with `porelo-release`, and its `$id` names its version. The one for
  issues comes with PJ105; the head reader takes the schema as a parameter, so issues reuse it.
- **The commands**, run in porelo-project's document checks:
  - `porelo-release stories index <dir>` writes each story's readable part and `README.md`: a table of how many stories
    stand in each state, then a section a state in the states' sequence, Idea first and Finished and Rejected last, each
    row the story with its title, its priority, its issues counted by prefix (`HO:6 · B:5`, the most first) and what a
    user gains.
  - `stories check <dir>` refuses, naming file and field: a head the schema refuses, a heading that differs from its
    head, a file name that differs from its id, a repeated id, a Finished story without a tested day, a tested day on
    any other, a story in Review while one of its issues is neither in Review, Finished nor Rejected, an issue no index
    holds, a repository `project.yml` does not name, and a readable part or an index that differs from what `index`
    writes.
  - `stories cut <version> <dir> --after <day>` writes the stories tested after the day of the last release, the
    earliest first, for the release's notes. The release tags stand in porelo, not in porelo-project, so the day is
    given rather than read from a tag here.
- **The issue indexes** are read as `plan` reads them: from the checkouts beside the project's, else from the forge.

## The validator

Decided by the operator (2026-11-08): `com.networknt:json-schema-validator` on the Jackson `porelo-release` already
has, if it builds in the native image. porelo-release has no native image, so nothing stands in its way. It is 1.5.9,
the one CycloneDX's bill reader already brings, since two versions on one class path broke the bill reader. Its SLF4J
log goes to `slf4j-nop`, so a run says only what the command says; its messages are in English whatever the
machine's language.

## Built

1. The schema, the head reader and `stories check`.
2. The index, each story's readable part, the issues known and the check that both equal what the heads give. Run
   against the 289 stories of porelo-project's branch `stories`: every head passed and every issue was found; after
   `stories index`, `stories check` passed.
3. `stories cut`, `doc/index.md`'s section *Stories*, and a changelog entry each step.

**Read once** at the end by Antigravity CLI (gemini-4-argon-high). Fixed, each with a test seen red: an impossible
`tested` day (`2026-11-31`) passed; `stories cut` listed a story whose state and day disagree; text after the head's
JSON was ignored; a fault was printed as a path, which lost the second slash of an address; ties in the order were
untested. Left as it is: the readable part is found by its `**Issues:**` line, so a person who writes that line by
hand above it, before the first `stories index`, loses what stands above it. The generator writes that line itself,
and the 289 stories carry it.
