# BT94 — Check Commits Takes The Porelo-Issue Trailer

```json
{
  "$schema": "issue-1.json",
  "id": "BT94",
  "title": "Check Commits Takes The Porelo-Issue Trailer",
  "state": "Accepted",
  "size": "-",
  "priority": "High",
  "rank": 1,
  "blockedBy": [],
  "covers": "From the cut-over day `check-commits` takes a `Porelo-Issue: <tracker reference>` trailer, one per issue, holds it to GitHub's reference form, still refuses an issue number in the subject, and refuses a closing keyword followed by an issue reference anywhere in the message; until then nothing changes."
}
```

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
