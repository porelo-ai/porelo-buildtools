# BT48 — Snapshots Filtered In The Agent Repositories

```json
{
  "$schema": "issue-1.json",
  "id": "BT48",
  "title": "Snapshots Filtered In The Agent Repositories",
  "state": "Backlog",
  "size": "S",
  "priority": "Medium",
  "rank": 1,
  "blockedBy": [],
  "covers": "The five agent repositories read Sonatype's snapshots only for the groups in `.mvn/rrf/`, in the shape `check-snapshot-filter` holds, and their Build runs it; built by Agent Smith there."
}
```

**Why this size.** 5× S in the agent repositories, by Agent Smith: each one un-ignores and commits `.mvn/maven.config`,
moves the agents' local repository split to MAVEN_ARGS, and turns the `standard` profile's activation from
`!env.MAVEN_SNAPSHOTS_URL` to the switch and the rrf file; XS here.

**What must be true.** porelo-claude-code, porelo-pi, porelo-omp, porelo-codex and porelo-agy read Sonatype's
snapshots only for the groups they list in `.mvn/rrf/groupId-central.sonatype.snapshots.txt`, in the shape
`check-snapshot-filter` holds, and their Build runs that check as porelo-message-matrix's and porelo-message-sluice's
do. A snapshot of another group can then not reach their builds.

## Why

Seen on 2026-11-08, read only, with porelo-release 0.5.2-SNAPSHOT on each repository's main: `check-snapshot-filter`
refuses all five, in the same three places.

- `settings.xml`: profile `standard` reads `central.sonatype.snapshots` without activating on the switch and on the
  file `groupId-central.sonatype.snapshots.txt`.
- `.mvn/maven.config` lacks `-Daether.remoteRepositoryFilter.groupId=true` and
  `-Daether.remoteRepositoryFilter.groupId.basedir=${session.rootDirectory}/.mvn/rrf`.
- `.mvn/rrf/groupId-central.sonatype.snapshots.txt` is missing, so every name would be asked.

None of their Builds runs the check, so nothing is red today. porelo-buildtools, porelo-message-matrix and
porelo-message-sluice already hold the shape; the last two also check it.

## The shape

- The change lands in the five repositories, one small commit each, by Agent Smith, who holds them. It copies the
  three pieces from porelo-message-matrix, with `org.fuin.porelo` as the one listed group. The Build gains the
  `check-snapshot-filter .` step beside its other checks.
- Here only `doc/index.md` states that a repository reading snapshots holds this shape and checks it, if it does not
  say so already.
- Each repository's build is run locally before its push, to show every snapshot it needs is still found:
  porelo-parent, porelo-buildtools and Porelo's own artifacts are all `org.fuin.porelo`.

## Size

5× S in the agent repositories, by Agent Smith: each one un-ignores and commits `.mvn/maven.config`, moves the agents'
local repository split to MAVEN_ARGS, and turns the `standard` profile's activation from `!env.MAVEN_SNAPSHOTS_URL` to
the switch and the rrf file; XS here.

## To be checked

- With Agent Smith: the order beside the move of their parent to 0.2.2-SNAPSHOT, best after it.
- Whether porelo, porelo-frontend, porelo-ecosystems, porelo-intellij and porelo-parent, which hold no `.mvn/rrf/`
  either, read snapshots at all. If they do, they are a second step.

## Built here (2026-11-12)

- `doc/index.md`, under *What `check-snapshot-filter` reads*, states that every repository reading Sonatype's
  snapshots holds the shape and runs the check in its Build, with `org.fuin.porelo` as its one listed group. The five
  agent repositories' commits stay with Agent Smith.
