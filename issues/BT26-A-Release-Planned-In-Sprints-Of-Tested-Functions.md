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
  unknown states are refused and fenced examples are ignored. Indexes are read as
  `IssueLinks` reads them: from the tree, a checkout beside it, or `main` on the central forge; subgroup links may
  include a fragment. Rows retain their source file path or GitHub URL; missing-row, unreadable-index, and
  unknown-heading refusals name the source read, so a neighboring checkout can be distinguished from `main`.
- **`PlanRender`** writes `plan/<release>.md`: a text bar per sprint by its issues' states, a mark per function tested
  or not, each issue linked to its index row; with `--html <file>`, the same as a local page. A second render changes
  nothing. Both destinations are checked before writing against all read local inputs, including aliases; the
  default Markdown destination must resolve inside the plan directory, while HTML may name another directory.
- **`PlanCheck`** also refuses an issue repeated within one function, naming that function separately from an
  assignment to two functions. It runs the five checks above, each in a test seen red first; the fifth refuses only a function
  with no issue, no note and no `tested` date. The real `0.6.yml` fixture passes with its fixture indexes, and a
  separate test guards that notes never count as done in the bars. `Main` gains `plan render` and `plan check`;
  `doc/` gains the command and the file's form.

## Acceptance

- `plan render` on porelo-project's plan writes the page, and a second run changes nothing.
- `plan check` passes on that plan and fails for each of the five faults, naming the issue or the function.
- Regression inputs cover output collisions with the project definition, own and neighboring indexes, subgroup
  indexes, issue files, symbolic links, hard links, and a default page escaping through a symlink (including a
  dangling target and a chain); every refused write preserves the input bytes.
- State closing hashes, trailing spaces, both fence characters, reordered table headers with an open-question
  column, three heading dashes, fragmented subgroup links, multiple YAML documents, and an issue repeated within
  one function have command tests. The release-level missing-row guard and both pages' test instructions are
  tested with their implementation deliberately removed and restored.
- A missing-row refusal names the neighboring checkout's index file in a regression seen to fail before the fix.
- Local verification: all 68 plan-command tests pass, as do all seven document tests, including the shared,
  citation, README, and documentation-site checks; the separate action check passes for all 14 steps. The release suite runs 440 tests with no assertion
  failures or skips, but 20 `CompareBillsTest` and two `WebTest` cases cannot open sockets in the sandbox. The
  requested Maven invocation cannot write to the default local repository; the suite uses a writable cache
  instead. VM and rented-machine tests have not run.
- The changed code is read against `graal` 2026.910.3 and `java-code-review`, `test-quality`, `security-audit`,
  `concurrency-review`, `clean-code`, and `solid-principles` 2026.906.3.
