# BT20 — Bills Read With A Current CycloneDX Library

**Status:** Implementing.
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

- [x] OSV lists no advisory for any of porelo-release's 39 dependency coordinates with `cyclonedx-core-java` 11.0.1
  and `commons-lang3` 3.18.0 (the operator's choice of version); the fuin BOM this pom imports held `commons-lang3` at
  3.17.0, so the root pom names 3.18.0 beside the override until the parent manages both.
- [x] No code needed adapting across the two major versions. The whole check green: `clean verify` (546 tests, none
  skipped), the deploy dry run with `check-deploy`, `-Pdocuments test`.
- [x] On real bills, porelo-pi's tree and package bills and porelo-message-matrix's, the same release code with
  9.0.5 and with 11.0.1 wrote byte-equal bills apart from timestamp and serial: `merge-tree-bill` (280 components),
  `add-component` (Node recorded), and `compare-bills` ("the same 142 components, unchanged licenses") said the same.
- [ ] After the parent manages both and this repository drops its two entries, its Build and Publish green against
  the parent's snapshot.
