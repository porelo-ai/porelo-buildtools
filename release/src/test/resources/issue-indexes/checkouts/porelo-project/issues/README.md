# Fixture issue index

## Implementing

| <a id="pj39"></a>[PJ39](PJ39-Porelo-Is-Built-In-Porelo-Tasks.md) | — | [PJ40](#pj40), [PJ45](#pj45), [PJ22](#pj22); `porelo` [B189](https://github.com/porelo-ai/porelo/blob/main/issues/base/README.md#b189) | The agents work in Porelo tasks on one bare-metal host, owned or rented by the hour and set up by a script (at least 8 cores with 16 threads, 64 GB, one SSD of 1 TB): an `online` project whose upstream is a local Forgejo, one task per issue, phase and repository, Forgejo building and testing, the operator approving there, Matrix, what an agent learns committed to its `AGENTS.md` instead of memory, `porelo-all` for the coordinator; a plan in nine sprints after the groundwork, each ending in functions the operator tests. |

## Backlog

| <a id="pj63"></a>[PJ63](PJ63-A-Release-Is-Planned-In-Sprints-Of-Tested-Functions.md) | M | — | A release planned once in a checked-in file - its sprints, each sprint's functions the operator tests and the issues serving them - and a page made from it with every issue's state, kept current by the coordinator and checked against the indexes. |
| <a id="pj45"></a>[PJ45](PJ45-Chosen-Data-Outlives-The-Machine.md) | M | — | A short list of data that lives only on a machine - the Matrix room, the token usage records and the Maven cache to start with - copied every hour and at down by restic into the project's S3 store, encrypted on the host, and put back at up; nothing else is kept. |

## Accepted

| <a id="pj62"></a>[PJ62](PJ62-The-Forge-Adapters-Live-In-One-Repository.md) | — | — | The forge adapters in one repository, `porelo-forge`, renamed from `porelo-build-github`: its `github` adapter, a `forgejo` adapter beside it, one contract suite both pass, and `porelo build status` to ask any of them. |
| <a id="pj61"></a>[PJ61](PJ61-The-Project-Porelo-Is-Online-With-The-Local-Forge-Upstream.md) | — | — | The project `porelo` online with the local Forgejo upstream: a task's push built there, its verdict read in the task, the central forge reached through the mirrors after approval; proved on Sluice first. |
| <a id="pj60"></a>[PJ60](PJ60-Every-Workflow-Runs-On-The-Local-Forge-As-On-The-Central-One.md) | — | — | Every repository's `build.yml` runs green on the local Forgejo and the central forge with no edit between: kinds, registry and provider as variables; one issue (S) per repository. |
| <a id="pj59"></a>[PJ59](PJ59-Each-Agent-Moves-Into-A-Porelo-Task.md) | — | — | Each agent moves into a Porelo task, one at a time in a fixed order, each move an issue (S) of its own that the agent works inside its new task; the channel file kept beside Matrix until the last has moved. |
| <a id="pj58"></a>[PJ58](PJ58-The-Coordinator-Works-In-One-Repository-Of-Them-All.md) | — | — | The coordinator reads every repository in `porelo-all` on the local Forgejo and changes only `porelo-project`, split back after the operator's approval. |
| <a id="pj37"></a>[PJ37](PJ37-Work-Reaches-The-Upstream-By-A-Machine-Key-Or-The-Persons-Token.md) | — | — | A machine reaches the upstream by keys of its own or by the person's own token, equal choices per project; the token as narrow as the forge allows, used on the host only, deposited for a time on a rented machine. |
| <a id="pj38"></a>[PJ38](PJ38-An-Estimate-Learns-From-The-Tokens-Spent.md) | — | `porelo` [B148](https://github.com/porelo-ai/porelo/blob/main/issues/base/README.md#b148), [B189](https://github.com/porelo-ai/porelo/blob/main/issues/base/README.md#b189); [PJ39](#pj39) | The new tokens of build and rework, counted per task tagged with its issue and phase, are written beside an issue's estimated size before it is Finished; a velocity per size is kept, and a new estimate is compared with finished issues of its size. |

## Implementing

| <a id="pj40"></a>[PJ40](PJ40-A-Project-Comes-Up-On-A-Server-At-One-Command.md) | XL | — | Any project of several agents and repositories comes up on a server, owned or rented by the hour, at one command: Porelo writes an Ansible inventory from `project.yml`, the repositories' own files and the person's machine file; Ansible sets up the host from the host itself, `host up` (a Dart package of porelo-frontend, for the interface and the command line) rents it by the hour (Scaleway Elastic Metal for Porelo's own; OpenTofu later for others); following, keys and tasks stay Porelo's; Porelo's own project first. |

## Estimated

| <a id="pj22"></a>[PJ22](PJ22-One-Build-Locally-As-On-The-Central-Forge.md) | XL | — | One build, run locally as on the central forge: the same workflows on a local Forgejo, its registry instead of the hand-over and the public repositories, acceptance legs on a machine rented through one interface (a local VM account or a provider's server), and a task's work built the same way, read through one reader contract by a `github` and a `forgejo` reader. |
