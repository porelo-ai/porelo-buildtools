# BT43 — The JDK Download Tries Again

```json
{
  "$schema": "issue-1.json",
  "id": "BT43",
  "title": "The JDK Download Tries Again",
  "state": "Review",
  "size": "XS",
  "priority": "Medium",
  "rank": 16,
  "blockedBy": [],
  "covers": "`porelo-machines jdk` tries the download of the pinned GraalVM again on a 5xx or a failed connection, five attempts with a growing wait; a 4xx or a wrong digest fails at once."
}
```

**Why this size.** the download of `PinnedJdk`, its tests and its documentation.

**What must be true.** One 5xx from the server of a release download does not turn a Build red. `porelo-machines jdk`
tries again when the server answers a 5xx or the connection fails or breaks off, a bounded number of times with a wait;
a 4xx and a wrong digest still fail at once.

## Plan

- **Where it is tried again:** in `PinnedJdk.install`, around the file and the digest, so each attempt starts with an
  empty file and a new digest and a body that broke off leaves nothing behind.
- **What is tried again:** `PinnedJdk.Unavailable`, which the download throws for a 5xx, also behind a redirect, for a
  connection that failed, and for a body that broke off. A write that fails is no such thing: a full disk stays full.
- **How often:** five attempts, waiting 15, 30, 45 and 60 seconds, each said on standard error with the answer.
- **Test first** in `PinnedJdkTest`, against a local HTTP server for what the download makes of an answer.

## Acceptance

- The tests above, seen red first, and the module's build green.
