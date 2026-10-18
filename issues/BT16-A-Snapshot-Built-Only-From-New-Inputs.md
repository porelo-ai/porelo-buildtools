# BT16 — A Snapshot Built Only From New Inputs

**Status:** Review. Built 2026-10-18; the Builds after the push are its proof.
**Size:** S - one name for what an image is made from, a label on the image, one option the workflow passes, and tests
seen red first.

**What must be true.** The `snapshots` job builds an image only when what it is made from changed since the last
published snapshot of that system. A dispatch always builds. A push that changes nothing an image is made from says it
built none and why, and rents nothing.

## Why

Decided by the operator on 2026-10-18. The job decided by a diff of the push: a pin line in `hetzner/pom.xml` or a file
of the recipe. That says what a push changed, not what the newest snapshot was made from. A push after a failed
snapshot found nothing moved, and a line that moved without changing the pin's value built again.

## As built

- **What an image is made from** is `Snapshots.inputs(os)`: 16 hex digits of a SHA-256 over three things:
  - the recipe the machine runs, with every pin filled in (`Snapshots.recipe(os)`: the GraalVM address and digest,
    the images and their digests, the packages);
  - the toolchain's installer (`install-musl.sh`);
  - the code that runs them (`Snapshots.class`).
- **A snapshot carries it** as its label `inputs`, beside `porelo=ci` and `os`.
- **`porelo-machines snapshot --unless-current`** asks, before anything is rented, whether the newest available
  snapshot of the system carries the same `inputs`. If it does, the command says "the newest <os> snapshot was made
  from these inputs (<hash>), so none is built" and ends. A snapshot from before this change carries no `inputs` and
  counts as not current, so the first push after it builds each image once.
- **The workflow** passes `--unless-current` on a push and nothing on a dispatch. The diff step is gone, and with it
  the full history the checkout fetched for it. The tooling build and the porelo checkout now run on every push to
  `main`, about two minutes on the runner, which rents nothing.

## Acceptance

- [x] The inputs' name is stable, differs between systems, and changes with the recipe and with the code
  (`SnapshotsTest.namesWhatAnImageIsMadeFromByOneHash`).
- [x] The newest snapshot counts as current only when its `inputs` label matches; one without the label, or none at
  all, does not (`takesTheNewestSnapshotAsCurrentOnlyWhenItWasMadeFromTheSameInputs`,
  `takesASnapshotWithoutInputsOrNoneAtAllAsNotCurrent`). All three were seen red against a stub.
- [ ] The first Build after the push builds both images once, labelled. The Build after that, with no input changed,
  says "none is built" for both, with no machine rented.
