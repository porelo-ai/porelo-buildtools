# BT98 — Machines Commands Only Map

```json
{
  "$schema": "issue-1.json",
  "id": "BT98",
  "title": "Machines Commands Only Map",
  "state": "Analyzed",
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

Read in `machines/src/main/java/org/fuin/porelo/machines/Main.java`, `Snapshots.java`, `Machines.java` and the
tests' `RecordingMachines`.

- **`Snapshots.renew(Machines machines, String os, List<String> types, Credential credential, @Nullable Path repo,
  boolean unlessCurrent)`** answers a record: built or current, with the inputs. It reads `inputs(os)` and
  `muslInstaller()`, archives the working tree when `repo` is given, and asks `current`. When `unlessCurrent` holds
  and the snapshot is current, it stops there. Otherwise it calls today's `build` with `Kind.AMD64_SNAPSHOT` and
  then `pruneSnapshots`. `archiveOf` moves into `Snapshots` unchanged, with its temporary file deleted after the
  build as today.
- **`snapshot` in `Main`** keeps its option parsing and the `--kind`/`--type` refusals. It opens the provider with
  its limit, calls `renew` and prints the line it prints today for a current snapshot.
- **`Machines.deleteLeased(Path leaseFile)`**, a default method beside `deleteRun`, reads the file's `run` and
  refuses with `IllegalArgumentException` when there is none; `Main` prints that refusal as today, exit 2.
  `tookThePool` moves to `LocalPool` as `tookThePool(Map<String, String> env)`, beside the pool it names. `sweep`
  calls it before opening a provider, as now.
- **Red first,** over `RecordingMachines`:
  - `renew` with a current snapshot records no build and no prune.
  - `renew` without `unlessCurrent` records one build and then a prune keeping that build.
  - `deleteLeased` on a file without `run` is refused and deletes nothing.

  They fail today because the methods do not exist; each is seen red once by breaking the method it tests.
- The harm guarded is a snapshot built and billed again, or a prune or delete reaching servers that are not this
  run's, while the paths are reachable only through `Main`.
- `MainTest`'s sweep cases stay as they are, as the proof that the command line did not change. `snapshot` has no
  `MainTest` case today, so the new `renew` tests are its first below the provider.

## Acceptance

- The new unit tests, seen red first, and the full build green.
- `Main` holds no call to `Snapshots.current`, `Snapshots.build`, `pruneSnapshots` or `git archive`.
