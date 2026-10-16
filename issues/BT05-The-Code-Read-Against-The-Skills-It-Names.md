# BT05 — The Code Read Against The Skills It Names

**Status:** Analyzed.
**Size:** L - every module, about 11,000 lines of Java and 7,100 of tests in five modules, read against seven skills,
and each finding a fix with a test seen to fail first; one commit per module.

**What must be true.** Every module of this repository has been read against the skills `AGENTS.md` names, and each
finding is either fixed with a test seen to fail first, or declined with the reason written here.

## Why

`AGENTS.md` has named seven skills since the repository was made public, and none of its code was read against
them here. Only `sokar-release` was read, while it still lived in `sokar`, against three of them (`java-code-review`,
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

### Found before the reading started

- **Fixed: `Ssh.run` read a command's output to its end before its errors**, so a command blocked writing more errors
  than the channel holds never closed its output, and the call waited for ever (found from `sokar`'s kit, which runs
  every scenario's commands through it). Both are read side by side; `SshOutputTest` holds it with pipes, red with
  the old order.
- **Open: `Ssh` accepts any host key (`PromiscuousVerifier`).** It is how a server rented a moment ago is reached
  before anything about it is known; whether that holds for every caller - the local VMs too - is read against
  `security-audit` here.

## Acceptance

- Each module above is listed here with the skills it was read against, their versions, and its commit.
- Every finding is fixed with a test seen to fail without the fix, or declined with the reason written here.
- The whole reactor and the shared checks are green on each module's commit.

## To be checked

- Whether `release`'s reading in `sokar` (`4a0e9332`, before the tooling moved) still matches the code here, so its two
  fixes are not read as new findings.
