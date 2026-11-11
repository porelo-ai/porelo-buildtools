# BT76 — The Musl Installer Names Its Pins

```json
{
  "$schema": "issue-1.json",
  "id": "BT76",
  "title": "The Musl Installer Names Its Pins",
  "state": "Idea",
  "size": "XS",
  "priority": "Medium",
  "rank": 29,
  "blockedBy": [],
  "covers": "`machines musl --pins` prints the installer's two digests, `MUSL_SHA256=<hex>` and `ZLIB_SHA256=<hex>`, read from the installer it carries, so a workflow keys its cache by them without a second copy."
}
```

**Why.** porelo's B618 caches the musl toolchain and its zlib in the Actions cache, keyed by their two pinned digests.
`machines musl` takes no option, so build.yml writes the digests a second time. When a pin moves in porelo-machines,
nothing flags that copy, and the old toolchain stays restored. Core asked for this on 2026-11-11 (16:13Z).

**What must be true.**
- `machines musl --pins` prints exactly two lines, `MUSL_SHA256=<64 hex>` and `ZLIB_SHA256=<64 hex>`, taken from
  `install-musl.sh` as the jar carries it, installs nothing, and exits 0.
- Any other option is refused as today, with exit 2.
- A step can append the output to `$GITHUB_OUTPUT` as it is.

## Plan

Red first: a test of `Main.musl` with `--pins` that expects the two lines and no installer run. Then read the two
assignments from `Snapshots.muslInstaller()`'s text, refusing an installer that lacks either. Docs:
the command's help line and `doc/machines.md`. Run the machines module's tests in the JVM.
