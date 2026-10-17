# BT10 — A Leg Takes No IPv6 Address

**Status:** Accepted.

**What must be true.** A leg's server is created without a public IPv6 address, so it holds one primary IP instead
of two. Before that, it is checked that nothing a leg does reaches out over IPv6: image pulls, package mirrors and
the runner's registration.

**Why.** The project may hold 16 primary IPs. With two per server, that caps it at 8 servers, whatever the vCPU
limits allow.
