# BT19 — A Component Told Apart By Its Group

**Status:** Review.
**Size:** XS - one comparison in `Recorded.into` and its test; no contract changes.

**What must be true.** `porelo-release add-component` records a runtime in a tree's bill beside a package of the same
name in another group - npm's `@types/node` beside the Node runtime - and still refuses a component of the same group
and name delivered another way.

## Plan

- **`Recorded.into`** matched an earlier component by `getName()` alone, so Pi 1.3.0's tree, whose `protobufjs`
  depends on `@types/node` at run time, made `add-component node` refuse with "the bill already names a 'node'
  delivered otherwise". It now matches on group and name; a recorded component has no group, npm's typings have
  `@types`. Not on the purl: it carries the version, and a second run must still replace the first.
- **Red first:** `AddComponentTest.recordsTheRuntimeBesideAPackageOfTheSameNameInAnotherGroup`, a bill holding
  `@types/node`, refused before the change and passes after it; the refusal of a same-named component delivered
  otherwise still holds.

## Acceptance

- [x] The test above seen red, then green, with the class's five others.
- [x] porelo-pi's `-Pnative,dist verify` with Pi 1.3.0 passes `build-pi-tree` against the published snapshot
  `0.5.1-SNAPSHOT` `20261018.165817-10`, with `@types/node` 26.6.5 and Node 22.23.3 both in the tree's bill; the only
  caller of `add-component` is porelo-pi's `buildtools/build-pi-tree.sh`.
