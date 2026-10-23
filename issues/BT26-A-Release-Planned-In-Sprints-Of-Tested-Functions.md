# BT26 — A Release Planned In Sprints Of Tested Functions

**Status:** Implementing.
**Size:** M - two verbs of `release`, a renderer and a file format porelo-frontend will read; like porelo-host
[HO18](https://github.com/porelo-ai/porelo-host/blob/main/issues/README.md#ho18) at 51 minutes, about three times
that.

**What must be true.** `porelo-release plan render <file>` writes a release's page from its plan file and every
repository's issue index, and `porelo-release plan check <file>` refuses a plan that names an issue no index has, an
issue in two functions, a page that differs from what `render` writes, a Finished issue in a function not yet tested,
or a function with no issue, no note and no `tested` date. A function with notes is planned, passes this fifth
check, and is shown as "not filed yet"; notes never count as done in any bar or count. It is the build part of
porelo-project
[PJ63](https://github.com/porelo-ai/porelo-project/blob/main/issues/README.md#pj63).

## Plan

- **The plan file**, `plan/<release>.yml` in porelo-project, as agreed in the channel: `release`, `goal`, an optional
  `issue`, and `sprints` in file order, each with a string `id`, `title`, `goal` and `functions`; a function has a
  `title` unique within its sprint, `test`, an optional `tested` (a `yyyy-MM-dd` string, absent until the person
  tried it), `issues` as `<repository>#<number>` and optional `notes`, steps not filed yet, never counted as done.
- **`Plan`** reads it with Jackson YAML into records and refuses, naming the place: a repository `project.yml` does
  not name, a number matching none of that repository's prefixes, a `tested` that is not such a date, a function title
  twice in a sprint, or more than one YAML document.
- **`IssueIndex`**, an extension of what `IssueLinks` parses, not a second parser: each index row's number, its state
  from the Markdown state heading it stands under, its size, and its title, taken from the issue file's
  `# <NUMBER> — <Title>` heading (also accepting an en dash or hyphen) where the file can be read, else from the
  table's named title column. Markdown closing hashes and trailing spaces are stripped from state headings;
  unknown states are refused and fenced examples are ignored. Both plan verbs read every index and optional issue
  heading from `main` on the central forge by default, including the owning project's index. `--indexes=beside`
  selects the tree, a checkout beside it, then the forge; `check-citations` keeps its existing source order.
  Subgroup links may include a fragment. Rows retain their source file path or GitHub URL; missing-row,
  unreadable-index, and unknown-heading refusals name the source read. Tests use the web fake to prove that stale
  local indexes are ignored by default and that an explicit beside read names the sibling file in a refusal.
- **`PlanRender`** writes `plan/<release>.md`: a text bar per sprint by its issues' states, a mark per function tested
  or not, each issue linked to its index row; with `--html <file>`, the same as a local page. A second render changes
  nothing. Both destinations are checked before writing against all read local inputs, including aliases; the
  default Markdown destination must resolve inside the plan directory, while HTML may name another directory.
  Markdown preserves inline backticks, emphasis and brackets, escapes block-leading heading and list markers,
  table separators and angle brackets, and draws bars with `█` for Finished and `░` for other states. Tests first
  reproduce escaped code spans, escaped bars and unsafe heading or table text before verifying the correction.
- **`PlanCheck`** also refuses an issue repeated within one function, naming that function separately from an
  assignment to two functions. It runs the five checks above, each in a test seen red first; the fifth refuses only a
  function with no issue, no note and no `tested` date. The real `0.6.yml` fixture passes with its fixture indexes, and a
  separate test guards that notes never count as done in the bars. `Main` gains `plan render` and `plan check`;
  `doc/` gains the command and the file's form.

## Acceptance

- `plan render` on porelo-project's plan writes the page, and a second run changes nothing.
- `plan check` passes on that plan and fails for each of the five faults, naming the issue or the function, and the
  index it read.
- Every text the HTML page takes from the plan file or an index is escaped.
- `plan render` never writes onto an input it read, nor out of the plan's directory through a link.
