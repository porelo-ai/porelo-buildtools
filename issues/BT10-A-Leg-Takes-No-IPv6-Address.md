# BT10 — A Leg Takes No IPv6 Address

**Status:** Backlog.
**Size:** S - one field in the one place every server is created (`Hetzner.createWhenThereIsRoom`), a test of the body
it sends, the limit error checked, and one leg of each kind run on a server with no IPv6 address.

**What must be true.** Every server the tools rent is created without a public IPv6 address, so it holds one primary IP
instead of two. Before that, it is checked that nothing a leg does reaches out over IPv6: image pulls, package mirrors
and the runner's registration.

**Why.** The project may hold 16 primary IPs. With two per server, that caps it at 8 servers, whatever the vCPU limits
allow. On 2026-10-18 the operator's overview showed 14 of 16 in use with 7 servers running: porelo's two legs,
frontend's leased machine, a snapshot build, an update run's leg and two of Agent Core's. One more server filled the
project, while the dedicated vCPUs stood at 20 of 32. The operator asked for this issue to be built, 2026-10-18.

## The shape

- `Hetzner.createWhenThereIsRoom` builds the body of every server create: legs, leases, agent legs and the snapshot
  builds of `machines.yml`. It gains `public_net: {enable_ipv4: true, enable_ipv6: false}`.
- Nothing reads the IPv6 address today: `Hetzner` reads only `public_net.ipv4.ip` (`Hetzner.java:213`, and the listing
  at `:609`), and every ssh goes to that address.
- The wait loop retries only `resource_limit_exceeded`. If a full primary-IP quota answers with another code, that
  code is retried the same way, and the waiting line says it is the primary IPs (which
  [BT09](README.md#bt09) makes general).

## Acceptance

- [ ] The body sent for a server create holds `enable_ipv6: false` and `enable_ipv4: true`; a test of the body, seen to
  fail without the field.
- [ ] A server created by a lease has no IPv6 address in the API's answer, and the project's primary IPs rise by one
  per server: read once from the API.
- [ ] porelo's leg on ubuntu and on fedora, an agent leg and a snapshot build each pass on such a server.
- [ ] The answer to a full primary-IP quota is known, written here, and retried like the vCPU limit.

## To be checked

- Which code the API answers when the primary IPs are used up: `resource_limit_exceeded` with a message naming them,
  or a code of its own such as one about primary IPs. It is read from the API's documentation, or from a run's log
  that hit it, not by filling the quota on purpose.
- Whether anything on the machines needs IPv6: the package mirrors of ubuntu and fedora, the registries the images
  are pulled from, and GitHub for a runner's registration are expected to answer over IPv4; a leg run is the proof.
