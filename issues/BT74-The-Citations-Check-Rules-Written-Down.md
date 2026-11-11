# BT74 — The Citations Check Rules Written Down

```json
{
  "$schema": "issue-1.json",
  "id": "BT74",
  "title": "The Citations Check Rules Written Down",
  "state": "Idea",
  "size": "-",
  "priority": "Medium",
  "rank": 28,
  "blockedBy": [],
  "covers": "What `porelo-release check-citations` refuses today, rule by rule, with what it reads and what it skips, written down for ST321 (porelo-project) before anything of it is removed."
}
```

**Why.** It is part of ST321 (porelo-project), "The citations check dropped". The check is dropped as a rule. This issue
only records what it held, so the decision and any later text rules can be read against it. Nothing is removed here.

## What it reads
- Every committed text file of one repository: in a git checkout what `git ls-files` names, in an exported tree
  every file. A link is listed as an entry.
- **Skipped:**
  - binary files (a NUL byte, or bytes that are not UTF-8);
  - files under `target/`, `build/`, `.git/`, `.idea/`, `.dart_tool/`, `node_modules/` or `.gradle/`;
  - files no person writes: `package-lock.json`, `*.lock`, `*.svg` and `stats/*.jsonl`.
- Nothing outside the repository is read. A link into another repository's index is noted and not checked.
- It refuses a root with no Markdown page at all.

## What it refuses
An issue number has the shape of one to three capital letters, then two or three digits (`[A-Z]{1,3}\d{2,3}`).
1. **A link to an issue's file from a page that is not an issues index.** Link to the index and name the number,
   because a finished issue takes its file with it. "Files that die" are `issues/<ID>-*.md`, `*_design.md` and
   anything under `<ID>.sub/`.
2. **A pointer to an issue that no longer exists**: a number with `[index](…)` beside it whose issue is gone.
3. **A number of this repository's own issues, among the issues, that names no issue file**: the mention a deletion
   missed.
4. **A sub-agent brief out of place.** Briefs lie in `<ID>.sub/` beside the issue's own file and are named
   `builder`, `tester`, `reader` or `builder-2` and on. Each is a plain `.md` file, not a link.
5. **An issue number outside the issues.** Name the constraint, not the issue that recorded it.
6. **An index row that does not open with the anchor of its number**, `<a id="<number in lower case>"></a>`.
7. **An issue number under `issues/` that is not a link to its row.** Write `[<NUMBER>](<index>#<number>)`, never a
   link to the file.
8. **A link to an index row that is not there**, or that names another number.
9. **A number no index has a row for.**
10. **An issue head's waits and parent** (`blockedBy`, parent) that name no issue.
11. **An exemption for a file that is not there.**

## What lets a number stand
- A whole file named in `citations-exempt.txt` at the root. `#` starts a comment.
- A line that says `not-a-citation` in its own text, such as test data for something that reads issue numbers.
- A linter's codes on the line they silence (`# noqa: E402`).
- `S256`, PKCE's challenge method.
- The issues index, which may name files, since naming them is what an index is. Issues may name each other's files
  and numbers, since they are deleted together.

## Where it runs today
- The workflows of 16 repositories (beside `check-shared` and `check-actions`).
- The Coordinator's prepush and chkpush, until 14:23Z on 2026-11-11, when they stopped.
- Agents run it locally before a push.
