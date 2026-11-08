# BT23 — One Upload Helper For Every Publish

**Status:** Review.
**Size:** S - the `artifactory` command of porelo-release on its Artifactory client, the retry rule in a module of its
own, the doc page.

**What must be true.** Every Porelo repository that publishes to Artifactory uploads through one helper that
porelo-buildtools publishes and each Publish job takes by version and digest. No repository keeps its own copy:
porelo, porelo-pi, porelo-omp, porelo-claude-code, porelo-codex, porelo-agy and porelo-message-matrix retire
`.github/scripts/artifactory.sh`, seven identical copies today, and porelo-message-sluice, porelo-intellij,
porelo-frontend and porelo-adapters take the helper in place of the curl calls their Publish steps carry.

## Acceptance

- Each of the eleven repositories' Publish job uploads with the published helper and none keeps a copy of it; a Publish
  that takes a helper whose digest does not match stops before it uploads.
- The token reaches the helper from the step's environment, never on a command line and never in a file: a secret never
  touches a filesystem in CI. The copies took it on curl's standard input only to keep it off curl's command line; a
  Java process reads it from its environment and has no such command line.

## Done

- **The command:** `porelo-release artifactory put|api|ping`, the calls the seven copies of `artifactory.sh` offer
  (`art_put`, `art_api`, the smoke jobs' ping), on the Artifactory client the snapshot pruning already uses.
  - `put` streams the file with `X-Checksum-Sha1` and `X-Checksum-Sha256`, takes the file's own name for a target
    ending in `/`, and sets the properties as matrix parameters. It refuses a missing file or a link before asking.
  - `api` prints the answer; a 404 fails. `ping` wants `OK`.
  - A target, a property, a method and a path are held to their form before anything is asked: only characters no URI
    escapes, no `.` or `..` segment, none escaped either, no empty segment. A target ending in `/` is checked again
    with the file's name. The client sends nothing whose path leaves `/artifactory`.
- **The token:** from `JF_ACCESS_TOKEN`, only to `JF_URL`'s origin, which must be https and without `/artifactory`.
  The checks are the ones prune-snapshots made, now in one place for both.
- **Asked again:** a 5xx or no answer, by the pinned rule's pauses and deadline; a 4xx fails at once. The client
  now tells the two apart: `Artifactory.Unanswered` for a 5xx or no answer. Not asked again: a refused certificate,
  a file that could not be read, and a file that changed while it was sent. prune-snapshots' probe says
  unanswered (2), not refused, when Artifactory does not answer it; an interruption is unanswered too.
- **The rule in one place:** `PinnedRetry` moves from porelo-machines into a module of its own,
  `porelo-pinned-rule`, which porelo-machines and porelo-release both use. porelo-release does not take on
  porelo-machines' SSH libraries.
- **The call:** through the parent's `ci-tools` profile, as seven repositories already run porelo-release. The doc page
  `doc/publishing.md` has it.
- **Every Publish job can run it:** the jobs that upload without the pinned GraalVM (release, smoke, and
  porelo-message-matrix's build) run it with the runner's Java 25, `JAVA_HOME_25_X64`. porelo-intellij takes it once
  0.5.2 is released, with its own Gradle class path.

## Reading

Read once by agy `gemini-4-argon-high` (634 s), with seven findings:
1. (grave) An api path could hold `..` and so leave `/artifactory`, or a character `URI.create` refuses. Taken:
   segments of a URI's own characters, no dot segment, escaped or not; the client refuses a path outside.
2. A property value or a target could hold a character a URI refuses, or `;`. Taken: only a file name's characters;
   the target is checked again with the file's name.
3. (grave) A failure of the file or of the certificate counted as unanswered and was asked again. Taken: those fail
   at once, and a file that changed while it was sent too; the dead catch is gone.
4. (grave) The retry and checksum tests were said to run on `ping` only. Half taken: they ran on `put` already, with
   the exact checksums; now they also show the whole file and the same checksums each time, and `api` is asked again.
5. An interruption exited refused. Taken: unanswered.
6. prune-snapshots' probe took no answer for a refusal. Taken.
7. A stale module name in the doc and a doubled comma. Taken.

Five mutations, one for each fix, each turn one test red.

## Tests

`ArtifactoryCommandTest`, against a stand-in server, which does not compile against the code before:
- a put under the file's own name: both checksums, the properties and the token;
- a put under the name given;
- two 5xx answers, then stored, with the rule's pauses;
- eight 504 answers: given up;
- a 403: refused at once, no pause;
- a missing file and a link: refused before anything is asked;
- a file changed while it was sent, and a file whose name no target holds: refused;
- an interruption: unanswered;
- nothing sent outside `/artifactory`; only no answer is asked again, not a certificate or a file;
- `api` asked again after a 5xx;
- `api`'s answer printed, and its 404 refused;
- `ping` with `OK` and without;
- 28 calls out of form refused; a query, an escape and a Debian package's name accepted;
- no `JF_URL`, an http one, no token, a call out of form: refused, the token never said.

`PinnedRetryTest` in the new module holds the rule's numbers. The shell's tests stay in porelo-machines.
