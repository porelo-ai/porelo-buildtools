# BT99 — The Maven Cache Follows What The Build Resolved

```json
{ "$schema": "issue-1.json", "id": "BT99", "title": "The Maven Cache Follows What The Build Resolved", "state": "Review",
  "size": "S", "priority": "High", "rank": 1, "blockedBy": [],
  "covers": "Bug: the Build's Maven cache is keyed by the poms alone and never saved after an exact hit, so versions that porelo-parent's snapshot moves are fetched from Central on every Build until a pom changes." }
```

**Why this size.** Two composite actions, `pinned-jdk` and `maven-cache-keep`, and the Build's prune job; no code
and no contract another repository reads.

**What must be true.** A Build whose Maven cache was restored fetches from Central only what it has never fetched
before: once a moved parent or BOM brings new versions, the next Build finds them in the cache.

## Plan

- `pinned-jdk` restores `maven-<os>-<hashFiles(**/pom.xml)>-<run id>-<attempt>` by the prefixes
  `maven-<os>-<hashFiles>-`, then `maven-<os>-`: no run hits exactly, so every run that fetched can save.
- `maven-cache-keep`, the build job's last step even after a failure (`if: always()`), saves only when a jar or pom
  changed status after a marker touched at the restore (`find -cnewer`: Maven dates a downloaded file by the
  server's Last-Modified, so its mtime says nothing), with the job's `job.status` as an input. After a green job it
  first drops what the job did not read; after a failed one it saves whole. Only the build job saves, so two jobs of
  one run never race for the run's key; the prune job keeps the saved or restored entry and deletes the others.
- The other repositories take the form proven in porelo-planner: `pinned-jdk`'s restore by run key and a marker, and
  a `maven-cache-save` action as each saving job's last step.

## Acceptance

- Two Builds in a row with the same poms and a parent that moved in between: the first saves a new entry, the
  second's log shows no artifact fetched from Central.
- With the new save step removed, the second Build fetches the moved versions again (seen once).

## To be checked

- Measured so far: in the 25 jobs of the last 7 days' Builds, each after an exact hit, no artifact but snapshot
  metadata was fetched in the steps whose output shows it; the `-q` steps show nothing, so their downloads are not
  measured.
