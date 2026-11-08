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
  there as `<sha256>.tar.gz`.

## Tried again by one rule

When a file must be fetched:
- **The pauses** double from 15 s, none longer than 5 min, until 15 min of pauses are spent: 15, 30, 60, 120, 240,
  300, 135 s, so eight attempts.
- **Only an answer that may be better next time** is tried again: a 5xx, or a connection that could not be made,
  timed out or broke off. A 4xx, a refused certificate, a full disk and a wrong digest fail at once.
- **A command whose failure does not say why**, such as the wrapper's Maven, is run again on any failure by the same
  pauses.

The rule is held twice:
- in Java, in porelo-machines' `PinnedRetry`;
- in shell, in `.github/actions/pinned-jdk/pinned.sh`.

A test holds the two to the same numbers. A script that fetches something pinned sources `pinned.sh` (bash) and calls:
- `pinned_fetch <url> <sha256> <file>`: keeps a file already in place that matches, fetches it otherwise, over HTTPS
  only;
- `pinned_retry <what> <command>...`: for a fetch it does not make itself, such as the wrapper's Maven.

What the two say goes to standard error, so it reaches the log when the command's own output is discarded.

**A job that fetches** something pinned needs `timeout-minutes` of at least its usual time plus 15, or the job is
stopped while the rule still waits.

## The action in a repository

The `pinned-jdk` action has no single source. Each repository holds its own copy, beside it `pinned.sh` copied
unchanged from porelo-buildtools.

**When to move:** only once the repository builds against a porelo-buildtools 0.5.2-SNAPSHOT that holds `--cache` and
`--print-sha256`. With porelo-parent 0.2.2-SNAPSHOT, that is after the buildtools snapshot with this change is
published.

The steps that replace the one installing the GraalVM, in a repository that takes the tools from `build/pom.xml`,
are in `.github/actions/pinned-jdk/steps-for-build-pom.yml`. They go into the action's `steps:`, indented as its other
steps.

**Variations:**
- The class path line is porelo-host's. A repository keeps its own class path line exactly as it has it, with its
  `-s settings.xml` where it has one, so its snapshots still resolve, and changes only the rest.
- A copy that also installs shfmt takes porelo-buildtools' own steps for it:
  - `actions/cache/restore` keyed `pinned-shfmt-<sha256>`;
  - `pinned_fetch` into the home's `.cache/porelo-pinned/shfmt`;
  - `actions/cache/save` when there was no hit.
