# BT57 — The Buildtools' Tests Follow The Pyramid

**Status:** Accepted.
**Size:** L - ten fixes, each XS, S or M, sized below; they come one after another, not as one change.

**What must be true.** porelo-buildtools' tests follow the test pyramid as AGENTS.md "Testing and machines" and
porelo-project's `doc/test-pyramid.md` set it out (story ST296). Each command's flow is plain logic behind a small
interface, proven by unit tests over fakes. Integration tests use a real `git`, `tar`, `bash`, a socket or the native
image narrowly on that core. The rented and VM runs (snapshot builds, agent legs, leases) are left to what nothing
lower can show.

## Where the tests stand

The audit was done on 2026-11-09 against main plus [BT55](README.md#bt55)'s build (3f99ccd). The counts are by agy's
reading. The times are from the Surefire reports of `./mvnw -B -s settings.xml -Pnative verify`, whose whole Build takes
1:56 min.

| Level | Methods (runs) | Share by count | Time |
|---|---|---|---|
| Unit | 726 (760) | 71 % | 11.3 s |
| Integration | 292 (423) | 29 % | 33.0 s, plus about 50 s for the native image |
| VM or rented | 0 tests | - | 2 rented snapshot legs in CI on a pin change, about 40 min each |

- **Integration** means loopback HTTP, `git`, `bash`, `tar`, `sleep` processes, a real DNS lookup, or the native
  image.
- **The VM and rented code is used, not tested here.** Agent legs, leases and `local` are used by other repositories
  and by the operator, with no test in this repository.
- **By count the shares fit the rule**, at 71 % unit. The time does not: integration takes three quarters of it.
- **Maven runs both levels in one Surefire step**, so no command runs the unit tests alone.

## Flows proven only through an integration, a VM or a rented run

- **`porelo-machines snapshot`** (`Snapshots.build`, `Main.snapshot`). It is seen only in the rented `snapshots`
  job. `instanceof Hetzner` at `Snapshots.java:320` and `Main.java:286` blocks a fake.
- **`porelo-machines acceptance` after the machine is acquired** (`AgentLeg.run`, `AgentLeg.cucumber`). It is seen
  only in other repositories' legs. Static `Ssh.to(...)` and the hard-coded `./mvnw test -Pacceptance` block a fake.
- **`porelo-machines lease` after SSH**, and **`Lease.awaitSsh` and `Lease.restart`**. Only the DNS-failure path in
  `RentalTest` is tested here.
- **`porelo-machines local` and `LocalVms`' wait for an address.** It is run by hand on a libvirt host only.
- **`check-linkage`** (`readelf`), **`check-package-version`** (`dpkg-deb`, `rpm`) and **`update`'s podman relock**.
  They are seen only in other repositories' package builds and update workflows. Only their string parsers are tested
  here.

## Tests that wait or need a real tool where a fake would do

- **`RentalTest.java:34-58`** makes a real DNS lookup and TCP connect to `porelo-test.invalid:22`. It takes 8.6 s, the
  slowest class.
- **`HetznerTest`**:
  - 42 of its 48 methods run against a loopback `HttpServer`, though `ApiTest` already tests `Api` itself;
  - it sleeps a fixed 2 s for an action to finish (`Hetzner.java:1009`), and makes 119 1-ms sleeps across 120 round
    trips;
  - it takes 7.3 s in all.
- **`StoppingTest`** waits at least 1.6 s across four `sleep 60` processes.
- **`ApiTest:55-64`** sleeps 1 s on `Retry-After`.
- **`PinnedRetryTest:86`** sleeps 1.3 s in `python3`.
- **`LocalPoolTest`** polls for 300 ms and 200 ms.
- **`CompareBillsTest` (20 methods) and `ArtifactoryCommandTest` (17 methods)** run against a loopback `HttpServer`,
  though `Web` and `Artifactory` are already interfaces with fakes in the same package.
- **`PlanCommandsTest` and `PlanStandTest`** start `git log -1` for every `file:` input, in 130 runs.
- **`StageTest`** starts `git init` and `git rev-parse` in 33 of its 34 methods.
- **`CheckCommitsTest:511-739`** makes about 140 `git` calls, though `CheckCommits.Git` is already an interface.
- **`CheckProvidersTest`** uses `git init`, `add` and `ls-files` in 10 of its 11 methods.
- **The `documents` tag** is missing on `WorkflowLimitsTest`, `WorkflowSigningTest`, `CheckActionsTest:25` and
  `PinTest:357`. They read `.github`, so `-Pdocuments` skips workflow guards a workflow change needs.

## Fixes, by value

1. **M: cut `RemoteSession`, `SshDialer` and a clock on `Lease`.**
   - What gets unit tests: `awaitSsh`, `restart`, `Rental.run` and `Main.local`.
   - Why it matters most: a fault here leaks or misprovisions a rented server. The DNS call in `RentalTest` goes.
2. **M: cut `SnapshotCatalog` and `TreeArchive`, and remove the `instanceof Hetzner` casts.** The snapshot build's
   steps, its reboot, imaging and pruning are then proven in milliseconds, where today they need a rented run of
   40 minutes.
3. **S: cut an `AcceptanceSuite` and the second users' `SshDialer` in `AgentLeg.run`.** This brings three things
   under unit tests: the candidate upload, the redaction of `acceptance.sh`'s credentials, and the lease's cleanup on
   a failed suite.
4. **S: run the unit tests apart from the integration tests.** `@Tag("integration")` is left out of Surefire's
   default run and run in a second execution at `verify`, as `documents` already works. `./mvnw test` then takes
   seconds.
5. **S: cut an `ElfReader` in `CheckLinkage` and `PackageMetadata` in `CheckPackageVersion`, with `Main` tests.** This
   closes two commands that have no test here today.
6. **XS: run `CompareBillsTest` over `FakeWeb` and `ArtifactoryCommandTest` over an in-memory `Artifactory`.** This
   removes 37 loopback servers.
7. **XS: an in-memory `FakeGit` for `CheckCommits.Git` in `CheckCommitsTest`,** and **XS: a `TrackedFiles` seam in
   `CheckProviders`.** Together they remove about 210 `git` calls.
8. **S: a `LocalCommitTime` seam in `PlanStand`,** and **S: a `GitDirectory` seam in `Stage`.** Hundreds of `git`
   calls in 190 runs go; one worktree test stays real.
9. **S: inject a clock and a sleeper into `Hetzner`, `Api`, `LocalPool` and `LocalVms`,** and **S: a `WatchedProcess`
   with a manual-tick scheduler in `Stopping`.** This removes about 5 s of sleep and the `sleep 60` processes.
10. **XS: tag the four workflow-reading tests `documents`,** and widen `DocumentTestsTaggedTest` so it finds them.

## Acceptance

- **The unit run:** `./mvnw test` runs the unit tests alone, with the share at 75 % or more by count. It takes under
  15 s.
- **The guarded flows:** the flows of fixes 1–3 each have unit tests over fakes. Each test was seen red with its rule
  removed.
- **No real network:** no test makes a real DNS lookup or a connection off the loopback.
- **No fixed sleeps:** no test sleeps longer than 50 ms.
- **The documents run:** `-Pdocuments` runs every test that reads `.github`.

## Why

The operator called the pyramid very important, and ST296 asks every repository to follow it. The costliest flows here
— leases, snapshots and agent legs — are proven only on rented machines or in other repositories. A fault in them is
paid in money or in another team's red Build.
