# BT01 — A Release Leg Installs The Release

**Status:** Estimated.
**Size:** S - one module (`hetzner`): the channel into `AgentLeg.install`, the name check moved behind `--candidate`, two
unit tests; no contract another repository reads beyond one new option.

**What must be true.** A leg on a rented machine for a release tests that release: `porelo-machines acceptance` and
`lease` install Porelo from the channel the run publishes to, and `acceptance --candidate` needs no package name.

## Why

The release of 0.4.0 found both on the first tag runs that rented a machine. `AgentLeg.install` writes the
repository line with the channel fixed to `snapshots`, so a tag leg installed Porelo from a channel the release had
emptied, and stopped at "Unable to locate package porelo". And `AgentLeg.run` checked the package name before it
looked at `--candidate`, so `--candidate` alone, which `acceptance` documents as needing no name, was refused with
"--package '' is not a package name".

## Plan

- `hetzner/.../AgentLeg.java`: `install(os, artifactory, installs)` gains a `channel` (`snapshots` or `releases`) that
  replaces the fixed `snapshots` in both the RPM `baseurl` and the Debian source line; `Rental.java:101` passes it too.
- The channel comes from a new `--channel snapshots|releases` option of `acceptance` and `lease` (`Main`, `Options`),
  defaulting to `releases` when `GITHUB_REF_TYPE=tag` and `snapshots` otherwise.
- `AgentLeg.run`: `packageArgument(options.packageName())` is checked only when there is no `--candidate`.
- Red first in `AgentLegTest`: the install script for `releases` still names `snapshots`; `--candidate` with an
  empty package name is refused.
- `doc/index.md` names the option.

## Acceptance

- `acceptance --candidate <dir>` without `--package` passes the name check and installs the candidate. Seen to fail:
  a unit test with an empty package name and a candidate, against the code as released in 0.4.0.
- The install script a leg sends names the channel it was given, `snapshots` or `releases`, for Debian and RPM alike,
  and a run on a tag gives `releases`. Seen to fail: a unit test reading the script for `releases` and finding
  `snapshots`.
- A tag run of an agent repository and of `porelo-frontend`, on `porelo-machines` with this change, installs the
  released Porelo and is green.

## To be checked

- Whether an explicit `--channel` wins over `GITHUB_REF_TYPE`, as planned (Agent Core's).
