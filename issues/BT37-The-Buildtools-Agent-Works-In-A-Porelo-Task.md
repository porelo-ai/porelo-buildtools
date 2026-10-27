# BT37 — The Buildtools' Agent Works In A Porelo Task

**Status:** Idea.

**What must be true.** porelo-buildtools' own agent works in a Porelo task of the project `porelo` on the move's host,
as porelo-project [PJ59](https://github.com/porelo-ai/porelo-project/blob/main/issues/README.md#pj59) lays out for
sprint 7, beside porelo-message-sluice. The task follows this repository through the gate and pushes to the host's
Forgejo, whose build is read through the `forgejo` reader. The agent is reached in the project's room under this
repository's name. This issue is taken from start to Review inside the task, with the forge's build green.

## What moves, and what to check

- **The repository:** `porelo-buildtools` alone. `porelo-host`, today kept by the same agent, moves last of the builders
  in sprint 8a, by an issue of that repository's own.
- **The task's image:** this repository's `porelo.yml`, Ubuntu 26.04 by digest with the pinned GraalVM and shfmt, as
  the Build's pinned-jdk step gives a job.
- **Forgejo's build:** `build.yml` builds and tests there. Its snapshots go to the forge's Maven registry by
  `PORELO_DEPLOY_PROFILE`, as porelo-project
  [PJ60](https://github.com/porelo-ai/porelo-project/blob/main/issues/README.md#pj60) sets out for sprint 4. Its legs
  that rent a machine, and Publish with the release keys, stay on the central forge: no task holds them. Which of its
  jobs `PORELO_PUBLISH` and the machines' kinds
  ([BT25](README.md#bt25)) already switch, and which still name the provider, is read before the move.
- **The way back:** none beyond the move's own step 5, which holds for every agent.
