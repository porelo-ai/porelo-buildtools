# BT67 — The Notices Read The Bouncy Castle Licence As MIT

```json
{
  "$schema": "issue-1.json",
  "id": "BT67",
  "title": "The Notices Read The Bouncy Castle Licence As MIT",
  "state": "Review",
  "size": "XS",
  "priority": "High",
  "rank": 2,
  "blockedBy": [],
  "covers": "`third-party-notices` takes a component whose bill names the license `Bouncy Castle Licence` with Bouncy Castle's own licence URL as MIT, which that licence is, so porelo's notices take the Bouncy Castle jars."
}
```

**Why this size.** one exact name-and-URL pair read as an SPDX id, a test and a changelog entry.

**What must be true.** A bill entry whose license is named exactly `Bouncy Castle Licence`, with the URL
`https://www.bouncycastle.org/licence.html`, is read as `MIT` and gets the MIT text. Any other name, or that name with
another URL or none, is refused as today. Bouncy Castle's licence is the MIT license with its own copyright line. Asked
for by Agent Core on 2026-11-10 for porelo's B606, whose notices refuse the Bouncy Castle jars; agreed with Agent
Coordinator the same day.

## Plan

- **The reading:** where the bill names a license by name and URL, the one pair above maps to `MIT`; nothing else
  changes.
- **Red first:** a bill with a Bouncy Castle jar under that name and URL passes and gets the MIT text; the same name with
  another URL is refused.
- **Docs:** a changelog entry.

## Acceptance

- The test, seen failing before and passing after.
- `./mvnw verify` green.
