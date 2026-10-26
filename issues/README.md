# Issues

The open tasks of `porelo-buildtools`, grouped by state; the order within a group is the priority. The rules for an
issue, and who sets which state, are in [AGENTS.md](../AGENTS.md). *Open questions*, in *Analyzed* only, counts what
waits on the operator: the items under an issue's own *Open questions*. Its prefix is `BT`. A group with nothing in it is
left out.

## Review

| # | Size | Blocked by | What it covers |
|---|---|---|---|
| <a id="bt10"></a>[BT10](BT10-A-Leg-Takes-No-IPv6-Address.md) | S | - | Every server is created with an IPv4 address and no IPv6 one, one primary IP instead of two; built with its test seen red, the full-quota answer read from the API's reference; every leg kind green on such servers, and one IPv4 primary IP per server read from the API. |
| <a id="bt13"></a>[BT13](BT13-Action-Moves-Merged-Without-A-Build.md) | S | - | The template every repository takes: a push of only `.github/` starts no Build (in everywhere since wave 1), and a light pull-request check with no rented machine that an action move is merged on by hand, not a required check; no auto-merge. |
| <a id="bt25"></a>[BT25](BT25-Machines-Asked-For-By-Kind.md) | L | - | `Spec` and `Main` ask for a machine by kind instead of a provider's server types, the setup chooses the local pool or the cloud provider, and a check in `porelo-buildtools` refuses a provider's name outside its implementation; the old call keeps working, routed onto the new one, until every caller has moved. |
| <a id="bt24"></a>[BT24](BT24-An-Account-Rented-From-The-Local-Pool.md) | M | - | `porelo-machines` rents one run account of the host's local VM pool per run from the file porelo-host's `vm_pool` writes, waits or refuses beyond the pool's size, and resets the account when it is given back. |

## Implementing

| # | Size | Blocked by | What it covers |
|---|---|---|---|
| <a id="bt17"></a>[BT17](BT17-Commit-Messages-Checked-Before-And-On-A-Push.md) | M | - | `porelo-release check-commits <range>` holds each commit to one line of at most 72 characters, no issue number and no who; run before a push over `origin/main..HEAD` and in each Build over the pushed range; GitHub's and Dependabot's own merges exempt; built here, left: each repository's pre-push routine and Build. |

## Backlog

| # | Size | Blocked by | What it covers |
|---|---|---|---|
| <a id="bt01"></a>[BT01](BT01-A-Release-Leg-Installs-The-Release.md) | S | - | A release leg installs Porelo from the channel the run publishes to, and `acceptance --candidate` needs no package name. |
| <a id="bt03"></a>[BT03](BT03-A-Rebuilt-Snapshot-Removes-Its-Predecessors.md) | S | - | A new CI snapshot, once available, deletes the older `porelo=ci` snapshots of its `os`, keeping one before it; nothing else is touched, each deletion said. |

## Accepted

| # | Size | Blocked by | What it covers |
|---|---|---|---|
| <a id="bt30"></a>[BT30](BT30-Check-Actions-Reads-Workflows-As-YAML.md) | - | - | `check-actions` judges permissions, pins and `persist-credentials` on the parsed YAML, so a quoted or flow-style form meets the same refusals as the plain one. |
| <a id="bt09"></a>[BT09](BT09-A-Limit-Wait-Names-The-Limit.md) | - | - | The waiting line at `resource_limit_exceeded` says which limit the API names (dedicated vCPUs, shared vCPUs, primary IPs), not only the server limit. |

## Idea

| # | Size | Blocked by | What it covers |
|---|---|---|---|
| <a id="bt33"></a>[BT33](BT33-Machines-From-Providers-Behind-One-Interface.md) | - | - | One interface for asking for a machine, each source a provider behind it: clouds provided through OpenTofu, the host's VM pool and a fixed server lent; the providers per kind as one variable on both forges with different values. |
| <a id="bt18"></a>[BT18](BT18-A-Runner-Named-By-Its-Version.md) | - | - | `check-actions` refuses `runs-on: ubuntu-latest` and the other moving labels, naming file and job; a runner named by its version passes. |
| <a id="bt23"></a>[BT23](BT23-One-Upload-Helper-For-Every-Publish.md) | - | - | One upload helper for Artifactory, published here and taken by digest in every Publish job, in place of the five copies of `.github/scripts/artifactory.sh` (porelo, porelo-pi, porelo-omp, porelo-claude-code, porelo-message-matrix) and the own curl calls of porelo-message-sluice, porelo-intellij and porelo-frontend. |

## Finished

| # | Title | What was built |
|---|---|---|
| <a id="bt32"></a>BT32 | Snapshots On The Forge's Own Registry | The same workflows resolve and deploy snapshots on Sonatype on GitHub and on the forge's Maven registry on Forgejo, chosen by variables and secrets alone, with release tags published only to Sonatype. |
| <a id="bt28"></a>BT28 | A Release's Publish Job May Write Its Contents | `porelo-release check-actions` lets the job `publish` of `release.yml` write `contents`, so porelo-host's release can attach its tarball and digest, and holds that job to runner commands alone: no fetched action, image or shell template, only its listed env names, and expressions only in env values and if. |
| <a id="bt31"></a>BT31 | A Function Names The Parents It Covers | A plan function names under `covers:` the parent issues it covers with no work of their own; both pages show them as one line with their state, outside the sprint's bar and counts, and `plan check` refuses a parent missing from its index, named twice, or behind its parts. |
| <a id="bt29"></a>BT29 | The Plan Page Tells Ready From Undecided | Nine colour groups in the states' flow order, Backlog, Estimated and Idea each its own; the progress image named `progress-<8 hex of its SHA-256>.svg`, the release's older ones removed, so a preview and GitHub show the new one. |
| <a id="bt15"></a>BT15 | Legs Taken From A Proven Run | `porelo-release proven-legs` lets a Build on `main` take its legs from a run of this repository that proved the same tree, so a squash merge rents no machine twice. |
| <a id="bt04"></a>BT04 | Issue Numbers Link Their Index Row | `check-citations` holds every issue number in `issues/` to a link to its index row, in its own repository or another's, and refuses a link to a row that is not there. |
| <a id="bt22"></a>BT22 | A Pin With Several Artifacts | `check-pin` and `add-fetched-cli` take `--pin <name>` and check and record one artifact of a CLI downloaded as several by the digest its own pin holds. |
| <a id="bt21"></a>BT21 | Jackson Without Its Known Advisories | porelo-release reads its YAML and XML through Jackson 2.22.3, past the advisories published against the versions before. |
| <a id="bt20"></a>BT20 | Bills Read With A Current CycloneDX Library | porelo-release reads and writes bills with `cyclonedx-core-java` 11.0.1 and `commons-lang3` 3.18.0, past their known advisories. |
| <a id="bt16"></a>BT16 | A Snapshot Built Only From New Inputs | A push builds a system's snapshot only when the newest one's `inputs` label, a hash of what it is made from, differs; a dispatch always builds. |
| <a id="bt14"></a>BT14 | The Newest Release That Has Stood | A release tool takes the newest release at least `porelo.release.min-age` old, even where newer, younger ones exist, so a pin moves for an upstream that releases daily. |
| <a id="bt12"></a>BT12 | A Rented Machine Carries Its Expiry | Every lease labels its server `porelo-expires` from its job's time limit, and `sweep` deletes only servers past it or carrying none. |
| <a id="bt11"></a>BT11 | A Rented Machine Answers With Its Own Key | A rented machine gets a host key made per run in its `user_data`, pinned by every connection to it; a local VM's any key is an accepted risk. |
| <a id="bt27"></a>BT27 | The Release Plan Page Drawn From Templates | `plan render` draws the HTML page from precompiled jte templates and the Markdown page like it, with one self-contained progress image, sprints in boxes and the inputs' latest commit time as *Stand*; `plan check` compares both with a fresh render. |
| <a id="bt26"></a>BT26 | A Release Planned In Sprints Of Tested Functions | `porelo-release plan render` and `plan check` read a release's plan file of sprints and tested functions against every repository's issue index, write its page and refuse five faults, naming the issue or the function. |
| <a id="bt19"></a>BT19 | A Component Told Apart By Its Group | `add-component` matches a recorded component on group and name, so npm's `@types/node` and the Node runtime stand side by side in a tree's bill. |
| <a id="bt05"></a>BT05 | The Code Read Against The Skills It Names | Every module read against the seven skills `AGENTS.md` names, each finding fixed with a test seen red or declined, and its three questions decided. |
| <a id="bt02"></a>BT02 | Check Actions Reads Permissions | `check-actions` reads `permissions:`: one at the top of every workflow, read or nothing, `write` only in a job its list names, anything else refused by name. |
| <a id="bt07"></a>BT07 | Renamed Porelo Here | The tools are published as `org.fuin.porelo` `0.5.0`, and what they rent, label and install is named Porelo, the CI snapshots labelled `porelo=ci`; a documents test refuses the old word. |
| <a id="bt08"></a>BT08 | Parent Renamed Porelo Parent | `org.fuin.porelo:porelo-parent` `0.2.0`, naming the tools as `org.fuin.porelo` `0.5.0`. |

## Rejected

| # | Title | Why |
|---|---|---|
| <a id="bt06"></a>BT06 | The Gathered Push Refuses A Moved Central Forge | Moved to porelo-host, which installs the command on the host, where there is no Java. |
