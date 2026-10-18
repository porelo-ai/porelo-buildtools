# BT11 — A Rented Machine Answers With Its Own Key

**Status:** Implementing. Built in porelo-buildtools; porelo's legs and kit, and a rented run of each leg kind, are
left.
**Size:** M - a host key made per run and passed in the server's `user_data`, `Ssh` taking the one key it may accept
instead of `PromiscuousVerifier`, every caller handing it on (`Lease`, `AgentLeg`, `porelo`'s legs and kit), the key in
`lease`'s file, a written risk for the local VMs, and a run of each leg kind on a rented machine.

**What must be true.** `Ssh` connects to a rented machine only when that machine shows the host key made for it in this
run. For the local VMs and `porelo`'s `deploy`, accepting any host key is a risk written into `doc/decisions.md`, not an
omission.

## Why

The reading of this repository's code against its skills found, on 2026-10-18, that `Ssh` accepts any host key
(`PromiscuousVerifier` in `Ssh.reachable` and `Ssh.connected`). Somebody on the path between the runner and the
provider could pose as a rented machine. They could not reach the real machine, since key authentication cannot be
relayed. But they would receive what a leg sends, and could answer a leg as passed.

Decided by the operator, 2026-10-18:
- **Rented machines:** a host key made per run on the runner, its private half passed in the server's `user_data`,
  installed by cloud-init at boot. `Ssh` accepts that one key only. This holds for `porelo`'s legs, the agent legs and
  the kit alike.
- **The local VMs and `porelo`'s `deploy`:** accepted as a written risk in `doc/decisions.md`. Only the operator uses
  this machine, and the exposure needs a place on its libvirt bridge.

## The shape

- `Hetzner.createWhenThereIsRoom` builds the server's body. It gains `user_data`: a cloud-config whose `ssh_keys`
  carries the run's host key (for example an `ed25519_private` and `ed25519_public` pair), with the key types cloud-init
  would otherwise generate turned off, so the machine has the one key.
- The key is made on the runner per run and never written to the log. Its public half travels with the machine
  (`Lease`, `Server` or `Spec`) to everyone that opens an `Ssh` to it.
- `Ssh.to` and `Ssh.reachable` take the expected public key. Their verifier accepts that key and refuses any other,
  naming the machine and the fingerprint it showed.
- `lease --write <file>` writes the public half beside `address`, `user` and `socket`, as
  `hostkey=ssh-ed25519 AAAA…`. A leg that reaches its machine with OpenSSH rather than `Ssh` puts that line into
  `known_hosts` instead of taking the first key `ssh-keyscan` returns: `porelo-frontend`'s leased leg (`tool/e2e.sh`)
  does that today, and its own change is an issue there.
- `AgentLeg` hands the expected key to the suite it starts, beside `-Dporelo.acceptance.host`, `user` and `key`, as a
  new property such as `-Dporelo.acceptance.hostkey`, and `porelo`'s kit checks it in its ssh client. Without the
  property the kit keeps working against the local VMs, which is the written risk; the agent repositories add one line
  to their `pom.xml` each.
- For a host that is not rented (the local VMs, `deploy`), the caller says so explicitly. That is the case written down
  as an accepted risk.

## Acceptance

- [ ] A rented machine is created with the run's host key in its `user_data`. On it, `/etc/ssh/ssh_host_*_key.pub`
  holds only that key.
- [ ] `Ssh` to a rented machine connects when the host shows the expected key. A test with a server showing another key
  is refused, naming the host and the fingerprint, and is seen to fail against today's `PromiscuousVerifier`.
- [ ] The private half of the host key appears in no log, post or file left on the runner after the run.
- [ ] `porelo`'s legs, the agent legs and `porelo`'s kit each pass on a rented machine with the check on.
- [ ] The kit given `porelo.acceptance.hostkey` refuses a host showing another key; without it, it connects to the
  local VMs as today.
- [ ] The file `lease --write` writes holds `hostkey=` with the machine's public host key, and a known-hosts line made
  from it lets OpenSSH connect with `StrictHostKeyChecking=yes`.
- [ ] Accepting any host key for the local VMs and `porelo`'s `deploy` is written into `doc/decisions.md`, with the
  reason above. Each call that accepts any key says so in its code, not by default.

## To be checked

- Whether the CI snapshots built by `machines.yml` still run cloud-init at first boot of a server made from them, and
  read `user_data` there. A snapshot taken after cloud-init ran once may consider itself already initialised.
- Whether Fedora's own key generation at sshd's start makes keys of other types once cloud-init has deleted them, so
  that `/etc/ssh` holds more than the one key; the client asks for the pinned key's type first either way.
- Whether the machine scenario in `porelo`'s `machine.feature` that runs `ssh -o StrictHostKeyChecking=no` to a local
  account counts as one of the written risk's cases, or connects to `localhost` and needs nothing.
