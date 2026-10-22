# BT22 — A Pin With Several Artifacts

**Status:** Review.
**Size:** S - two commands in `porelo-release` learn one option each, read through the named pins that exist; no
contract another repository reads changes, since every call without it works as before.

**What must be true.** An agent whose pinned CLI is downloaded as more than one artifact is checked and recorded by
`porelo-release` like one with a single artifact: `check-pin` checks every artifact the definition pins, and
`add-fetched-cli` records each in the package's bill as fetched, not shipped, so no repository bridges it in its own
test code.

Measured in porelo-codex: the Codex CLI ships two artifacts, `codex` and `codex-code-mode-host`. `check-pin --pin codex`
exits 2, since its usage takes only `--definition`, `--offline` and `--pom`, and it refuses a definition whose
`install.artifacts` holds anything but one entry. `add-fetched-cli` refuses a definition with two artifacts, because which
one is the CLI is not to be guessed. porelo-codex calls the bill recorder once per temporary single-artifact definition
in test scope until this is built.

## Plan

- **What porelo-codex declares**, read by its agent: two named pins, `porelo.release.pin.codex.*` and
  `porelo.release.pin.helper.*`, both with `property=agent.cli.version`, each with its own `digest` source and its own
  `sha256` property (`agent.cli.sha256`, `agent.helper.sha256`); the filtered definition holds two `install.artifacts`
  at that one version. So an artifact is told apart by its digest, which the named pin's `sha256` property holds.
- **`check-pin --pin <name>`** (`Main`, `CheckPin`): reads the pin by `Pin.of(pom, name)`, and from the definition the one
  artifact whose `sha256` equals the pom's value of the pin's `sha256` property, refused by name when none or two
  match; then the checks of today on that artifact: the definition's version is the pin's, the URL names it, the digest
  is the one `pin.digests()` reads as published, and the license as `Release.licenses()` declares it. Without `--pin`
  it is as today; on a definition with several artifacts it is refused naming the pins the pom declares, instead of
  "no complete pinned artifact". With `--pin`, nothing of the agent's own release configuration is read, since setting
  it only for the reader would weaken the check beside it: the definition is found by `porelo.release.definition` or
  named with `--definition`, the license by `porelo.release.license` where it is declared.
- **`add-fetched-cli <bill> <definition> <name> [--pin <pin>] [--pom <file>]`** (`Main`, `AddFetchedCli`): the same
  selection by the pin's digest picks the artifact recorded under `<name>`; porelo-codex calls it twice, `codex
  --pin codex` and `codex-code-mode-host --pin helper`. Without `--pin`, one artifact is recorded as today and several
  are refused, the message naming `--pin`.
- **Red first**: `AddFetchedCliTest` with a two-artifact definition and `--pin` records the selected artifact's URL and
  digest, red on today's refusal; `CheckPinTest` with the same definition and a `Web` answering each digest checks each
  pin's artifact, and one with a wrong digest for the second artifact fails naming it - red on today's exit 2.
- **The docs**: the two commands' lines in `doc/`, and a changelog entry under `[Unreleased]`.

## Acceptance

- In porelo-codex, its bridge removed: `check-pin --pin codex` and `--pin helper` each exit 0 against the published
  digests, and the bill made by two `add-fetched-cli --pin` calls names `codex` and `codex-code-mode-host` as fetched,
  the same as the bridge's bill by `compare-bills`.
- A definition whose digest matches no pin, or a pin whose digest matches two artifacts, is refused by name.
- Every call without `--pin` behaves as before: the existing tests of both commands pass unchanged.

## Read against the skills

- `clean-code`, `concurrency-review`, `graal`, `java-code-review`, `security-audit`, `solid-principles` and
  `test-quality`, each `2026.906.3` (`graal` `2026.910.3`). Fixed, each with its test seen red first: `--pom` without
  `--pin` was ignored, now refused; the pin's digest is read in one place, `Pin.heldDigest`.
- **Declined:** a pin whose version differs from the definition's. A definition holds one `install.version` for all
  its artifacts, so every pin of one is checked against that version; one with its own version is refused, never
  passed.
