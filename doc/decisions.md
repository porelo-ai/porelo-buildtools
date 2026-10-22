# Decisions

The choices the build tools are built on, each with why it holds and, for an accepted risk, the exposure, why it stays
and what would change the answer.

| Subject | What holds | Link |
|---|---|---|
| **Machines** | | |
| A rented machine answers with its own key; a local one with any | a host key made per machine in its `user_data`, pinned by every connection to it; a local virtual machine accepts any key, its caller saying why | [entry](#a-rented-machine-answers-with-its-own-key-a-local-one-with-any) |
| A sweep deletes by expiry, not by age | every server a lease creates is labelled `porelo-expires` from its job's time limit plus 15 minutes; `sweep` deletes only servers past it or without one, `--including-in-use` alone those within it, and `--older-than` is refused | [entry](#a-sweep-deletes-by-expiry-not-by-age) |
| A pool VM goes back to its snapshot after each run | each pool VM holds one run account; given back with `sudo porelo-pool-give-back`, it is reverted to its base snapshot on the host before anyone rents it again | [entry](#a-pool-vm-goes-back-to-its-snapshot-after-each-run) |

## A rented machine answers with its own key; a local one with any

A rented machine is created with an Ed25519 host key made for it on the runner, its private half in the server's
`user_data` and installed by cloud-init at first boot, the keys the image came with deleted and none generated. Every
`Ssh` to it accepts that key alone, compared on its wire encoding, and refuses any other naming the host and the
SHA-256 fingerprint it showed; `lease --write` records the public half as `hostkey=`, and an agent leg hands it to its
suite as `porelo.acceptance.hostkey`. A local virtual machine has no key anybody made for it: `LocalVms` accepts
whatever it shows, with `HostKey.any` and a reason, and so does `porelo`'s `deploy`. No call gets either by default.
**The exposure:** whoever can answer at a local machine's address can pose as it, take what a leg or a deploy sends
there, and answer as passed; key authentication cannot be relayed, so it reaches neither the machine nor anything
behind it. **Why it stays:** only the operator uses this host, and answering at that address needs a place on its
libvirt bridge, which is the host itself or a guest on it. **What would change the answer:** a second person on the
host, a local machine reached across a network the operator does not hold alone, or a local machine whose host key is
made the way a rented one's is.

## A sweep deletes by expiry, not by age

Every server `Hetzner` creates carries `porelo-expires`, the UTC minute its time ends (`20261018T0528Z`): its
creation, plus the time limit the renting command was given (`--for <minutes>`, 60 by default, at most 360), plus 15
minutes. The sweep of everyone's servers deletes a server past that minute and a `porelo=ci` server with no such label;
it keeps one still within its time, whatever its age, and deletes it only with `--including-in-use`. `--older-than` is
gone, and refused with a message saying what replaced it. `--mine` and `--from` delete their own run's servers
whatever the label. **Why:** an age threshold cannot tell a forgotten server from a long test's: an hourly sweep at 60
minutes took the machine of any job that ran longer, and `--older-than 0 --now` deleted every server in use. Only the
job that rents knows how long it may run, so it writes that down where every sweep can read it. GitHub does not hand a
job its `timeout-minutes`, so a workflow whose job runs longer than an hour passes `--for` beside it. The margin covers
the clean-up step after the renting one - the snapshot job gives its 60-minute step 75 minutes - and clocks that
differ; minutes are fine enough against it, and that form is readable in the console without converting. Keeping
`--older-than` beside the label would have been a second rule for the same question, and the one that does the harm.
**What would change the answer:** a provider that ends a server at a time it is given, which would make the label the
provider's own; or jobs whose length cannot be known before they start, which would need a lease that renews its
label while it runs.

## A pool VM goes back to its snapshot after each run

`porelo-machines acceptance --pool` holds one run account of the host's local VM pool per run, and each pool VM holds
one run account. When the run gives it back, or a run that died left it, `porelo-machines` connects as that account and
runs `sudo porelo-pool-give-back`, the one root-owned command the account may run; the VM writes `resetting` and powers
off, and porelo-host's role on the host, outside every job, reverts it to its base snapshot and starts it, which says
`ready` again. A run account installs packages through sudo and is therefore root on its VM; only the revert makes sure
that nothing a run did reaches the next. No key that reaches the host or another VM is in a job, since the builds that
rent from the pool include task branches nobody reviewed. One account per VM costs a small VM for each account the pool
offers, and a revert per run.

The run key reaches a job as a forge Actions secret in `PORELO_POOL_SSH` (or `--key <file>` on a developer's machine),
never as a file in CI, and the pool file holds nothing secret.
