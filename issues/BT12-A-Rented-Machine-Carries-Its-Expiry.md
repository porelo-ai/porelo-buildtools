# BT12 — A Rented Machine Carries Its Expiry

**Status:** Backlog.
**Size:** M - a label written at creation, `sweep` deciding by it instead of by age, two options changed, and
`porelo`'s `sweep.yml` moved with them; each workflow that rents names its time limit.

**What must be true.** `sweep` never deletes a server that is still within its time. Each server a lease creates says
when its time ends, and `sweep` deletes only servers past that time, and `porelo=ci` servers that say nothing.

## Why

[BT05](README.md#bt05) found that `sweep` deletes by age: any `porelo=ci` server older than its window (`--older-than`,
60 minutes by default), whoever made it, against the shared rule that a run never sweeps by age. A test that runs longer
than the window loses its machine to `porelo`'s hourly `sweep.yml`. `--older-than 0` with `--now` deletes every server
in use.

Decided by the operator, 2026-10-18: an expiry per machine.
- Every lease writes a label `porelo-expires=<instant>` on the server it creates: the job's own time limit plus a
  margin. A long test gets its time without anyone widening the sweep.
- `sweep` deletes only servers whose expiry has passed, and `porelo=ci` servers with no expiry label (leftovers nobody
  can attribute). It never deletes one that is still within its time.
- `--older-than 0` is refused. Deleting servers in use needs an explicit `--including-in-use`.
- The workflows that call `sweep` change with it.

## The shape

- `Hetzner.createWhenThereIsRoom` adds `porelo-expires` to the labels it already writes (`porelo`, the run label).
  Hetzner's label values allow only letters, digits, `-`, `_` and `.`, so an ISO instant with `:` does not fit. The
  value is the instant in epoch seconds, or a form like `20261018T0413Z`.
- `Hetzner.sweep` reads the label from each server (`Server` gains the expiry). It keeps a server whose expiry lies
  ahead and says so with the expiry, as it says "keeping" today. It deletes one past its expiry or with no label.
- `Main`'s `sweep` refuses `--older-than 0` and takes `--including-in-use`. Whether `--older-than` stays at all, once
  the expiry decides, is part of the change.
- Callers: `porelo`'s `.github/workflows/sweep.yml` runs `sweep --older-than 60 --now` and changes. Every other caller
  found runs `sweep --mine`, which deletes its own run's servers and is not changed: `porelo-buildtools`, `porelo`,
  `porelo-frontend`, and the Build and `update.yml` of `porelo-claude-code`, `porelo-pi` and `porelo-omp`.
- `--mine` (and `--from <file>`) deletes the run's own servers whatever their expiry: they are still within their time
  when the run clears them up, and that clean-up is what they exist for. The expiry rule is for the sweep of everyone's
  servers.

## Acceptance

- [ ] A server created by a lease carries `porelo-expires`, later than its creation by the time limit given plus the
  margin.
- [ ] `sweep` keeps a server whose expiry lies ahead, whatever its age, and deletes one past its expiry. A test with a
  server older than 60 minutes and within its time is kept, and is seen to fail against today's age rule.
- [ ] `sweep` deletes a `porelo=ci` server with no `porelo-expires` label.
- [ ] `sweep --mine` deletes the run's own servers within their expiry, as today.
- [ ] `sweep --older-than 0` is refused with a message naming `--including-in-use`. Only with `--including-in-use` does
  a sweep delete a server within its time.
- [ ] `porelo`'s `sweep.yml` runs the new sweep and is green on its next hourly run.
- [ ] [BT05](README.md#bt05)'s finding names this issue.

## To be checked

- How a lease learns the job's time limit: GitHub does not hand a job its `timeout-minutes`. The likely answer is an
  option the workflow passes beside it (for example `--for <minutes>`), with a default that fits the longest job today
  (`porelo`'s legs, 60 minutes), and the margin a fixed amount on top.
- Whether a snapshot built by `machines.yml` from a rented server needs an expiry as well, or is made by a path that
  never reaches `sweep`.
