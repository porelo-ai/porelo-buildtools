# BT47 — Check Commits Takes Porelo's Own Trailers

```json
{
  "$schema": "issue-1.json",
  "id": "BT47",
  "title": "Check Commits Takes Porelo's Own Trailers",
  "state": "Review",
  "size": "XS",
  "priority": "Medium",
  "rank": 14,
  "blockedBy": [],
  "covers": "`check-commits` takes `Porelo-Change-Id` and `Porelo-Signs-Anew`, each in its own form, which Porelo writes on its own commits; due before Porelo's next commit is pushed."
}
```

**Why this size.** one pattern in `check-commits`, its tests and a changelog entry.

**What must be true.** `porelo-release check-commits` takes the trailers Porelo writes on the commits it makes
itself: `Porelo-Change-Id: <id>` on each, and `Porelo-Signs-Anew: <path>` on a commit that signs a file anew. Each
is taken only in its own form, so a Build that pushes such a commit is not red. Every other key still is refused as
today.

## Why

Porelo's organization tree (`TreeRepository` in porelo `organization/git-tree`) commits with `git commit-tree -S`
and the message `<subject>`, a blank line, `Porelo-Change-Id: <id>`, and, when it signs anew,
`Porelo-Signs-Anew: <path>`. `check-commits` takes only `Assisted-by`, `Signed-off-by` and `Co-authored-by` below
the subject, so it refuses both lines. This was seen on 2026-11-08, read only, on the signing commits of 2026-11-07
in porelo-claude-code (`aaf3bb7`), porelo-pi (`602f824`) and porelo-omp (`21f8588`): two faults each, with 0.5.1 as
with 0.5.2-SNAPSHOT. They are outside every push range checked since. The next commit Porelo makes in a repository
whose Build runs `check-commits` turns that Build red.

## The shape

- `CheckCommits.TRAILER` gains the two keys, each with its value's form:
  - `Porelo-Change-Id`: one token, no space;
  - `Porelo-Signs-Anew`: one repository path, relative, no `..`, no space.
- A key in any case is taken, as git reads one. A wrong value is refused, naming the line.
- Porelo's commit carries no `Assisted-by`, and `check-commits` asks for none today, so nothing else changes.
- Tests:
  - the message exactly as `TreeRepository` writes it, with and without `Porelo-Signs-Anew`, passes;
  - a malformed value fails;
  - an unknown `Porelo-` key still fails.
- A changelog entry. The commit rule in `AGENTS.md`'s Shared Area is left as it is: the area is held the same in
  every repository, so a sentence naming the two keys goes to its keeper and lands everywhere at once.

## Size

XS: one pattern, its tests and a changelog entry.

## Done

- `CheckCommits.TRAILER` takes `Porelo-Change-Id: <one token>` and `Porelo-Signs-Anew: <path>`; keys in any case.
  - It refuses a path that starts with `/`, holds a `..` part, or has a character other than letters, digits and
    `. _ - /` (a backslash included).
  - It refuses a value with a space, and any other `Porelo-` key.
- `CheckCommitsTest`:
  - Porelo's messages exactly as `TreeRepository` writes them pass. This was seen red first.
  - Five values outside their form are refused, each named.
  - Six paths that leave the repository are refused, a `..` in the middle and at the end and a backslash among them.
    The pattern broken on purpose, without its `.*/` or its end case, turns them red.

## Due

Before Porelo's next commit is pushed in any repository whose Build runs `check-commits`. The repositories then take
it with the next snapshot of the tools through their parent.
