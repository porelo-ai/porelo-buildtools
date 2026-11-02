# BT41 — Mutation Testing With PIT

**Status:** Accepted.

**What must be true.** The Java modules can be run under PIT mutation testing by hand, so the tests' strength is
measured, not assumed: a `pit` Maven profile, never run in CI and with no threshold, with PIT and its JUnit 5 plugin
pinned by version; run on a VM wherever the tests call real tools (git, gpg, ssh); and a report that sorts the
surviving mutants into real gaps in the tests and harmless ones.

## Model

The try in porelo-message-matrix: its report `~/.porelo/handover/porelo-message-matrix/pit-014d86a/report.md` and
its branch `pit-try`, later its pushed profile.
