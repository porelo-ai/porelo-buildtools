# BT42 — Snapshots From Central And What The Host Built

**Status:** Implementing.
**Size:** M - `settings.xml`, its check and every Maven workflow here; each other repository takes its own copy.

**What must be true.** A build on a host's forge reads snapshots from the project's central source, Sonatype or its
Artifactory, and from the host's registry, which holds only what was built there and has not yet gone through the push and
the Build on the central forge. An empty registry on a freshly rented host builds at once. A private project's artifact
names are not asked of the central source, and nobody who claims such a name there is resolved.

## Why

`settings.xml`'s `forge` profile reads the host's registry alone, so that registry needs a first fill from a Build of
`porelo-parent` and then this repository before anything builds there.

## The shape

Measured with Maven 3.9.15 and two local servers; not yet on a forge.

- **Both are read, and the newest timestamp wins.** A newer registry beat central's higher build number, and a newer
  central beat the registry. An empty registry resolves from central alone.
- **The filter holds:** with `aether.remoteRepositoryFilter.groupId=true` and a file
  `groupId-<repository id>.txt` naming the groups that may go out, a group not listed was never asked of that
  repository. **With no file for that id, everything is asked**, so a missing file must not leave the door open.
- **A forge answering 401 is only a warning:** the build goes on from central, and a local change is silently not
  built against. So a missing read token must fail the job, as a missing `MAVEN_SNAPSHOTS_URL` does in
  [BT39](README.md#bt39).
- **Only `main` and tags deploy snapshots on a host.** A task branch's snapshot would be newest for every build there. Each
  repository keeps to that, and a shared check holds it, since the forge's credentials alone would allow it.
- **A missing token is not a missing artifact.** A build with no read token fails: it would not see what the host built. An
  artifact the host's registry does not hold is no failure: it resolves from central.
- **The profile's central repository keeps the id `central.sonatype.snapshots`**, so the filter's file has one name
  whichever source is named. Its address is an organization's variable, Sonatype's by default.
- **The groups are a file in the repository**, `.mvn/rrf/groupId-central.sonatype.snapshots.txt`, with the two settings in
  `.mvn/maven.config`, not a list in `settings.xml`. So they are reviewed with the code and hold alike on a desktop,
  in a task and on any forge, with no value carried through the host.

## Which copies change

No rule distributes `settings.xml`: each repository keeps its own, and they differ today. Each changes its own copy, by its
own agent, in the shape this repository sets first. The same change adds `.mvn/maven.config`, which none of them has, and
the filter's file. A shared check in `porelo-release` holds it: a repository with a `forge` profile has the central
repository, the filter's two settings and the file.

| Repository | Today | Change | Who |
|---|---|---|---|
| porelo-buildtools | `standard`, `forge` | the shape, its tests and the shared check | Agent Machines, first |
| porelo-message-sluice | `standard`, `forge` | its copy, before its trial move | Agent Sluice |
| porelo-claude-code, porelo-pi, porelo-omp | `standard`, `forge` | its copy, with its work on the local forge | Agent Smith |
| porelo-codex, porelo-agy | `standard` | takes the `forge` profile in this shape, with its work on the local forge | Agent Smith |
| porelo | `standard` | the same, with its Build on the local forge | Agent Core |
| porelo-message-matrix, porelo-adapters, porelo-frontend | `standard` | the same, each when its Build moves | its own agent |
| porelo-forge-clients, porelo-ecosystems | `standard` | the same, when its Build moves | its owner |
| porelo-parent | no profile | none: its pom uses nothing of another Porelo repository, and its `porelo-forge` server only deploys | - |
| porelo-intellij, porelo-host, porelo-ai.github.io | no `settings.xml` | none | - |

A repository with `standard` alone asks Sonatype about every name today. The filter's file closes that for a private project
too, as the repository's id is the same in both profiles.

## When central is newer than the host

A push from elsewhere can make central's snapshot newer, holding changes the host does not have, and that can break a
local build. It stays rare, so it gets no machinery.

- **Recognized:** the build's log says `Downloaded from central.sonatype.snapshots: …`, and `_remote.repositories` beside
  the jar in the local repository names the source of each.
- **The remedy:** bring central's `main` into the host's, through the usual pull and review; its Build deploys a newer
  snapshot, and the local one wins again.
- **Pinned until then, without a proxy:** `-Daether.remoteRepositoryFilter.groupId.basedir=<dir>`, a directory whose file
  for that repository is empty. Measured: central is asked nothing, and the host's snapshot is taken.
- **Left out:** detecting it automatically, a lock on versions, a proxy.

## Acceptance

- On `build/molecule.sh`'s VM or a forge: a Build with an empty registry is green; a snapshot deployed there is taken over
  an older central one; a private group's name appears in no request to the central source. Each seen to fail first.
- A repository with no filter file asks the central source nothing, and a job with no read token on a forge fails.

## To be checked

- **Failing closed:** whether a profile in `settings.xml` activates on the filter file's existence together with
  `MAVEN_SNAPSHOTS_URL`, so a repository with no file reads no central snapshots; otherwise a shared check refuses it.
- That the filter holds for the plugin repository and a parent pom as for a dependency.
- That a private group's release is not asked of Maven Central, whose `central` repository the filter leaves open.
- That the Artifactory's snapshots can be read as Sonatype's are, and with which credential.
- **Open for the operator:** the groups as a file in each repository, as above, or as a value in `project.yml`, which needs
  the follow, a variable and a step that writes the file.
