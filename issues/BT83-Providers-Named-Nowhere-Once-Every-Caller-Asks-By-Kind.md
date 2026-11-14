# BT83 — Providers Named Nowhere Once Every Caller Asks By Kind

```json
{
  "$schema": "issue-1.json",
  "id": "BT83",
  "title": "Providers Named Nowhere Once Every Caller Asks By Kind",
  "state": "Analyzed",
  "size": "M",
  "priority": "Medium",
  "rank": 1,
  "blockedBy": [],
  "covers": "Once porelo asks for machines by kind, `check-providers` joins the shared rules of every repository, and `porelo-machines` drops the old call with a provider's server types and the `HETZNER_API` and `HETZNER_SSH` fallbacks (ST22)."
}
```

**What must be true.** No repository outside the provider implementation names a provider or its server types, and
the shared rules refuse one that does:

- `check-providers` runs in every repository's *Shared rules check*, not only here.
- The deprecated call by provider and server types is gone: `--type` on `snapshot`, `acceptance` and `lease`, `Spec`'s
  type constructors and accessors, and the routing of an explicit type order onto a kind.
- `porelo-machines` reads only `PORELO_MACHINES_API` and `PORELO_MACHINES_SSH`; the fallback to `HETZNER_API` and
  `HETZNER_SSH` is gone.

## Why

The move to kinds kept the old call and the old variable names so that no caller had to move at once. On main of
2026-11-12, porelo-frontend, porelo-claude-code, porelo-pi, porelo-omp and porelo-agy ask by kind; only porelo still
names `HETZNER_API`, `HETZNER_SSH` and `--type` (`.github/workflows/build.yml`, `sweep.yml`, and its legs' `Main`). Once
porelo has moved, through its own issue, nothing calls the old way any more.

## Plan

Read in porelo-buildtools 0d307f4 and on every repository's main of 2026-11-12.

- **Where the check fails today.** `check-providers` run on each repository's main of 2026-11-12: 18 pass; porelo finds
  37 matches, not only in the workflows and the legs that B667 names but also in `doc/decisions.md` (9), `AGENTS.md`,
  `agents/README.md`, `acceptance/legs/README.md` and `SshRestart.java`; porelo-host finds 1, a comment in `porelo.yml`
  that names the old `hetzner/pom.xml`; porelo-message-sluice finds 3 in
  `filter/app/src/test/resources/english-corpus.txt`, third-party text that must stay as it is.
- **An exemption file (S).** `CheckProviders` reads `providers-exempt.txt` at a repository's root, one committed path
  per line, as `CheckCitations` reads `citations-exempt.txt`; a listed path that is not committed is refused, so the
  list cannot rot. Its own paths move from the constant into this repository's file. Cases in `CheckProvidersTest`, each
  seen red first: a listed file skipped, a missing listed file refused, a match outside the list still found.
- **The old call removed (M).** The 16 `@Deprecated(forRemoval = true)` members in `Spec`, `AgentLeg`, `Rental`,
  `Snapshots` and `Hetzner`, `--type` on `snapshot`, `acceptance` and `lease` with `Spec.order` and `Spec.kindOf`, and
  `OLD_API_TOKEN` and `OLD_SSH_KEY` in `CentralSetup` and `Main`. Their tests go with them; a `MainTest` case that
  `--type` and a `HETZNER_API`-only environment are refused, naming `--kind` and `PORELO_MACHINES_API`, is seen red
  first. Usage texts, `machines/README.md` and one changelog entry say it. It removes public members of a published
  module, so the next release is 0.6.0.
- **The shared set (XS here).** This repository already runs the command among its own documents tests. Every other
  repository adds the step `check-providers .` to its *Shared rules check*, each by its owner once porelo-release with
  the exemption file is released: porelo-message-sluice with its corpus listed, porelo-host after its comment is mended,
  and porelo after B667. The Coordinator hands those out; nothing of them is built here.
- **Order and size.** Exemption file first, released with the next snapshot; the removal after B667 is on porelo's main;
  S 27 + M 60 + XS 8, about 95 min, so M.

## Waits for

- This issue waits for porelo's B667, "Porelo's Legs Ask For Machines By Kind", which moves porelo to kinds. B667
  checks how it meets porelo's B322, whose pool leg on the host's forge keeps `--type` today.
