# BT39 — Snapshots Resolved With A Read-Only Token

**Status:** Analyzed.
**Size:** M - `settings.xml`, every Maven workflow and their registry tests; the settings' names are a contract other
repositories read.

**What must be true.** On the host's forge a build resolves snapshots with a token that can only read them; the
publisher's token is held only by the step that deploys on `main`, so a test in an unreviewed task branch can neither
overwrite a snapshot nor publish one. A build on any forge other than github.com that is given no
`MAVEN_SNAPSHOTS_URL` fails, instead of quietly resolving Central's snapshots.

## Plan

- **The contract** (operator's decision, 2026-10-29; the names are fixed in the room): the forge's organization holds
  the secret `PORELO_PACKAGES_READ_TOKEN`, a token with the single scope `read:package`, beside
  `PORELO_PUBLISHER_TOKEN`; GitHub holds a placeholder under the same name.
- **`settings.xml`:** the forge profile's repository and plugin repository get their own id, `porelo-forge-snapshots`,
  with a server of that id whose password is `${env.PORELO_PACKAGES_READ_TOKEN}`. The server `porelo-forge`, which the
  parent names for deploying, keeps `${env.PORELO_PUBLISHER_TOKEN}`. Resolving never sends the publisher's token.
- **Workflows** `build.yml` (`build`, `snapshots`, `deploy`), `workflows.yml`, `machines.yml`, `shared-rules.yml`:
  each job's `env` names `PORELO_PACKAGES_READ_TOKEN` and no longer `PORELO_PUBLISHER_TOKEN`; only the deploying step
  of `deploy` names `PORELO_PUBLISHER_TOKEN` in its own `env`, and only on `main` or a tag, as its job's condition
  says today.
- **The missing variable:** each job that runs Maven starts with a step that fails with `::error::` naming
  `MAVEN_SNAPSHOTS_URL` when it is empty and `github.server_url` is not `https://github.com`. On github.com the
  `standard` profile keeps resolving Central's snapshots.
- **Tests first,** in `RegistrySettingsTest` and `WorkflowRegistryTest`: the resolving server's id and password; no
  job-level `PORELO_PUBLISHER_TOKEN` in any workflow, and exactly one step that names it, the deploying one; every
  Maven job's first step checks `MAVEN_SNAPSHOTS_URL` against `github.server_url`. Each is red before the change.
- `CHANGELOG.md`, and the README's paragraph on the registries.

## Acceptance

- The unit tests above, each seen red first.
- On the host's forge, a task branch's Build resolves the organization's snapshots with the read token, and an
  attempt to deploy from it is refused; `main`'s Build deploys. A run with `MAVEN_SNAPSHOTS_URL` removed fails at the
  first step with its error and resolves nothing.
- On GitHub the Build stays green, unchanged in what it resolves and publishes.

## To be checked

- Whether Forgejo checks the user name Maven sends against the token's owner; the settings send one name for every
  organization's publisher.
- That the parent names `porelo-forge` only in `distributionManagement`, so the new repository id changes nothing it
  reads.
