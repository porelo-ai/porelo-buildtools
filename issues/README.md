# Issues

The open tasks of `porelo-buildtools`, grouped by state; the order within a group is the priority. The rules for an
issue, and who sets which state, are in [AGENTS.md](../AGENTS.md). *Open questions*, in *Analyzed* only, counts what
waits on the operator: the items under an issue's own *Open questions*. Its prefix is `BT`. A group with nothing in it is
left out.

## Review

| # | Size | Blocked by | What it covers |
|---|---|---|---|
| <a id="bt07"></a>[BT07](BT07-Sokar-Renamed-Porelo-Here.md) | M | `sokar-buildtools` [BT08](README.md#bt08) | The tools renamed by script and published as `org.fuin.porelo` `0.5.0`; machines rented, labelled `porelo=ci` and swept under the new name, old snapshots built again; red first a test refusing the old word. |
| <a id="bt08"></a>[BT08](BT08-Sokar-Parent-Renamed-Porelo-Parent.md) | XS | - | `sokar-parent` becomes `org.fuin.porelo:porelo-parent` `0.2.0`, naming the tools as `org.fuin.porelo` `0.5.0`; the window's first commit. |
| <a id="bt02"></a>[BT02](BT02-Check-Actions-Reads-Permissions.md) | S | - | `check-actions` reads `permissions:`: one at the top of every workflow, read or nothing, `write` only in a job its list names, anything else refused by name. |

## Implementing

| # | Size | Blocked by | What it covers |
|---|---|---|---|
| <a id="bt04"></a>[BT04](BT04-Issue-Numbers-Link-Their-Index-Row.md) | M | - | `check-citations` holds issue numbers under `issues/` to links to their index rows, every row to its anchor, every link to an anchor that is there; built, pushed after every repository's anchors. |

## Analyzed

| # | Size | Blocked by | What it covers | Open questions |
|---|---|---|---|---|
| <a id="bt05"></a>[BT05](BT05-The-Code-Read-Against-The-Skills-It-Names.md) | L | - | Every module read against the seven skills `AGENTS.md` names, at recorded versions and SHA-256: `release`, `hetzner` and its script, `json`, `ffm-check`, `cpu-check`; each finding fixed with a test seen red or declined with why; one commit per module. | - |
| <a id="bt01"></a>[BT01](BT01-A-Release-Leg-Installs-The-Release.md) | S | - | A release leg installs Porelo from the channel the run publishes to, and `acceptance --candidate` needs no package name. | - |
| <a id="bt03"></a>[BT03](BT03-A-Rebuilt-Snapshot-Removes-Its-Predecessors.md) | S | - | A new CI snapshot, once available, deletes the older `porelo=ci` snapshots of its `os`, keeping one before it; nothing else is touched, each deletion said. | - |

## Accepted

| # | Size | Blocked by | What it covers |
|---|---|---|---|
| <a id="bt06"></a>[BT06](BT06-Push-Mirrors-Started-Through-The-Forge.md) | - | - | The local forge's push mirrors to the central forge run only when started through its API, staggered; the API token stays on the host. |
