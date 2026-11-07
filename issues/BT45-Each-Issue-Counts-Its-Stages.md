# BT45 — Each Issue Counts Its Stages

**Status:** Review.
**Size:** M - a new `porelo-release stage` command, a file format other repositories commit, and a rule in
`check-commits`.

**What must be true.** An agent records each stage of an issue - its start, end, time and tokens - with one command,
the record is committed with the issue in its own repository, and an estimate for a new issue is read from those
records instead of being guessed.

## Plan

- **The command,** in `release/`, Java:
  `porelo-release stage begin|end <issue> <stage> [--actor agent|subagent] [--model <model>] [--effort <effort>]
  [--session <file>] [--result done|returned|refused|abandoned] [--status-after <state>] [--wait <reason>]`
  and `porelo-release stage median <size>`.
- **Stages:** `analyze`, `estimate`, `plan`, `implement`, `test`, `review`, `rework`, `handover`, and `wait` with a
  one-word reason (`operator`, `build`, `agent`, `vm`); a repeated stage is counted, `review` 2.
- **The record:** `stats/<issue>.jsonl` in the issue's repository, one JSON object a line per stage, with `issue`,
  `stage`, `n`, `actor`, `model`, `effort`, `size`, `status_before`, `status_after`, `start`, `end`, `wall_min`,
  `vm_min`, `estimate_min` (estimate only), `result`, the tokens (Claude input, output, cache read, cache write,
  messages; Codex input, cached, output, reasoning; agy input, cache read, output), `wait_reason` and `note`. Written
  into the work tree and taken into the issue's next commit; none of its own. No value holds a path, a machine name
  or an account.
- **One open stage of the agent's own at a time:** `begin` refuses a second. A sub-agent's stage is counted from its
  own session file, given with `--session`, never from the time window, so two sub-agents running at once are not
  mixed. Which session log the agent's own tokens come from is given by the caller, never written into the code.
- **`check-commits`** refuses a commit whose `stats/` holds a stage without an end, and one that changes an issue
  with no `stats/<issue>.jsonl`.
- **The estimate:** `stats/seed.csv` per repository holds the general median per size (XS 8, S 27, M 53, L 80).
  `stage median <size>` prints the median of the seed and the repository's finished issues of the same starting
  size, measured over the agent's own stages `plan` to `handover` without `wait`.
- **Red first:** `begin` refusing a second open stage; `end` writing one line with the window's tokens from a given
  session file; `check-commits` refusing an open stage; the median of a seed and two finished issues.

## Acceptance

- The unit tests, seen red first, and the full build green.
- On this repository, one issue's stages recorded with the command and committed with it, and `check-commits`
  refusing that commit while a stage is open.
