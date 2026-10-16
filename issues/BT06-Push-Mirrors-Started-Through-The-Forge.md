# BT06 — Push Mirrors Started Through The Forge

**Status:** Accepted.

**What must be true.** Each repository on the local forge mirrors its pushes to the central forge only when a mirror
is started through the local forge's API - in an order and staggered as the gathered pushes are today - never on its
own; the token for that API stays on the host, and whoever starts a mirror from a task holds only a broker's token.

From [PJ39](https://github.com/porelo-ai/porelo-project/blob/main/issues/README.md#pj39), where the operator approves each pull request on the local forge and the central forge receives only what
was approved.
