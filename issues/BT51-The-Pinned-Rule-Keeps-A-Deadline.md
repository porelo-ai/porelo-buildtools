# BT51 — The Pinned Rule Keeps A Deadline

**Status:** Review.
**Size:** XS - the deadline in `PinnedRetry`, `PinnedJdk` and `pinned.sh`, their tests, and the text the copies take.

**What must be true.** A pinned download starts no attempt later than 15 minutes after its first attempt started, the
attempts' own time counted with the pauses, in Java and in `pinned.sh` alike, and a pause is cut so the next attempt
starts by then.

## Why

The rule of [BT49](README.md#bt49) counts 15 minutes of pauses, not the time the attempts take. In its first real run,
Build 42474538794 of `ae30d0d`, each failing attempt of the GraalVM download hung about 2 minutes before it answered 504
or timed out. Eight such attempts and their pauses take about 31 minutes, while the jobs that fetch have 15 minutes
more than they need, and a job stopped while it waits says nothing of why.

## The shape

- `PinnedRetry.pauseAfter(attempt, elapsed)`: the rule's pause after a failed attempt, cut to what is left of the 15
  minutes since the first attempt started, or empty when the attempts are spent or no time is left.
- Both count whole seconds on a clock that only counts up, and take a time below none as none.
- `PinnedJdk` reads the time from its `Pause`, which gains `now()`: `System.nanoTime()`, which no step of the system
  clock moves, unless a test keeps its own. When it gives up, it says after how many of at most 8 attempts and in how
  many seconds.
- `pinned.sh`: `pinned_pause <attempt> [<seconds since the first attempt>]` does the same, by `pinned_now`. That is
  the system's uptime from `/proc/uptime`, which no caller resets as one can `SECONDS`, and which a test replaces.
  `pinned_fetch` and `pinned_retry` read the time once per failed attempt and pass it on.
- `pinned_retry` runs its command in a subshell under `set -e`, outside any condition, so a function given stops at
  its first failing command. The caller's errexit stays as it was. Called as a condition itself, bash ignores `set -e`
  all the way down; the comment says so.
- The pauses stay 15, 30, 60, 120, 240, 300, 135 s while the attempts are quick. A fetch takes at most 15 minutes plus
  the one attempt then running. The jobs' `timeout-minutes`, raised by 15 in [BT49](README.md#bt49), stay.
- `doc/pinned-downloads.md` names the deadline. The copies in the other repositories take the new `pinned.sh`
  unchanged.

## Reading

Read once by agy `gemini-4-argon-high` (456 s), with eight findings, all taken:
1. `pinned_pause 0` paused where Java does not. Attempt 0 now fails in both, and the shell test runs from attempt 0.
2. (grave) Java's wall clock: a step of the system clock could end the deadline at once or never. It is now
   `System.nanoTime()`, and a time below none counts as none.
3. (grave) `SECONDS`, which a caller can reset or unset. It is now `/proc/uptime`, with a test that resets `SECONDS`.
4. (grave) `until "$@"` switched off `set -e` in the command. It now runs in a subshell under `set -e`, with a test.
5. A pause under a second was told as "0 s". Both now count whole seconds.
6. The time was read twice when giving up. It is read once.
7. (grave) Tests that a dead clock or a `pinned_retry` without the deadline would pass. Added:
   - reference values for `pauseAfter`;
   - the real clocks of both;
   - `pinned_retry`'s deadline and its `set -e`.

   Four mutations turn one test each red: `pinned_retry` without the time, `pinned_now` as `echo 0`, `Pause.now()` as
   `Instant.EPOCH`, and the command run as a condition.
8. A dated story in the doc, no Acceptance section here, assertions without `.as(...)`. All fixed.

## Tests

Seen red first: the Java tests do not compile against the code before, and three shell tests fail against the
`pinned.sh` before.
- `PinnedJdkTest`:
  - attempts that each hang 2 minutes: six start, the last at the deadline, with pauses of 15, 30, 60, 120 and 75 s;
  - a first attempt of 14 min 50 s: one pause of 10 s, then the last attempt.
- `PinnedRetryTest`:
  - `pinned_pause` answers as `pauseAfter` does for attempts 0 to 8 at -5 to 1000 s;
  - `pinned_fetch` on a clock its stand-ins move starts attempts at 0, 135, 285, 465, 705 and 900 s and gives up after
    6 of at most 8;
  - `pinned_retry` keeps the same deadline, stops a given function at its first failing command, and leaves the
    caller's errexit as it was;
  - `pinned_now` counts up across a reset of `SECONDS`.

## Acceptance

- A pinned download whose attempts each hang gives up within 15 minutes plus the one attempt then running, in a Build
  and in a script that sources `pinned.sh`.
- `check-actions`, verify and the documents tests pass.
