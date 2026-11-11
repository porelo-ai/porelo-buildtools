# BT70 — The Release Plan Commands Removed

```json
{
  "$schema": "issue-1.json",
  "id": "BT70",
  "title": "The Release Plan Commands Removed",
  "state": "Estimated",
  "size": "S",
  "priority": "Medium",
  "rank": 24,
  "blockedBy": [],
  "covers": "porelo-release has no `plan` command any more: Plan, PlanCheck, PlanRender, PlanStand and PlanTemplate, their tests, documentation and changelog entries are deleted, not moved."
}
```

**Why.** Part of ST320 (porelo-project). The release plans were dropped, so the `plan` commands have no work left.

**Who calls it today.**
- No workflow of any porelo repository calls `plan`.
- The one caller is the Coordinator's own `plan-render.sh` (`plan render plan/<release>/plan.yml`), last changed 2026-10-28. It is retired first, by the Coordinator.

**What must be true.**
- `porelo-release plan …` answers as an unknown command.
- The five classes, their tests and the plan pages' documentation are gone.
- `check-actions` keeps no rule that only the plan pages' publish job needed, if one is left.
- A changelog entry says the commands were removed.

## Built

- `plan` is gone from `porelo-release`'s commands and answers as an unknown one, seen red first.
- Plan, PlanCheck, PlanRender, PlanStand and PlanTemplate, their two test classes, the jte templates and the jte
  runtime and Maven plugin are deleted.
- `stories` read the project above them through Plan; that reader stays, as `ProjectFile`, unchanged.
- The issue index fixtures the plan tests had shared move to `issue-indexes/`, because an issue index test still
  reads them. The plan-only fixtures are deleted.
- `check-actions` loses `plan-pages.yml/publish`, because no porelo repository has that workflow any more.
- `check-citations` no longer skips a rendered plan page, because porelo-project keeps none.
- The docs' release plan section is down to a note that the command was removed.
- The two unreleased changelog entries of the plan commands are deleted, and one says they were removed. The
  released versions' entries stay as history.
