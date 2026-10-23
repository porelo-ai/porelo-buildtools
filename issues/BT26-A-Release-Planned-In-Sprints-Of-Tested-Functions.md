# BT26 — A Release Planned In Sprints Of Tested Functions

**Status:** Implementing.
**Size:** M - two verbs of `release`, a renderer and a file format porelo-frontend will read; like porelo-host
[HO18](https://github.com/porelo-ai/porelo-host/blob/main/issues/README.md#ho18) at 51 minutes, about three times
that.

**What must be true.** `porelo-release plan render <file>` writes a release's page from its plan file and every
repository's issue index, and `porelo-release plan check <file>` refuses a plan that names an issue no index has, an
issue in two functions, a page that differs from what `render` writes, a Finished issue in a function not yet tested,
or a function with neither an issue nor a test date. It is the build part of porelo-project
[PJ63](https://github.com/porelo-ai/porelo-project/blob/main/issues/README.md#pj63).

## Plan

- **The plan file**, `plan/<release>.yml` in porelo-project, as agreed in the channel: `release`, `goal`, an optional
  `issue`, and `sprints` in file order, each with a string `id`, `title`, `goal` and `functions`; a function has a
  `title` unique within its sprint, `test`, an optional `tested` (a `yyyy-MM-dd` string, absent until the person
  tried it), `issues` as `<repository>#<number>` and optional `notes`, steps not filed yet, never counted as done.
- **`Plan`** reads it with Jackson YAML into records and refuses, naming the place: a repository `project.yml` does
  not name, a number matching none of that repository's prefixes, a `tested` that is not such a date, a function title
  twice in a sprint.
- **`IssueIndex`**, an extension of what `IssueLinks` parses, not a second parser: each index row's number, its state
  from the `## <State>` heading it stands under, its size, and its title, taken from the issue file's
  `# <NUMBER> — <Title>` heading where the file can be read, else from the row's text. Indexes are read as
  `IssueLinks` reads them: from the tree, a checkout beside it, or `main` on the central forge.
- **`PlanRender`** writes `plan/<release>.md`: a text bar per sprint by its issues' states, a mark per function tested
  or not, each issue linked to its index row; with `--html <file>`, the same as a local page. A second render changes
  nothing.
- **`PlanCheck`** runs the five checks above, each in a test seen red first; `Main` gains `plan render` and
  `plan check`; `doc/` gains the command and the file's form.

## Acceptance

- `plan render` on porelo-project's plan writes the page, and a second run changes nothing.
- `plan check` passes on that plan and fails for each of the five faults, naming the issue or the function.
