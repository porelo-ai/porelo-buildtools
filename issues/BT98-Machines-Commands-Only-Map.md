# BT98 — Machines Commands Only Map

```json
{
  "$schema": "issue-1.json",
  "id": "BT98",
  "title": "Machines Commands Only Map",
  "state": "Accepted",
  "size": "S",
  "priority": "High",
  "rank": 4,
  "blockedBy": [],
  "covers": "porelo-machines' `snapshot` and `sweep` commands parse their options, call one method of `Snapshots` or `Machines` and print its answer; the snapshot's current-build-prune use case, the archive of the working tree and the sweep's run file and pool rule move out of `Main`."
}
```

**Why this size.** One module's entry class gives three pieces of work to the classes beside it, with their tests;
no command line changes.

**What must be true.** An entry point only checks its input, maps it, calls one piece that does the work and maps
the answer back. The piece that does the work is tested without the command line. Read against this:

- `porelo-release`'s `Main` maps: each command parses its options and calls one class (`CheckCommits`,
  `CompareBills` and the rest), whose `check` or `compare` its tests call directly.
- In porelo-machines' `Main`, `acceptance`, `lease` and `jdk` map to `AgentLeg`, `Machines` and `PinnedJdk`.
  Three commands carry work of their own:
  - **`snapshot`** runs the use case itself: whether the newest snapshot is current for its inputs, then build, then
    prune the older ones. It also archives the working tree by running `git archive` (`archiveOf`).
  - **`sweep`** reads a lease file's run with `--from`, refuses one that names none, and decides that a run which
    took the host's pool and holds no provider token deletes nothing.

## Plan

- **`Snapshots.renew(machines, os, kind, types, credential, archive, musl, unlessCurrent)`** answers whether it
  built, and holds current, build and prune. `snapshot` maps its options to it and prints the answer. `archiveOf`
  moves beside it, into `Snapshots`.
- **`Machines.deleteLeased(Path)`** reads the lease file and deletes its run, refusing a file with no run. The pool
  rule becomes one static method, which `sweep` calls before it opens a provider.
- **Red first:** a unit test over `Snapshots.renew` with a fake `Machines`, where a current snapshot builds nothing
  and prunes nothing, and one where a new build prunes the older one. A unit test over `deleteLeased` where a file
  with no run is refused and deletes nothing. Today each path is reachable only through `Main`.
- `MainTest`'s cases for `snapshot` and `sweep` stay as they are, as the proof that the command lines did not change.

## Acceptance

- The new unit tests, seen red first, and the full build green.
- `Main` holds no call to `Snapshots.current`, `Snapshots.build`, `pruneSnapshots` or `git archive`.
