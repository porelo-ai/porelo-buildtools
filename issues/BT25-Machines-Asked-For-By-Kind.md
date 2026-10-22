# BT25 — Machines Asked For By Kind

**Status:** Accepted.

**What must be true.** A caller of `porelo-machines` asks for a machine by kind, operating system, and account count, never by a provider's name or server types; the setup decides whether the local VM pool ([BT24](README.md#bt24)) or a cloud provider meets that kind; and a check in `porelo-buildtools` fails whenever a repository outside the provider implementation names a provider or its server types.

## Why

In `porelo-project` [PJ22](https://github.com/porelo-ai/porelo-project/blob/main/issues/README.md#pj22) (not read directly; quoted as given):

- "1. Kinds: `Spec` asks for a kind, the setup says who meets it; the module and workflows stop naming the provider. Seen to fail first: a check in `porelo-buildtools` that finds a provider's name outside its implementation, red on today's tree."
- "`porelo-machines` … rent a machine of a kind, run on it, return it, list what a run holds, delete what a run left behind … afterwards it asks for a kind, never for a provider's server types."
- Acceptance in `porelo-project` [PJ22](https://github.com/porelo-ai/porelo-project/blob/main/issues/README.md#pj22): "An acceptance leg runs on a rented VM account locally and on a rented server centrally, through the same interface call; no repository outside the interface's implementation names a provider (a check in `porelo-buildtools` finds a name that slips in)."

Today the module directory is named `hetzner/` (`pom.xml:71`), `Spec` carries a list of Hetzner server type names (`hetzner/src/main/java/org/fuin/porelo/machines/Spec.java:12-21`), `Main` opens `Hetzner` directly from `HETZNER_API` and `HETZNER_SSH` (`hetzner/src/main/java/org/fuin/porelo/machines/Main.java:32-35,50-54`), and workflows across eight repositories name `HETZNER_API`, `HETZNER_SSH`, `-Phetzner`, or `--type`.

## 1. Today's interface in `hetzner/`

Read in `hetzner/src/main/java/org/fuin/porelo/machines/`:

- **Module path.** `pom.xml:71` declares `<module>hetzner</module>`, while `hetzner/pom.xml:15` publishes `<artifactId>porelo-machines</artifactId>`.
- **`Spec` (`hetzner/src/main/java/org/fuin/porelo/machines/Spec.java`).**
  - Record component `List<String> serverTypes` (`Spec.java:13,21`), documented as "Hetzner types to try, in the order they should be tried" (`Spec.java:12`).
  - `Spec.DEFAULT_TYPES = List.of("cpx42", "cx43", "cpx32", "cx33")` (`Spec.java:48`), which `Spec.of(String name, String os, String user, Credential credential)` uses by default (`Spec.java:76-78`).
  - `Spec.tryingInOrder(List<String> types)` (`Spec.java:86-88`) and `Spec.order(@Nullable String types)` (`Spec.java:96-107`, with `"cx23,cx33,cpx12"` in its Javadoc at `Spec.java:93`).
- **`Machines` (`hetzner/src/main/java/org/fuin/porelo/machines/Machines.java`).**
  - Declares `acquire(Spec spec)` (`Machines.java:26`), `acquireFromStock(Spec spec, String image)` (`Machines.java:39`), `runId()` (`Machines.java:46`), and `close()` (`Machines.java:49`).
  - Its Javadoc names `{@link Hetzner}` and `{@link LocalVms}` (`Machines.java:8-10`).
  - Listing what a run holds (`servers()`, `Hetzner.java:751`), deleting what a run left behind (`deleteMine()`, `Hetzner.java:781`; `deleteRun(String)`, `Hetzner.java:798`; `sweep(boolean, boolean)`, `Hetzner.java:867`), setting the job time limit (`forJobsOf(Duration)`, `Hetzner.java:147`), shutting down a server (`shutdown(long)`, `Hetzner.java:705`), and taking a snapshot (`snapshot(...)`, `Hetzner.java:720`) are methods on `Hetzner`, not on `Machines`.
- **`Hetzner` (`hetzner/src/main/java/org/fuin/porelo/machines/Hetzner.java`).**
  - Public class implementing `Machines` (`Hetzner.java:32`), opened by callers with `Hetzner.with(String token, String runId)` (`Hetzner.java:132`).
- **`LocalVms` (`hetzner/src/main/java/org/fuin/porelo/machines/LocalVms.java`).**
  - Public class implementing `Machines` (`LocalVms.java:26`), opened with `LocalVms.fromEnvironment()` (`LocalVms.java:118`) or `LocalVms.of(String uri, Map<String, String> domains)` (`LocalVms.java:133`).
  - `acquire(Spec)` and `acquireFromStock(Spec, String)` ignore `spec.serverTypes()` and `image` and look up `domains.get(spec.os())` (`LocalVms.java:79,159-165`).
- **`Snapshots`, `AgentLeg`, and `Rental`.**
  - `Snapshots.BUILD_TYPES = List.of("cx23", "cpx22", "cx33")` (`hetzner/src/main/java/org/fuin/porelo/machines/Snapshots.java:48`). `Snapshots.build(Hetzner hetzner, String os, List<String> types, ...)` (`Snapshots.java:228,255`) and `Snapshots.current(Hetzner hetzner, String os, String inputs)` (`Snapshots.java:455`) take `Hetzner` directly.
  - `AgentLeg.Options` (`hetzner/src/main/java/org/fuin/porelo/machines/AgentLeg.java:65,78,101`) and `Rental.Options` (`hetzner/src/main/java/org/fuin/porelo/machines/Rental.java:47,56`) take `List<String> types`. `AgentLeg.run(Machines hetzner, ...)` (`AgentLeg.java:126`) and `Rental.run(Machines hetzner, ...)` (`Rental.java:70`) name their `Machines` parameter `hetzner`.
- **`Main` (`hetzner/src/main/java/org/fuin/porelo/machines/Main.java`).**
  - Reads `API_TOKEN = "HETZNER_API"` (`Main.java:32`) and `SSH_KEY = "HETZNER_SSH"` (`Main.java:35`).
  - `Main.main` wires `() -> Hetzner.with(token(System.getenv(API_TOKEN)), runId(...))` (`Main.java:50-54`), and `Main.run` takes `Supplier<Hetzner> open` (`Main.java:64`).
  - Commands and options (`Main.java:96-112`):
    - `sweep [--mine | --from <file>] [--now] [--including-in-use]` (`Main.java:96,141-209`): calls `hetzner.deleteRun`, `hetzner.deleteMine`, and `hetzner.sweep` (`Main.java:193,196,200`).
    - `snapshot --os <ubuntu|fedora> [--key <file>] [--repo <dir>] [--type <t>] [--unless-current] [--for <minutes>]` (`Main.java:97-98,220-285`): `--type` overrides `Snapshots.BUILD_TYPES` (`Main.java:233,279`).
    - `acceptance (--package <p> | --candidate <dir>) (--script <f> | --cucumber <dir>) [--os <o>] [--type <t,t...>] [--keep] [--accounts <n>] [--for <minutes>]` (`Main.java:99-101,296-383`): `--os` defaults to `"ubuntu"` (`Main.java:301`); `--accounts` defaults to `1` (`Main.java:306`); `--type` is parsed by `Spec.order(type)` or defaults to `Spec.DEFAULT_TYPES` when `accounts > 1` and `List.of("cpx12")` when `accounts == 1` (`Main.java:333,357-360`). There is no `--pool` option yet ([BT24](README.md#bt24) adds `--pool [<file>]`, `issues/BT24-An-Account-Rented-From-The-Local-Pool.md:29`).
    - `lease --os <ubuntu|fedora> [--key <file>] [--write <file>] [--candidate <dir>] [--type <t>] [--for <minutes>]` (`Main.java:102-105,520-566`): `--type` defaults to `"cpx12"` (`Main.java:525,541`). There is no `--pool` option yet.
    - `jdk [--into <dir>] [--github]` (`Main.java:106-109,395-449`) and `musl` (`Main.java:110-112,464-474`) do not open `Machines`.

## 2. Where callers name the provider or runner OS today

Searched across the twelve sibling checkouts under `/home/michi/git/` (`porelo`, `porelo-pi`, `porelo-omp`, `porelo-claude-code`, `porelo-agy`, `porelo-codex`, `porelo-message-matrix`, `porelo-message-sluice`, `porelo-intellij`, `porelo-frontend`, `porelo-host`, and `porelo-buildtools`) for `porelo-machines`, `HETZNER`, `--type`, server type names (`cx*`, `cpx*`, `ccx*`, `cax*`), and `runs-on:` with `windows` or `macos`. No workflow in the twelve repositories names `cax*` or `runs-on: macos*` today.

| Repository | File and line | What it asks for today |
|---|---|---|
| `porelo-buildtools` | `.github/workflows/build.yml:93-95,111-116,122-126` | Builds `-pl hetzner`, sets `HETZNER_API` and `HETZNER_SSH`, runs `org.fuin.porelo.machines.Main snapshot --os ${{ matrix.os }} --unless-current` (defaults to `Snapshots.BUILD_TYPES`: `cx23`, `cpx22`, `cx33`), then `sweep --mine`. |
| `porelo-buildtools` | `.github/workflows/machines.yml:72,84,86` | Runs `porelo-release` against `--pom hetzner/pom.xml` to move `<machines.*>` pins. |
| `porelo-buildtools` | `.github/actions/pinned-jdk/action.yml:45-48` | Builds `-pl hetzner` and runs `org.fuin.porelo.machines.Main jdk --github`. |
| `porelo-buildtools` | `.github/CODEOWNERS:11` | Guards `/hetzner/src/main/resources/org/fuin/porelo/machines/`. |
| `porelo` | `.github/workflows/build.yml:156-162,177-179` | Sets `HETZNER_API` and `HETZNER_SSH`; runs `exec:exec@legs` with `leg --os ${{ matrix.os }} --repo . --acceptance --accounts 4 ... ${{ vars.LEG_TYPES && format('--type {0}', vars.LEG_TYPES) || '' }}`; then `exec:exec@machines` with `sweep --mine`. |
| `porelo` | `.github/workflows/sweep.yml:29-31` | Sets `HETZNER_API` and runs `exec:exec@machines -Dmachines.args="sweep --now"`. |
| `porelo` | `.github/actions/pinned-jdk/action.yml:46` | Runs `exec:exec@machines -Dmachines.args="jdk --github"`. |
| `porelo` | `acceptance/legs/src/main/java/org/fuin/porelo/legs/Main.java:9,23,26,44-50,56,68,178,206,217-226` | Imports `Hetzner`; reads `HETZNER_API` and `HETZNER_SSH`; opens `Hetzner.with(...)`; parses `--type <t,t...>` and passes `Spec.order(types)` to `Leg.run`. |
| `porelo` | `acceptance/legs/src/main/java/org/fuin/porelo/legs/Leg.java:154-177` | Takes `Machines hetzner` and `List<String> types`, builds `Spec.of(...).tryingInOrder(types)`, and calls `hetzner.acquire(spec)`. |
| `porelo` | `acceptance/legs/src/test/java/org/fuin/porelo/legs/LegTypesTest.java:35` | Asserts that `.github/workflows/build.yml`'s `leg` command contains `vars.LEG_TYPES` and `--type`. |
| `porelo-pi` | `.github/workflows/build.yml:306-322,329-332` | Sets `HETZNER_API` and `HETZNER_SSH`; runs `org.fuin.porelo.machines.Main acceptance --os ${{ matrix.os }} --candidate packages --cucumber src/acceptance/resources` (1 account, defaults to `cpx12`); then `sweep --mine`. |
| `porelo-pi` | `.github/workflows/update.yml:175-183,192-200,207-216` | Sets `HETZNER_API` and `HETZNER_SSH`; runs `Main acceptance --os ubuntu` and `--os fedora` with `--candidate packages --cucumber src/acceptance/resources`; then `sweep --mine` twice. |
| `porelo-pi` | `.github/actions/pinned-jdk/action.yml:45-48` | Resolves `-Pci-tools` and runs `org.fuin.porelo.machines.Main jdk --github`. |
| `porelo-omp` | `.github/workflows/build.yml:273-289,296-299` | Sets `HETZNER_API` and `HETZNER_SSH`; runs `Main acceptance --os ${{ matrix.os }} --candidate packages --cucumber src/acceptance/resources` (1 account, defaults to `cpx12`); then `sweep --mine`. |
| `porelo-omp` | `.github/workflows/update.yml:221-228,237-244,252-261` | Sets `HETZNER_API` and `HETZNER_SSH`; runs `Main acceptance --os ubuntu` and `--os fedora`; then `sweep --mine` twice. |
| `porelo-omp` | `.github/actions/pinned-jdk/action.yml:45-48` | Resolves `-Pci-tools` and runs `org.fuin.porelo.machines.Main jdk --github`. |
| `porelo-claude-code` | `.github/workflows/build.yml:267-281,288-291` | Sets `HETZNER_API` and `HETZNER_SSH`; runs `Main acceptance --os ${{ matrix.os }} --candidate packages --cucumber src/acceptance/resources --accounts 2` (2 accounts, defaults to `Spec.DEFAULT_TYPES`: `cpx42,cx43,cpx32,cx33`); then `sweep --mine`. |
| `porelo-claude-code` | `.github/workflows/update.yml:230-237,246-253,261-270` | Sets `HETZNER_API` and `HETZNER_SSH`; runs `Main acceptance --os ubuntu` and `--os fedora` with `--accounts 2`; then `sweep --mine` twice. |
| `porelo-claude-code` | `.github/actions/pinned-jdk/action.yml:45-48` | Resolves `-Pci-tools` and runs `org.fuin.porelo.machines.Main jdk --github`. |
| `porelo-agy` | `.github/workflows/build.yml:217-228,235-238` | Sets `HETZNER_API` and `HETZNER_SSH`; runs `Main acceptance --os ${{ matrix.os }} --candidate packages --cucumber src/acceptance/resources` (1 account, defaults to `cpx12`); then `sweep --mine`. |
| `porelo-agy` | `.github/workflows/update.yml:222-229,238-245,253-262` | Sets `HETZNER_API` and `HETZNER_SSH`; runs `Main acceptance --os ubuntu` and `--os fedora`; then `sweep --mine` twice. |
| `porelo-agy` | `.github/actions/pinned-jdk/action.yml:45-48` | Resolves `-Pci-tools` and runs `org.fuin.porelo.machines.Main jdk --github`. |
| `porelo-codex` | `.github/workflows/build.yml:241-257,264-267` | Sets `HETZNER_API` and `HETZNER_SSH`; runs `Main acceptance --os ${{ matrix.os }} --candidate packages --cucumber src/acceptance/resources` (1 account, defaults to `cpx12`); then `sweep --mine`. |
| `porelo-codex` | `.github/workflows/update.yml:255-263,272-280,287-296` | Sets `HETZNER_API` and `HETZNER_SSH`; runs `Main acceptance --os ubuntu` and `--os fedora`; then `sweep --mine` twice. |
| `porelo-codex` | `.github/actions/pinned-jdk/action.yml:45-48` | Resolves `-Pci-tools` and runs `org.fuin.porelo.machines.Main jdk --github`. |
| `porelo-message-matrix` | `.github/workflows/build.yml:59-60`, `.github/workflows/shared-rules.yml:48-49` | Runs `org.fuin.porelo.machines.Main jdk --github` (rents no machine). |
| `porelo-message-sluice` | `.github/workflows/build.yml:57-58`, `.github/workflows/shared-rules.yml:49-50` | Runs `org.fuin.porelo.machines.Main jdk --github` (rents no machine). |
| `porelo-intellij` | `.github/workflows/windows.yml:23`, `.github/actions/pinned-jdk/action.yml:40`, `build.gradle.kts:63,256-260` | Job `windows` sets `runs-on: windows-2025`; Linux jobs run `./gradlew -q --no-daemon machines -Pmachines.args="jdk --github"` (rents no machine). |
| `porelo-frontend` | `.github/workflows/build.yml:203,353-355,383-391,403` | Job `windows` sets `runs-on: windows-2025`; job `integration` sets `HETZNER_API` and `HETZNER_SSH`, resolves `-Phetzner`, runs `./mvnw -B -s settings.xml verify -Pe2e,hetzner -DskipTests`, and runs `Main sweep --mine`. |
| `porelo-frontend` | `.github/workflows/windows.yml:20` | Job `windows` sets `runs-on: windows-2025`. |
| `porelo-frontend` | `.github/actions/pinned-jdk/action.yml:29` | Runs `org.fuin.porelo.machines.Main jdk --github`. |
| `porelo-frontend` | `pom.xml:256-325` | Profile `hetzner` (`hetzner-lease`, `hetzner-sweep`, `hetzner-done`): reads `HETZNER_KEY`, runs `Main lease --os ubuntu ... --write build/leased.properties` (defaults to `cpx12`), then `Main sweep --from build/leased.properties --now`. |
| `porelo-frontend` | `tool/e2e.sh:18,38,72,79` | Reads `HETZNER_KEY` and `HETZNER_SSH` when loading `build/leased.properties` into `ssh-agent`. |
| `porelo-host` | `.github/workflows/build.yml:36,82-126`, `.github/workflows/shared-rules.yml:35` | Runs `org.fuin.porelo.machines.Main jdk --github`; job `molecule` runs on `ubuntu-26.04` with `/dev/kvm` (`build.yml:90-97`) and rents nothing through `porelo-machines`. |

## 3. The kinds today's workflows need and what meets them

### What workflows ask for today

1. **amd64 Linux with KVM and rootless podman for package acceptance legs and leases.**
   - Used by `porelo-pi`, `porelo-omp`, `porelo-claude-code`, `porelo-agy`, `porelo-codex` (`Main acceptance`), and `porelo-frontend` (`Main lease`).
   - Needs `ubuntu` or `fedora` on amd64 with rootless podman (`hetzner/src/main/java/org/fuin/porelo/machines/AgentLeg.java:244` runs `podman run --rm` inside the leg) and 1 or 2 run accounts (`Main.java:360`).
2. **`porelo`'s build-and-acceptance leg (`ccx33` / `cpx42`).**
   - `porelo/.github/workflows/build.yml:162` runs `leg --os ${{ matrix.os }} --repo . --acceptance --accounts 4` and passes `--type` from `vars.LEG_TYPES`.
   - Why it needs a larger machine: the leg compiles six native images on the rented machine and runs the acceptance suite across four accounts (`hetzner/src/main/java/org/fuin/porelo/machines/Spec.java:43-45`, `porelo/.github/workflows/build.yml:162`). Two cores (`cx23`) stretched a leg to about 25 minutes (`hetzner/src/main/java/org/fuin/porelo/machines/Spec.java:38`, `porelo/doc/decisions.md:744-745`), so `Spec.DEFAULT_TYPES` (`Spec.java:34-39,48`) orders 8-core, 16 GB machines (`cpx42`, `cx43`) ahead of 4-core fallbacks (`cpx32`, `cx33`). Why the repository variable `LEG_TYPES` names `ccx33` is not written in a tracked file (`porelo/acceptance/legs/src/test/java/org/fuin/porelo/legs/LegTypesTest.java:14-18` records only that `vars.LEG_TYPES` lets a run choose server types without a commit after shared-vCPU types on a busy host ran out of the step time).
3. **Snapshot builder (`Snapshots.BUILD_TYPES`).**
   - `porelo-buildtools/.github/workflows/build.yml:115-116` runs `snapshot --os ${{ matrix.os }} --unless-current`.
   - Needs at least 4 GB RAM because `native-image` peaks above 3 GB resident and OOMs on 2 GB (`cpx12`), and must use the smallest disk size (40 GB) so any leg server type can restore the snapshot (`hetzner/src/main/java/org/fuin/porelo/machines/Snapshots.java:38-48`, `AGENTS.md:25-26`).
4. **Windows on GitHub-hosted runners (`runs-on: windows-2025`).**
   - Used by `porelo-intellij/.github/workflows/windows.yml:23` and `porelo-frontend/.github/workflows/build.yml:203` and `windows.yml:20`.
   - These jobs run directly on GitHub's runner and never call `porelo-machines` (`porelo-intellij/.github/workflows/windows.yml:34-35`). Windows should **stay outside** `porelo-machines`: `porelo-machines` rents Linux machines (or VM accounts) reached over SSH, whereas a Windows job is selected by the workflow runner label (`runs-on:`).
5. **arm64 later (`porelo-project` [PJ55](https://github.com/porelo-ai/porelo-project/blob/main/issues/README.md#pj55), not measured).**
   - `porelo-frontend` [F109](https://github.com/porelo-ai/porelo-frontend/blob/main/issues/README.md#f109) (`porelo-frontend/issues/F109-The-Interface-On-macOS-With-Porelo-In-A-Local-VM.md:24-25,47-50,172`) names Linux arm64 packages and an Apple silicon Mac rented through `porelo-machines` for release test windows (not measured).

### Proposed kinds and what meets them

`Spec` asks for a `Kind` (or `kind` + `os` + `accounts`), never a list of provider server types:

| Proposed kind | What the caller needs | What meets it in the local pool (`porelo-host` [HO12](https://github.com/porelo-ai/porelo-host/blob/main/issues/README.md#ho12) / [BT24](README.md#bt24)) | What meets it on Hetzner |
|---|---|---|---|
| `amd64` (or `leg`, 1 account) | amd64 Linux of `spec.os()` (`ubuntu` or `fedora`) with rootless podman and KVM, 1 account (`acceptance` with 1 account, `lease`) | One free run account on a VM entry in the `porelo-machines-pool/1` pool file (`porelo-host` [HO12](https://github.com/porelo-ai/porelo-host/blob/main/issues/README.md#ho12), [BT24](README.md#bt24)) whose `os` equals `spec.os()`, using that entry's address, host key, and run account. | Stock or snapshot server of `spec.os()`, server type `cpx12` (or the setup's configured single-account type list). |
| `amd64-build` (or `amd64` with `accounts > 1` / `build-leg`) | amd64 Linux of `spec.os()` able to build GraalVM native images and run multi-account acceptance (`porelo` `leg --accounts 4`, `porelo-claude-code` `acceptance --accounts 2`) | A VM entry in the `porelo-machines-pool/1` pool file (`porelo-host` [HO12](https://github.com/porelo-ai/porelo-host/blob/main/issues/README.md#ho12), [BT24](README.md#bt24)) of `spec.os()` with the requested number of free run accounts. | Snapshot server of `spec.os()`, tried in the order the Hetzner setup configures for multi-account or heavy legs (`cpx42,cx43,cpx32,cx33` by default, or `ccx33` when the setup's environment variable for heavy legs sets it). |
| `amd64-snapshot` (or internal to `Snapshots.build`) | amd64 Linux of `spec.os()` with >= 4 GB RAM and the smallest disk (`snapshot --os <os>`) | Refused (`snapshot` builds cloud images; local pool VMs are provisioned by `porelo-host` [HO12](https://github.com/porelo-ai/porelo-host/blob/main/issues/README.md#ho12)). | Stock server of `spec.os()` from `Snapshots.BUILD_TYPES` (`cx23,cpx22,cx33`, `Snapshots.java:48`). |
| `arm64` / `macos-arm64` (later, `porelo-project` [PJ55](https://github.com/porelo-ai/porelo-project/blob/main/issues/README.md#pj55), not measured) | arm64 Linux or Apple silicon macOS leg | Refused unless the pool file declares an arm64 entry (`porelo-host` [HO12](https://github.com/porelo-ai/porelo-host/blob/main/issues/README.md#ho12) writes only `os`, address, host key, and run accounts per VM today). | Provider mapping added when `porelo-project` [PJ55](https://github.com/porelo-ai/porelo-project/blob/main/issues/README.md#pj55) is built (not measured). |

Alternatively, if `Kind` names only the hardware/capability class (`amd64-kvm`, and later `arm64-kvm`, `macos-arm64`) and `Spec` carries `(String name, Kind kind, String os, int accounts, boolean buildNative, ...)`, or if `Kind` is `SMALL` vs `BUILD` on `amd64`, the mapping from `(Kind, accounts)` to `cpx12` vs `cpx42,cx43,cpx32,cx33` vs `ccx33` lives entirely inside `Hetzner` and its provider configuration.

## 4. The check that finds a provider's name outside its implementation

- **Where it lives and runs.** In `porelo-release` (`release/src/main/java/org/fuin/porelo/release/`), either as a new subcommand `check-providers <root>` in `release/src/main/java/org/fuin/porelo/release/Main.java:60` (bound to `validate` in `pom.xml` like `check-citations` and `check-actions`) or folded into `check-shared` / `check-actions`. Putting it in `check-shared` (or a `check-providers` step called everywhere `check-shared` runs) means every repository's `validate` phase and `Shared rules check` workflow runs it without adding a separate tool dependency.
- **What it searches.** Every committed text file under the repository root (listed via `git ls-files -z` as `CheckCitations.committed` does in `release/src/main/java/org/fuin/porelo/release/CheckCitations.java:359-384`), skipping:
  - the provider implementation itself in `porelo-buildtools` (`Hetzner.java`, `HetznerTest.java`, `StubApi.java`, and the provider package/tests);
  - `issues/`, `CHANGELOG.md`, and `changelog/` (which record historical changes and open migration issues), or files explicitly listed in an exemption file where `porelo` or `porelo-frontend` tests user-facing machine-file provider names (`porelo/apps/app/src/test/java/org/fuin/porelo/app/MachineFileTest.java:25`, `ProjectInventoryCommandTest.java:132`, `porelo-frontend/lib/src/ui/new_machine.dart:258`).
- **What patterns it refuses.**
  - Case-insensitive provider names: `\bhetzner\b`, `\bhcloud\b`, `HETZNER_API`, `HETZNER_SSH`, `HETZNER_KEY`.
  - Provider server type names: `\b(cpx|ccx|cx|cax)\d{2}\b` (such as `cpx12`, `cpx22`, `cpx32`, `cpx41`, `cpx42`, `cx22`, `cx23`, `cx33`, `cx43`, `ccx13`, `ccx33`).
- **Seen red on today's tree.** Running this check on today's committed files (outside `Hetzner.java`, `HetznerTest.java`, `StubApi.java`, `issues/`, and `changelog/`) finds at least these hits:
  - **`porelo-buildtools`**:
    - `pom.xml:71` (`<module>hetzner</module>`)
    - `.github/workflows/build.yml:93,94,95,111,112,115,122,125` (`hetzner`, `HETZNER_API`, `HETZNER_SSH`)
    - `.github/workflows/machines.yml:2,72,84,86` (`hetzner/pom.xml`)
    - `.github/actions/pinned-jdk/action.yml:3,45,46,48` (`hetzner`)
    - `.github/CODEOWNERS:11` (`/hetzner/...`)
    - `porelo.yml:14` (`hetzner/pom.xml`)
    - `AGENTS.md:7,22,29`, `README.md:16`, `CONTRIBUTING.md:14`, `doc/decisions.md:29` (`hetzner/`, `Hetzner`)
    - `hetzner/README.md:5,17,51` (`Hetzner`, `HETZNER_API`)
    - `hetzner/src/main/java/org/fuin/porelo/machines/package-info.java:2` (`Hetzner`)
    - `hetzner/src/main/java/org/fuin/porelo/machines/Machines.java:8` (`Hetzner`)
    - `hetzner/src/main/java/org/fuin/porelo/machines/Spec.java:12,29,34,35,37,38,48,93` (`Hetzner`, `cpx42`, `cx43`, `cpx32`, `cx33`, `cx23`, `cpx12`)
    - `hetzner/src/main/java/org/fuin/porelo/machines/Snapshots.java:39,48,221,228,230,245,255,280,294,352,353,449,455,458` (`Hetzner`, `cpx12`, `cx23`, `cpx22`, `cx33`)
    - `hetzner/src/main/java/org/fuin/porelo/machines/Main.java:31,32,35,50,64,156,169,193,196,200,220,227,260,264,277,296,308,360,374,376,520,525,528,557,559,690` (`Hetzner`, `HETZNER_API`, `HETZNER_SSH`, `cpx12`)
    - `hetzner/src/main/java/org/fuin/porelo/machines/AgentLeg.java:121,126,134,140` (`hetzner`)
    - `hetzner/src/main/java/org/fuin/porelo/machines/Rental.java:64,70,81,88,108` (`hetzner`)
    - `hetzner/src/main/java/org/fuin/porelo/machines/LocalVms.java:22` (`Hetzner`)
    - `hetzner/src/main/java/org/fuin/porelo/machines/Fingerprint.java:12` (`Hetzner`)
    - `hetzner/src/main/java/org/fuin/porelo/machines/Api.java:16,29` (`Hetzner`, `api.hetzner.cloud`)
    - `hetzner/src/test/java/org/fuin/porelo/machines/MainTest.java:36,90,104,121,171,239`, `LeaseTest.java:33,48,84,85,123,137,141,148,176,215,249,272,275,312,314,316,335,356,359,386,410,413,440,441,475,499,532,590,614`, `SnapshotsTest.java:85,88,312,313,316,339,340,343`, `RentalTest.java:57`, `FingerprintTest.java:22`, `HostKeyPairTest.java:108`
    - `release/src/test/java/org/fuin/porelo/release/PinTest.java:360` (`../hetzner/pom.xml`) and `CheckReleasesTest.java:118` (`<id>hetzner</id>`)
  - **`porelo`**:
    - `.github/workflows/build.yml:150,156,157,177` (`HETZNER_API`, `HETZNER_SSH`)
    - `.github/workflows/sweep.yml:29` (`HETZNER_API`)
    - `acceptance/legs/src/main/java/org/fuin/porelo/legs/Main.java:9,22,23,26,44,56,178,217,219` (`Hetzner`, `HETZNER_API`, `HETZNER_SSH`)
    - `acceptance/legs/src/main/java/org/fuin/porelo/legs/Leg.java:141,154,171,177` (`hetzner`)
    - `acceptance/legs/README.md:5`, `acceptance/kit/src/main/java/org/fuin/porelo/acceptance/SshRestart.java:13`, `AGENTS.md:344`, `agents/README.md:311`, `doc/decisions.md:116,742-748` (`Hetzner`, `cpx42`, `cx43`, `cpx32`, `cx33`, `cx23`)
  - **`porelo-pi`, `porelo-omp`, `porelo-claude-code`, `porelo-agy`, `porelo-codex`**:
    - `.github/workflows/build.yml` and `.github/workflows/update.yml` in each (`HETZNER_API`, `HETZNER_SSH`, and `Hetzner` in comments)
    - `porelo-pi/build.md:148,155,193`, `porelo-pi/src/main/resources/agent/pi.yaml:58`, `porelo-omp/src/main/resources/agent/omp.yaml:60`, `porelo-claude-code/build.md:188,195,234`, `porelo-claude-code/src/main/resources/agent/claude.yaml:86` (`Hetzner`, `HETZNER_API`, `HETZNER_SSH`, `cpx12`)
  - **`porelo-frontend`**:
    - `.github/workflows/build.yml:353,355,383,391` (`HETZNER_API`, `HETZNER_SSH`, `-Phetzner`, `-Pe2e,hetzner`)
    - `pom.xml:249,250,256,270,272,281,287,308` (`hetzner`, `HETZNER_API`, `HETZNER_SSH`, `HETZNER_KEY`, `hetzner-lease`, `hetzner-sweep`, `hetzner-done`)
    - `tool/e2e.sh:18,38,72,79` (`-Phetzner`, `HETZNER_KEY`, `HETZNER_SSH`)
    - `AGENTS.md:467` (`-Pe2e,hetzner`)
  - **`porelo-host`**:
    - `porelo.yml:11` (`hetzner/pom.xml` in comment)

## 5. Which repositories' workflows and build files change

Eight repositories have workflows, actions, or build files that change (per-repository follow-up issues are not filed now):

1. **`porelo-buildtools`**: `.github/workflows/build.yml`, `.github/workflows/machines.yml`, `.github/actions/pinned-jdk/action.yml`, `.github/CODEOWNERS`, `pom.xml`, and `hetzner/` (renamed to `machines/`).
2. **`porelo`**: `.github/workflows/build.yml`, `.github/workflows/sweep.yml`, `acceptance/legs/src/main/java/org/fuin/porelo/legs/Main.java`, `acceptance/legs/src/main/java/org/fuin/porelo/legs/Leg.java`, `acceptance/legs/src/test/java/org/fuin/porelo/legs/LegTypesTest.java`.
3. **`porelo-pi`**: `.github/workflows/build.yml`, `.github/workflows/update.yml`.
4. **`porelo-omp`**: `.github/workflows/build.yml`, `.github/workflows/update.yml`.
5. **`porelo-claude-code`**: `.github/workflows/build.yml`, `.github/workflows/update.yml`.
6. **`porelo-agy`**: `.github/workflows/build.yml`, `.github/workflows/update.yml`.
7. **`porelo-codex`**: `.github/workflows/build.yml`, `.github/workflows/update.yml`.
8. **`porelo-frontend`**: `.github/workflows/build.yml`, `pom.xml`, `tool/e2e.sh`.

The workflows of `porelo-message-matrix`, `porelo-message-sluice`, `porelo-intellij`, and `porelo-host` only call `porelo-machines jdk --github` and do not name a provider or server type (`porelo-host/porelo.yml:11` has a comment naming `hetzner/pom.xml`).

## 6. Plan in parts and suggested size

**Suggested size:** **L** — changes the public API and CLI of `porelo-machines` (`Spec`, `Machines`, `Main`, `AgentLeg`, `Rental`, `Snapshots`), renames the `hetzner/` module directory, adds the provider-name check in `porelo-release`, and changes the command-line and environment contract that seven other repositories call.

### Part 1 — Provider-name check in `porelo-release` (red first)

- Add the check to `porelo-release` that scans committed text files for provider names (`hetzner`, `hcloud`, `HETZNER_*`) and server type names (`cpx*`, `ccx*`, `cx*`, `cax*`) outside the provider implementation and exemptions.
- Watch it fail on `porelo-buildtools` and on the sibling repositories' checkouts before changing `porelo-machines`.

### Part 2 — `Kind`, `Spec`, and `Machines` in `porelo-machines`

- Introduce `Kind` (such as `amd64`, `amd64-build`, `amd64-snapshot`) and replace `List<String> serverTypes`, `Spec.DEFAULT_TYPES`, `Spec.tryingInOrder`, and `Spec.order` on `Spec` (`hetzner/src/main/java/org/fuin/porelo/machines/Spec.java:12-107`) with `Kind`.
- Move `sweep`, `deleteMine`, `deleteRun`, and `forJobsOf` onto `Machines` (`hetzner/src/main/java/org/fuin/porelo/machines/Machines.java:17-50`), so callers and `Main` hold `Machines`, never `Hetzner`.
- Move `Spec.DEFAULT_TYPES` and `Snapshots.BUILD_TYPES` into `Hetzner` (and its provider configuration), where a `Kind` and account count map to Hetzner server types.
- Have `LocalPool` ([BT24](README.md#bt24)) and `LocalVms` match a `Spec` by `kind`, `os`, and `accounts`.

### Part 3 — Provider-neutral `Main` CLI, environment variables, and module directory `machines/`

- Rename module directory `hetzner/` to `machines/` (`pom.xml:71`, `.github/workflows/build.yml`, `.github/workflows/machines.yml`, `.github/actions/pinned-jdk/action.yml`, `.github/CODEOWNERS`).
- Replace `HETZNER_API` and `HETZNER_SSH` in `Main` (`hetzner/src/main/java/org/fuin/porelo/machines/Main.java:32-35`) with provider-neutral environment variables (or a setup configuration / `--pool` from [BT24](README.md#bt24)), keeping the old variable names accepted during the transition until all seven caller repositories move.
- Replace `--type` on `Main acceptance`, `Main lease`, and `Main snapshot` (`Main.java:97-105,233,333,541`) with `--kind` (defaulting to the standard leg/lease/snapshot kind), and remove `Hetzner` from `AgentLeg` and `Rental` parameter names.
- Make `porelo-buildtools` pass the new provider-name check.

### Part 4 — Caller repositories' workflows and `porelo`'s `acceptance/legs`

- Update `porelo`'s `acceptance/legs` (`Main.java`, `Leg.java`, `LegTypesTest.java`) to open `Machines` through `porelo-machines`' setup factory and ask for the build-leg kind instead of `Hetzner` and `--type`.
- Update the workflows and `pom.xml` / `tool/e2e.sh` in `porelo`, `porelo-pi`, `porelo-omp`, `porelo-claude-code`, `porelo-agy`, `porelo-codex`, and `porelo-frontend` to use the provider-neutral environment variables, profile name, and `--kind`, then drop the transition fallback in `porelo-machines`.

## Acceptance

- An acceptance leg runs on a rented VM account locally ([BT24](README.md#bt24)) and on a rented server centrally through the same `Machines` and `Spec` call, asking for a kind and never for server types.
- The check in `porelo-buildtools` fails when a provider name or server type is placed outside the provider implementation, is seen red on the tree before the migration, and passes on `porelo-buildtools` and every migrated repository.

## To be checked

- **Exact `Kind` names and whether the `porelo-machines-pool/1` pool file adds a `kind` field.** In `porelo-host` [HO12](https://github.com/porelo-ai/porelo-host/blob/main/issues/README.md#ho12) and [BT24](README.md#bt24), the pool file writes `os`, address, host key, and run accounts for each VM. Whether `LocalPool` treats every `porelo-machines-pool/1` entry as meeting `amd64` / `amd64-build` or whether the pool file records `kind` explicitly must be agreed with `porelo-host` [HO12](https://github.com/porelo-ai/porelo-host/blob/main/issues/README.md#ho12) and [BT24](README.md#bt24).
- **Environment variable names for the central setup.** Which provider-neutral names replace `HETZNER_API`, `HETZNER_SSH`, `HETZNER_KEY`, and `vars.LEG_TYPES` in CI secrets and workflows (for example `PORELO_MACHINES_API` and `PORELO_MACHINES_SSH`, or a setup variable that selects the provider).
- **How `porelo`'s 4-account leg runs against the local pool.** `porelo`'s `leg` asks for `root` to set up the `build` user and `--accounts 4` (`porelo/.github/workflows/build.yml:162`, `porelo/acceptance/legs/src/main/java/org/fuin/porelo/legs/Leg.java:36-39,169-176`), whereas [BT24](README.md#bt24) rents unprivileged run accounts from `porelo-host` [HO12](https://github.com/porelo-ai/porelo-host/blob/main/issues/README.md#ho12); how `Leg` maps its build and acceptance accounts onto rented pool accounts is not measured.
