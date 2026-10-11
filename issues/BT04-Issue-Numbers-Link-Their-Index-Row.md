# BT04 — Issue Numbers Link Their Index Row

**Status:** Implementing.
**Size:** M - `CheckCitations` and a new `IssueLinks` in `release` with their tests, other repositories' indexes read
over https through `Web`, and `doc/index.md`; no new command, so no repository changes how it calls the check.

**What must be true.** `check-citations` holds every repository to the rule for issue numbers under `issues/`: each
index row opens with an anchor named after its number, each issue number there is a link to its row in an index, never
to its file, and each such link reaches an anchor that exists - in the same repository from the tree, in another from
that repository's index on `main`.

## Why

Since 2026-10-11 the rules say an issue number is a link to its row, `[B102](README.md#b102)`, because the row
outlives the file: a finished issue keeps its row under *Finished*. Twelve repositories made the change by hand and
with a script on the same morning; without a check the next number written plainly, or the next row added without its
anchor, goes unnoticed until somebody follows a link that leads nowhere.

## Plan

- `release/.../CheckCitations.java` gains a fourth reading, of every Markdown file under `issues/`:
  - **A row without its anchor:** in an `issues/**/README.md`, a table row whose first cell holds an issue number and
    does not open with `<a id="<number in lower case>"></a>`.
  - **A number that is not a link to its row:** an issue number in the text, outside headings, code spans, fenced
    code, a line marked `not-a-citation`, and the first cell of an index row. A link whose text is the number and whose
    target ends in `#<number>` is the one accepted form; the same number linked to an issue's file is refused, as is a
    link whose anchor names another number.
  - **A link whose anchor does not exist:** a link ending in `#<number shape>` to an index of this repository whose
    file has no such anchor; or to `https://github.com/sokar-ai/<repository>/blob/main/<path>#<number>` whose index,
    read from `https://raw.githubusercontent.com/sokar-ai/<repository>/main/<path>`, has none.
  - **A number with no row anywhere:** a number of a known prefix that no index read has a row for - an issue finished
    before rows were kept. Said apart, as "rewrite the text, it cannot be linked".
- Which prefixes are issue numbers: those of this repository's own issue files, and those of the rows in every index
  read - this repository's and the ones its links point at. A shape no index uses (`SHA256`, a guideline's `LLM03`) is
  not a number.
- Another repository's index is read once per run. One that cannot be read - not published yet, as `sokar-intellij`
  before its first push, or no network - is named in the output with the links that were not checked, and does not
  make the check fail. `--checkouts <dir>` reads `<dir>/<repository>/<path>` instead, for a run on a machine with the
  repositories beside each other.
- Red first in `CheckCitationsTest`, with the module's stand-in web (`FakeWeb`): a row without its anchor, a plain
  number, a number linked to its file, a link to a missing anchor here and there, a number with no row; and the forms
  that pass - a heading, a code span, a marked line, a link to a row.
- `doc/index.md`'s "What `check-citations` reads" says the new reading, the option, and what an unreadable index means.

**Push order.** Every repository's anchors and links first, then this check: before them, every unchanged repository
would turn red on its next push.

## Acceptance

- Each of the faults above is refused by name with its file and line, seen red first in a test.
- The twelve repositories' trees as of their anchored commits pass, each run with `--checkouts` on the host.
- A link into a repository whose index cannot be read is named and not failed.

Measured 2026-10-11 on the host, without `--checkouts`: the public repositories' indexes are read from
`raw.githubusercontent.com`; a private repository's (`sokar-build-github`) answers 404 there, as `sokar-intellij`'s
before its first push, so its links are named as not checked and do not fail the check. With `--checkouts ~/git` all
twelve trees as of that morning's anchored commits pass.
