# BT47 — Check Commits Takes Porelo's Own Trailers

**Status:** Idea.

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
- A changelog entry, and the commit rule in `AGENTS.md`'s shared block names the two keys as Porelo's own.

## Size

XS: one pattern, its tests, a changelog entry and a sentence in the shared rules.

## Due

Before Porelo's next commit is pushed in any repository whose Build runs `check-commits`. The repositories then take
it with the next snapshot of the tools through their parent.
