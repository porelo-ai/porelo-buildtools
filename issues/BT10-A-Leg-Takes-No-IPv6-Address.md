# BT10 — A Leg Takes No IPv6 Address

**Status:** Review.
**Size:** S - one field in the one place every server is created (`Hetzner.createWhenThereIsRoom`), a test of the body
it sends, the limit error checked, and one leg of each kind run on a server with no IPv6 address.

**What must be true.** Every server the tools rent is created without a public IPv6 address, so it holds one primary IP
instead of two. Before that, it is checked that nothing a leg does reaches out over IPv6: image pulls, package mirrors
and the runner's registration.

**Why.** The project may hold 16 primary IPs. With two per server, that caps it at 8 servers, whatever the vCPU limits
allow. On 2026-10-18 the project's overview showed 14 of 16 in use with 7 servers running: porelo's two legs,
frontend's leased machine, a snapshot build, an update run's leg and two more of porelo's. One more server filled the
project, while the dedicated vCPUs stood at 20 of 32.

## The shape

- `Hetzner.createWhenThereIsRoom` builds the body of every server create: legs, leases, agent legs and the snapshot
  builds of `machines.yml`. It gains `public_net: {enable_ipv4: true, enable_ipv6: false}`.
- Nothing reads the IPv6 address today: `Hetzner` reads only `public_net.ipv4.ip` (`Hetzner.java:213`, and the listing
  at `:609`), and every ssh goes to that address.
- The wait loop retries only `resource_limit_exceeded`. If a full primary-IP quota answers with another code, that
  code is retried the same way, and the waiting line says it is the primary IPs (which
  [BT09](README.md#bt09) makes general).

## Acceptance

- [x] The body sent for a server create holds `enable_ipv6: false` and `enable_ipv4: true`; a test of the body, seen to
  fail without the field. `LeaseTest.createsTheServerWithAnIpv4AddressAndNoIpv6One`, red against the code before.
- [x] A server created by a lease has no IPv6 address in the API's answer, and the project's primary IPs rise by one
  per server: read from the API, read-only, every 30 seconds during the Builds of 2026-10-26 20:00Z-20:11Z, whose
  servers the same `Hetzner.createWhenThereIsRoom` creates as a lease's. Before them the project held no server and
  no primary IP. With one server running (an acceptance leg) it held one primary IP; with three (that leg and porelo's
  legs on ubuntu and fedora) it held three, every one IPv4, and each server's `public_net.ipv6` was empty.
- [x] porelo's leg on ubuntu and on fedora, an agent leg and a snapshot build each pass on such a server. Read on
  GitHub, each Build green in the named jobs and made after `c8f747b` reached `main`:
  - porelo `40302687639` (*Leg - ubuntu*, *Leg - fedora*);
  - porelo-buildtools `40382569563` (*Snapshot - ubuntu*, *Snapshot - fedora*);
  - porelo-frontend `40219868423` (*Test against a leased machine*);
  - porelo-claude-code `39822354763` (*Acceptance - ubuntu*, *Acceptance - fedora*).

  That each took a porelo-machines snapshot with the change is inferred: each resolves the newest snapshot, and every
  one published since carries it.
- [x] The answer to a full primary-IP quota is known, written here, and retried like the vCPU limit. The API's
  reference (`cloud.spec.json`, read 2026-10-18) names one code for every quota of an account: `403
  resource_limit_exceeded`, "Error when exceeding the maximum quantity of a resource for an account". Its
  `primary_ip_*` codes (`primary_ip_assigned`, `primary_ip_version_mismatch` and the like) are about assigning an
  address, not about running out of them. The wait loop already retries `resource_limit_exceeded`, so nothing changes
  there.
