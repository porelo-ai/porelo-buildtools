# BT91 — Check Shared Checks The Shared Skills

```json
{
  "$schema": "issue-1.json",
  "id": "BT91",
  "title": "Check Shared Checks The Shared Skills",
  "state": "Review",
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

- `check-shared` accepts a directory, `.agents/skills`, besides files, and checks a repository's `.agents/skills`
  even when the call does not name it, as it checks `pinned.sh`, so no workflow has to change for it. In it, every `SKILL.md` that holds a block
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
  directory's name and is lower-case letters and digits joined by single hyphens, `description` is present and at
  most 1024 characters, as agentskills.io specifies. The frontmatter is read a line per key, as the harnesses read
  it, not as strict YAML: every Porelo skill's description holds a colon and a blank, which strict YAML refuses.
- **Each repository is held to its own skills**: a check run from another directory, or from another repository,
  reads the skills beside the named `AGENTS.md`, and the skill tables there are held to those skills alone.
- **Nothing hides a skill from the check**: a shared skill holds only blank lines outside its frontmatter and its one
  block; a skill named in a shared table but carried without its block, a link where a skill, its `SKILL.md`,
  `.agents` or `.claude` should be, a skill directory without `SKILL.md`, a file beside the skills, a carriage
  return, a table row naming no skill in code, and a frontmatter line that is no `key: value` on one line are each refused. A `SKILL.md` named alone is checked as its
  skill.
- Exit code and output as today: non-zero and one line per refusal, naming the file.

## Plan

- `release/src/main/java/org/fuin/porelo/release/SharedSkills.java`, new: reads the skill tables out of a shared
  block's lines (`named`), checks a skills directory, each skill's frontmatter and block, and the `.claude/skills`
  link, and refuses each skill a table names that is not there whole (`present`). The hash is `CheckShared.hashOf`
  over the frontmatter lines followed by the lines between the markers.
- `CheckShared.java`: `check(List<Path>, Path)` takes a named directory, or a path ending in `.agents/skills`, as a
  skills directory and a named `SKILL.md` as its skills directory. It adds the `.agents/skills` of the root and of
  every repository whose `AGENTS.md` is named, and the `AGENTS.md` of every repository whose skills are checked. The
  skill tables of each `AGENTS.md` are held to that repository's own skills, never to those of the directory the
  check runs in.
- `Main.java`: the usage line names `check-shared [FILES|DIRECTORIES]`.
- `CheckSharedTest`: red first, an edited description of a shared skill passing today; then an unchanged copy, an
  edited body, a renamed directory, a skill named in the table but missing or carried as the repository's own,
  `.claude/skills` as a directory, a link elsewhere or missing, an own skill without `description` or with one over
  1024 characters, the shapes that could hide a skill, a line outside the block and a carriage return, a table row
  naming no skill, a `SKILL.md` named alone, and the skills checked unnamed, each with its own message, over a
  temporary tree. A test tagged `documents` checks this repository's own `AGENTS.md` and `.agents/skills`.
- `doc/index.md` and the changelog entry under `[Unreleased]` say what `check-shared` now checks.

## Acceptance

- The unit tests above pass and each refusal is seen to fail with its own message; `./mvnw -Pdocuments test` passes
  on this repository's own `AGENTS.md` and `.agents/skills`.
- `check-shared AGENTS.md .agents/skills` run against a checkout of another repository's `main` passes, and fails
  once one of its shared skills' descriptions is changed by hand.
