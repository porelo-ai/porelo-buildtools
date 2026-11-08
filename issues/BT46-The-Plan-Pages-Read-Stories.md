# BT46 — The Plan Pages Read Stories

**Status:** Idea.

**What must be true.** A story's data stands in a JSON head in its own file in porelo-project's `stories/`, checked
by a JSON schema held here; `stories/README.md` is generated from the heads and the document checks refuse an index
that differs from its generation. `porelo-release plan render` and `plan check` read a release as a set of stories
from those heads in place of sprints, each story's state derived as its least advanced issue's, and `plan check`
refuses a story naming an issue that does not exist and a release naming a story that does not exist.

## Why

The project replaces sprints by stories (porelo-project PJ104): one function of value to a user, with a test the
person runs at its end and every issue it needs. A story's data is held once, in a JSON head of its file, checked by a
schema, and the index is made from it (PJ105, decided 2026-11-08); issues follow in the same form later. The plan
pages, `plan check`, the schema and the generator are built here, so they change with it.

## The head

Decided by the operator (2026-11-08), as proposed: the file's first line is its heading, then a fenced code block with
the info string `json`, then the prose; the fields are `id`, `title`, `gain`, `test`, `issues` and `tested`, and there
is no state field.

````markdown
# ST1 — A Person Rents A Host And Gives It Back

```json
{
  "id": "ST1",
  "title": "A Person Rents A Host And Gives It Back",
  "gain": "A person works on a rented host as on the laptop and leaves nothing behind.",
  "test": "Rent porelo-move-3, run a task to its pull request, give the host back.",
  "issues": ["porelo-host#HO146", "porelo#PO412"],
  "tested": null
}
```

What a user gains, in prose ...
````

- **Why this fencing:** GitHub and Forgejo show the heading as a heading, the head as a highlighted block a reader can
  read, and the prose below it as before. The head ends without doubt: a line that is only three backticks cannot
  stand inside a JSON value, as a JSON string holds no raw line break. The block is the first after the heading, so
  no other code block in the prose is taken for it.
- **The heading repeats the head**: `# <id> — <title>`, and the check refuses a heading that differs from it. The
  forges then show a title, and the duplication cannot drift.
- **Not chosen:**
  - An HTML comment hides the head on the forges, but `--` cannot stand inside a comment, so the schema would have
    to forbid it in every text field.
  - YAML front matter (`---`) holding JSON is rendered by both forges as a table, but a YAML reader also takes what
    is not JSON, and nested lists render poorly.
- **The state is never written.** It is derived from the issues' states, so it has no field. `tested` is a date or
  `null`.

## The shape

- **The schema** is a JSON Schema file here, published with `porelo-release`, and its `$id` names its version. There
  is one for stories now; the one for issues comes with PJ105.
- **The commands:**
  - `porelo-release stories index <dir>` writes `README.md` from the heads, grouped by state in the issue indexes'
    order.
  - `stories check <dir>` refuses, naming file and field:
    - a head that fails the schema;
    - a heading that differs from its head;
    - a file name that differs from its id;
    - a repeated id;
    - an index that differs from its generation.
  - Both run in porelo-project's document checks.
- **`plan/<release>/plan.yml` names its stories** (`stories: [ST1, …]`) in place of `sprints:`. The operator decided
  (2026-11-08) that every plan is converted to stories once, the tested functions too, by the coordinator. The reader
  has no path for sprints, and it refuses a plan that still names `sprints:`, saying so.
- **A story's state** is the least advanced state of its issues, in the indexes' order. A tested date moves it past
  Finished. `plan check` refuses:
  - a story with no issue and no tested date;
  - an issue no index holds;
  - a story no head holds;
  - a Finished story without a tested date;
  - a wait (`plan/after.yml`) on a story that comes later or not at all.
- **Order without sprints:** the stories follow the plan's list, and none stands before one it waits on. The "Sprint
  progress" picture becomes one bar per story's state.
- **What it touches, in `release`:**
  - `Plan`, `PlanCheck`, `PlanRender`, `PlanTemplate`, a new head reader and index generator.
  - The templates `plan.jte`, `progress.jte`, `sprint.jte` (to `story.jte`).
  - `CheckCitations`' recognition of a plan file.
  - `Main`'s usage, `PlanCommandsTest`, new tests for heads and index, the fixtures under
    `src/test/resources/plans`, `doc/index.md` and a changelog entry.

## Size and plan

**L**, roughly 2 to 2.5 days, guessed and not measured: the schema and the index generator add to the plan reader.
It can be built in three steps, each its own commit with tests seen red:

1. The schema, the head reader and `stories check`, without the index (M).
2. The index generator and the check that the index equals it (S).
3. `plan.yml` with stories, `plan check` and the pages (M).

Steps 1 and 2 serve PJ105 too: the head reader and the generator take the schema as a parameter, so issues reuse them.

## The validator

Decided by the operator (2026-11-08): the schema is checked with `com.networknt:json-schema-validator` on the Jackson
`porelo-release` already has, if it builds in the native image. Step 1 tries that first. If it does not build, step 1
falls back to a small check of our own covering the few rules the head uses (types, required fields, patterns, no
other fields), and says so in this issue.

## Waits on

- The operator's word to build. The fencing, the fields and the validator are decided.
- Step 3 goes live together with the coordinator's conversion of the plans in porelo-project: porelo-project's
  Plan pages build from buildtools `main`, and after step 3 a plan with sprints is refused. Fixtures in the agreed form
  are enough to build and test it before then.
