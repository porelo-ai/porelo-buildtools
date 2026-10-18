# BT20 — Bills Read With A Current CycloneDX Library

**Status:** Analyzed.
**Size:** S - the `release` module's use of one library across major versions, with its tests and a run on real
bills; porelo-parent's two version lines are Agent Core's.

**What must be true.** porelo-release reads and writes bills with a `cyclonedx-core-java` that carries no known
advisory, and `commons-lang3` beside it likewise, without a red build in between.

## Plan

- **Why**: porelo-parent manages `cyclonedx-core-java` 9.0.5, which OSV lists with GHSA-6fhj-vr9j-g45r (an XXE in
  XML validation, fixed in 11.0.1); its `commons-lang3` 3.17.0 has GHSA-j288-q9x7-2f5v, fixed in 3.18.0. porelo-release
  parses bills with `JsonParser` only, so the XXE reads as unreachable; it is moved all the same, since a build tool
  reads bills other builds wrote.
- **The only user** is this repository's `release` module; no other repository declares either library.
- **The order, so nothing is red:**
  1. Here, on a local tree, `cyclonedx-core-java.version` overridden in `release/pom.xml` to the newest release that
     has stood `porelo.release.min-age`, its API changes adapted, and `commons-lang3` managed likewise.
  2. Proven with the whole check, the deploy dry run, and `merge-tree-bill`, `compare-bills` and `add-component` run on
     real bills from porelo's and porelo-pi's last builds, their output compared with today's.
  3. In one push wave: porelo-parent's two version lines first (Agent Core), then this repository with the override
     dropped and the adapted code kept.
- **Red first**: a test that reads a bill the newer library writes and today's code mis-reads, if the adaptation
  needs one; otherwise the real bills' output, compared before and after, is the proof.

## Acceptance

- [ ] OSV lists no advisory for the two versions taken.
- [ ] The whole check green on the adapted tree, and the three commands' output on real bills the same as before, or
  each difference named.
- [ ] After the wave, this repository's Build and Publish green against the parent's snapshot.

## To be checked

- Which release of each is the newest that has stood the minimum age, and what `porelo.release.min-age` is set to.
