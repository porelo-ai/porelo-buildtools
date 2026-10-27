# BT37 — The Buildtools' Agent Works In A Porelo Task

**Status:** Idea.

**What must be true.** porelo-buildtools' own agent, `Agent Buildtools` in the project's room, works in a Porelo task on
the move's host as porelo-project [PJ59](https://github.com/porelo-ai/porelo-project/blob/main/issues/README.md#pj59)
lays out for sprint 7, beside porelo-message-sluice.

## Acceptance

- The task follows `porelo-buildtools` through the gate, its pushes reach the host's Forgejo, and the build there is
  green and read through the `forgejo` reader.
- This issue is taken from start to Review inside the task, and the agent is reached in the room as
  `Agent Buildtools`, from the nickname `Buildtools` in porelo-project's `project.yml`.

## To be checked

- **The repository alone moves.** `porelo-host`, kept by the same agent today, moves last of the builders in sprint 8a,
  by an issue of that repository's own.
- **The task's image** is `porelo.yml`'s: Ubuntu 26.04 by digest with the pinned GraalVM and shfmt, as the Build's
  pinned-jdk step gives a job. Neither machine credentials nor release keys enter it.
- **`build.yml` on Forgejo:** *Build and unit tests* runs on every push and pull request, and *Publish* deploys
  `main`'s snapshots to the forge's Maven registry by `PORELO_DEPLOY_PROFILE=forge-release`, as porelo-project
  [PJ60](https://github.com/porelo-ai/porelo-project/blob/main/issues/README.md#pj60) sets out for sprint 4. The cloud
  snapshot images (`snapshots`, switched off where `PORELO_PUBLISH` is `false`) and a release tag's *Publish*, with
  `central-sonatype-release` and the release keys, stay on the central forge. That this holds on the host's runner is
  read before the move.
- **The check of `main`'s last day** ([BT35](README.md#bt35)) reads GitHub's pushes; how it behaves in a build on the
  host's forge is read before the move.
