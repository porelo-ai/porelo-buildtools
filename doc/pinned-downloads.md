# Pinned downloads

Every file a Build fetches by a pinned digest — the GraalVM, shfmt, the wrapper's Maven, and fd, ripgrep or Node in
a script — follows the same two rules.

## Kept by its digest

A pinned download is cached with `actions/cache`, keyed by its digest:
- The cache is restored before the fetch and saved right after it with `actions/cache/save`, so a job that fails later
  still keeps it.
- A cached file is checked against the digest again before it is used. One that does not match is deleted and fetched
  anew, never unpacked or run.
- `porelo-machines jdk --print-sha256` prints the GraalVM's digest for the key. `jdk --cache <dir>` keeps the archive
  there as `<sha256>.tar.gz`, or `<sha256>.zip` on Windows.

## The GraalVM on Linux and Windows

`porelo-machines jdk` installs the archive pinned for the system it runs on: the tar.gz on Linux x86-64, the zip of the
same release on Windows x86-64, each checked against its own digest before anything is unpacked. Any other system or
architecture is refused. On Windows the zip is unpacked by the command itself, and an entry that would land outside the
JDK's directory is refused. With `--github` it sets `JAVA_HOME` and `GRAALVM_HOME` and puts its `bin` first on the
`PATH` for the job's later steps, on either system; it prints one line,
`GraalVM <version> in <directory>, checked against <sha256>`, and `--print-sha256` prints that system's digest alone.

Both builds are one pin: `porelo.release.pin.graalvm.also` names `windows`, and its `also.windows.digest`,
`.sha256` and `.url` say where its digest is read and which properties it is written to. The pin's update reads every
build of the release before it writes anything, so a release without the Windows zip moves neither.

## Tried again by one rule

When a file must be fetched:
- **The pauses** double from 15 s, none longer than 5 min, until 15 min of pauses are spent: 15, 30, 60, 120, 240,
  300, 135 s, so eight attempts at most.
- **The deadline:** the same 15 min, counted from the start of the first attempt with the attempts' own time in it. No
  attempt starts later, and a pause is cut so the next one starts by then. A fetch so takes at most 15 min plus the
  one attempt then running, however long a failing attempt hangs. The time is a clock that only counts up, in whole
  seconds.
- **Only an answer that may be better next time** is tried again: a 5xx, or a connection that could not be made,
  timed out or broke off. A 4xx, a refused certificate, a full disk and a wrong digest fail at once.
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
- **A 401 or 403 fails at once,** as any 4xx does.

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
