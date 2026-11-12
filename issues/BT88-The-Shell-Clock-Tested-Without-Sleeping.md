# BT88 — The Shell Clock Tested Without Sleeping

```json
{
  "$schema": "issue-1.json",
  "id": "BT88",
  "title": "The Shell Clock Tested Without Sleeping",
  "state": "Backlog",
  "size": "S",
  "priority": "Medium",
  "rank": 6,
  "blockedBy": [],
  "covers": "`PinnedRetryTest.theShellsClockCountsUpWhateverTheCallerDoesWithSeconds` proves `pinned_now` without waiting 1.3 s of real time, so no test of the buildtools sleeps longer than 50 ms."
}
```

**Why this size.** `pinned.sh` is the shared pinned rule, held by `check-shared` in every repository: a clock it can be
given for a test has to keep the rule's text the same everywhere, or move with it.

**What must be true.** The test proves that `pinned_now` counts up whatever the caller does with `SECONDS`, and no step
of it sleeps longer than 50 ms.

**Why.** [BT57](README.md#bt57)'s acceptance holds every test to sleeps of 50 ms at most. Its fixes gave Java's waits a
clock the tests move; this test sleeps 1.3 s in `python3` because the shell's clock is the real one.
