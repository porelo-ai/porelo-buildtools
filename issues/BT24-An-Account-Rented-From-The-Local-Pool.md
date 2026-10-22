# BT24 — An Account Rented From The Local Pool

**Status:** Idea.

**What must be true.** `porelo-machines` rents a run account of the host's local VM pool as it rents a server: it
reads the pool from the file porelo-host's role `vm_pool` writes, holds one free account per run, waits or refuses
when every account is held, and resets the account when it is given back, after a failure too. Today `LocalVms`
reads no file, knows no accounts and starts a whole VM for a run.

## Acceptance

- Two runs at once get two different accounts of the same VM; a fifth run on a pool of four waits, then refuses
  after its time, naming the pool's size.
- An account given back, by a run that ended or one that failed, holds nothing of that run when the next run gets
  it.
- A pool file that is missing, unreadable or names an account twice stops the run with that said.

## To be checked

- How the pool file is versioned and read, as the contract porelo-host
  [HO12](https://github.com/porelo-ai/porelo-host/blob/main/issues/README.md#ho12) writes.
