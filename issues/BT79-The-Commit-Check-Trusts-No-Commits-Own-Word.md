# BT79 — The Commit Check Trusts No Commit's Own Word

```json
{
  "$schema": "issue-1.json",
  "id": "BT79",
  "title": "The Commit Check Trusts No Commit's Own Word",
  "state": "Backlog",
  "size": "S",
  "priority": "High",
  "rank": 1,
  "blockedBy": [],
  "covers": "`check-commits` gives no exemption, no trailer and no switch-off of the stage rule to whoever writes a commit: no bot exemption by a recorded name for a pull request from outside, trailers read for numbers and names, no `Co-authored-by:`, a deleted seed or stage record refused, and the log's records counted against git's own count."
}
```

**Size S.** One class, `CheckCommits` in `release`, and its tests; the command's arguments and exit codes stay.

**What must be true.** Nobody who writes a commit can make `check-commits` skip a rule by what the commit says of
itself, for as long as porelo-buildtools carries the check, and it holds the same rules as the planner's commit check.

- A pull request from outside (`--outside`) gets no exemption: every commit is held to every rule, its author's
  `Signed-off-by:` among them. Today `dependabot[bot]` or `github-actions[bot]` as author, or `GitHub` as committer,
  skips every rule there, and anyone can write those names into a commit. Without `--outside` (a push, or a pull
  request from a branch of the repository itself, Dependabot's among them) the exemption by those names stays, since
  that writer may push anyway.
- Every trailer's value is read for a requirement number, and every trailer but `Signed-off-by:`, which names its
  author by design, for who asked or made a change.
- `Co-authored-by:` is not an allowed trailer, since the shared rules say an agent never writes one.
- A commit whose first parent's tree holds `stats/seed.csv` or a `stats/<id>.jsonl` its own tree lacks is refused,
  naming each file it deletes; deleting them no longer switches the stage rule off for that commit or for a later one
  that brings the seed back.
- The log is split into exactly as many commits as `git rev-list --count` names for the range; when the separators do
  not add up, the check gives no answer (exit 2), so a message holding the record and field separators cannot forge a
  second commit or hide its own body.

## Plan

- `CheckCommits.check(range, outside)`: `Commit.exempt()` counts only when `outside` is false.
- `faults`: every trailer's value goes into the words searched by `NUMBER`, every trailer but `Signed-off-by:` into
  those searched by `Who.named`; `Co-authored-by` leaves `TRAILER`.
- `stageFaults`: read the first parent's tree too; a seed or a `stats/<id>.jsonl` it holds and the commit lacks is a
  fault naming the file; a commit that changes nothing under `stats/` still passes.
- `parse`: take the count from `git rev-list --count <range>` through the same git seam, and refuse a log whose records
  differ from it.
- Tests in `CheckCommitsTest`, each red first against today's code: with `--outside`, a commit of author
  `dependabot[bot]` and one of committer `GitHub`, with a requirement number and no sign-off, are refused; a number in
  `Assisted-by:` is refused; `Co-authored-by:` is refused; deleting the seed is refused, and so is deleting one stage
  record alone; a message with a record and five field separators makes the check stop with exit 2.

## Acceptance

- The five tests above go red on today's code and green after; the module's suite and the full build pass.
- `doc/index.md`'s section on `check-commits` states the rules as they then are, with a changelog entry.

## To be checked

- Whether a stage record is ever deleted on purpose (a rejected issue's), and if so, which commit may do it.
- That `check-commits` on this repository's own history since the stage rule's rollout still passes.
