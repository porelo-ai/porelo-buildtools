# BT70 — The Release Plan Commands Removed

```json
{
  "$schema": "issue-1.json",
  "id": "BT70",
  "title": "The Release Plan Commands Removed",
  "state": "Idea",
  "size": "-",
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
