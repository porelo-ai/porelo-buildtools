# Issues

The open tasks of `porelo-buildtools`, grouped by state; the order within a group is the priority. The rules for an
issue, and who sets which state, are in [AGENTS.md](../AGENTS.md). *Open questions*, in *Analyzed* only, counts what
waits on the operator: the items under an issue's own *Open questions*. Its prefix is `BT`. A group with nothing in it is
left out.

## Review

| # | Size | Blocked by | What it covers |
|---|---|---|---|
| <a id="bt26"></a>[BT26](BT26-A-Release-Planned-In-Sprints-Of-Tested-Functions.md) | M | - | `porelo-release plan render` and `plan check`: a release's page from its plan file and every index, five faults refused; porelo-project [PJ63](https://github.com/porelo-ai/porelo-project/blob/main/issues/README.md#pj63)'s build part. |
| <a id="bt20"></a>[BT20](BT20-Bills-Read-With-A-Current-CycloneDX-Library.md) | S | - | porelo-release on `cyclonedx-core-java` 11.0.1 and `commons-lang3` 3.18.0, from the parent; built, proven on real bills. |
| <a id="bt21"></a>[BT21](BT21-Jackson-Without-Its-Known-Advisories.md) | XS | - | porelo-release on Jackson 2.22.3, past the advisories against 2.21.2. |
| <a id="bt12"></a>[BT12](BT12-A-Rented-Machine-Carries-Its-Expiry.md) | M | - | Every lease labels its server `porelo-expires`, from the job's time limit plus a margin; `sweep` deletes only servers past their expiry or without one, refuses `--older-than`, and deletes servers in use only with `--including-in-use`; `porelo`'s `sweep.yml` moves with it; built here, left: `porelo`'s `sweep.yml` and a run of the hourly sweep. |
| <a id="bt11"></a>[BT11](BT11-A-Rented-Machine-Answers-With-Its-Own-Key.md) | M | - | A rented machine gets a host key made per run in its `user_data`, and `Ssh` accepts only that key for `porelo`'s legs, the agent legs and the kit; the local VMs and `deploy` accepting any key is a written risk; built here, left: `porelo`'s legs and kit, and a rented run of each leg kind. |
| <a id="bt16"></a>[BT16](BT16-A-Snapshot-Built-Only-From-New-Inputs.md) | S | - | A CI snapshot is built only when the newest one was made from other inputs - the recipe with its pins, the toolchain, the code - named as a label on the image; a dispatch always builds. |
| <a id="bt14"></a>[BT14](BT14-The-Newest-Release-That-Has-Stood.md) | S | - | A release tool takes the newest release at least `porelo.release.min-age` old, even where newer, younger ones exist; every upstream source reads its history for it. |
| <a id="bt22"></a>[BT22](BT22-A-Pin-With-Several-Artifacts.md) | S | - | `check-pin` and `add-fetched-cli` take `--pin <name>` and pick the artifact by that pin's digest, so the Codex CLI's two artifacts are checked and recorded without a bridge. |

## Implementing

| # | Size | Blocked by | What it covers |
|---|---|---|---|
| <a id="bt27"></a>[BT27](BT27-The-Release-Plan-Page-Drawn-From-Templates.md) | M | - | The plan's HTML page from jte templates and its Markdown page built like it, with one progress image, sprints in boxes and the inputs' commit time as Stand. |
| <a id="bt24"></a>[BT24](BT24-An-Account-Rented-From-The-Local-Pool.md) | M | - | `porelo-machines` rents one run account of the host's local VM pool per run from the file porelo-host's `vm_pool` writes, waits or refuses beyond the pool's size, and resets the account when it is given back. |
| <a id="bt17"></a>[BT17](BT17-Commit-Messages-Checked-Before-And-On-A-Push.md) | M | - | `porelo-release check-commits <range>` holds each commit to one line of at most 72 characters, no issue number and no who; run before a push over `origin/main..HEAD` and in each Build over the pushed range; GitHub's and Dependabot's own merges exempt; built here, left: each repository's pre-push routine and Build. |
| <a id="bt13"></a>[BT13](BT13-Action-Moves-Merged-Without-A-Build.md) | S | - | The template every repository takes: a push of only `.github/` starts no Build (in everywhere since wave 1), and a light pull-request check with no rented machine that the operator requires on `main` before merging an action move by hand; no auto-merge, decided 2026-10-18. |
| <a id="bt15"></a>[BT15](BT15-Legs-Taken-From-A-Proven-Run.md) | M | - | A Build on `main` takes its legs from a run of the same tree, from a trusted event, at most 24 h old, with its leg jobs green: `porelo-release proven-legs`, built; each Build adopts it. |
| <a id="bt10"></a>[BT10](BT10-A-Leg-Takes-No-IPv6-Address.md) | S | - | Every server is created with an IPv4 address and no IPv6 one, one primary IP instead of two; built with its test seen red, the full-quota answer read from the API's reference; left: the leg kinds green on such servers in the Builds after the push. |
| <a id="bt04"></a>[BT04](BT04-Issue-Numbers-Link-Their-Index-Row.md) | M | - | `check-citations` holds issue numbers under `issues/` to links to their index rows, every row to its anchor, every link to an anchor that is there; built, pushed after every repository's anchors. |

## Backlog

| # | Size | Blocked by | What it covers |
|---|---|---|---|
| <a id="bt01"></a>[BT01](BT01-A-Release-Leg-Installs-The-Release.md) | S | - | A release leg installs Porelo from the channel the run publishes to, and `acceptance --candidate` needs no package name. |
| <a id="bt03"></a>[BT03](BT03-A-Rebuilt-Snapshot-Removes-Its-Predecessors.md) | S | - | A new CI snapshot, once available, deletes the older `porelo=ci` snapshots of its `os`, keeping one before it; nothing else is touched, each deletion said. |

## Accepted

| # | Size | Blocked by | What it covers |
|---|---|---|---|
| <a id="bt06"></a>[BT06](BT06-Push-Mirrors-Started-Through-The-Forge.md) | - | - | The local forge's push mirrors to the central forge run only when started through its API, staggered; the API token stays on the host. |
| <a id="bt09"></a>[BT09](BT09-A-Limit-Wait-Names-The-Limit.md) | - | - | The waiting line at `resource_limit_exceeded` says which limit the API names (dedicated vCPUs, shared vCPUs, primary IPs), not only the server limit. |
| <a id="bt25"></a>[BT25](BT25-Machines-Asked-For-By-Kind.md) | L | - | `Spec` and `Main` ask for a machine by kind instead of a provider's server types, the setup chooses the local pool or the cloud provider, and a check in `porelo-buildtools` refuses a provider's name outside its implementation. |

## Idea

| # | Size | Blocked by | What it covers |
|---|---|---|---|
| <a id="bt18"></a>[BT18](BT18-A-Runner-Named-By-Its-Version.md) | - | - | `check-actions` refuses `runs-on: ubuntu-latest` and the other moving labels, naming file and job; a runner named by its version passes. |
| <a id="bt23"></a>[BT23](BT23-One-Upload-Helper-For-Every-Publish.md) | - | - | One upload helper for Artifactory, published here and taken by digest in every Publish job, in place of the five copies of `.github/scripts/artifactory.sh` (porelo, porelo-pi, porelo-omp, porelo-claude-code, porelo-message-matrix) and the own curl calls of porelo-message-sluice, porelo-intellij and porelo-frontend. |

## Finished

| # | Title | What was built |
|---|---|---|
| <a id="bt19"></a>BT19 | A Component Told Apart By Its Group | `add-component` matches a recorded component on group and name, so npm's `@types/node` and the Node runtime stand side by side in a tree's bill. |
| <a id="bt05"></a>BT05 | The Code Read Against The Skills It Names | Every module read against the seven skills `AGENTS.md` names, each finding fixed with a test seen red or declined, and its three questions decided. |
| <a id="bt02"></a>BT02 | Check Actions Reads Permissions | `check-actions` reads `permissions:`: one at the top of every workflow, read or nothing, `write` only in a job its list names, anything else refused by name. |
| <a id="bt07"></a>BT07 | Renamed Porelo Here | The tools are published as `org.fuin.porelo` `0.5.0`, and what they rent, label and install is named Porelo, the CI snapshots labelled `porelo=ci`; a documents test refuses the old word. |
| <a id="bt08"></a>BT08 | Parent Renamed Porelo Parent | `org.fuin.porelo:porelo-parent` `0.2.0`, naming the tools as `org.fuin.porelo` `0.5.0`. |
