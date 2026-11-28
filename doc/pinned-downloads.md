# Pinned downloads

Every file a Build fetches by a pinned digest — the GraalVM, shfmt, the wrapper's Maven, the musl toolchain and its
zlib, and fd, ripgrep or Node in a script — follows the same two rules.

## Kept by its digest

A pinned download is cached with `actions/cache`, keyed by its digest:
- The cache is restored before the fetch and saved right after it with `actions/cache/save`, so a job that fails later
  still keeps it.
- A cached file is checked against the digest again before it is used. One that does not match is deleted and fetched
  anew, never unpacked or run.
- `porelo-machines jdk --print-sha256` prints the GraalVM's digest for the key. `jdk --cache <dir>` keeps the archive
  there as `<sha256>.tar.gz`, or `<sha256>.zip` on Windows.

## One entry of each cache

porelo-buildtools' own Build keeps one entry of each cache, the newest under its prefix:
- **The local Maven repository** is restored by its prefix in every job, and saved by the build job alone with
  `maven-cache-keep` as its last step. Before the save, this repository's own modules at its version go, and so does
  every version directory in which the job read no file. `pinned-jdk` marks each restored file unread, and the runner
  records the first read. A directory is kept whole, since Maven only asks whether a jar exists. A runner that records
  no reads, as a probe file shows, keeps the repository whole and says so.
- **The wrapper's Maven**, shfmt and the GraalVM keep their keys; the wrapper's is saved once the wrapper ran.
- **build.yml's `prune` job** deletes every older entry under each prefix, `maven-Linux-`, `maven-wrapper-Linux-`,
  `pinned-shfmt-` and `pinned-graalvm-`, of the Build's own ref only. It keeps the key the build restored or saved, and deletes
  nothing of a prefix whose key is not stored for that ref. It takes only those four prefixes, each with a key of its
  own. It is a job of its own, since no code it fetches runs beside a token that deletes. On another forge it does not
  run; that forge ages its caches out by its own rule. A deletion that fails is a warning and the Build stays green.

## The GraalVM on Linux and Windows

`porelo-machines jdk` installs the archive pinned for the system it runs on: the tar.gz on Linux x86-64, the zip of the
same release on Windows x86-64, each checked against its own digest before anything is unpacked. Any other system or
architecture is refused. On Windows the zip is unpacked by the command itself, and an entry that would land outside the
JDK's directory is refused. With `--github` it sets `JAVA_HOME` and `GRAALVM_HOME` and puts its `bin` first on the
`PATH` for the job's later steps, on either system; it prints one line,
`GraalVM <version> in <directory>, from <source>, checked against <sha256>`, where the source is the pinned address,
the mirror's or the cached archive, with `; JAVA_HOME and PATH set for the rest of the job`
after it under `--github`, and `--print-sha256` prints that system's digest alone.

Both builds are one pin: `porelo.release.pin.graalvm.also` names `windows`, and its `also.windows.digest`,
`.sha256` and `.url` say where its digest is read and which properties it is written to. The pin's update reads every
build of the release before it writes anything, so a release without the Windows zip moves neither.

The pin holds a floor, `porelo.release.pin.graalvm.floor`, at 25.6: GraalVM 25.1 to 25.5, built on a JDK below 25.0.5.1,
carry CVE-2026-114938, a Base64 decoder intrinsic that writes past its buffer. `update --pin graalvm` refuses a version
below the floor before it reads upstream, `upstream-version --pin graalvm` answers `update=no` with `floor=` for a
release below it and refuses one named by hand, and the Build's tests fail when `machines/pom.xml` names a version below
it or `porelo.yml` installs another archive or digest. Any pin followed by its version may hold a floor of its own; a pin
followed by its digest takes none, and a floor declared blank is refused. For an agent's pin, `check-pin --pin <name>`
fails a pom whose pinned version is below the pin's floor.

## Tried again by one rule

When a file must be fetched:
- **The pauses** double from 15 s, none longer than 5 min, until 15 min of pauses are spent: 15, 30, 60, 120, 240,
  300, 135 s, so eight attempts at most.
- **The deadline:** the same 15 min, counted from the start of the first attempt with the attempts' own time in it. No
  attempt starts later, and a pause is cut so the next one starts by then. A fetch so takes at most 15 min plus the
  one attempt then running, however long a failing attempt hangs. The time is a clock that only counts up, in whole
  seconds.
- **Only an answer that may be better next time** is tried again: a 5xx, or a connection that could not be made,
  timed out or broke off. A 4xx, a refused certificate, a full disk and a wrong digest are not asked again at the same
  source.
- **A command whose failure does not say why**, such as the wrapper's Maven, is run again on any failure by the same
  pauses and deadline. It runs in a subshell under `set -e`, so a function given stops at its first failing command,
  as long as `pinned_retry` is a statement of its own and not a condition.

The rule is held twice:
- in Java, in `PinnedRetry` of the module `porelo-pinned-rule`, which porelo-machines and porelo-release use;
- in shell, in `.github/actions/pinned-jdk/pinned.sh`.

A test holds the two to the same numbers. A script that fetches something pinned sources `pinned.sh` (bash) and calls:
- `pinned_fetch <url> <sha256> <file>`: keeps a file already in place that matches, fetches it otherwise, over HTTPS
  only;
- `pinned_retry <what> <command>...`: for a fetch it does not make itself, such as the wrapper's Maven.

What the two say goes to standard error, so it reaches the log when the command's own output is discarded.

## From the mirror when the source fails

A pinned file has a second source, the organization's Artifactory, which keeps it by its digest:

```
https://fuinorg.jfrog.io/artifactory/porelo-files/pinned/<sha256>/<file name>
```

The file name is the last path segment of the pinned address. The repository `porelo-files` is read without a token,
so no pin names the mirror and nothing is configured to use it:
- **The budget split:** the pinned address is tried by the rule three times at most, and no attempt there starts later
  than 10 min after the first started. The mirror is then asked at once and tried by the same pauses until the 15 min
  are spent, counted from the same start. A Build once waited 630 s on a source's connect timeouts while the mirror held
  the file. Without a mirror, the pinned address has all eight attempts and the whole 15 min.
- **What moves on to the mirror:** anything the pinned address did not serve, such as a 5xx or no answer that lasts, a
  4xx from a release asset that moved, a refused certificate, or a file with a wrong digest, as zlib.net once served
  to GitHub's runner.
- **Checked the same:** the mirror's file is taken only when its digest matches the pin, and is moved into place only
  then. What the two say names each address, and the line of a file taken names the source that served it.
- **No token:** the job's GitHub token never goes to the mirror, whatever address it has.
- **Another mirror or none:** `PORELO_PINNED_MIRROR` names another mirror's base, an `https://` address without
  whitespace, a user, a query or a fragment; set empty, it turns the mirror off. Any other value fails before anything is
  fetched.

`pinned.sh` keeps the base in `pinned_mirror`, porelo-machines in `PinnedJdk.MIRROR`, and the test that holds the two
to one rule holds both to the same base, the same 10 min, `PinnedRetry.FIRST_SOURCE`, and the same three attempts,
`PinnedRetry.FIRST_SOURCE_ATTEMPTS`.

### How the mirror is filled

`porelo-release pinned-mirror [<root>]`, run in a porelo-buildtools checkout, reads every file the checkout pins: the
GraalVM for Linux and Windows from `machines/pom.xml`, shfmt from `.github/actions/pinned-jdk/action.yml`, and the
musl toolchain and its zlib from the musl installer. For each:
- a file already under `pinned/<sha256>/<name>` is left alone;
- any other is fetched from its pinned address by the rule, checked against its digest, and uploaded by the code of
  `artifactory put`, which sends its SHA-1 and SHA-256, so Artifactory refuses bytes that do not match them.

A pin that fails does not keep the others out. The command ends with 1 when a file did not match or a source answered
404, 2 when a source or Artifactory did not answer. The Build's Publish job runs it on `main` on github.com, with
`JF_URL` and `JF_ACCESS_TOKEN`, after the jars are deployed. musl.cc does not answer GitHub's runners, so the musl
toolchain reaches the mirror only by a run of the command from a machine musl.cc answers; until then the Publish job
ends with 2 on it, and the other files still go up.

## With the job's token

On GitHub, a download from `https://github.com` goes with the job's token, so GitHub counts it against the job's own
limit and not against the shared runner's address:
- **Only to `https://github.com`:** only when `GITHUB_SERVER_URL` is exactly `https://github.com`, the address asked
  for is on it, and the token has a token's form. On a host's forge the job's token is the forge's own, and goes
  nowhere.
- **Never along a redirect:** a release download is sent on to a signed address, which is fetched without it, as is
  any address a redirect leads to.
- **Never told:** `pinned.sh` hands it to curl on its standard input, never as an argument; Java sends it as a header.
  Neither puts it in a message.
- **A 401 or 403 is not asked again,** as any 4xx is not; the mirror is asked next.

The token comes from the step's environment, `PORELO_PINNED_GITHUB_TOKEN`, which each step that downloads sets:

```yaml
env:
    PORELO_PINNED_GITHUB_TOKEN: ${{ github.server_url == 'https://github.com' && github.token || '' }}
```

Without it, a download goes without the token, as before.

`check-pin` reads GitHub's API by the same rule when an address does not answer or answers a 5xx, each address
read up to 15 minutes. The other release commands read once. They all send `GITHUB_TOKEN`, when the step sets it, to
`https://api.github.com` alone, and not on a host's forge.

**A job that fetches** something pinned needs `timeout-minutes` of at least its usual time plus 15, or the job is
stopped while the rule still waits.

## The action in a repository

The `pinned-jdk` action has no single source. Each repository holds its own copy, beside it `pinned.sh` copied
unchanged from porelo-buildtools. `check-shared` refuses a `pinned.sh` that differs from the one the porelo-release it
runs carries, whether the call names it or not.

**When to move:** only once the repository builds against a porelo-buildtools 0.5.2-SNAPSHOT that holds `--cache` and
`--print-sha256`. With porelo-parent 0.2.2-SNAPSHOT, that is after the buildtools snapshot with this change is
published.

The steps that replace the one installing the GraalVM, in a repository that takes the tools from `build/pom.xml`,
are in `.github/actions/pinned-jdk/steps-for-build-pom.yml`. They go into the action's `steps:`, indented as its other
steps.

**Variations:**
- A line that captures a command's output, such as the digest, says `::error::` when the command fails: `set -e`
  alone ends the step at the assignment without a word. A command run through Maven with `-q` writes even its
  `[ERROR]` lines to standard output, so the captured text is printed too.
- The class path line is porelo-host's. A repository keeps its own class path line exactly as it has it, with its
  `-s settings.xml` where it has one, so its snapshots still resolve, and changes only the rest.
- Each step that downloads sets `PORELO_PINNED_GITHUB_TOKEN` as above.
- A copy that also installs shfmt takes porelo-buildtools' own steps for it:
  - `actions/cache/restore` keyed `pinned-shfmt-<sha256>`;
  - `pinned_fetch` into the home's `.cache/porelo-pinned/shfmt`;
  - `actions/cache/save` when there was no hit.
