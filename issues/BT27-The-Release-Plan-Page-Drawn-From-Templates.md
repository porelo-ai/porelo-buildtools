# BT27 — The Release Plan Page Drawn From Templates

**Status:** Backlog.
**Size:** S - one template engine and the HTML page of `plan render`; the Markdown page and the checks stay as they are.

**What must be true.** `porelo-release plan render --html` draws the page from jte templates (https://jte.gg), which
escape by default, so it can show what the person's local page shows - a bar per sprint by its issues' states, chips
per issue with their state, a mark per function tested or not - with every text escaped by the engine rather than by
hand.

## Plan

- Comes after [BT26](README.md#bt26), whose escaping test of every text taken from the plan file and the indexes
  stays the guard: it must pass unchanged against the templates.
- To be read once [BT26](README.md#bt26) is out: jte's precompiled templates in the `release` module, the dependency
  it adds, and how the native and the FFM checks take it.

## Acceptance

- The HTML page shows the bars, chips and marks above, and [BT26](README.md#bt26)'s escaping test passes unchanged
  against it.
