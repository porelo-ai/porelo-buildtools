# BT56 — Sub-Agent Briefs Beside Their Issue

**Status:** Analyzed.
**Size:** S - one shape of path learnt by three checks of `porelo-release`, with their tests; no new command and no
contract another repository reads, beyond the path itself.

**What must be true.** An issue may keep the briefs of its proposed sub-agents in a directory beside it,
`issues/<group>/<ID>.sub/<name>.md` (`<name>` for instance `builder`, `tester`, `reader`). The shared checks treat a
brief as part of its issue, never as an issue of its own:
- the index reading takes no brief for an issue;
- `check-citations` holds a brief's text to the rules of a page under `issues/`, and treats it as a file that dies with
  its issue, so no lasting page may link to it;
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
    directory, and holds only regular `*.md` files whose name is `[a-z][a-z0-9-]*`. Anything else is a fault naming
    the path: a brief of a gone issue, a nested directory, a link.
- **`CheckCommits`:** the stats rule's issue path (line 382, `issues/(?:[^/]+/)*([A-Z]{1,3}\d{2,3})-[^/]+\.md`)
  already misses `…/B560.sub/builder.md`, since the last segment must start with the number. A test pins that a commit
  changing only a brief of an Implementing issue needs no stats file.
- **`IssueLinks`:** the index and its pages are read as today. A test pins that a brief under a group is read as a
  page, never as a row.
- **Tests that go red first:**
  - `CheckCitationsTest.aBriefWithoutItsIssueIsRefused`;
  - `aSubDirectoryBesideNoIssueIsRefused`;
  - `aLastingPageMayNotLinkIntoABrief`.

  The pins above are green at once and stay so.
- **Docs:** `doc/index.md` names the brief path under the citation rules; changelog under *Added*.

## Open questions

1. Whether a brief's own issue number must be a link to its row, as on every page under `issues/`. That is today's
   rule, which this plan keeps. The other choice exempts a brief's own number, since a brief always sits beside its
   issue.
2. Whether `<name>` is free, or limited to `builder`, `tester` and `reader` (and numbered variants such as `builder-2`).

## Acceptance

- The red-first tests above, seen red, then green; the pins green.
- In a copy of porelo-buildtools with a brief added beside [BT55](README.md#bt55): `check-citations` and `check-commits`
  are clean. With that issue's file deleted and the brief left, `check-citations` names the brief.
