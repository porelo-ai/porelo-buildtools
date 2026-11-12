# BT82 — The Commit Check Takes Its Range From The Event

```json
{
  "$schema": "issue-1.json",
  "id": "BT82",
  "title": "The Commit Check Takes Its Range From The Event",
  "state": "Idea",
  "size": "-",
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

## To be checked

- Whether the command reads the event from `GITHUB_EVENT_PATH` and the runner's variables itself, or takes each field
  as an option; the second keeps the unit tests free of files and the environment.
- How the shared workflows move: each repository's owner changes its own workflow once the command is released.
