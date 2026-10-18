# BT12 — A Rented Machine Carries Its Expiry

**Status:** Review. Built and published by Build 39131553741 on `8377caa`, 2026-10-18; porelo's `sweep.yml` moves in a
later wave, after a quiet hour.
**Size:** M - a label written at creation, `sweep` deciding by it instead of by age, two options changed, and
`porelo`'s `sweep.yml` moved with them; each workflow that rents names its time limit.

**What must be true.** `sweep` never deletes a server that is still within its time. Each server a lease creates says
when its time ends, and `sweep` deletes only servers past that time, and `porelo=ci` servers that say nothing.

## Why

The reading of this repository's code against its skills found, on 2026-10-18, that `sweep` deletes by age: any
`porelo=ci` server older than its window (`--older-than`, 60 minutes by default), whoever made it, against the shared
rule that a run never sweeps by age. A test that runs longer than the window loses its machine to `porelo`'s hourly
`sweep.yml`. `--older-than 0` with `--now` deletes every server in use.

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
  value is the UTC minute as `20261018T0413Z`, readable in the console without converting.
- The time limit: GitHub does not hand a job its `timeout-minutes`, so `snapshot`, `acceptance` and `lease` take
  `--for <minutes>` (60 by default, the longest job today, `porelo`'s legs and the snapshot step; at most 360, the
  longest a GitHub-hosted job runs), and a library caller `Hetzner.forJobsOf`. The expiry is creation plus that plus
  `Hetzner.EXPIRY_MARGIN`, 15 minutes, for the clean-up step after the renting one and clocks that differ. A workflow
  whose renting step runs longer than an hour passes `--for` beside its `timeout-minutes`.
- `Hetzner.sweep` reads the label from each server (`Server` gains the expiry). It keeps a server whose expiry lies
  ahead and says so with the expiry, as it says "keeping" today. It deletes one past its expiry or with no label.
- `Main`'s `sweep` takes `--including-in-use` and refuses `--older-than` with any value, naming what replaced it
  ([the decision](../doc/decisions.md#a-sweep-deletes-by-expiry-not-by-age)). A label `sweep` cannot read keeps its
  server and fails the sweep, naming it.
- The snapshot builder's server needs the expiry and gets it: `Snapshots.build` rents through
  `Hetzner.acquireFromStock`, which creates through `createWhenThereIsRoom` like every lease, so the server is
  `porelo=ci`, seen by the sweep, and labelled from `snapshot --for` (60 by default, the snapshot step's own limit in
  `build.yml`'s `snapshots` job, which is where the snapshots are built; `machines.yml` only moves pins). The image it
  takes is never swept: `sweep` lists servers, not images.
- Callers: `porelo`'s `.github/workflows/sweep.yml` runs `sweep --older-than 60 --now` and changes to `sweep --now`,
  which the tooling before this change reads as the same 60-minute sweep, so it can move first. Every other caller
  found runs `sweep --mine`, which deletes its own run's servers and is not changed: `porelo-buildtools`, `porelo`,
  `porelo-frontend`, and the Build and `update.yml` of `porelo-claude-code`, `porelo-pi` and `porelo-omp`.
- A server rented by tooling from before this change carries no `porelo-expires`, so the new sweep deletes it at its
  next run, in use or not. `sweep.yml` takes the new tooling only once every caller that rents takes it too.
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
