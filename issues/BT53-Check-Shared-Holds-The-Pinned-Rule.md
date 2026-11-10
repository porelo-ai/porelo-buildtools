# BT53 — Check Shared Holds The Pinned Rule

```json
{
  "$schema": "issue-1.json",
  "id": "BT53",
  "title": "Check Shared Holds The Pinned Rule",
  "state": "Review",
  "size": "XS",
  "priority": "Medium",
  "rank": 9,
  "blockedBy": [],
  "covers": "`check-shared` refuses a repository's `pinned.sh` that differs from porelo-buildtools' own, named in the call or not."
}
```

**Why this size.** `CheckShared` and its tests, `pinned.sh` carried in porelo-release, the doc.

**What must be true.** `check-shared` refuses a repository's `.github/actions/pinned-jdk/pinned.sh` that differs from
the one porelo-buildtools' own action runs, whether the call names it or not. It refuses one that is named but
missing.

## Why

The Shared Area names `pinned.sh` as the one helper a repository copies unchanged from porelo-buildtools, since its
step runs before there is Java, and says `check-shared` refuses a copy that differs. Until now nothing compared the
copies, and fourteen repositories hold one.

## The shape

- `pinned.sh` is carried on porelo-release's class path as `org/fuin/porelo/release/pinned.sh`, taken from
  `.github/actions/pinned-jdk/pinned.sh` when the jar is built, as the shared `.editorconfig` is. A release so carries
  the rule it was built with.
- `CheckShared` compares a file whose path ends in `.github/actions/pinned-jdk/pinned.sh` whole, line for line, as it
  compares `.editorconfig`. A named one that is missing is refused.
- A repository that holds `.github/actions/pinned-jdk/pinned.sh` at its root has it compared even when the call does
  not name it, so every repository's existing `check-shared` call holds the rule. The root is the working directory,
  where the shared-rules workflow runs it.
- A refusal names the file, the first line that differs and both texts, as for `.editorconfig`.
- Each file is checked once, however its path is written: paths are compared absolute and normalized.
- A link in place of either shared file is refused, dangling or not: the rule asks for a copy, which a checkout of the
  repository alone holds.

## Reading

Read once by agy `gemini-4-argon-high` (530 s), with six findings, all taken:
1. (grave) A test kept the first call's output when it read the second's. Now the buffer is reset, and each line is
   counted once.
2. (grave) A link was followed, and a dangling one went unnoticed. A link is refused now, with a test.
3. Paths compared as written: a roundabout path was checked twice, and naming another directory's copy skipped the
   root's own. Now they are compared absolute and normalized, with tests.
4. This repository's own copy was not checked by the documents tests. It is now, from the root, tagged `documents`.
5. The difference's line and both texts were not asserted. They are now.
6. `sharedEditorconfig()` had no caller left. It is removed.

Two mutations, the link check and the single count taken out, each turn one test red.

## Tests

`CheckSharedTest`, which does not compile against the code before:
- the shared copy, named or not, is accepted;
- a copy that differs is refused when not named, with the line;
- a named copy that is missing is refused;
- a repository without one is left alone;
- a path written roundabout is one file, and naming another directory's copy still checks the root's;
- a link is refused, dangling or not;
- the copy porelo-release carries is porelo-buildtools' own, and this repository passes from its root.

## Acceptance

- `check-shared AGENTS.md .editorconfig` in a repository whose `pinned.sh` differs from porelo-buildtools' main is
  red, and green once it is copied again.
- verify and the documents tests pass.
