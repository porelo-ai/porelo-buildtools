# BT27 — The Release Plan Page Drawn From Templates

**Status:** Review.
**Size:** M - jte templates, one progress image, the Markdown page rebuilt like the HTML page, and the Stand line;
like [BT26](README.md#bt26), in the `release` module alone.

**What must be true.** `porelo-release plan render` draws the HTML page from precompiled jte templates, which escape
by default, and writes the Markdown page built like it: the same sections in the same order, a bar per sprint by its
issues' states, a mark per function tested or not, and chips per issue in their state's colour.

## Plan

- **jte**, pinned in the root's dependency management; its Maven plugin compiles the templates into the module's JAR.
  The module has no native image or FFM check, so nothing more is registered.
- **The HTML page** (`plan.jte`) takes the operator's reference page's look: palette with its dark variant, legend,
  bars, marks and sprint cards, its content only from the plan and the indexes. Its inline bars take the page's
  palette.
- **The Markdown page:** the generated marker on its first line, `# Release <x>`, the goal, *Stand*, *Sprint
  progress* with one image `<release>/progress.svg` (legend, a labelled bar per sprint, "x of y done", the marks),
  the fixed sentence below it, then *Issues Details*: each sprint in a table of one cell, its functions in bold with
  their mark, the test, and the chips as a coloured square and the linked number, the chip's tooltip as the link's
  title. The image's alt text counts each state apart, in the legend's order.
- **The image** writes its colours into its own attributes, since a CSS variable does not reach an SVG shown as an
  image, and switches them in its own dark-mode style.
- **The plan file's `issue` is gone** and refused as an unknown field.
- **`check-citations`** skips a Markdown page only when the marker is its first line and a plan beside it, of the
  same name, names its `release` and `sprints`.
- **The Stand line** is the latest commit time of the inputs read: git for a local file, one request per repository
  for the latest commit on `main` touching `issues` on the forge. A source without one is named; a malformed answer,
  trailing tokens included, fails the command.
- **`plan check`** compares the Markdown page and the image with a fresh render.

## Acceptance

- `plan render` on porelo-project's plan writes both pages and the image, and a second run writes the same bytes.
- Every text from the plan file or an index is escaped in the HTML, the image and the Markdown, where it can open
  no image, no fence, no HTML block and no early end of a sprint's box.
- `plan check` fails on a changed or missing image.
