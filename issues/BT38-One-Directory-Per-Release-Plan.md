# BT38 — One Directory Per Release Plan

**Status:** Implementing
**Size:** S - paths in `PlanRender`, `Plan` and `CheckCitations`, and the tests' layout; no other reader.

**What must be true.** A release plan is `plan/<release>/plan.yml`; `plan render` writes its page as `README.md` and
its progress image beside it, so the forge shows the page on opening `plan/<release>/`, and `plan check` checks that
layout. The old `plan/<release>.yml` is not read: porelo-project is its only user and moves its files.

## Plan

- `Plan.read` refuses a plan that is not `<release>/plan.yml`; `PlanRender` writes `README.md` and
  `progress-<hash>.svg` beside the plan and removes older images there; `CheckCitations` skips a generated
  `README.md` with a `plan.yml` beside it. The plan tests move to the new layout, red first.

## Acceptance

- The plan and citation tests pass on the new layout and failed on the old code; porelo-project renders and checks its
  moved plans with the published snapshot.
