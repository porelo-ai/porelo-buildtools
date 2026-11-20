# BT100 — The Skills Link Is Read As A Path On Windows

```json
{ "$schema": "issue-1.json", "id": "BT100", "title": "The Skills Link Is Read As A Path On Windows", "state": "Backlog",
  "size": "XS", "priority": "High", "rank": 1, "blockedBy": [],
  "covers": "Bug: check-shared compares the target of `.claude/skills` as a string, so on Windows, which gives it back with backslashes, a right link is refused." }
```

**Why this size.** One method of `SharedSkills` and its test; the command's contract is unchanged.

**What must be true.** `check-shared` passes a `.claude/skills` link to `../.agents/skills` on every platform, and
still refuses one that points anywhere else.

## Plan

- `SharedSkills.checkLink` compares `Files.readSymbolicLink(link).toString()` with `../.agents/skills`; Windows
  answers `..\.agents\skills`. The target is split by the link's own file system's separator and compared by its
  names, `..`, `.agents`, `skills`.
- Test first, red: a target with backslashes and the backslash as separator passes; one with backslashes on a
  file system whose separator is `/`, an absolute one and another directory are refused.

## Acceptance

- The new test is seen red on today's comparison and green after; the Windows job of a repository on the next
  snapshot runs check-shared green.
