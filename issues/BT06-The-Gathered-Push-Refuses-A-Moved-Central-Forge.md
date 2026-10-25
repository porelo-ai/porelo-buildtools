# BT06 — The Gathered Push Refuses A Moved Central Forge

**Status:** Accepted.

**What must be true.** The gathered push sends each repository from the local forge to the central forge with a plain
`git push` without force, in an order and staggered as the gathered pushes are today. When the central forge holds a
commit the local forge lacks, the push of that repository is refused, says so, and loses nothing on either forge; the
change is then merged on the local forge and pushed again. The token for the central forge stays on the host, and
whoever starts a gathered push from a task holds only a broker's token.

From [PJ39](https://github.com/porelo-ai/porelo-project/blob/main/issues/README.md#pj39), where the operator approves each
pull request on the local forge and the central forge receives only what was approved.

## Decided

- **No push mirrors (operator's decision).** Forgejo's push mirror force-pushes. Measured with a stand-in for the
  central forge: with one commit on its `main` and another on the local `main`, a mirror sync answered 200, reported
  no error, and the central commit was gone. porelo-host
  [HO08](https://github.com/porelo-ai/porelo-host/blob/main/issues/README.md#ho08) therefore configures no push mirror.
