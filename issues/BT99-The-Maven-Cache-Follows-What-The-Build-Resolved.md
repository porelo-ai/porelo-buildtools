# BT99 — The Maven Cache Follows What The Build Resolved

```json
{ "$schema": "issue-1.json", "id": "BT99", "title": "The Maven Cache Follows What The Build Resolved", "state": "Backlog",
  "size": "S", "priority": "High", "rank": 1, "blockedBy": [],
  "covers": "Bug: the Build's Maven cache is keyed by the poms alone and never saved after an exact hit, so versions that porelo-parent's snapshot moves are fetched from Central on every Build until a pom changes." }
```

**Why this size.** Two composite actions, `pinned-jdk` and `maven-cache-keep`, and the Build's prune job; no code
and no contract another repository reads.

**What must be true.** A Build whose Maven cache was restored fetches from Central only what it has never fetched
before: once a moved parent or BOM brings new versions, the next Build finds them in the cache.

## Plan

- Today `pinned-jdk` restores `maven-<os>-<hashFiles(**/pom.xml)>`, and `maven-cache-keep` saves only on a miss
  ("Nothing is saved after an exact hit"). The versions come from `porelo-parent`'s snapshot, which moves while no
  pom here changes, so after an exact hit whatever the move added is fetched again by every Build and never kept.
- `maven-cache-keep` also saves after an exact hit when the job wrote any file into the repository after
  `PORELO_MAVEN_CACHE_SINCE`, under the key with the run's id appended; `pinned-jdk`'s restore key prefix then takes
  the newest such entry, and the prune job deletes the entries it replaces, as it does today.
- The `-q` Maven calls in `pinned-jdk` say what they fetched (`-B -ntp` without `-q`, or a count of files written
  after the restore), so a Build log shows downloads after a hit.

## Acceptance

- Two Builds in a row with the same poms and a parent that moved in between: the first saves a new entry, the
  second's log shows no artifact fetched from Central.
- With the new save step removed, the second Build fetches the moved versions again (seen once).

## To be checked

- Measured so far: in the 25 jobs of the last 7 days' Builds, each after an exact hit, no artifact but snapshot
  metadata was fetched in the steps whose output shows it; the `-q` steps show nothing, so their downloads are not
  measured.
