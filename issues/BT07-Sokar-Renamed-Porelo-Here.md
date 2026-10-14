# BT07 — Sokar Renamed Porelo Here

**Status:** Estimated.
**Size:** M - 850 occurrences in 162 of 194 files and 114 paths, five modules and their artifacts, and names at the
cloud provider and Artifactory that must be renamed or swept before the tools reach them under the new name.

**What must be true.** The build tools are Porelo's: no tracked file outside the exceptions below says `sokar`,
they are published as `org.fuin.porelo` at `0.5.0`, and what they rent, label and install is named Porelo. From [PJ41](https://github.com/sokar-ai/sokar-project/blob/main/issues/README.md#pj41).

## Plan

**How:** one commit, made by a script this repository's agent runs and reviews, never by hand: `git mv` for every
path naming `sokar`, then `sokar`/`Sokar`/`SOKAR` replaced by `porelo`/`Porelo`/`PORELO`, then the exceptions below
put back. Nothing is renamed before the window is announced.

**What carries the name:** the artifacts `sokar-json`, `sokar-machines` (`hetzner`), `sokar-release`,
`sokar-ffm-check`, `sokar-cpu-check`, read by every Maven repository through the parent; the commands
`sokar-machines` and `sokar-release`; the property `sokar.ffm.update`; the acceptance properties
`sokar.acceptance.*` the legs pass to the suite; the paths and packages a leg installs (`sokar setup`, `sokard`,
`sokar-agent-*`, `sokar-message-sluice-filter`, `/run/user/<uid>/sokar/sokard.sock`).

**Names outside, renamed in the window's preparation or swept first:**

- **The cloud provider's label `sokar=ci`** (`Hetzner.LABEL_SELECTOR`) on the CI snapshots, and the server names
  `sokar-acc-*`, `sokar-e2e-*`, `sokar-snapshot-*`: after the rename the tools look for `porelo=ci`, so the old
  snapshots are invisible to them. Every rented server is swept before the window, and the snapshots are built again
  under the new label - nothing is relabelled by hand.
- **Artifactory:** the repositories `sokar-dist-deb`/`-rpm` the legs install from become Porelo's; the keypair
  `sokar-packages` whose public half a leg fetches is named as the preparation decides.

**Exceptions:** the 36 files under `changelog/v*` (released entries), and a sentence that says what the old name was.

**What the script cannot do by replacement:** nothing found - no image, no binary, no name built across the word;
the 10 `sokard` become `porelod`, as decided.

**Published before this builds:** `porelo-parent` `0.2.0` ([BT08](README.md#bt08)), and the organisation and this repository renamed
on GitHub.

**How it is tested:** red first, a test tagged `documents` that fails while any tracked file outside the exceptions
says `sokar`; then the whole reactor, the shared checks, one rented leg with the new label (a snapshot built, found
and swept), and the release of `0.5.0`.

## Acceptance

- The test above is red before the script and green after it, and stays.
- A leg rents, labels, finds and sweeps its machine under `porelo`; the reactor and the shared checks are green.
- `0.5.0` is published as `org.fuin.porelo`.
