# BT24 — An Account Rented From The Local Pool

**Status:** Backlog.
**Size:** M - one new `Machines` beside `LocalVms` and `Hetzner`, a file format porelo-host writes, and the option
that picks it; no provider is touched.

**What must be true.** `porelo-machines` rents a run account of the host's local VM pool as it rents a server: it
reads the pool from the file porelo-host's role `vm_pool` writes, holds one free account per run, waits or refuses
when every account is held, and has the account made anew when it is given back, after a failure too. Today
`LocalVms` reads no file, knows no accounts and starts a whole VM for a run.

## Plan

- **The pool file is the contract** with porelo-host
  [HO12](https://github.com/porelo-ai/porelo-host/blob/main/issues/README.md#ho12), JSON with
  `"format": "porelo-machines-pool/1"`: per VM its `os`, its address, its host key, its run accounts, and the pool's
  admin account and key file, which `porelo-machines` uses only to reset. A format it does not know, a missing
  field, an account named twice or a key file anybody but its owner can read stops the run with that said.
- **`LocalPool implements Machines`** in `hetzner/`: `acquire(spec)` takes the first VM of `spec.os()` with a free
  account. Holding is a lock on `<state>/<vm>/<account>.lock` (`FileChannel.tryLock`), kept for the lease's life, so a
  process that dies gives it up by itself. While every account is held it waits, then refuses after its time, naming
  the pool's size. The `Lease` connects as the account with the pool's run key and checks the host key the file
  names; `acquireFromStock` ignores the image, as `LocalVms` does.
- **Reset as the operator decided:** on `close`, `ssh` as the admin runs `sudo porelo-pool-reset <account>` on the VM
  (installed by porelo-host's role). It runs `porelo-reset-account` as the account, stops its processes, removes
  its containers, then `userdel -r`, `useradd` and the run key again. Packages a run installed with sudo stay, the
  accepted risk written in `doc/decisions.md`. A `dirty` marker beside the lock, written on acquire and removed
  after the reset, makes the next acquire reset an account a dead process left before handing it out.
- **`--pool <file>`** on `acceptance` and `lease` picks `LocalPool` instead of the provider; nothing else changes
  for a caller.
- **Red first:** `LocalPoolTest` with a fake `Ssh` and a pool file in a temporary directory. Two acquires get two
  accounts; a fifth on four waits and refuses; `close` runs the reset; a `dirty` account is reset before it is handed
  out; each malformed file is refused.

## Acceptance

- Two runs at once get two different accounts of the same VM; a fifth run on a pool of four waits, then refuses
  after its time, naming the pool's size.
- An account given back, by a run that ended or one that died, holds nothing of that run when the next run gets it:
  measured in porelo-host's Molecule scenario on a VM the role made.
- A pool file that is missing, unreadable, of another format or names an account twice stops the run with that said.
