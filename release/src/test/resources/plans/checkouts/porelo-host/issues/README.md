# Fixture issue index

## Review

| <a id="ho19"></a>[HO19](HO19-The-Machine-Installs-Its-Agents-And-Starts-No-Task.md) | M | - | `up` installs every agent Porelo's repository offers and starts no task, no stop for a sign-in; `down` stops every running task before `vault clear`. |
| <a id="ho11"></a>[HO11](HO11-The-Homeservers-Packages.md) | S | - | The Matrix transport's and homeserver's packages installed, nothing enabled; the first task start brings the homeserver up. |
| <a id="ho02"></a>[HO02](HO02-Porelos-Own-Commands-Called-Never-Redone.md) | L | - | Role `porelo`: the package, the account, `porelo setup`, keys from the drive into `porelo vault put` on the host, `follow --signed-by`, `task start`; idempotent by Porelo's own answers, no secret through Ansible; `up` pauses for the person to open the vault. |

## Implementing

| <a id="ho10"></a>[HO10](HO10-A-Vault-Opened-With-Nobody-There.md) | XS | - | `up` to the end with the vault opened by the caller over ssh with the passphrase on standard input; the playbook only checks and stops. |
| <a id="ho18"></a>[HO18](HO18-A-Failed-Loop-Says-Why.md) | S | - | A failed step's JSON line carries the command's own words, a loop's by each failed item, `no_log` still holding. |
| <a id="ho08"></a>[HO08](HO08-The-Forge-With-Its-Registry.md) | L | porelo [B233](https://github.com/porelo-ai/porelo/blob/main/issues/base/README.md#b233), porelo [B234](https://github.com/porelo-ai/porelo/blob/main/issues/base/README.md#b234) (tasks' side only) | Role `forge`: Forgejo and its runner in rootless podman under two accounts of their own, on the VPN and the tasks' address only, its registry over TLS from a CA made on the host, the project's repositories migrated from the central forge with push mirrors, credentials made and kept on the host, every build publishing with a publisher's token kept as an Actions secret; run where the host setting `porelo_host_forge` asks for it. |
| <a id="ho12"></a>[HO12](HO12-The-VM-Pool.md) | L | porelo-buildtools [BT24](https://github.com/porelo-ai/porelo-buildtools/blob/main/issues/README.md#bt24) | Role `vm_pool`: VMs with run accounts `porelo-machines` rents from, made by the same code as the test VM, deleted by name at `down`. |

## Backlog

| <a id="ho07"></a>[HO07](HO07-The-Projects-Store-Restored-And-Kept.md) | L | porelo-project [PJ45](https://github.com/porelo-ai/porelo-project/blob/main/issues/README.md#pj45), porelo-message-matrix [MX24](https://github.com/porelo-ai/porelo-message-matrix/blob/main/issues/README.md#mx24), porelo [B231](https://github.com/porelo-ai/porelo/blob/main/issues/base/README.md#b231) | Role `store`: restic pinned, each entry's own copy and restore command, an hourly user timer, a last copy at `down`, the restore before anything reads it. |

## Accepted

| <a id="ho17"></a>[HO17](HO17-The-Forges-CA-In-Porelos-Account.md) | XS | - | Role `porelo`: the host's forge CA in `~/.config/porelo/ca-certificates.pem` of Porelo's account where the forge runs, owner-only, so a task reaches the forge's API through the broker; `down` takes it out again. |

## Finished

| <a id="ho05"></a>HO05 | The VPN | The role `porelo.host.vpn` brings WireGuard up with the host's private key read from the drive by `wg` itself under a local AppArmor rule, loads the `inet porelo_host` nftables table leaving only ssh and the VPN on the public interfaces, and stops the interface and removes its file at `down`. |
| <a id="ho06"></a>HO06 | The Person's Drive Mounted | The role `porelo.host.drive` mounts a `noexec` sparse-file filesystem for the drive, installs Tresorit's client once its signature under the pinned CA checks and runs its daemon as a user unit, stops `up` for `drive` while logged out, syncs only the person's tresor into the folder, and at `down` waits until it is synced before logging out with `--unlink` and unmounting. |
| <a id="ho09"></a>HO09 | Up And Down In Their Order | `playbooks/up.yml` runs in two plays around the waiting stop for what only the person can give and resumes from the top by idempotence, `playbooks/down.yml` takes down what is there in reverse order, and the stdout callback `porelo.host.lines` reports each task and stop as version 1 JSON lines. |
