# BT33 — Machines From Providers Behind One Interface

**Status:** Idea.

**What must be true.** `porelo-machines` asks for a machine through one interface, and each place a machine can come
from is a provider behind it that knows what providing one takes. A workflow names only the kind
([BT25](README.md#bt25)); which providers serve that kind, with their parameters and in which order, is data: the same
variable on both forges, with a different value on each.

## Why

Renting a machine and taking one that already runs differ only in what providing it takes: a rented one is created
and started first, a pool's or a fixed server's is there. Today Hetzner is the provider in the code, and the host's
pool is a way of its own beside it (`--pool`). With providers behind one interface, a new place for machines is one
implementation, the choice between them is configuration, and no workflow and no variable exists on one forge only.

## The design idea

### Three parts

1. **The interface** a leg talks to. It knows kinds, systems and runs, never a provider.
2. **The providers**, one implementation each, knowing what providing a machine means for them.
3. **The description**, which says for each kind which providers serve it, in which order, with which parameters
   and which secrets. It is data, read at the start of a run.

A run asks the interface for a kind; the interface goes through that kind's providers in the description's order and
takes the first that can provide one now; the lease that comes back says nothing of where the machine is from.

### The interface

Today's `Machines` (`machines/src/main/java/org/fuin/porelo/machines/Machines.java`) already has `acquire(Spec)`
returning a `Lease` that is given back by closing it, and both `Hetzner` and `LocalPool` implement it. What it lacks
for a provider of its own:

| Operation | What it does | A cloud provider | A pool or a fixed server |
|---|---|---|---|
| **acquire** | a machine of the kind and system, for this run; its address, account and host key | create, start, wait until ssh answers | lock a free run account |
| **release** | the lease's close | delete the machine | reset the account, then unlock it |
| **sweep** | what a broken run left, found and cleaned up | machines carrying the run's or an old label | accounts locked by a run that is gone, reset and unlocked |
| **capacity** | how many more it can provide now, and whether at all | the provider's limit (Hetzner's count of servers) minus what runs | free accounts |

`capacity` lets the interface go on to the next provider instead of failing, and lets a run wait for a while when
every provider is full. `sweep` replaces today's Hetzner-only sweep (`sweep --mine`) by one sweep over every provider
of the description, so the same step works on both forges.

### Two sorts of provider

Behind the interface, providers come in two sorts, since they do different things:

- **Providing**: a machine is made for the run and removed afterwards. This is what OpenTofu does, with a provider
  of its own for most clouds and hypervisors (Hetzner, Scaleway, AWS, Azure, Proxmox, vSphere, libvirt). So
  `porelo-machines` has **one** providing implementation that runs OpenTofu, with one module per place, instead of
  code of its own for each cloud. The host itself is rented through OpenTofu too, so host and test machines use
  the same tool and the same modules where they can.
- **Lending**: the machine already runs; a run account on it is locked for the run and reset afterwards. The host's
  VM pool and a fixed server are lent, never provided, since nothing is made or removed. OpenTofu has no part in it.

What OpenTofu does not do stays in the interface: choosing among a kind's providers in order, capacity, waiting for
a free one, and the sweep.

- **OpenTofu's state.** A run that dies loses the state file on its runner, so what it made is found again by its
  labels, as today's sweep does, or the state lives where every run reaches it. Which one is to be checked.
- **OpenTofu's own providers are a supply chain.** They are pinned with their digests in a lock file, fetched once
  into the runner's image or a cache, and never resolved by a moving version at run time.
- **Its cost per run**, `tofu init` and the plan, is measured against today's direct API calls before Hetzner's own
  code goes.

### The providers

- **Cloud through OpenTofu** (Hetzner today, Scaleway or others as further modules): parameters are the module, the
  machine types in order (today's `Spec.serverTypes`), the location, the image or snapshot, the labels; secrets are
  the provider's API token and the ssh key. A lease belongs to the run alone. Today's `Hetzner` class is the
  provider until the OpenTofu one replaces it.
- **Host pool** (porelo-host's VM pool):
  parameter is the pool file the host writes; secret is the run key. Today's `LocalPool`.
- **Fixed server**: a server that runs all the time, anywhere the runner reaches, with run accounts that can be
  reset. Parameters are its address, host key and accounts; secret is the ssh key. Several runs share it, so the lock
  and the reset live on the server, not in the run.

A provider is one class behind the interface, plus its entry in the description's schema. Nothing outside it knows
what it does.

### The description

One variable on both forges, for example `vars.PORELO_MACHINES`, a JSON document:

```json
{
  "amd64": [
    {"provider": "fixed", "address": "…", "hostKey": "…", "accounts": 4, "secret": "PORELO_MACHINES_FIXED_SSH"},
    {"provider": "tofu", "module": "hetzner", "types": ["cpx42", "cx43"], "location": "fsn1",
     "secrets": {"api": "PORELO_MACHINES_API", "ssh": "PORELO_MACHINES_SSH"}}
  ],
  "amd64-snapshot": [
    {"provider": "tofu", "module": "hetzner", "types": ["ccx33"], "location": "fsn1",
     "secrets": {"api": "PORELO_MACHINES_API", "ssh": "PORELO_MACHINES_SSH"}}
  ]
}
```

- **On the central forge** a kind names a cloud provider, or a fixed server first and the cloud when it is full.
- **On the host's forge** the same kinds name the host pool, set by porelo-host at `up`.
- **The secrets** stay secrets; the description holds only their names, and a workflow passes every secret the
  description may name, as it does today. A secret not set on a forge is empty there and unused.
- **Checked when read**: an unknown provider, a missing parameter or a secret named but empty is refused before
  anything is asked for, naming the kind and the entry.

### What stays as it is

- The workflow's call: `porelo-machines acceptance --kind … --os …` ([BT25](README.md#bt25)).
- The lease and what a leg does with it (`AgentLeg`, `Rental`).
- The check that no workflow names a provider (`check-providers`).

## To be checked

- How a fixed server's lock and reset are held when runs on different runners share it, and what resets an account
  whose run died without releasing it.
- Whether `capacity` can be known for a cloud provider before asking (Hetzner's limit is per project, not per
  run), or only by trying.
- Whether a run waits for a full provider, and for how long, or fails at once.
- Whether the host's VM pool, as libvirt machines made per run through OpenTofu, would be simpler than lending
  accounts, against a boot per run instead of a reset in seconds.
- Whether `acquireFromStock` (a stock image instead of one of ours) stays part of the interface or becomes a
  parameter of the cloud provider.
