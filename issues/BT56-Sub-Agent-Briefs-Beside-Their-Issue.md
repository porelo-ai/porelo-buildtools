# BT56 — Sub-Agent Briefs Beside Their Issue

**Status:** Analyzed.
**Size:** M - was S for briefs as files beside an issue; the operator's decision of 2026-11-10 makes a sub-agent's
work a sub-issue with its own state, which the index reading, three checks and the stories count all learn, and the
brief files already on main go again.

**What must be true.** An issue may keep the briefs of its proposed sub-agents in a directory beside it,
`issues/<group>/<ID>.sub/<name>.md`, `<name>` one of `builder`, `tester` and `reader`, or a numbered variant such as
`builder-2`. The shared checks treat a brief as part of its issue, never as an issue of its own:
- the index reading takes no brief for an issue;
- `check-citations` holds a brief's text to the rules of a page under `issues/`, its own issue's number a link to that
  issue's row like every other, and treats it as a file that dies with its issue, so no lasting page may link to it;
- `check-commits`' stats rule does not ask for `stats/<name>.jsonl` because a brief changed.

A brief whose issue file is gone, or a `.sub` directory beside no issue, is refused, naming it. So briefs are deleted
together with their issue at Finished.

## Why

ST294's board shows an issue's proposed sub-agents in its Start dialog, and the adapter delivers each brief's text with
the issue. Briefs written as files beside the issue must not break the checks every repository runs, and must not
outlive their issue.

## Plan

Read in porelo-buildtools 5582aee, `release/src/main/java/org/fuin/porelo/release/`:

- **`CheckCitations`:**
  - `DIES` (line 48), the files that die with their subject, gains `/[A-Z]{1,3}\d{2,3}\.sub/[^/]+\.md$`. A
    lasting page's link into a brief is then refused as a link into an issue's file is.
  - `ISSUE_FILE` (line 62) matches on the file name alone and already does not take `builder.md`. A test pins that.
  - New check: every `<ID>.sub/` under the root's own `issues/` lies beside an issue file `<ID>-*.md` in the same
    directory, and holds only regular `*.md` files whose name is `(builder|tester|reader)(-[2-9]|-[1-9][0-9]+)?`. Anything else is a fault naming
    the path: a brief of a gone issue, a nested directory, a link.
- **`CheckCommits`:** the stats rule's issue path (line 382, `issues/(?:[^/]+/)*([A-Z]{1,3}\d{2,3})-[^/]+\.md`)
  already misses `…/B560.sub/builder.md`, since the last segment must start with the number. A test pins that a commit
  changing only a brief of an Implementing issue needs no stats file.
- **`IssueLinks`:** the index and its pages are read as today. A test pins that a brief under a group is read as a
  page, never as a row.
- **Tests that go red first:**
  - `CheckCitationsTest.aBriefWithoutItsIssueIsRefused`;
  - `aSubDirectoryBesideNoIssueIsRefused`;
  - `aLastingPageMayNotLinkIntoABrief`;
  - `aBriefOfAnotherNameIsRefused`;
  - `aBriefsOwnIssueNumberMustBeALink`.

  The pins above are green at once and stay so.
- **Docs:** `doc/index.md` names the brief path under the citation rules; changelog under *Added*.

## Acceptance

- The red-first tests above, seen red, then green; the pins green.
- In a copy of porelo-buildtools with a brief added beside [BT55](README.md#bt55): `check-citations` and `check-commits`
  are clean. With that issue's file deleted and the brief left, `check-citations` names the brief.

## Rework (the operator, 2026-11-10, ST311)

**What was found.** ST294 is closed; the planning moves into a new app, porelo-planner (ST311). Its store holds only
stories (the project repository) and issues with optional sub-issues (each repository). A sub-agent's work is a
sub-issue with its own state, and its text is the brief. There are no briefs as files of their own. The briefs-as-files
rules above are on main (2d73e6f, 574afbb) and go with this rework; the plan and acceptance above are kept only as the
record of what was built first.

**The sub-issue, as porelo-buildtools defines it:**
- **Id:** the parent's id, a dot and a number from 1, never reused within the parent: `B560.1`, `B560.2`. Its role
  (`builder`, `tester` or `reader`) is a field of its head, not part of the id.
- **Place:** a file beside its parent, in the same `issues/` directory or group: `<parent>.<n>-<Title-Words>.md`. It
  starts `# <id> — <title>`, then `**Status:**`, `**Role:**` and `**Parent:** [<parent>](README.md#<parent anchor>)`;
  the rest is the brief, written as an issue's text and held to the same citation rules.
- **Index row:** a row in the parent's index (`README.md` of the same directory), in the table of its own state, with
  the anchor `<id in lower case>` (`b560.1`) and a link to its file; columns as an issue's, the size `-`.
- **Lifetime:** it is finished or rejected on its own. A sub-issue whose parent's file and row are gone is refused,
  naming it. A parent may be Finished only when every sub-issue under it is Finished or Rejected; a Rejected
  sub-issue counts as done (the operator, 2026-11-10).

**How the checks and the stories count it:**
- **Index reading (`IssueIndexes`, `IssueLinks`):** the row pattern takes `<parent>.<n>` and gives each row its parent;
  a dotless id reads as today, so an index without sub-issues reads unchanged.
- **`check-citations`:** a sub-issue's number is a link to its row like an issue's; its file dies with it at Finished.
  The `.sub/` brief rules are removed.
- **`check-commits`:** a sub-issue's stages are recorded in its parent's `stats/<parent>.jsonl`, with `--actor
  subagent` (the operator, 2026-11-10); changing a sub-issue's file in a measured state needs that file, not one of
  its own.
- **Stories:** a story names parent issues only; a sub-issue named in a story's `issues` is refused, naming the
  parent to write instead. Sub-issues do not count in a story's state, nor in the index's count by prefix.
- **Docs:** `doc/index.md` says the format; the changelog says the briefs' rules removed and sub-issues added.
- **Red first:** a sub-issue row read with its parent; a sub-issue without its parent refused; a parent Finished with
  a sub-issue neither Finished nor Rejected refused, and with one Rejected accepted; a story naming a sub-issue
  refused; a sub-issue's change satisfied by its parent's stats; every index without a dot read as today.
