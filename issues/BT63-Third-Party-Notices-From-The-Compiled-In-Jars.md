# BT63 — Third-Party Notices From The Compiled-In Jars

```json
{
  "$schema": "issue-1.json",
  "id": "BT63",
  "title": "Third-Party Notices From The Compiled-In Jars",
  "state": "Idea",
  "size": "M",
  "priority": "Medium",
  "rank": 5,
  "blockedBy": [],
  "covers": "One generator writes a package's THIRD-PARTY-NOTICES from the jars compiled into its executable: each jar's LICENSE and NOTICE, the licenses it embeds and the GraalVM runtime's, as full texts; a jar with no license text found is refused."
}
```

**Why this size.** one `porelo-release` command that reads a resolved runtime classpath and the JDK's legal files,
its tests on real jars, docs and a changelog entry; no package changes here.

**What must be true.** A repository that ships a .deb or an .rpm with third-party code compiled into a native
executable makes its `THIRD-PARTY-NOTICES` with one generator from porelo-buildtools, not one of its own. The file
holds, for every compiled-in jar, the jar's own `LICENSE` and `NOTICE` texts and the licenses of what the jar embeds
(ASM inside spring-core, FastDoubleParser inside jackson-core and the like), and the GraalVM runtime's, in full.
Decided by the operator on 2026-11-10. Agent Planner builds it for Agent Machines from what its reading for
porelo-planner's PL11 found; PL11 takes it first. The repositories that adopt it (porelo, porelo-planner, the agent
adapters and others) file an issue of their own each, after this one is on main.

## Plan

- **`porelo-release third-party-notices <classpath file> <JDK home> <out>`:** reads each jar of the classpath (one
  path per line, as `dependency:build-classpath` writes it, the project's own modules left out by a pattern given),
  and writes one text file. Each jar is one section headed by its Maven coordinates and its POM's license name,
  followed by every `LICENSE*`, `NOTICE*` and `COPYING*` it carries, at `META-INF/` and below, and at the jar's root.
  The GraalVM runtime is one more section, from the JDK's `legal/` directory and its `LICENSE`.
- **Same text once:** a license text found in several jars is written in full once and named by the others.
- **Fails closed:** a jar with no license text found is refused, naming the jar; a pinned file of texts given by hand
  (coordinates, the license's name, the text) is the only way past, and a text given there is refused as stale once
  the jar carries one of its own.
- **Stable:** the same classpath and JDK give the same bytes; sections by coordinates, texts in a fixed order.
- **Docs and changelog**; the shipping rule for adopters: the file goes to
  `/usr/share/doc/<package>/THIRD-PARTY-NOTICES`, named in `debian/copyright`, and as `%license` in the rpm.
- **Red first:** a jar without a license text is refused; an embedded license (jackson-core's FastDoubleParser) is in
  the file; two runs give the same bytes.

## Acceptance

- Run on porelo-planner's runtime classpath and the pinned GraalVM, the file holds a section for every jar and the
  runtime, each with its full text, and nothing is refused that carries a license text of its own.
- The tests above, each seen red first, pass in the Build.

## To be checked

- Which license texts of the GraalVM runtime belong in a native executable, read from the JDK's `legal/` directory and
  the executable's own reachable modules.
