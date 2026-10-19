# BT21 — Jackson Without Its Known Advisories

**Status:** Review.
**Size:** XS - one version property and the check that the tools still read and write as before.

**What must be true.** porelo-release runs with a Jackson that OSV lists no advisory against.

## Plan

- **Why:** `jackson.version` 2.21.2, set in this repository's root `pom.xml`, gives porelo-release `jackson-core` (3
  advisories) and `jackson-databind` (16) through the `jackson-dataformat-xml` and `-yaml` it declares at runtime.
  porelo-machines resolves no Jackson. porelo-release reads the repositories' own bills and files, so the reach is
  their content, not the network.
- **The move:** 2.22.3, the newest 2.22 that has stood (published 2026-09-21; 2.22.4 is a day old), for all five
  modules the tools load: `jackson-core`, `-databind` and `-annotations` (2.22, its own line) are named in the root
  pom's `dependencyManagement` beside the two dataformats, since the fuin BOM it imports manages core and databind
  at 2.21.2, and only an entry of the pom's own wins over an import. `cyclonedx-core-java` is
  [BT20](README.md#bt20)'s.
- **The proof:** the whole check, the deploy dry run, and the documents tests; the tools read bills and YAML through
  these dataformats, which their tests run.

## Acceptance

- [x] `dependency:tree` resolves `jackson-core`, `-databind` and both dataformats 2.22.3 and `-annotations` 2.22 in
  porelo-release; OSV lists no advisory for `jackson-core` or `jackson-databind` 2.22.3. Seen before the fix: managing
  the dataformats alone left core and databind at the fuin BOM's 2.21.2.
- [x] The whole check green: `clean verify` (546 tests, none skipped), the deploy dry run with `check-deploy`,
  `-Pdocuments test` and `check-citations`.
- [x] Build 39204393221 on `bb8ab59` green with Publish, 2026-10-19 05:24Z; porelo-host's tools resolve Jackson
  2.22.3 from the published snapshot.
