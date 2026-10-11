# BT03 — A Rebuilt Snapshot Removes Its Predecessors

**Status:** Analyzed.
**Size:** S - one module (`hetzner`): a listing and a delete after `snapshot`, a stand-in API test; no contract.

**What must be true.** After `sokar-machines snapshot` has taken a new CI snapshot for an `os` and seen it available,
it deletes the older snapshots of that `os` labelled `sokar=ci`, keeping the newest one before it as a fallback. It
never touches a snapshot without `sokar=ci`, nor any snapshot of another `os`, and it says each deletion in the run's
output.

## Why

Each rebuild leaves the older `sokar=ci` snapshot of its `os` in the Hetzner project, where it costs storage and is
never rented again: a leg rents the newest (`Hetzner.newestSnapshot`). They were deleted by hand, keeping the newest
per system.

## The shape

- **When:** only after the new snapshot's status is `available`. A rebuild that failed deletes nothing.
- **What:** `GET /images?type=snapshot&label_selector=sokar=ci,os=<os>`, sorted by `created`; all but the two newest
  are deleted - the one just taken, and the one before it as a fallback for a rebuild that turns out broken.
- **Proposed fallback: one.** Rolling back to it is renting by its id; two or more were never used.
- **Said:** `deleted  snapshot <id> (<os>, <created>, <description>)` per deletion, and `kept  <id>` for the two.
- **Refused to act:** a snapshot without `sokar=ci`, one of another `os`, and the newest of the `os` - whatever the
  listing says. A deletion that fails is said and does not fail the run: the snapshot just taken is what matters.

## Plan

- `hetzner/.../Hetzner.java`: a `pruneSnapshots(os)` beside `newestSnapshot`: list
  `/images?type=snapshot&label_selector=sokar=ci,os=<os>`, sort by `created`, delete all but the two newest with
  `DELETE /images/<id>`, each said; a failed delete said and not thrown.
- Called from the `snapshot` command (`Main`) after the new image's status is `available`; never from a leg.
- Red first in a test against the stand-in API the module's tests use: four `ubuntu`, one `fedora`, one unlabelled
  snapshot; afterwards the two newest `ubuntu`, the `fedora` and the unlabelled are left.

## Acceptance

- Seen to fail first, then green, against a stand-in Hetzner API: with four `sokar=ci` snapshots of `ubuntu`, one of
  `fedora` and one unlabelled, a new `ubuntu` snapshot leaves the newest two `ubuntu`, the `fedora` one and the
  unlabelled one; each deletion is said.
- A rebuild whose snapshot never becomes available deletes nothing.
- `doc/index.md` says it under `sokar-machines`.

## To be checked

- Whether the sweep should also delete `sokar=ci` snapshots of an `os` no workflow builds any more.
