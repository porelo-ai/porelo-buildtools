# BT97 — The Issue And Story Tools Leave With The Markdown Store

```json
{
  "$schema": "issue-1.json",
  "id": "BT97",
  "title": "The Issue And Story Tools Leave With The Markdown Store",
  "state": "Analyzed",
  "size": "M",
  "priority": "High",
  "rank": 3,
  "blockedBy": ["BT96"],
  "covers": "`porelo-release issues` and `stories`, the index-row and issue-link rules of `check-citations`, the `issues/` reading of `check-commits` and the tracker lookup go, since no project keeps issues or stories in Markdown. Built after the carry-over has read the Markdown store."
}
```

**Why this size.** It removes two commands other repositories call and the parts of two shared checks that read
`issues/`, with documentation and this repository's workflow step: a contract change, mostly removal, all in
`release/`.

**What must be true.** Porelo keeps stories and issues only in a tracker. So no tool here reads, writes or checks
`issues/` or `stories/`, and the rule stays that code and anything that ships cite no issue number. Every commit and
file is held to that rule the one way that works without an index: an issue number is told by its shape or as a
GitHub reference.

## Plan

It is built only after the carry-over has read the Markdown store once, as a step of its own, and after BT96, since
both change `CheckCommits` and `Main`.

- **`issues` and `stories` go:** `Issues.java`, `Stories.java`, their cases and usage lines in `Main`, and their
  names in its list of commands. Their tests go too: `IssuesTest`, `StoriesTest`, `StoriesIndexTest`,
  `IssueIndexFixturesTest`, and the `issue-indexes` resources with `issue-index-fingerprints.json`.
- **`check-citations` keeps only what holds without an index:** a file that ships cites no number by `SHAPE` or
  `GITHUB_REFERENCE`, as it does now in a tracked project. `IssueLinks`, `ProjectFile` and the index-row, anchor,
  file-link and row-less-number rules go. So does `issuePrefixes` with its reading of `issues/` and `project.yml`'s
  `issues` prefixes, and `CheckProviders`' exemption for `issues/`.
- **`check-commits` has one mode, today's tracked one:** a subject's number is told by its shape or as a GitHub
  reference (`BARE_REFERENCE` included). The `Prefixes` interface, its reading of `issues/` and the `tracked` flag
  go. `check-pushes` follows unchanged, since `Main` wires it to `check-commits` per push.
- **`Tracker` goes**, with `Main.unlessTracked` and the read of porelo-project's `project.yml` from GitHub. Nothing
  needs to tell the two stores apart any more, so these checks no longer stop when GitHub cannot be reached.
- **This repository:** `issues/` goes, and so does the step "The issue heads" in `.github/workflows/shared-rules.yml`.
  The section "Where the issues are kept" and the index-citation rules go from `doc/index.md`, replaced by one
  paragraph on the shape rule. One changelog entry under `[Unreleased]`, *Removed*.

Red first: a `CheckCitationsTest` case where a repository with an `issues/` index row and a link to it fails on the
number's shape in a shipped file, as a tracked project does today, where the Markdown mode lets the linked number
pass. A `CheckCommitsTest` case where a subject naming another repository's prefix is refused with no
`project.yml` above. Each is seen red against today's code.

## Acceptance

- The two tests above, seen red first, and the full build green.
- On a scratch clone with no `project.yml` and the network cut, `check-commits` and `check-citations` answer without
  reaching GitHub. `porelo-release issues check` and `porelo-release stories check` answer as unknown commands.

## To be checked

- Which other repositories' workflows still call `issues check`, `issues index`, `stories check` or `stories cut`.
  Each owner drops the step before this is published, since an unknown command turns that step red. The coordinator
  gathers the list.
