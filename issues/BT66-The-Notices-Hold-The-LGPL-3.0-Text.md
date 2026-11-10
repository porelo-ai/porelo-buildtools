# BT66 — The Notices Hold The LGPL-3.0 Text

```json
{
  "$schema": "issue-1.json",
  "id": "BT66",
  "title": "The Notices Hold The LGPL-3.0 Text",
  "state": "Implementing",
  "size": "XS",
  "priority": "High",
  "rank": 1,
  "blockedBy": [],
  "covers": "`third-party-notices` takes a component under LGPL-3.0-or-later, `porelo-stories` among them, and writes the LGPL-3.0 text with the GPL-3.0 text it builds on, so a program that links the stories library builds its notices."
}
```

**Why this size.** one pinned standard text, one entry in the map of standard texts, a test and a changelog entry.

**What must be true.** A build whose bill names a component under `LGPL-3.0-or-later` passes `third-party-notices`, and
its notices hold the LGPL-3.0 text and the GPL-3.0 text, whose additional permissions the LGPL-3.0 is. Today the
command refuses it: "the bill's LGPL-3.0-or-later is none of the standard texts pinned". `porelo-stories` is under
LGPL-3.0-or-later since [BT61](README.md#bt61), so porelo-planner, which links it, cannot build its notices; reported by
Agent Planner on 2026-11-10 for PL03's hand-over.

## Plan

- **The text:** `licenses/LGPL-3.0.txt` beside `GPL-3.0.txt`, the SPDX list's text of the same release as the others,
  its digest checked as theirs are.
- **The map:** `STANDARD` gains `"LGPL-3.0-or-later" -> ["LGPL-3.0", "GPL-3.0"]`.
- **Red first:** a bill with `porelo-stories` under LGPL-3.0-or-later passes, and the notices hold both texts once each.
- **Docs:** a changelog entry.

## Acceptance

- The test, seen failing before and passing after.
- `./mvnw verify` green.
