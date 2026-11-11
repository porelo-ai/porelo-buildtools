# Fixture issue index

## Implementing

| <a id="bt26"></a>[BT26](BT26-A-Release-Planned-In-Sprints-Of-Tested-Functions.md) | M | - | `porelo-release plan render` and `plan check`: a release's page from its plan file and every index, five faults refused; porelo-project [PJ63](https://github.com/porelo-ai/porelo-project/blob/main/issues/README.md#pj63)'s build part. |
| <a id="bt24"></a>[BT24](BT24-An-Account-Rented-From-The-Local-Pool.md) | M | - | `porelo-machines` rents one run account of the host's local VM pool per run from the file porelo-host's `vm_pool` writes, waits or refuses beyond the pool's size, and resets the account when it is given back. |

## Accepted

| <a id="bt06"></a>[BT06](BT06-Push-Mirrors-Started-Through-The-Forge.md) | - | - | The local forge's push mirrors to the central forge run only when started through its API, staggered; the API token stays on the host. |
| <a id="bt25"></a>[BT25](BT25-Machines-Asked-For-By-Kind.md) | L | - | `Spec` and `Main` ask for a machine by kind instead of a provider's server types, the setup chooses the local pool or the cloud provider, and a check in `porelo-buildtools` refuses a provider's name outside its implementation. |
