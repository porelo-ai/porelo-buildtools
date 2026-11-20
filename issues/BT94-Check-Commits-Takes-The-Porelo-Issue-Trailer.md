# BT94 — Check Commits Takes The Porelo-Issue Trailer

```json
{
  "$schema": "issue-1.json",
  "id": "BT94",
  "title": "Check Commits Takes The Porelo-Issue Trailer",
  "state": "Backlog",
  "size": "S",
  "priority": "High",
  "rank": 1,
  "blockedBy": ["BT92"],
  "covers": "From the cut-over day `check-commits` takes a `Porelo-Issue: <tracker reference>` trailer, one per issue, holds it to GitHub's reference form, still refuses an issue number in the subject, and refuses a closing keyword followed by an issue reference anywhere in the message; until then nothing changes."
}
```

**Why this size.** One class and its tests, the documents and a changelog entry: S. The form of the trailer is
decided, so no contract is designed here. It waits for [BT92](README.md#bt92), which moves the number rule off
`issues/` and adds the GitHub number to it; this issue builds on that rule, and both are built on the cut-over day.

**What must be true.** From the cut-over day every commit made for an issue names it in a trailer. The tracker then
links the commit to the issue without the subject citing a number, and GitHub never closes the issue, since only the
operator sets Finished.

- **The trailer.** `Porelo-Issue: <tracker reference>`, one line per issue, below the subject with the other
  trailers. For GitHub the reference is `<owner>/<repository>#<number>`, as in `Porelo-Issue: porelo-ai/porelo#123`.
  The form leaves room for another tracker's reference later; today only GitHub's is accepted. A `Porelo-Issue` line
  of any other form is refused, naming the line.
- **The subject stays in words.** An issue number in the subject, an old id or a GitHub number, is still refused. The
  `Porelo-Issue` trailer is the one place a number may stand.
- **No closing keyword.** `close`, `closes`, `closed`, `fix`, `fixes`, `fixed`, `resolve`, `resolves` or `resolved`
  followed by an issue reference, as GitHub reads one (`#12`, `porelo-ai/porelo#12` or an issue's URL), is refused
  anywhere in the message. The bare word stays allowed: 83 subjects on the `main` of porelo, porelo-buildtools,
  porelo-host and porelo-planner use it in prose, such as "Fix the pin".
- **Until the cut-over, nothing changes**: a `Porelo-Issue` line is refused as a trailer of another key, as today.

## Plan

- `release/src/main/java/org/fuin/porelo/release/CheckCommits.java`: a pattern `PORELO_ISSUE`,
  `Porelo-Issue: <reference>`, its key in any case as the other trailers, the reference GitHub's
  `<owner>/<repository>#<number>` (owner and repository as GitHub names them, the number from 1). `TRAILER` takes it as
  one more key; a line whose key is `Porelo-Issue` but whose value is of another form gets its own fault, naming the
  line, instead of "a line that is not a trailer".
- `faults`: the number rule reads every trailer but `Porelo-Issue`, as `Who` reads every trailer but the sign-off.
  A pattern `CLOSING` (the nine keywords, case-insensitive, an optional colon, then `#<n>`, `<owner>/<repository>#<n>`
  or `https://github.com/<owner>/<repository>/issues/<n>`) is matched over the whole message, trailers included, and
  each hit is a fault naming it.
- `CheckCommitsTest`: red first, a commit carrying `Porelo-Issue: porelo-ai/porelo#123` is refused today. Then: two
  such trailers pass; `Porelo-Issue: #123`, `Porelo-Issue: porelo-ai/porelo` and a trailing word are refused with the
  trailer's own message; `Fixes #12`, `closes porelo-ai/porelo#12` and `Resolved: https://github.com/o/r/issues/12`
  are refused; "Fix the pin" passes; `#12` in the subject is still refused.
- `doc/index.md`'s section on `check-commits` and a changelog entry under *Changed*.

## Acceptance

- The tests above pass, each refusal seen to fail first with its own message.
- `check-commits` over the last 200 commits of porelo, porelo-buildtools, porelo-host and porelo-planner refuses no
  commit the current version passes, so the closing-keyword rule hits no subject in use today.
- Before the cut-over: nothing of this is merged.
