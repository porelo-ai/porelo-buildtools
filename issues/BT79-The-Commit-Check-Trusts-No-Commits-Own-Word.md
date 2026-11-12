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
  "covers": "`check-commits` gives no exemption, no trailer and no switch-off of the stage rule to whoever writes a commit: no bot exemption by a recorded name, trailers read for numbers and names, no `Co-authored-by:`, a deleted seed or stage record refused, and the log's records counted against git's own count."
}
```

**Size S.** One class, `CheckCommits` in `release`, and its tests; the command's arguments and exit codes stay.

**What must be true.** Nobody who writes a commit can make `check-commits` skip a rule by what the commit says of
itself, for as long as porelo-buildtools carries the check, and it holds the same rules as the planner's commit check.

- The exemption is never taken from a name the commit records. Today `dependabot[bot]` or `github-actions[bot]` as
  author, or `GitHub` as committer, skips every rule, a pull request from outside's `Signed-off-by:` among them, and
  anyone can write those names into a commit.
- The trailers are read for a requirement number and for who asked or made a change, as the lines above them are.
- `Co-authored-by:` is not an allowed trailer, since the shared rules say an agent never writes one.
- A commit that deletes `stats/seed.csv`, or a stage record its parent holds, is refused; deleting them no longer
  switches the stage rule off for that commit or for a later one that brings the seed back.
- The log is split into exactly as many commits as `git rev-list --count` names for the range; when the separators do
  not add up, the check gives no answer (exit 2), so a message holding the record and field separators cannot forge a
  second commit or hide its own body.

## Plan

- `CheckCommits`: drop `EXEMPT_AUTHORS`, `WEB_COMMITTER` and `Commit.exempt()`; what replaces the bots' exemption is
  the one the planner takes for the same gap, so both checks stay alike (see *To be checked*).
- `faults`: the trailer lines go into the words searched by `NUMBER` and `Who.named`; `Co-authored-by` leaves
  `TRAILER`.
- `stageFaults`: read the parent's tree too; a seed or a `stats/*.jsonl` the parent has and the commit lacks is a fault,
  and the rule applies whenever the parent or the commit holds a seed.
- `parse`: take the count from `git rev-list --count <range>` through the same git seam, and refuse a log whose records
  differ from it.
- Tests in `CheckCommitsTest`, each red first against today's code: a commit authored as `dependabot[bot]` with a
  requirement number is refused; a number in a trailer is refused; `Co-authored-by:` is refused; deleting the seed and
  bringing it back with a measured issue is refused, and so is deleting one stage record; a message with a record and
  five field separators makes the check stop with exit 2.

## Acceptance

- The five tests above go red on today's code and green after; the module's suite and the full build pass.
- `doc/index.md`'s section on `check-commits` states the rules as they then are, with a changelog entry.

## To be checked

- How the planner replaces the bots' exemption (a list of Dependabot's own signed commits by the caller, or no
  exemption at all), asked of Agent Planner, so the two checks stay alike.
- Which repositories' `main` today holds a Dependabot or web-interface commit the stricter check would refuse in a
  range their workflows check.
