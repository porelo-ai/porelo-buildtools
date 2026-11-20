# BT91 — Check Shared Checks The Shared Skills

```json
{
  "$schema": "issue-1.json",
  "id": "BT91",
  "title": "Check Shared Checks The Shared Skills",
  "state": "Backlog",
  "size": "M",
  "priority": "Medium",
  "rank": 2,
  "blockedBy": [],
  "covers": "`check-shared` takes `.agents/skills`: each shared skill's block hashed with its frontmatter, its name equal to its directory's and its frontmatter's, every skill the shared block's skill table names present, `.claude/skills` a link to `../.agents/skills`, and a repository's own skill held to the agentskills.io frontmatter."
}
```

**Why this size.** One class and its tests, but its call is a contract every repository's workflow reads, and the
documents and changelog change with it: M.

**What must be true.** `check-shared` holds the shared skills to their copies as it holds the shared blocks today, so
a skill edited in one repository alone, a shared skill missing, or a skills directory Claude Code cannot find is
refused before it is pushed.

- `check-shared` accepts a directory, `.agents/skills`, besides files. In it, every `SKILL.md` that holds a block
  `Shared Skill <name>` or the adapter group's `adapter-building` is checked like a shared block, with one
  difference: **the hash of a `SKILL.md` covers its frontmatter too** (the lines between the opening and closing
  `---`, each ending in a newline), followed by the lines strictly between its markers. A copy whose description was
  edited on its own is refused as one whose rules were.
- **The block's name is the skill's**: `Shared Skill <name>` equals both the directory's name and the frontmatter's
  `name`; a mismatch is refused, naming both.
- **No shared skill is missing**: every skill the `Which skill to load` table of the shared block in `AGENTS.md`
  names exists as `.agents/skills/<name>/SKILL.md` with its block; one named but absent is refused. The table lies
  inside the hashed block, so the list itself is shared. The adapter group's table works the same way for
  `adapter-building` in the repositories that carry the Agent Adapter Area.
- **`.claude/skills` is a symbolic link to `../.agents/skills`** wherever `.agents/skills` exists; a directory, a
  copy or a link elsewhere is refused.
- A skill of the repository's own (no `Shared Skill` block) is checked only for its frontmatter: `name` equals the
  directory's name, `description` is present and at most 1024 characters, as agentskills.io specifies.
- Exit code and output as today: non-zero and one line per refusal, naming the file.

## Plan

- `release/src/main/java/org/fuin/porelo/release/CheckShared.java`: `check(List<Path>, Path)` expands a named
  directory into its `*/SKILL.md` files; a new `checkSkill` splits the frontmatter off, checks `name` and
  `description`, and for a `Shared Skill` block hashes frontmatter lines plus the lines between the markers with the
  existing `hashOf`; the skill table is read from the `Which skill to load` table inside the checked shared block of
  `AGENTS.md`, and `.claude/skills` is checked with `Files.readSymbolicLink` beside the directory.
- `Main.java`: the usage line names `check-shared [FILES|DIRECTORIES]`.
- `CheckSharedTest`: red first, an edited description of a shared skill passing today; then an unchanged copy, an
  edited body, a renamed directory, a skill named in the table but missing, `.claude/skills` as a directory and an
  own skill without `description`, each with its own message, over a temporary tree. The test tagged `documents`
  that checks this repository's own rules passes `.agents/skills` too.
- `doc/index.md` and the changelog entry under `[Unreleased]` say what `check-shared` now checks.

## Acceptance

- The unit tests above pass and each refusal is seen to fail with its own message; `./mvnw -Pdocuments test` passes
  on this repository's own `AGENTS.md` and `.agents/skills`.
- `check-shared AGENTS.md .agents/skills` run against a checkout of another repository's `main` passes, and fails
  once one of its shared skills' descriptions is changed by hand.

## To be checked

- The exact form of the adapter group's table and its `adapter-building` block, read in a repository that carries
  the Agent Adapter Area.
- Whether the coordinator's `rehash.sh` computes the skill hash the same way (frontmatter, then the lines between
  the markers), by running it on one skill and comparing.
