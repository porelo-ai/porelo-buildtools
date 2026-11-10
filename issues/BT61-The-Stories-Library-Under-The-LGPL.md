# BT61 — The Stories Library Under The LGPL

**Status:** Review.
**Size:** XS - the `porelo-stories` module's license block, its own license file and a changelog entry; no code.

**What must be true.** `org.fuin.porelo:porelo-stories` is published under LGPL-3.0-or-later. A program under another
license, porelo-planner (LGPL-3.0-or-later) first, may link it into its native executable without becoming a work under
the GPL. The rest of porelo-buildtools stays GPL-3.0-or-later. Decided by the operator on 2026-11-10, asked for by
Agent Planner.

## Plan

- **`stories/pom.xml`:** its own `<licenses>` block, LGPL-3.0-or-later, which replaces the GPL block it inherits from
  the parent, so the flattened, published pom names the LGPL.
- **`stories/LICENSE`:** the text of the GNU Lesser General Public License, version 3. The LGPL is a set of permissions
  added to the GPL, whose text stays in the repository's `LICENSE`.
- **`stories/README.md`** names the module's license; the repository's `README.md` says that `porelo-stories` is the
  one module under the LGPL.
- **Changelog:** an entry under *Changed*.
- **Check:** the built `porelo-stories` pom names LGPL-3.0-or-later and the `release` module's still the GPL.

## Acceptance

- `./mvnw verify` green; the flattened pom of `porelo-stories` names LGPL-3.0-or-later, the other modules' the GPL.
- The snapshot version that carries it is said in the room for Agent Planner.

## Checked

- `./mvnw verify` green. The flattened `stories/.flattened-pom.xml` names LGPL-3.0-or-later, `release`'s still
  GPL-3.0-or-later.
- `stories/LICENSE` is byte for byte the text at gnu.org's lgpl-3.0.txt.
