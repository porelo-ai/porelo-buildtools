# BT82 — The Commit Check Takes Its Range From The Event

```json
{
  "$schema": "issue-1.json",
  "id": "BT82",
  "title": "The Commit Check Takes Its Range From The Event",
  "state": "Estimated",
  "size": "M",
  "priority": "Medium",
  "rank": 1,
  "blockedBy": [],
  "covers": "The test pyramid (ST296) for the workflows' shared shell: `check-commits` decides a push's range and a pull request's origin from the event itself, and `check-workflows` checks that every workflow loads with a trigger and a job, each unit-tested, so a workflow only calls a command."
}
```

**What must be true.** The shell every Porelo repository repeats in its workflows is a command of `porelo-release`,
unit-tested over the event's fields, and a workflow only calls it:

- **A push's range.** `check-commits` takes the event's name, ref, ref type, `before` and `sha` and decides the range:
  no `before` is an error; a tag checks `sha~1..sha`; a start the checkout does not hold is an error on `main` and
  `origin/main` elsewhere. Today `build.yml` of every repository decides this in shell.
- **A pull request's origin.** For a pull request it takes base, head, the head's repository, the author's
  association, the author and the server, and decides `--outside` itself: inside when it comes from the same
  repository by an `OWNER` or `MEMBER`, from `github-actions[bot]`, or on a server other than github.com
  ([BT36](README.md#bt36)); outside otherwise. Today `shared-rules.yml` decides this in shell.
- **Every workflow loads.** A command `check-workflows`, beside `check-actions`, refuses a workflow file that does not
  load as YAML or names no trigger or no job. Today `workflows.yml` runs this as inline Python.

## Why

Asked by Agent Adapters for porelo-adapters' GH23 (its test pyramid, ST296), 2026-11-12: the same shell sits in every
repository's workflows, untested, and each copy is changed by hand. Moved into `porelo-release`, each rule is a unit
test, and the commands keep [BT79](README.md#bt79)'s rule that a commit's own words earn no exemption.

## Plan

Read in porelo-buildtools 727a1cc: `check-commits RANGE [--outside]` (`Main.checkCommits`), the shell in
`.github/workflows/build.yml` (the push range) and `shared-rules.yml` (the pull request's origin), and the Python in
`workflows.yml`.

- **Where the fields come from (decided).** `check-commits --event` takes no range: it reads `GITHUB_EVENT_NAME`,
  `GITHUB_REF`, `GITHUB_REF_TYPE`, `GITHUB_SHA`, `GITHUB_REPOSITORY` and `GITHUB_SERVER_URL` from the environment and
  `before`, or the pull request's base, head, head repository, author association and author, from the event file
  `GITHUB_EVENT_PATH`, which GitHub and Forgejo both write. A workflow then passes nothing and cannot pass a field
  wrongly. The unit tests stay free of files and the environment because reading and deciding are apart: `Main` already
  hands the environment in, and the reader turns the file's text into a record.
- **The decision (S).** A pure `CommitRange.decide(event, holds)` over that record and one question to git, whether the
  checkout holds a commit, gives the range and `--outside`, or the error the shell gives today. Cases in
  `CommitRangeTest`, each seen red with its rule removed: no `before`; a tag (`sha~1..sha`); a start not held, on `main`
  (error) and elsewhere (`origin/main`); a pull request from the same repository by `OWNER`, `MEMBER`, `CONTRIBUTOR`, by
  `github-actions[bot]`, from another repository, and on a server other than github.com with an empty association
  ([BT36](README.md#bt36)); an event that is neither a push nor a pull request is refused.
- **The reader (XS).** `GitHubEvent.read(environment, text)` parses with the Jackson already on the class path; cases
  for a push and a pull request payload as strings, and for a missing field named in the error. One narrow test in
  `CheckCommitsTest` runs `--event` on a temporary repository with an event file.
- **`check-workflows` (S).** The `workflows.yml` Python as a command beside `check-actions`: every
  `.github/workflows/*.y*ml` loads, names a trigger and at least one job. Jackson's YAML reads `on` as the key `on`,
  where PyYAML read `true`; both spellings are tested. Cases over strings in `CheckWorkflowsTest`; listed in
  `DocumentTestsTaggedTest` where it reads the tree.
- **Here (S).** `build.yml`, `shared-rules.yml` and `workflows.yml` of this repository call the commands instead of the
  shell; usage, `release/README.md` and one changelog entry. The other 18 repositories that carry the same shell (every
  Porelo repository but porelo-org, porelo-website and, in part, porelo-parent and porelo-project) move through issues
  of their own owners once the command is released.
- **Order and size.** Reader, decision, `check-commits --event`, `check-workflows`, this repository's workflows; S 27 +
  XS 8 + S 27 + S 27, about 89 min, so M. [BT79](README.md#bt79) also changes `CheckCommits`; whichever comes second
  rebases on the first, without a wait between them.

## Decided by the operator (2026-11-12)

- **The fields come from the event.** `check-commits --event` reads `GITHUB_EVENT_PATH` and the runner's `GITHUB_*`
  variables; no field is an option. Plan and size stay: M, about 89 min.

## To be checked

- How the shared workflows move: each repository's owner changes its own workflow once the command is released.
