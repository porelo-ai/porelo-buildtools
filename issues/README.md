# Issues

The open tasks of `sokar-buildtools`, grouped by state; the order within a group is the priority. The rules for an
issue, and who sets which state, are in [AGENTS.md](../AGENTS.md). *Open questions* counts what waits on the
operator: the items under an issue's own *Open questions*. Its prefix is `BT`. A group with nothing in it is
left out.

## Review

| # | Size | Blocked by | What it covers | Open questions |
|---|---|---|---|---|
| <a id="bt02"></a>[BT02](BT02-Check-Actions-Reads-Permissions.md) | S | - | `check-actions` reads `permissions:`: one at the top of every workflow, read or nothing, `write` only in a job its list names, anything else refused by name. | - |

## Analyzed

| # | Size | Blocked by | What it covers | Open questions |
|---|---|---|---|---|
| <a id="bt01"></a>[BT01](BT01-A-Release-Leg-Installs-The-Release.md) | S | - | A release leg installs Sokar from the channel the run publishes to, and `acceptance --candidate` needs no package name. | - |
| <a id="bt03"></a>[BT03](BT03-A-Rebuilt-Snapshot-Removes-Its-Predecessors.md) | S | - | A new CI snapshot, once available, deletes the older `sokar=ci` snapshots of its `os`, keeping one before it; nothing else is touched, each deletion said. | - |
