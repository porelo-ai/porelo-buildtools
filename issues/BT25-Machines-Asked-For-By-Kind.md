# BT25 — Machines Asked For By Kind

**Status:** Implementing.
**Size:** L - the module and `Spec` by kind (M), a provider-name check (S), and the old call routed onto the new one with its test (S); the callers move later, through their own issues.

**What must be true.** A caller of `porelo-machines` asks for a machine by kind, operating system, and account count, never by a provider's name or server types; the setup decides whether the local VM pool ([BT24](README.md#bt24)) or a cloud provider meets that kind; and a check in `porelo-buildtools` fails whenever a repository outside the provider implementation names a provider or its server types.

## Why

In `porelo-project` [PJ22](https://github.com/porelo-ai/porelo-project/blob/main/issues/README.md#pj22) (not read directly; quoted as given):

- "1. Kinds: `Spec` asks for a kind, the setup says who meets it; the module and workflows stop naming the provider. Seen to fail first: a check in `porelo-buildtools` that finds a provider's name outside its implementation, red on today's tree."
- "`porelo-machines` … rent a machine of a kind, run on it, return it, list what a run holds, delete what a run left behind … afterwards it asks for a kind, never for a provider's server types."
- Acceptance in `porelo-project` [PJ22](https://github.com/porelo-ai/porelo-project/blob/main/issues/README.md#pj22): "An acceptance leg runs on a rented VM account locally and on a rented server centrally, through the same interface call; no repository outside the interface's implementation names a provider (a check in `porelo-buildtools` finds a name that slips in)."

Today the module directory is named `hetzner/` (`pom.xml:72`), `Spec` carries a list of Hetzner server type names (`Spec.java`), `LocalPool.tryAccount` forwards `spec.serverTypes()` (`LocalPool.java:219`), `Main` opens `Hetzner` directly from `HETZNER_API` and `HETZNER_SSH` (`Main.java:34-37,55`), and workflows across seven repositories (`porelo-buildtools` and six callers) name `HETZNER_API`, `HETZNER_SSH`, `-Phetzner`, or `--type`.

## Plan

- **Backward compatible (decided):** the old call, with a provider and server types, keeps working. Inside
  `porelo-machines` it is routed onto the by-kind function, so no caller changes now. Only porelo-message-sluice moves
  to kinds now, through its own issue under porelo-project
  [PJ60](https://github.com/porelo-ai/porelo-project/blob/main/issues/README.md#pj60). The old call and the routing are
  removed once every caller has moved, through the issue for each repository under [PJ60](https://github.com/porelo-ai/porelo-project/blob/main/issues/README.md#pj60).
  - **Red first:** a test that the old call, with a provider and server types, lands on the by-kind function with the
    kind those types map to. It fails while the old call still opens the provider directly.
- **Part 1 — Provider-name check in `porelo-release` (Size S, red first).**
  - Add `CheckProviders` in `release/src/main/java/org/fuin/porelo/release/` (wired into `Main`, `pom.xml` `validate`, and `.github/workflows/shared-rules.yml`, and listed in `DocumentTestsTaggedTest`) scanning git-tracked text files via `Tracked.filesIn`.
  - Refuse case-insensitive provider names (`\bhetzner\b`, `\bhcloud\b`, `HETZNER_API`, `HETZNER_SSH`, `HETZNER_KEY`) and Hetzner server type names (`cpx12`, `cpx22`, `cpx32`, `cpx41`, `cpx42`, `cx22`, `cx23`, `cx33`, `cx43`, `ccx13`, `ccx33`, or `(?<!#)\b(cpx|ccx|cax)\d{2}\b`). Matching the explicit `cx` server types (or requiring `[2-5]\d` after `cx` and no leading `#`) avoids false positives on the `cx16` CPU flag in `porelo-host/tests/unit/roles/cpuinfo/*.txt` and on `porelo-codex` issue anchors such as `#cx01` and `#cx02`.
  - Skip the provider implementation and its tests in `porelo-buildtools` (`Hetzner.java`, `Api.java`, `Fingerprint.java`, `HetznerTest.java`, `StubApi.java`, `FingerprintTest.java`, `HostKeyPairTest.java`), `issues/`, `CHANGELOG.md`, `changelog/`, and files listed in an exemption file (such as `porelo-message-sluice/filter/app/src/test/resources/english-corpus.txt`, `porelo` machine-file tests, and `porelo-frontend/lib/src/ui/new_machine.dart`).
  - It is a command of its own, `porelo-release check-providers`, run in porelo-buildtools' `validate` and *Shared rules
    check* only. Each caller adds it in the commit that moves it to kinds, and it joins the shared set once the last
    caller has moved; there is no per-repository exemption for what has not moved yet.
  - **Red first:** `CheckProvidersTest` (with `@Tag("documents")` on its self-check) fails on `porelo-buildtools` (`pom.xml:72`, `.github/workflows/build.yml`, `Spec.java`, `Main.java`, `LocalPool.java:219`, `Snapshots.java`) and on a temporary repository containing `HETZNER_API`, `"cpx12"`, and `<id>hetzner</id>`.
- **Part 2 — Module `machines/`, `Kind`, `Spec`, `Machines`, and `Main` (Size M).**
  - Rename module directory `hetzner/` to `machines/` in `pom.xml:72`, `machines/pom.xml`, `.github/workflows/build.yml`, `.github/workflows/machines.yml`, `.github/actions/pinned-jdk/action.yml`, `.github/CODEOWNERS`, `porelo.yml`, `AGENTS.md`, `README.md`, `CONTRIBUTING.md`, `doc/decisions.md:34`, `machines/README.md`, and `PinTest.java:360`.
  - Introduce `Kind`: `amd64` (1-account acceptance leg or `lease`), `amd64-build` (multi-account or native-image build leg), and `amd64-snapshot` (snapshot build). Windows jobs (`runs-on: windows-2025` in `porelo-intellij` and `porelo-frontend`) run on GitHub-hosted runners without `porelo-machines` and stay outside `Kind`.
  - Make `Kind` and the account count the request in `Spec`, `AgentLeg.Options`, `Rental.Options`, and `Snapshots.build`; keep the previous constructors, type accessors, constants, and functions as deprecated adapters carrying their explicit type order as a provider override. The one-account default maps to `amd64`, the snapshot-build order to `amd64-snapshot`, and other explicit orders to `amd64-build`; explicit types always win over the kind's defaults. Snapshot operations always request `amd64-snapshot`, including previous explicit type overrides, and refuse reusable kinds before provisioning can reach a local disk.
  - Move `forJobsOf`, `deleteMine`, `deleteRun`, `sweep`, and a setup factory onto `Machines` so `Main` and callers hold `Machines`, never `Hetzner`; remove `{@link Hetzner}` from `Machines` and `LocalVms` Javadoc and `hetzner` parameter names from `AgentLeg` and `Rental`.
  - Move `Spec.DEFAULT_TYPES` (`cpx42,cx43,cpx32,cx33`), `cpx12`, and `Snapshots.BUILD_TYPES` (`cx23,cpx22,cx33`) into `Hetzner`, mapping `(spec.kind(), accounts)` and any provider override for heavy legs to server types in `Hetzner.acquire`.
  - Update `LocalPool` ([BT24](README.md#bt24), already in `hetzner/` with `Pool` and `acceptance --pool <file>`) so `LocalPool.tryAccount` (`LocalPool.java:219`) passes `spec.kind()` instead of `spec.serverTypes()`, and `LocalPool.acquire` and `LocalVms.acquire` check `spec.kind()` and refuse `amd64-snapshot`. (`lease` has no `--pool`, because file locks end with the process.)
  - Every `porelo-machines-pool/1` entry supplies `amd64` and `amd64-build` without a format change; local sources refuse `amd64-snapshot` before touching a VM or account. Credentials use `PORELO_MACHINES_API` and `PORELO_MACHINES_SSH`, with the previous names read when the current ones are missing or blank.
  - In `Main`, take `Supplier<Machines>`, add `--kind` beside deprecated `--type` on `snapshot`, `acceptance`, and `lease`, and replace `HETZNER_API` and `HETZNER_SSH` (`Main.java:34-37`) with provider-neutral environment variables (keeping the old names as a fallback until the six caller repositories move). Update `MainTest`, `LeaseTest`, `LocalVmsTest`, `LocalPoolTest`, `AgentLegTest`, `RentalTest`, `SnapshotsTest`, `HetznerTest`, and `CheckReleasesTest`.
- **Part 3 — Six caller repositories, later, each through its own issue under [PJ60](https://github.com/porelo-ai/porelo-project/blob/main/issues/README.md#pj60); nothing of them is built here** (what each will change):
  - **`porelo` (Agent Core):** `.github/workflows/build.yml:150-179`, `.github/workflows/sweep.yml:29-31`, `acceptance/legs/src/main/java/org/fuin/porelo/legs/Main.java` (drop `Hetzner`, `HETZNER_API`, `HETZNER_SSH`, `--type`, `Spec.DEFAULT_TYPES`, `Spec.order`), `Leg.java:154-177` (replace `new Spec(..., types, ...)` with `Kind`), and `LegTypesTest.java`.
  - **`porelo-frontend` (Agent Frontend):** `.github/workflows/build.yml:370-420`, `pom.xml:249-308` (profile `hetzner` and `Main lease`), `tool/e2e.sh:18,38,72,79`, and `AGENTS.md:475`.
  - **`porelo-claude-code` (Agent Smith):** `.github/workflows/build.yml:267-290` and `.github/workflows/update.yml:230-270` (`Main acceptance` with 1 account and `Main sweep --mine`), plus `build.md` and `claude.yaml`.
  - **`porelo-pi` (Agent Smith):** `.github/workflows/build.yml:306-331` and `.github/workflows/update.yml:175-216`, plus `build.md` and `pi.yaml`.
  - **`porelo-omp` (Agent Smith):** `.github/workflows/build.yml:240,273-298` and `.github/workflows/update.yml:221-261`, plus `omp.yaml`.
  - **`porelo-agy` (Agent AGY):** `.github/workflows/build.yml:220-241` (`update.yml` only runs `porelo-release check-pin`).
  - `porelo-codex`, `porelo-message-sluice`, `porelo-message-matrix`, `porelo-intellij`, `porelo-host`, and `porelo-adapters` only call `Main jdk --github` in workflows (`porelo-host/porelo.yml:11` has a comment naming `hetzner/pom.xml`).

## Acceptance

- An acceptance leg runs on a rented VM account locally ([BT24](README.md#bt24)) and on a rented server centrally through the same `Machines` and `Spec` call, asking for a kind and never for server types.
- A caller left on the old call, with a provider and server types, still gets its machine, through the by-kind function.
- The check in `porelo-buildtools` fails when a provider name or server type is placed outside the provider implementation, is seen red on the tree before the migration, and passes on `porelo-buildtools` and every migrated repository.

## To be checked

- **How `porelo`'s 4-account leg runs against the local pool.** `porelo`'s `leg` asks for `root` to set up the `build` user and `--accounts 4` (`porelo/.github/workflows/build.yml:162`, `porelo/acceptance/legs/src/main/java/org/fuin/porelo/legs/Leg.java:36-39,169-176`), whereas [BT24](README.md#bt24) rents unprivileged run accounts from `porelo-host` [HO12](https://github.com/porelo-ai/porelo-host/blob/main/issues/README.md#ho12); how `Leg` maps its build and acceptance accounts onto rented pool accounts is not measured.

- **Full reactor validation.** The API stub binds a loopback HTTP server; the current sandbox refuses that socket with `Operation not permitted`, so its provider integration tests need a runner that permits loopback sockets.
