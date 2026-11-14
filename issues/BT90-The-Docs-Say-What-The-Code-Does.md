# BT90 — The Docs Say What The Code Does

```json
{
  "$schema": "issue-1.json",
  "id": "BT90",
  "title": "The Docs Say What The Code Does",
  "state": "Review",
  "size": "M",
  "priority": "Highest",
  "rank": 1,
  "blockedBy": [],
  "covers": "Every sentence of the README, `doc/` but its decisions' history, and the changelog's latest section that names a command, flag, path, default, state, message or behavior is borne out by the code on `main`, or a bug is filed for the code."
}
```

**Why this size.** About 1,000 lines of user-facing text over four pages, the tools' reference alone 730, each claim
read against the commands and options it names; no behavior changes.

**What must be true.** A reader who follows the documents finds the commands, flags, paths, defaults, states and
messages they name. Where the document is wrong it says what the code does; where the code is wrong a bug stands in the
backlog, and the document is left as it is.

## Plan

- **The reading.** A Claude sub-agent on Sonnet (`claude-sonnet-5-5`) reads `README.md`, `doc/*.md` but
  `doc/decisions.md`, and the changelog's latest section page by page against the code on `main`, and lists each
  sentence the code does not bear out: the claim at file:line and the code at file:line that contradicts it. It runs no
  test suite.
- **The check.** Each finding is checked in the code by the lane, not the sub-agent; only a confirmed one is changed.
- **The change.** The document is corrected where the code is right; where the code is wrong, its own bug goes to the
  backlog, estimated.

## Acceptance

- One commit; the document tests, `check-commits`, `check-citations` and the local traces check green.
- The Push ready names the count: claims checked, confirmed, fixed, filed as bugs.

## As built

A Sonnet sub-agent read six pages of 599bd91 and checked about 370 claims against the code. It reported six findings,
and I confirmed all six in the code. The code was right each time, so the documents are fixed and no bug is filed:

- **The changelog's 0.5.2 section.** The section and its source files under `changelog/v0.5.2/` said two things the
  code does not do:
  - `jdk` tries eight times at most, over fifteen minutes of pauses, by `PinnedRetry`'s 15 to 300 s waits; the
    changelog said five.
  - `stories check` has no rule for a story in Review whose issues stand earlier. It refuses a completed story
    without a tested day by the workflow's closed, non-rejected states, and only warns where a story's stated state
    is not the one its issues give.
- **`doc/index.md`.** The table of `CheckActions.WRITES` now has its `release.yml` / `publish` / `contents` row.
- **`doc/publishing.md`.** A property's key takes `._-` and its value `._+~:@,-`, as `ArtifactoryCommand.PROPERTY`
  has them, not "the target's set less `=`".
- **`doc/pinned-downloads.md`.**
  - `jdk --github` adds `; JAVA_HOME and PATH set for the rest of the job` to its line.
  - The prune job's Maven prefixes are `maven-Linux-` and `maven-wrapper-Linux-`.
