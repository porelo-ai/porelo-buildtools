# Fixture issue index

## Review

| <a id="b255"></a>[B255](B255-No-Stored-Kind-Is-Invented-For-An-Empty-Vault.md) | XS | - | A start with nothing stored for its agent says the credential is missing, where today an empty vault reads as a stored `api-key` its default provider refuses |

## Backlog

| <a id="b193"></a>[B193](B193-A-Dependency-Cache-Shared-Across-Tasks.md) | XL | - | Downloaded dependencies cached once for a project's tasks and handed in read-only; what a task installs stays its own; analyzed: filled only from reviewed work, mounted `:ro,z`, read through `maven.repo.local.tail`, filled by `dependency:go-offline` on request and when the default branch moved, Maven first |

## Analyzed

| <a id="b148"></a>[B148](B148-Token-Counting-Per-Task.md) | L | - | The broker reads four answer formats, streamed or not, into a per-task `usage/<task>.jsonl` kept after removal: agent, provider, models, five counts (`null` when not reported), tools, how it ended, a reported cost; asks for the streamed Chat Completions count; sums in `task status` and `Usage`. | - |
| <a id="b231"></a>[B231](B231-The-Usage-Records-Kept-Off-The-Machine.md) | M | `porelo` [B148](README.md#b148) | `porelo keep <dir>` copies the usage records up to their last complete line, `keep --restore <dir>` puts them back before a task runs and never shortens one. | 0 |
| <a id="b253"></a>[B253](B253-Every-Forge-Adapter-Speaks-The-Build-Contracts-Second-Version.md) | M | - | Porelo asks a forge adapter only over `org.fuin.porelo.Build2`, `Look` renamed `Build`, and refuses a `Build1` adapter by name; `none` joins the logs asked for; the stub moves with it, and `porelo-build-api` needs nothing new for `porelo-forge-contract`, the suite `porelo-forge` builds | 0 |

## Accepted

| <a id="b233"></a>[B233](B233-A-Host-Service-Reached-By-Name.md) | L | - | A task reaches a service on its own host, a forge or a registry, by a declared name on 443 and nothing else there; the reach through pasta measured first |
| <a id="b234"></a>[B234](B234-The-Hosts-Authority-In-A-Tasks-Trust-Store.md) | M | - | A task trusts a certificate authority the host names, without a project putting it into its image; where the images read added authorities measured first |

## Idea

| <a id="b256"></a>[B256](B256-A-Person-Asks-What-A-Branch-Or-A-Commit-Built.md) | - | `porelo` [B253](README.md#b253) | `porelo build status -p <project> -r <repository>` with `--branch` or `--commit` asks the forge the project's `builds:` names for the verdict, the jobs and the logs asked for, the token from the vault and shown nowhere; a project without `builds:` is refused naming why |

## Finished

| <a id="b192"></a>B192 | An Image Chosen Per Repository | A repository's `porelo.yml` (checked in) or `.porelo.yml` (a local override) names only its image - `base_image`, a snippet, `env` - chosen key by key with `project.yml`'s `repositories.<name>.image` and the mirror's reviewed default branch after the gate fetches (or the checkout's files in `default`); built and cached per repository as `porelo/<project>_<repository>`, each key's source named by `task start` and `task prepare -r`, and the files ranked dangerous in review. |
| <a id="b189"></a>B189 | Values Handed To A Task At Its Start | A task started with values its project declares - `--set`, `--set-file`, `--set-env` - each checked before anything exists and fixed for the task's life, recorded with its origin, in the container's environment and in `task status` and the daemon's `Start` |

## Implementing

| <a id="b261"></a>[B261](B261-A-Stop-Of-Everything-Says-What-Still-Runs.md) | M | - | `task stop --all`, `panic` and `task stop` end 0 only when no stopped task's container still runs, and name a survivor with 70, since a host's `down` goes on to `vault clear` on 0 |

## Implementing

| <a id="b259"></a>[B259](B259-A-Persons-Message-Id-The-Filter-Takes.md) | XS | - | A person's message reaches a task with an id the message filter accepts, so an answer naming it as `contextId` is not bounced |

## Implementing

| <a id="b258"></a>[B258](B258-The-Machine-Installs-Every-Agent-And-Names-No-Task.md) | M | - | A machine file names the project and its keys, never tasks or agents; a host installs every agent from the setup script's catalogue and starts no task, and porelo-host [HO19](https://github.com/porelo-ai/porelo-host/blob/main/issues/README.md#ho19) waits on it |
