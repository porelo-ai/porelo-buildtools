# BT49 — Pinned Downloads Cached And Tried Again By One Rule

**Status:** Review.
**Size:** S - the cache and the rule in `porelo-machines jdk`, a shell twin for the action, buildtools' own copy of the
action, and the text the other copies take.

**What must be true.** Every pinned download a Build makes is cached with `actions/cache`, keyed by its digest, and
checked against that digest again after a cache hit:
- the GraalVM archive;
- shfmt;
- and the like.

When one must be fetched, it is tried again by one rule: pauses doubling from 15 s, none longer than 5 min, until 15
min of pauses are spent (15, 30, 60, 120, 240, 300, 135 s; eight attempts). Only a 5xx or no answer is tried again. A
4xx and a wrong digest fail at once.

## Why

On 2026-11-08, GitHub's release download of the pinned GraalVM answered 504 from at least 10:12 to 10:31Z. The five
attempts of [BT43](README.md#bt43) wait two and a half minutes in all, so Builds in porelo-host, porelo-pi, porelo-omp,
porelo-message-matrix and others went red twice, and re-runs could not help until the asset answered again. Every job
fetched the same archive anew, though its digest never changed.

## Done

- `PinnedRetry` holds the rule. `PinnedJdk` takes its pauses in place of the 15/30/45/60 s of [BT43](README.md#bt43).
- `porelo-machines jdk --cache <dir>`:
  - keeps the archive that matched as `<dir>/<sha256>.tar.gz`;
  - hashes a cached one again before use;
  - deletes and fetches anew one that does not match, never unpacking it;
  - puts nothing in the cache that did not match.
- `jdk --print-sha256` prints the pinned digest alone and fetches nothing, as the key of the CI cache.
- `.github/actions/pinned-jdk/pinned.sh` is the shell twin, for shfmt, the wrapper's Maven and any pinned fetch of a
  script:
  - `pinned_pause`, `pinned_fetch <url> <sha256> <file>` and `pinned_retry <what> <command>`;
  - a file already in place that matches is kept, a link never;
  - only a 5xx or a failed, timed out or broken connection is tried again, by curl's exit code. A 4xx, a refused
    certificate and a full disk fail at once;
  - HTTPS only, redirects included. Each attempt writes a temporary file of its own beside the target, so none is
    left;
  - what it says goes to standard error.
  - `PinnedRetryTest` runs it in bash with stand-ins for curl and sleep, and holds its pauses to `PinnedRetry`'s.
- buildtools' own `pinned-jdk` action:
  - restores shfmt and the GraalVM archive from `actions/cache` by their digests;
  - fetches either with the rule when missing;
  - saves it right after the fetch with `actions/cache/save`, so a job that fails later still keeps it.
- Tests, seen red first: `PinnedJdkTest` (cache kept, taken, a mismatch fetched anew, nothing kept that does not
  match, the digest printed, `--cache` consulted and refused without a directory, eight attempts with the rule's
  pauses) and `PinnedRetryTest` (the rule in Java and in the shell, a kept file, a 5xx tried again, a 404 and a wrong
  digest failing at once, a command run again).

- Every job that uses the action has its `timeout-minutes` raised by 15 (10 to 25, 20 to 35, 30 to 45, 75 to 90), so a
  job is not stopped while the rule still waits.

## Reading

Read once by agy `gemini-4-argon-high` (792 s) with eleven findings:

1. The copied class path line: it is porelo-host's own, which resolves without `-s`. The text now says to keep each
   repository's line as it is.
2. `BT43's` unlinked. It was not refused, but it is linked now.
3. `pinned_retry ... >/dev/null` swallowed its own warnings. They go to standard error now, with a test.
4. curl's error swallowed, and everything retried on any exit. Now curl's own message shows, and the exit code
   decides, with tests for exit 7, 60 and 23.
5. `pinned_retry` tries every failure again. This is meant, for a command that does not say why, and now said so.
6. A fixed `.part` file and a followed link. Now `mktemp` beside the target, a link is never taken, a failed `mv`
   fails, and `--proto =https`.
7. Variables shared between the functions. They are `local` now, with a test of a nested call.
8. Tests that pass on broken code. Added:
   - a cached file that does not match;
   - a link;
   - eight attempts spent;
   - `pinned_attempts` held to Java's;
   - each run's exit code checked.

   The digest check on a hit, taken out on purpose, turns two of them red.
9. A stale cached archive that would not show if unpacked. It now carries a file of its own, which the JDK must not
   hold.
10. Job timeouts shorter than the rule. They are raised by 15 minutes.
11. `--cache ""`. Refused now, with a test, and `jdk`'s usage names both new options.

## The other copies

The action has no single source: each repository holds its own copy in about nine variants. Each repository's agent
moves the new shape into its copy, once that repository builds against a porelo-buildtools 0.5.2-SNAPSHOT that holds
this change. An older one does not know `--cache` or `--print-sha256`. The text to take is in
`doc/pinned-downloads.md`, the steps in `.github/actions/pinned-jdk/steps-for-build-pom.yml`.

## Acceptance

- A Build whose cache holds the archive installs the GraalVM without a download, checked against the digest.
- A Build that meets a 504 tries again by the rule and goes green when the asset answers within 15 min.
