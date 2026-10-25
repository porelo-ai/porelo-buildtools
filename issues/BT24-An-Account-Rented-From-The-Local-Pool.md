# BT24 — An Account Rented From The Local Pool

**Status:** Review.
**Size:** M - one new `Machines` beside `LocalVms` and `Hetzner`, a file format porelo-host writes, and the option
that picks it; no provider is touched.

**What must be true.** `porelo-machines` rents a run account of the host's local VM pool as it rents a server: it
reads the pool from the file porelo-host's role `vm_pool` writes, holds one free account per run, waits or refuses
when every account is held, and has the account's VM made clean when it is given back, after a failure too. Today
`LocalVms` reads no file, knows no accounts and starts a whole VM for a run.

## Plan

- **The pool file is the contract** with porelo-host
  [HO12](https://github.com/porelo-ai/porelo-host/blob/main/issues/README.md#ho12), JSON with
  `"format": "porelo-machines-pool/1"`: per VM its `os`, its address, its host key and its run accounts, and nothing
  secret in the file. A format it does not know, a missing field, an account named twice or a file still carrying
  `runKey` stops the run with that said.
- **`LocalPool implements Machines`** in `machines/`: `acquire(spec)` takes the first VM of `spec.os()` with a free
  account. Holding is a lock on `<state>/<vm>/<account>.lock` (`FileChannel.tryLock`), kept for the lease's life, so a
  process that dies gives it up by itself. While every account is held it waits, then refuses after its time, naming
  the pool's size. The `Lease` connects as the account with the run key taken from `PORELO_POOL_SSH` or `--key` and
  checks the host key the file names; `acquireFromStock` ignores the image, as `LocalVms` does.
- **Reset as the operator decided, (c):** each VM holds one run account. On `close`, `ssh` as the account runs
  `sudo -n porelo-pool-give-back`, the one root-owned command it may run (installed by porelo-host's role), and waits
  until the VM's `/var/lib/porelo-pool/<account>` says `ready`. The VM writes `resetting` and powers off; the host,
  outside every job, reverts it to its base snapshot, taken with `ready` written, and starts it. So nothing a run
  did, as root through the package manager too, reaches the next. No key that reaches the host leaves it: the builds
  that rent from the pool include task branches nobody reviewed. A `dirty` marker beside the lock, written on acquire
  and removed after the reset, makes the next acquire reset an account a dead process left before handing it out.
- **`--pool <file>`** on `acceptance` picks `LocalPool` instead of the provider, and asks no provider. Not on
  `lease`, which leaves its machine to later steps: the lock ends with the process, so another run could take the
  account while it is still in use. Not with `--accounts`, whose further accounts are made with sudo and outlive the
  reset.
- **Red first:** `LocalPoolTest` with a fake `Ssh` and a pool file in a temporary directory. Two acquires get two
  accounts; a fifth on four waits and refuses; `close` runs the reset; a `dirty` account is reset before it is handed
  out; an account is not ready until its VM says `ready`; each malformed file is refused.

## Measured

- **Local VM, 2026-10-25 09:31Z-09:50Z:** porelo-host's Molecule scenario on its test VM with nested KVM uses
  `porelo-machines` built from this branch's head to rent the pool's account `run`. A renter returns its lease
  normally; another receives SIGKILL while holding its lease. Each leaves a file in the account's home and one
  written as root in `/etc`; after each case, the next rental of the same account finds neither file.
- **Acceptance in the pool:** `acceptance --pool` installs published Porelo, creates its `acceptance` account and
  runs a leg in it; the next rental finds that account gone.
- **Provisioning:** a pool lease whose account is not root runs provisioning as root through `sudo -n sh -c`
  and creates the acceptance account with the lease account's own `authorized_keys`. A root lease's commands
  are unchanged.
- **Seen red, 2026-10-25 09:51Z-10:08Z:** with the reset of an account a killed renter leaves dirty omitted
  from the build, the rental after SIGKILL finds both files, and the check fails.

## Acceptance

- Two runs at once get two different accounts of the same VM; a fifth run on a pool of four waits, then refuses
  after its time, naming the pool's size.
- An account given back, by a run that ended or one that died, holds nothing of that run when the next run gets it:
  measured in porelo-host's Molecule scenario on a VM the role made.
- A pool file that is missing, unreadable, of another format or names an account twice stops the run with that said.
