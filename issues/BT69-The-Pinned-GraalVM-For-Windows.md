# BT69 — The Pinned GraalVM For Windows

```json
{
  "$schema": "issue-1.json",
  "id": "BT69",
  "title": "The Pinned GraalVM For Windows",
  "state": "Review",
  "size": "M",
  "priority": "High",
  "rank": 1,
  "blockedBy": [],
  "covers": "`porelo-machines jdk` installs the pinned GraalVM on a Windows x64 runner too: the Windows zip of the same version, its URL and SHA-256 pinned beside the Linux archive's and moved with them by the pin's update, checked before anything is unpacked."
}
```

**Why this size.** One contract change and two modules: the release tooling's pin learns a second artifact of the same
version (its reading and its update), and porelo-machines reads, fetches and unpacks it; tests in both, the
documentation and the changelog.

**What must be true.** A build on a `windows-2025` runner gets the same GraalVM the Linux builds use, by one command:
`porelo-machines jdk` on Windows fetches the pinned Windows x64 zip, checks it against the pinned SHA-256 before
anything is unpacked, installs it, and with `--github` makes it `JAVA_HOME`, `GRAALVM_HOME` and the first entry of
`PATH` for the job's later steps. `jdk --print-sha256` on Windows prints the zip's digest, for a cache key. The Windows
pin can never name another version than the Linux one: the pin's update writes both, or neither.

**Why.** A Java program built for Windows is compiled on GitHub's Windows runner, and today only the Linux archive is
pinned, so such a job would take whatever JDK the runner has, checked against nothing.

## Plan

- **The pin's second artifact (release).** `Pin` reads, beside `digest`, `sha256` and `url`, an optional
  `porelo.release.pin.<name>.also.<key>.digest`, `.sha256` and `.url` for each `<key>` named in
  `porelo.release.pin.<name>.also`: the same version, another asset. `Update.move` reads every artifact before writing
  anything and refuses the whole move when one has no digest; a further build without a digest source and a `sha256`
  property is refused when the pin is read. A pin without `also` reads and moves as today. (`check-pin` checks an
  agent's filtered definition, not the machines pom, so it does not change.)
- **The pom (machines).** `machines.graalvm.windows.url` and `machines.graalvm.windows.sha256`, today
  `https://github.com/graalvm/graalvm-ce-builds/releases/download/graal-25.4.4.1.1/graalvm-community-jdk-25i4-25.0.4.1.1_windows-x64_bin.zip`
  and `2733ea1331f1a98b05dd55a768b07347051dad48f030dbbfa191b11a2ccf4144` (the release's asset digest, read on
  2026-11-11), declared as the pin `graalvm`'s `also` key `windows` with the asset pattern
  `graalvm-community-jdk-.*_windows-x64_bin\.zip`, and filtered into `machines.properties`.
- **The contents (machines).** `Snapshots.Contents` carries the Windows URL and digest, validated like the Linux ones; a
  small `Platform` (linux-x64, windows-x64) chosen from `os.name` and `os.arch` picks the archive, refusing any other
  system or architecture with words.
- **The install (machines).** `PinnedJdk.install` takes the platform's address and digest; the cache file is
  `<sha256>.zip` on Windows. A zip is unpacked in Java (`java.util.zip`), its first path component stripped, an entry
  that leaves the home directory refused; the tar.gz keeps `tar`. The check after unpacking looks for `bin/java.exe`
  and `bin/native-image.cmd` on Windows, as regular files, since Windows keeps no executable bit (both measured in the
  pinned zip, whose digest was measured equal to the pin). `exportTo` writes the home with the platform's separators.
- **From the plan's reading.** `Pin.of` also refuses a further build whose `github-asset` address has no `url`
  property, and any property written twice by the pin and its builds. The zip's entries are all judged before the
  first is written, and a name with a drive's colon or a NUL is refused on every system. Not taken: a check that both
  addresses name `graal-<version>`, since only the pin's update writes them, from one release, and that is tested.
- **From the code's reading.** Taken: a zip naming one file twice is refused before anything is written, a directory
  entry ending in a backslash is a directory, the image property and the followers count among the properties a pin
  writes, and `Main.jdk` takes the platform, so the Windows digest is tested on Linux. Not taken: an entry whose first
  component is empty, `.` or `..` lands inside the JDK's directory or is refused as leaving it, either way nothing
  outside; a `github-asset` first build without `url`, and a follower spelling out a build's address, are what a pin
  read before this change, and no pin has both a further build and followers.
- **The command.** `Main.jdk` passes the platform; options stay as they are.
- **The test that goes red first.** `PinnedJdkTest`: a zip whose digest matches installs with the Windows platform
  given, a zip entry naming `../` is refused and nothing is installed; `UpdateTest`: a pin with an `also` artifact
  whose upstream lacks the asset writes nothing.
- **Documentation.** The machines module's page on `jdk`, the release tooling's page on pins, a changelog entry under
  `[Unreleased]`.

## Acceptance

- `jdk --into <dir>` with the Windows platform installs from a zip served by a test server and refuses one whose
  digest differs, leaving nothing unpacked; a zip entry escaping the home is refused.
- `update` of the pin `graalvm` writes the Windows URL and digest with the version, and writes nothing when the Windows
  asset is missing upstream; a further build declared without a digest source is refused before anything is read.
- The Linux install behaves as before: its tests pass unchanged.
- Not measured here: a run on a real `windows-2025` runner, which is the consumer's first Build.
