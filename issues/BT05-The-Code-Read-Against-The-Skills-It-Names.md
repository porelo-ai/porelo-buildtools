# BT05 — The Code Read Against The Skills It Names

**Status:** Analyzed.
**Size:** L - every module, about 11,000 lines of Java and 7,100 of tests in five modules, read against seven skills,
and each finding a fix with a test seen to fail first; one commit per module.

**What must be true.** Every module of this repository has been read against the skills `AGENTS.md` names, and each
finding is either fixed with a test seen to fail first, or declined with the reason written here.

## Why

`AGENTS.md` has named seven skills since the repository was made public, and none of its code was read against
them here. Only `porelo-release` was read, while it still lived in `porelo`, against three of them (`java-code-review`,
`test-quality`, `security-audit`); that reading found two defects its tests had not - a CI token sent wherever a pom
property pointed, and a fault that answered the question with exit 1. The rest was written without them.

## Plan

**The skills, read against these versions** (from `https://fuinorg.jfrog.io/artifactory/agent-skills/`, each zip
checked against its SHA-256 before it is unpacked; a newer version taken while this is open is named in its module's
commit):

    graal               2026.910.3  b24ec6272fb13490e32977c21d4543869a7e85ed474fdc0ff34022fbddd88bf6
    java-code-review    2026.906.3  802f86339442ed9fa9d5bca14b6d16e1f6420ae92a047b18bba11239e32c698b
    test-quality        2026.906.3  9c5d23ad7a6d9c6f5c3679f3cc44a7fcc2fb3cc1c0f0d0ba4a2c0f7812428bad
    security-audit      2026.906.3  a46fd1be07bd539b955291537dea0f24f65a2f8b15d40d50a6a9098bbd992747
    concurrency-review  2026.906.3  3a0aab612f25483a3fe777eb18fbcc434e936b19298708bf88beff8b22d79e56
    clean-code          2026.906.3  5cf777a24630298b4b801c934f599722978c21aeac53d61683243908fc041c7b
    solid-principles    2026.906.3  bd47cdab9126dde76cf1703472167a1c88c85306a4e0263a8f2db4c462740cf5

**What is read,** in this order - what holds a credential or reaches another host first, so a finding there never
waits behind a style note:

1. `release` (34 classes, ~6,200 lines, ~4,600 of tests): the shared checks and the update decisions, which hold a
   GitHub token and read other repositories; all seven, `graal` for what `check-*` asks of a native build.
2. `hetzner` (17 classes, ~4,100 lines, ~2,200 of tests) and its `install-musl.sh`: the cloud API token, ssh to rented
   machines, deletion of what a run made; all seven, the script against `security-audit`.
3. `json` (~340 lines): the reader the others share, against input from outside.
4. `ffm-check` and `cpu-check` (~350 lines): they judge others' native binaries, so `graal` is read for what they
   check as much as for how.

Each module ends in one commit, green with the whole reactor and the shared checks, its findings listed here under
the module's name: fixed (with the test seen red) or declined (with why). A finding is a guess until a failing test
reproduces it.

## Done so far

- Every module's main and test code read against all seven skills at the versions above; `graal`, `clean-code` and
  `solid-principles` fetched and checked against those digests, the other four taken unpacked at the same versions.
- `release`: `91e5451` *Read job permissions at any indent, and fix three release checks*.
- `hetzner` and `install-musl.sh`: `4787ac5` *Keep the leg's key off command lines, and sweep past a refused delete*.
- `json`: `ec8d297` *Say why the JSON writer takes a buffer, and drop a check never reached*.
- `ffm-check`: `caa73e0` *Check every downcall record the tracing agent left, not the last one*.
- `cpu-check`: read, nothing to change, so no commit.
- On each commit: `verify` of the whole reactor, the tests tagged `documents`, `check-shared`, `check-citations`
  (`--checkouts`), `check-readmes`, `check-doc-site` and `check-actions` green; the deploy dry run and `check-deploy`
  green on `91e5451`. No VM and no rented machine ran.
- `release`'s earlier reading in `porelo` still holds here: a GitHub token goes only to `https://api.github.com`
  (`Release.githubHeaders`), and a fault inside a command exits 2, never 1 (`Main.run`).

## Findings

Each fixed finding names the harm and the test, which was run red before the fix and green after it.

### `release`

- **Fixed: `check-actions` read no job's `permissions:` in a workflow indented by four spaces**, the shared style: a
  job was only found at two spaces, so an unlisted job granting `contents: write` passed (measured: exit 0 on such a
  workflow). A `jobs:` followed by a comment hid the file's permissions altogether. Harm: a check passed around, a
  token that can write beside every fetched step. The depth of a job and of its keys is now read from the file.
  `CheckActionsTest.refusesAWriteInAJobOfAWorkflowIndentedByFourSpaces` and
  `readsTheJobsOfAWorkflowWhoseJobsKeyCarriesAComment`; `acceptsTheWritesTheListNamesInAWorkflowIndentedByFourSpaces`
  holds that nothing listed is refused. Run against every repository checked out beside this one: no result changed.
- **Fixed: `update` rewrote the line a release shipped with**: its own earlier line was looked for in the whole
  changelog, so after a release the next move replaced the released section's line and wrote nothing under
  `[Unreleased]`. Harm: a released record changed, the new move unrecorded. It is looked for under `[Unreleased]` only.
  `ChangelogTest.leavesTheLineAReleaseShippedWithAndWritesANewOneUnderUnreleased`.
- **Fixed: a release dated by its day alone (Node's index) counted from the start of that day**, the earliest it can
  have been, so it was taken up to a day before it was as old as `--min-age`, against the rule its own Javadoc stated.
  Harm: a check passed around. It counts from the end of the day.
  `PinTest.aNodeReleaseOfADayLessThanTheBoundAgoIsNotTaken` (red: `update=yes`); the test that held the start of the
  day was rewritten to the end of it, and `AgeTest`'s copy of it deleted.
- **Fixed: `check-deploy` excepted any host whose name ended in `apache.org`**, so an upload to `repo.notapache.org`
  passed the dry run. Harm: a check passed around. Only `apache.org` and its subdomains are excepted.
  `CheckDeployTest.refusesAnUploadToAHostWhoseNameOnlyEndsLikeApachesOwn`.
- **Fixed without a test, no harm:** `CheckReleases` left the effective pom's stream open; `NoOldNameTest` ran git
  without `GIT_CONFIG_GLOBAL=/dev/null`; a Javadoc in `CheckActions` sat above the wrong field.
- **Not fixed, waits on a decision: `check-citations` passes when another repository's index cannot be read for a
  failure**, not only when it is not published: an unreachable host or an HTTP 500 becomes a `NOTE` and exit 0, and the
  links into it go unchecked. The shared rule says fail closed; making it exit 2 changes what a command every
  repository runs answers, and touches what [BT04](README.md#bt04) builds.
- **Declined:**
  - `update` writes its files one after another, not as one: a failure in between leaves a working tree that git
    restores, and a move is a pull request a person reviews.
  - `Pinned` takes SnakeYAML's typed scalars, so `1.10` would read as `1.1`: the CLI's version is three numbers, and an
    update writes the digest quoted.
  - `check-releases` refuses `-SNAPSHOT` and not a timestamped snapshot version: Maven writes no such version into an
    effective pom unless a person typed it.
  - `check-citations` looks for numbers of one or two letters outside the issues: every prefix in use has at most two,
    and three would read `SHA256` as one.
  - `compare-bills` keys components by purl, so two of one purl count once; `check-doc-site` counts a page named in a
    comment of `mkdocs.yml`: neither does harm.
  - Most assertions in the existing tests carry no `.as(...)`, against `AGENTS.md`: a sweep of every test is a change
    of its own; the tests added here carry one.

### `hetzner` and `install-musl.sh`

- **Fixed before the reading started: `Ssh.run` read a command's output to its end before its errors**, so a command
  blocked writing more errors than the channel holds never closed its output, and the call waited for ever. Both are
  read side by side; `SshOutputTest` holds it with pipes, red with the old order.
- **Fixed: an agent leg put the OpenRouter key and the model on the far shell's command line**, so the key was in that
  shell's arguments for the whole suite and in the message a failed leg ends with, printed as `::error::`. Harm: a
  credential in a log. They now go on the far shell's standard input, which exports them, and the suite reads no
  input. `AgentLegTest.sendsTheSuitesCredentialOnItsInputNeverOnItsCommandLine`; the far command was run in a local
  bash, not on a machine.
- **Fixed: `sweep` and a run's clean-up stopped at the first server they could not delete**, leaving the rest. Harm:
  machines left billing. Each refusal is kept, the rest deleted, and the call fails naming every server left.
  `HetznerTest.deletesTheRestOfARunsServersWhenOneCannotBeDeletedAndNamesIt` and
  `sweepsTheRestWhenOneServerCannotBeDeletedAndNamesIt`.
- **Fixed: `Credential.InMemory`, a record, printed its private key as text**, and so did a `Spec` holding one. Harm:
  a secret in a log line or a failed assertion, in `porelo`'s kit and legs, which use both. It prints redacted.
  `CredentialTest.neverPrintsTheKeyItHolds`.
- **Fixed without a test, no harm:** the module's `README.md` showed an API that is gone (`rent`, `Rental.close()`)
  and `--older-than <hours>` where the code reads minutes, as `Main`'s Javadoc did; a Javadoc in `MainTest` sat above
  the wrong field.
- **Not fixed, waits on a decision: `Ssh` accepts any host key.** For a rented machine the reason holds: its key is
  new on every run and nothing about it is known before. Somebody on the path between runner and provider could pose
  as it, and could not reach the real machine, since key authentication cannot be relayed, but would receive what is
  sent and could answer a leg as passed. For the local VMs and `porelo`'s `deploy` the reason does not hold: their keys
  do not change. The exposure there needs a place on the host's libvirt bridge. Pinning their keys changes how `Ssh`
  behaves for `porelo`'s legs and kit. Accepting the rented case is a risk to write into `doc/decisions.md`; a key
  made per run and passed in the machine's user data would change the answer.
- **Not fixed, waits on a decision: `sweep` deletes by age** any `porelo=ci` server older than its window, whoever
  made it, against the shared rule that a run never sweeps by age; `--older-than 0` with `--now` deletes every server
  in use. Both are `porelo-machines` options workflows call.
- **Declined:**
  - `Ssh.reconnect` and a failed authentication leave a client open; `Lease.ssh` is set without a lock and may be read
    stale by the stop hook: their threads are daemons and the connections die with the machine.
  - `Values.created` reads a missing date as the epoch, and `acquire` takes a server's id before the step that
    deletes it on failure: only an API answer of another shape reaches either.
  - `Ssh.run` waits on a command without a limit: a leg runs for long on purpose, and the job's own limit ends it.
  - `AgentLeg.install` and `Snapshots` put the caller's Artifactory address and the pom's pins unquoted into a root
    script: they come from the caller's own command line and the pom, the address and digests checked.
  - `install-musl.sh` does not check a toolchain that is already installed again: by design, its digests are fixed
    in the script and checked before anything is unpacked.
  - `StubApi.asked` is a list written by the server's thread and read by the test's: no test reads it while one runs.
  - The module is not built as a native image, so `graal` has nothing to judge in it.

### `json`

- **Fixed without a test, no harm:** the Javadoc of `write(Object, StringBuilder)` named a consumer that no longer
  uses it; a second duplicate-key check could never fire.
- **Declined:**
  - An integer above 2^53 is rounded to a double: every id it reads today, a server's, a snapshot's or a run's, is far
    below it, and refusing one changes what a published reader accepts.
  - It reads more than strict JSON - leading zeros, `+1`, `.5`, raw control characters, whitespace beyond JSON's -
    and writes `NaN` and `Infinity` as bare words: no caller uses it to judge whether a file is valid for a stricter
    reader, and `parse` refuses both words.
- Measured and sound: nesting refused past 256, trailing content, truncation, bad escapes, a trailing comma and a
  duplicate key refused, `1e999` refused.

### `ffm-check` and `cpu-check`

- **Fixed: `ffm-check` read only the agent's record whose path sorts last**, ignoring every other. The agent names a
  session by its process id before its time, so the last by path is not the newest, and a run with several forks
  leaves several. Harm: a downcall that is not registered passes, and fails only in the shipped native binary. Every
  record is read. `ForeignRegistrationsTest.failsOnADowncallInAnyOfSeveralRecordsWhicheverSortsLast`, which replaces
  the test that held the old rule. `porelo`'s `core`, `shield` and `vault` records as they stand still pass.
- `cpu-check`: sound. It fails loud on no list, on two lists, on fewer or more features than x86-64 v1, and on an
  image that is not there. The skill names `-march=compatibility` for the widest reach; this repository's rule is
  `-march=x86-64`, checked as exactly v1, and the rule wins.
- **Declined:** `ffm-check` writes the committed file in place, which git restores; it compares no upcalls, and
  `porelo` makes none.

### Style only, from `clean-code` and `solid-principles`, declined

- Long classes doing several jobs (`Hetzner`, `Main` of both tools, `AgentLeg`, `Snapshots`, `Release`,
  `CheckActions`) and long methods (`Hetzner.acquire`, `AgentLeg.run`, `CheckActions.permissions`).
- Repeated code: the image maps in three places, the user set-up in `AgentLeg` and `Rental`, three private `sleep`s.
- Fully qualified names written inline instead of imports; boolean parameters (`sweep(…, dryRun)`, `Spec.keep`,
  `ForeignRegistrations.check(…, update)`); records of many components (`AgentLeg.Options`); a nullable `String` as
  a check's failure; `Json` reading and writing in one class.

## Acceptance

- Each module above is listed here with the skills it was read against, their versions, and its commit.
- Every finding is fixed with a test seen to fail without the fix, or declined with the reason written here.
- The whole reactor and the shared checks are green on each module's commit.

## Open questions

- `check-citations`: should an index that cannot be read for a failure, rather than one not published, make it exit 2
  instead of passing with a note? Every repository's workflow would then go red while GitHub cannot be reached.
- `Ssh`'s host key: accept it for rented machines as a written risk, and pin the local VMs' keys, which changes
  `porelo`'s legs and kit, or accept both?
- `sweep` by age: keep it as the net it is, or hold it to the shared rule, which changes options workflows call?
