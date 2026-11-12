# BT84 — Acceptance Takes The Pool A Job Is Told

```json
{
  "$schema": "issue-1.json",
  "id": "BT84",
  "title": "Acceptance Takes The Pool A Job Is Told",
  "state": "Review",
  "size": "S",
  "priority": "Medium",
  "rank": 31,
  "blockedBy": [],
  "covers": "`porelo-machines acceptance` takes the host's VM pool from `PORELO_MACHINES_POOL` when `--pool` is not given, and `sweep --mine` passes where the job rented from the pool, so one workflow runs on the host's forge and on GitHub (porelo-host's HO27)."
}
```

**What must be true.** A job told `PORELO_MACHINES_POOL` takes a run account of the host's VM pool through
`acceptance --kind amd64` without `--pool`, and its last step, `sweep --mine`, does not fail there:

- `acceptance` reads `PORELO_MACHINES_POOL` when `--pool` is not given; an empty value counts as unset, and `--pool`
  wins over it.
- `--accounts` above 1 is refused with the variable as it is with `--pool`.
- `sweep --mine` with the variable set and no provider token says that nothing was rented from a provider and exits
  0; today it stops on the missing token.

**Why this size.** S, about 27 min: `Main` and `MainTest`, each case seen red first, the usage text and one changelog
entry.

## Decided by the operator (2026-11-12)

- porelo-host's HO27 is built now, so this issue, which it waits for, comes first.

## Acceptance

- `MainTest`: with the variable and no `--pool`, `acceptance` rents from a pool read from that file; empty is unset;
  `--pool` wins; `--accounts 2` is refused; `sweep --mine` exits 0 with the variable and no token, and still fails
  without the variable and without a token.

## As built

- `Main.POOL` names the variable; `acceptance` reads it only when `--pool` is not given, before the `--accounts` check,
  whose refusal now names it too. `sweep --mine` without `--from` returns 0 before opening anything when the variable
  is set and neither `PORELO_MACHINES_API` nor `HETZNER_API` holds a token; with a token it sweeps as before.
- Six cases in `MainTest`; the three that change behaviour (the told pool, its accounts refused, the sweep passing)
  were seen red first. `machines` 277 tests and `-Pdocuments` pass; Antigravity read the diff and found nothing red.
