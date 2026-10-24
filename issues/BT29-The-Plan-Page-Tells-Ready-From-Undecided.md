# BT29 — The Plan Page Tells Ready From Undecided

**Status:** Implementing.
**Size:** S - the legend's groups and colours in `PlanTemplate` and `plan.jte`, and the progress image's reference in
`PlanRender`; in the `release` module alone.

**What must be true.** On the release plan's page, an issue in Backlog, ready to build, reads differently from an Idea
nobody has accepted, and an issue the operator estimated reads differently from one only accepted or analyzed. A
preview that showed the page once shows the new progress image after the next `plan render`, without being reopened.

## Plan

- **The groups, in `PlanTemplate.STATES` and `chip`**, follow the flow of the states, `AGENTS.md`'s Idea → Accepted
  → Analyzed → Estimated → Backlog → Implementing (Rework) → Review → Finished. Each bar fills from the left with the
  furthest state, and the legend reads in the same order, so a bar reads as progress from left to right: Finished /
  Rejected (green), Review (blue), Implementing / Rework (yellow), **Backlog** (light blue, ready to build),
  **Estimated** (its own shade, decided by the operator), **Analyzed**, **Accepted** (two grays, Analyzed the darker,
  as the further one), **Idea** (orange), Not filed yet / Missing (outlined). Today Backlog shares Idea's orange, and
  Estimated, Analyzed and Accepted share one gray, in an order that is not the flow's.
- **The colours** go into `plan.jte`'s palette, its dark variant, the chip classes, `progress.jte`'s own attributes and
  `Chip.emoji()`'s Markdown squares, one per group: 🟩 🟦 🟨 🔲 🟪 ⬛ ⬜ 🟧 ◻️ in flow order (Unicode has no light blue
  square, so Backlog's is the outlined one in Markdown and light blue in the HTML page and the image).
- **The image is named by its content:** `<release>/progress-<first 8 hex of its SHA-256>.svg`, which the Markdown
  links, and `plan render` removes the release's older `progress-*.svg` and `progress.svg`, only those, never outside
  the plan's directory nor an input. A new name is fetched afresh by GitHub and by IntelliJ's preview alike, where a
  query (`?v=`) could not be measured on GitHub without pushing a page. `plan check` compares the name and the image
  with a fresh render, as it compares the page.
- **Red first:** the Markdown's image reference carries the hash of the image written beside it, and a second render
  of a changed plan changes the reference. Colours and groups only read wrong, so they get no test of their own; the
  existing escaping and `plan check` tests stay green.
- **`doc/index.md`** names the groups and the image reference.

## Acceptance

- On porelo-project's `0.6` plan, a Backlog issue's chip and bar segment are light blue, an Idea's orange, an
  Estimated issue's apart from an Accepted one's, in the HTML page, the Markdown page and the image.
- After a `plan render` that changes the image, IntelliJ's open Markdown preview of the page shows the new image, and
  so does the page on GitHub.
