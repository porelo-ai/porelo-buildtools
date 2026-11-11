# BT72 — The Native-Image Checks Handed To Core

```json
{
  "$schema": "issue-1.json",
  "id": "BT72",
  "title": "The Native-Image Checks Handed To Core",
  "state": "Idea",
  "size": "-",
  "priority": "Medium",
  "rank": 26,
  "blockedBy": [],
  "covers": "Once porelo builds porelo-ffm-check and porelo-cpu-check itself, porelo-buildtools drops both modules; porelo-json stays here, where porelo-release, porelo-machines and ffm-check's new home read it."
}
```

**Why.** Part of ST320 (porelo-project). The two checks hold porelo's native images to their FFM and CPU-feature sets. They belong with the
code they check.

**Who uses what today.**
- **porelo-ffm-check.** porelo's root `pom.xml` uses it.
- **porelo-cpu-check.** porelo's root `pom.xml` uses it, and so does porelo-planner's `planner/app/pom.xml`. The
  planner then takes it from porelo's coordinate.
- **porelo-json.** It is not one of the checks:
  - porelo-release, porelo-machines and porelo-ffm-check use it here;
  - porelo uses it in `dist/package-check`.

  It stays a build-tools library. ffm-check keeps depending on it from its new home.

**What must be true.**
1. Core's issue is done: porelo builds and publishes both checks under its own coordinates, and porelo and porelo-planner
   are green on them.
2. porelo-buildtools drops `ffm-check/` and `cpu-check/`, their modules in the parent `pom.xml` and their
   documentation.
3. Nothing in any repository still names `org.fuin.porelo:porelo-ffm-check` or `porelo-cpu-check` at a buildtools
   version.

It is blocked by Core's issue for taking the two checks over, which is filed with ST320 (porelo-project).
