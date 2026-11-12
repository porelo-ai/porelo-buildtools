# BT72 — The Native-Image Checks Handed To Core

```json
{
  "$schema": "issue-1.json",
  "id": "BT72",
  "title": "The Native-Image Checks Handed To Core",
  "state": "Review",
  "size": "S",
  "priority": "Medium",
  "rank": 26,
  "blockedBy": [],
  "covers": "Once porelo builds porelo-ffm-check and porelo-cpu-check itself, porelo-buildtools drops both modules; porelo-json stays here, where porelo-release and porelo-machines read it."
}
```

**Why.** Part of ST320 (porelo-project). The two checks hold porelo's native images to their FFM and CPU-feature sets. They belong with the
code they check.

**Who uses what today.**
- **porelo-ffm-check.** porelo's root `pom.xml` uses it.
- **porelo-cpu-check.** porelo's root `pom.xml` uses it, and so does porelo-planner's `planner/app/pom.xml`. The
  planner then takes it from porelo's coordinate, at porelo's version.
- **porelo-json.** It is not one of the checks:
  - porelo-release, porelo-machines and porelo-ffm-check use it here;
  - porelo uses it in `dist/package-check`.

  It stays a build-tools library. In its new home ffm-check reads JSON through porelo-wire's `Json` (Core's B621),
  so porelo-json loses that user.

**What must be true.**
1. Core's issue is done: porelo builds and publishes both checks under its own coordinates, and porelo and porelo-planner
   are green on them.
2. porelo-buildtools drops `ffm-check/` and `cpu-check/`, their modules in the parent `pom.xml` and their
   documentation.
3. Nothing in any repository still names `org.fuin.porelo:porelo-ffm-check` or `porelo-cpu-check` at a buildtools
   version.

It is blocked by Core's issue for taking the two checks over, which is filed with ST320 (porelo-project).

## Analysis (2026-11-11, the operator's order of 15:44Z)

**Aligned with Core's B621.** B621 moves both modules to porelo's `build/ffm-check` and `build/cpu-check`, under the
same artifact ids and packages, and publishes them with porelo's snapshots and releases. ffm-check reads JSON through
porelo-wire's `Json` there, not porelo-json. That is why the covers line above no longer names ffm-check as a user of
porelo-json. B621 says porelo-buildtools drops both modules once porelo's first publish holds them, which is this
issue.

**What porelo-buildtools does, once porelo's first snapshot holds both artifacts and porelo-planner takes
porelo-cpu-check from porelo:**
1. Delete `ffm-check/` and `cpu-check/` and their two `<module>` lines in the parent pom.
2. Remove their rows from `README.md` and `doc/index.md`, and their links. porelo-json's row stops saying "the tools
   above share" and names its users: porelo-release, porelo-machines and porelo's `dist/package-check`.
3. Add a `removed` changelog entry that names porelo as their new home.
4. Check that no repository still names `porelo-ffm-check` or `porelo-cpu-check` at a build-tools version, or by
   `porelo.buildtools.version`: a search of every porelo repository's `main`, posted in the room.
5. Check that the publish set stays right. The parent pom's module list is what is deployed, so dropping the modules
   drops them from the next snapshot. The versions already published stay on Central and the snapshot host.
   porelo's coordinates differ only by version (same groupId and artifact id, porelo's version line). Every pom that
   names either check names porelo's version explicitly, never `porelo.buildtools.version`, so nothing resolves a
   build-tools version by mistake (B621's acceptance).

There is nothing to see red first: no behavior changes here. The proof is the full JVM verify of the remaining
modules, and porelo's and porelo-planner's builds staying green on porelo's coordinates.

**Size S, 27 min at the measured median.** It is mostly deletion and documentation (about 15 min), plus the search
across repositories and the run on main (about 12 min).

**Order.** Core's hand-over order in B621: B621 lands and porelo's next snapshot holds both checks; then
porelo-planner takes porelo-cpu-check at porelo's version (a release, or the snapshot until one exists); only then
does this issue drop the modules. Their earlier releases stay on Central, so the checks are always published.

**For the operator to decide.** Nothing. Core took the version point into B621's acceptance (15:50Z).

## As built

- `ffm-check/` and `cpu-check/` are gone with their two `<module>` lines; README.md, AGENTS.md and doc/index.md no
  longer list them, and porelo-json's rows name its users. A `removed` changelog entry names porelo as their home.
- Read on the fetched mains (2026-11-12): porelo takes both checks at `${porelo.version}`; porelo-planner's
  `planner/app/pom.xml` still names porelo-cpu-check at `${porelo.buildtools.version}`, so this is pushed only after
  the planner's change is on its main. No other repository names either check at a build-tools version.
- `clean verify` and `-Pdocuments test` green on the remaining modules.
