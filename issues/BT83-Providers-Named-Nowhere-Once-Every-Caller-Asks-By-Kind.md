# BT83 — Providers Named Nowhere Once Every Caller Asks By Kind

```json
{
  "$schema": "issue-1.json",
  "id": "BT83",
  "title": "Providers Named Nowhere Once Every Caller Asks By Kind",
  "state": "Idea",
  "size": "-",
  "priority": "Medium",
  "rank": 1,
  "blockedBy": [],
  "covers": "Once porelo asks for machines by kind, `check-providers` joins the shared rules of every repository, and `porelo-machines` drops the old call with a provider's server types and the `HETZNER_API` and `HETZNER_SSH` fallbacks (ST22)."
}
```

**What must be true.** No repository outside the provider implementation names a provider or its server types, and
the shared rules refuse one that does:

- `check-providers` runs in every repository's *Shared rules check*, not only here.
- The deprecated call of [BT25](README.md#bt25) is gone: `--type` on `snapshot`, `acceptance` and `lease`, `Spec`'s
  type constructors and accessors, and the routing of an explicit type order onto a kind.
- `porelo-machines` reads only `PORELO_MACHINES_API` and `PORELO_MACHINES_SSH`; the fallback to `HETZNER_API` and
  `HETZNER_SSH` is gone.

## Why

BT25 kept the old call and the old variable names so that no caller had to move at once. On main of 2026-11-12,
porelo-frontend, porelo-claude-code, porelo-pi, porelo-omp and porelo-agy ask by kind; only porelo still names
`HETZNER_API`, `HETZNER_SSH` and `--type` (`.github/workflows/build.yml`, `sweep.yml`, and its legs' `Main`). Once
porelo has moved, through its own issue, nothing calls the old way any more.

## To be checked

- porelo's issue that moves it to kinds: this issue waits for it, and names it under `blockedBy` once it is filed.
