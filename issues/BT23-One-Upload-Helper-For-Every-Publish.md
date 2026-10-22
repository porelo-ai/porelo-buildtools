# BT23 — One Upload Helper For Every Publish

**Status:** Idea.

**What must be true.** Every Porelo repository that publishes to Artifactory uploads through one helper that
porelo-buildtools publishes and each Publish job takes by version and digest. No repository keeps its own copy:
porelo, porelo-pi, porelo-omp, porelo-claude-code and porelo-message-matrix retire `.github/scripts/artifactory.sh`,
five identical copies today, and porelo-message-sluice, porelo-intellij and porelo-frontend take the helper in place of
the curl calls their Publish steps carry.

## Acceptance

- Each of the eight repositories' Publish job uploads with the published helper and none keeps a copy of it; a Publish
  that takes a helper whose digest does not match stops before it uploads.
- The token reaches the helper on standard input, as curl `-K -` takes it per call, never on a command line and never
  in a file: a secret never touches a filesystem in CI, and the copies today write one.

## To be checked

- Whether every one of the eight Publish jobs has the pinned GraalVM when it uploads, so the helper can be a
  `porelo-release` command in Java, as the shared rules ask, rather than a shell file.
