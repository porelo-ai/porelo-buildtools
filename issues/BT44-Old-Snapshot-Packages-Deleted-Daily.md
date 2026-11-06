# BT44 — Old Snapshot Packages Deleted Daily

**Status:** Idea.
**Size:** M - one command in `release` with a stand-in Artifactory for its tests, a workflow, its documentation and
one dry run on GitHub.

**What must be true.** Once a day, a workflow of `porelo-buildtools` deletes every snapshot package file older than
four weeks from Artifactory's `porelo-dist-deb` and `porelo-dist-rpm`, but always keeps the newest three versions of
each package name, however old, so a package not built for four weeks still installs from `snapshots`. Nothing of
`releases` is ever deleted, and after a deletion `apt` and `dnf` still install the newest snapshot of each package.

## Why

Eleven repositories publish a snapshot package on every push to `main`, and nothing ever removes one: the `snapshots`
channel held 411 Debian packages under 13 names and 418 files in its RPM folder, almost all of them versions nobody
installs any more.

## The shape

- **What the rule is:** a file is deleted when its version is not among the three newest of its package name and its
  `created` time in Artifactory is more than 28 days before the run. Versions are compared as dpkg compares them
  (`0.5.0~snapshot.290` before `0.5.1~snapshot.292`), and every file of a version that stays, stays.
- **Where `snapshots` is:** the Debian packages of both channels share `pool/main/p/<name>/`; a file's channel is only
  its `deb.distribution` property. A Debian file is a candidate only when that property is exactly `snapshots`, and
  it is refused whatever its property says when the `releases` index lists its file name. An RPM is a candidate only
  under `porelo-dist-rpm/snapshots/`. Only `.deb` and `.rpm` files are touched; the bill and the setup script beside
  them are not.
- **A dry run first:** `workflow_dispatch` with `delete` left unticked lists what would go, per file, and what stays,
  per package name, and deletes nothing. The schedule goes live, deleting, only after the operator has read one such
  list.
- **The token can delete before it is trusted to:** every run puts one probe file under `prune-probe/` of each
  repository, outside the pool and outside `snapshots/`, which no index reads, and deletes it again. A missing
  `JF_ACCESS_TOKEN`, a refused put or a refused delete stops the run before anything is selected, naming the probe it
  could not remove.
- **The indexes follow:** after deleting, the run reads the `snapshots` Debian index and the RPM primary metadata until
  neither lists a deleted file. If one still does after two minutes, it asks Artifactory to recalculate that index
  once and reads again; a file still listed then fails the run, named.
- **Fail closed and loud:** a listing, a property or a date that cannot be read stops the run before any deletion; a
  deletion that fails is named and fails the run after the others.

## Plan

- `release/.../PruneSnapshots.java`: the selection as a pure function over listed files (name, version, channel,
  created) and the run around it - probe, list, select, delete, wait for the indexes - over an `Artifactory` interface.
- `release/.../Artifactory.java`: the storage listing, a file's `created` and properties, put, delete, the Debian index,
  the RPM primary metadata and the two recalculation calls, with `JF_ACCESS_TOKEN` read from the environment and sent
  only to `JF_URL`'s host.
- `release/.../DebianVersion.java`: dpkg's version comparison.
- `Main`: `prune-snapshots [--delete]`.
- `.github/workflows/prune-snapshots.yml`: beside `delete-runs.yml`, `workflow_dispatch` with a `delete` input; the
  schedule added once the operator has read a list.
- Red first in `PruneSnapshotsTest`, against a stand-in Artifactory: a file of `releases` among old snapshots is never
  deleted, the newest three of a name are kept however old, and a failing probe deletes nothing.

## Acceptance

- The tests above, seen red first, and the module's build green.
- One dry run on GitHub, its list read by the operator before the schedule is added.
- After the first deleting run: `apt` and `dnf` on a local VM still install the newest `porelo` from `snapshots`, and
  the RPM primary metadata still carries `rpm:recommends`.

## To be checked

- Whether `JF_ACCESS_TOKEN` is a secret `porelo-buildtools` can read and may delete with in both repositories: the
  dry run's probe says.
- Whether Artifactory recalculates both indexes by itself after a deletion: the first deleting run says.
