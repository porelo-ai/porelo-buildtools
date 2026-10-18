# Decisions

The choices the build tools are built on, each with why it holds and, for an accepted risk, the exposure, why it stays
and what would change the answer.

| Subject | What holds | Link |
|---|---|---|
| **Machines** | | |
| A rented machine answers with its own key; a local one with any | a host key made per machine in its `user_data`, pinned by every connection to it; a local virtual machine accepts any key, its caller saying why | [entry](#a-rented-machine-answers-with-its-own-key-a-local-one-with-any) |

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
