# BT28 — A Release's Publish Job May Write Its Contents

**Status:** Review.
**Size:** M - one entry in `check-actions`' list of writing jobs, and an allowlist, read from the parsed YAML, of what
such a job may hold; grown from XS through four review rounds, each finding a way past a narrower rule. In the
`release` module alone.

**What must be true.** `porelo-release check-actions` lets the job `publish` of a `release.yml` write `contents`, so
porelo-host [HO13](https://github.com/porelo-ai/porelo-host/blob/main/issues/README.md#ho13) can create its release
and attach the collection's tarball and `SHA256SUMS`; and it refuses that job as soon as it runs a fetched action or a
checkout beside its token.

## Plan

- **`CheckActions.WRITES`** gains `release.yml/publish` → `contents`, with its reason.
- **`CheckActions.NO_FETCHED_CODE`** names each writing job that may run only the runner's own commands, with the env
  names it may set: `release.yml/publish` → `GH_TOKEN`, `TAG`, `VERSION`, `ARTIFACT`. `fetchedByWritingJobs` reads the
  workflow with Jackson's YAML parser, duplicate keys refused, an unreadable file a fault, and holds such a job to
  allowlists, since what the runner can run, and what a program reads from its environment, only grows:
  - job keys `name`, `runs-on`, `timeout-minutes`, `needs`, `if`, `permissions`, `env`, `concurrency`, `outputs`,
    `continue-on-error`, `steps`; no `uses`, `container`, `services`, `strategy` or `defaults`;
  - step keys `name`, `id`, `if`, `run`, `env`, `timeout-minutes`, `continue-on-error`, and a string `run` in each; no
    `uses`, `with`, `shell` or `parallel`;
  - env names only from its list, in the job and its steps; no workflow-level `env` or `defaults` in such a file;
  - `${{` only in `env` values and `if`, so an earlier job's output reaches the shell as data;
  - no YAML alias or merge key inside the job or at `jobs`.
  The check reads a directory, not a repository, so every repository with such a job is held to it.
- **Red first:** in `CheckActionsTest`, a `release.yml` whose `publish` job writes `contents` is accepted only with
  the entry; a checkout or another fetched action in that job is refused, naming the step, also in flow style, under a
  quoted key or job id, by an alias or a merge key, in `container`, `services` or `parallel`; a `shell`, `defaults`,
  an env name off the list (`BASH_ENV`, `UNZIP`, `HTTPS_PROXY`, `CDPATH`) and an expression in `run` or `runs-on` are
  refused; an unreadable file is refused; a `uses:` in the same
  workflow's `build` job, which writes nothing, stays accepted; a write in its `build` job, or an `actions: write` in
  `publish`, is refused.
- **The changelog** gains its entry through `changelog/unreleased/`.

## Acceptance

- porelo-host's `.github/workflows/release.yml` passes `check-actions` with this snapshot, and failed it before.
- A `publish` job with an `actions/checkout` step is refused, naming the line and the step.
