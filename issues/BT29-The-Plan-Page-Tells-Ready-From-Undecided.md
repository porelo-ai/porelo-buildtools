# BT29 — The Plan Page Tells Ready From Undecided

**Status:** Backlog.
**Size:** S - the legend's groups and colours in `PlanTemplate` and `plan.jte`, and the progress image's reference in
`PlanRender`; in the `release` module alone.

**What must be true.** On the release plan's page, an issue in Backlog, ready to build, reads differently from an Idea
nobody has accepted, and an issue the operator estimated reads differently from one only accepted or analyzed. A
preview that showed the page once shows the new progress image after the next `plan render`, without being reopened.

## Plan

- **The groups, in `PlanTemplate.STATES` and `chip`**, each with its own colour, in the legend's and the bars' order:
  Finished / Rejected (green), Review (blue), Implementing / Rework (yellow), **Backlog** (light blue, ready to build),
  **Estimated** (its own shade, decided by the operator), Accepted / Analyzed (gray), **Idea** (orange), Not filed yet
  / Missing (outlined). Today Backlog shares Idea's orange and Estimated shares Accepted's gray.
- **The colours** go into `plan.jte`'s palette, its dark variant, the chip classes, `progress.jte`'s own attributes and
  `Chip.emoji()`'s Markdown squares, one square per group.
- **The image reference** in the Markdown changes with the image's content, so a preview cannot keep an older one:
  `<release>/progress.svg?v=<first 8 hex of its SHA-256>` where both GitHub and IntelliJ's Markdown preview show it;
  otherwise the file itself is named by that hash and `plan render` removes the release's older progress images, only
  those it wrote. `plan check` compares the reference with a fresh render, as it compares the page today.
- **Red first:** the Markdown's image reference carries the hash of the image written beside it, and a second render
  of a changed plan changes the reference. Colours and groups only read wrong, so they get no test of their own; the
  existing escaping and `plan check` tests stay green.
- **`doc/index.md`** names the groups and the image reference.

## Acceptance

- On porelo-project's `0.6` plan, a Backlog issue's chip and bar segment are light blue, an Idea's orange, an
  Estimated issue's apart from an Accepted one's, in the HTML page, the Markdown page and the image.
- After a `plan render` that changes the image, IntelliJ's open Markdown preview of the page shows the new image, and
  so does the page on GitHub.

## To be checked

- Whether GitHub's rendering of a repository's Markdown keeps a query on a relative image link, and whether
  IntelliJ's Markdown preview fetches the image again when only the query changes; measured on each before the
  reference's form is chosen.
