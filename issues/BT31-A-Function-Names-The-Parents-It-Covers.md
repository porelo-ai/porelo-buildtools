# BT31 — A Function Names The Parents It Covers

**Status:** Implementing
**Size:** S - one module (`release`'s plan reading, page and check) and its tests, no contract another repository reads
beyond the optional new field.

**What must be true.** A release plan's function names, beside its issues, the parent issues it covers that have no
work of their own; the pages show them as one line under the function with their state, they do not count in the
sprint's bar, and `plan check` refuses a parent that has fallen behind its parts.

## Plan

- `Plan.Function` takes an optional `covers:` list beside `issues:`, each entry `<repository>#<number>` checked like
  an issue reference; `PlanRender.indexes` reads the indexes of the covered repositories too.
- `PlanTemplate.Function` carries the covered parents with number, state and link; `plan.jte` shows one line
  "covers <number> · Accepted" under the function's chips, and the Markdown page the same line, linked. They add nothing
  to the bar's segments, the done count, the alt text's counts or the SVG's description.
- `PlanCheck` refuses, naming sprint, function and parent:
  - a covered parent with no row in its index;
  - a parent named twice in one function, or named both under `covers:` and under some function's `issues:`;
  - a parent in a state before Implementing (Idea, Accepted, Analyzed, Estimated, Backlog) while one of its
    function's issues is Implementing, Rework, Review or Finished.
- `doc/index.md`'s *Release plans* section names the field and the new refusals; a changelog entry under
  `changelog/unreleased/`.
- Red first in `PlanCommandsTest`: a parent behind an Implementing part is refused (and passes once the parent is
  Implementing); a covered parent leaves a sprint's bar and done count unchanged and shows its line on both pages.

## Acceptance

- `plan render` of a plan with `covers:` shows the line with the parent's state on the HTML and the Markdown page, and
  the sprint's bar and counts equal those of the same plan without it.
- `plan check` refuses a parent at Accepted or Backlog whose function has an issue in Implementing, naming the
  parent, seen red; it passes once the parent is Implementing or later.
- A covered parent with no index row, or one also listed under `issues:`, is refused with its place.
