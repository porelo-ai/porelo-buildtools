# BT63 — Third-Party Notices From The Compiled-In Jars

```json
{
  "$schema": "issue-1.json",
  "id": "BT63",
  "title": "Third-Party Notices From The Compiled-In Jars",
  "state": "Review",
  "size": "M",
  "priority": "Medium",
  "rank": 5,
  "blockedBy": [],
  "covers": "One generator writes a package's THIRD-PARTY-NOTICES from the jars compiled into its executable, its CycloneDX bill and the JDK: the standard text of each license the bill names for a jar, the jar's own texts and those of what it embeds, and the GraalVM runtime's; a jar whose license it cannot tell is refused."
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

- **`porelo-release third-party-notices <classpath file> <bill> <JDK home> <out> [--own <regex>]`:** the classpath
  is what `dependency:build-classpath -DincludeScope=runtime` writes after `package` (lines or the path separator),
  the bill the build's CycloneDX JSON, `--own` the project's modules by `groupId:artifactId`. One text file, new class
  `ThirdPartyNotices` beside `CheckPackageVersion`, dispatched in `Main`.
- **Each jar is one section headed by its coordinates**: from the `pom.properties` its file is named after
  (`<artifact>-<version>[-<classifier>].jar`, a classifier beginning with a letter and not `SNAPSHOT`), otherwise - a
  fat jar, Spring Boot's built by Gradle - from the bill entry carrying its SHA-256, otherwise refused; so `--own`
  cannot skip a jar that only claims coordinates. A classifier heads the section and keys the bill (its purl's
  `classifier`), so netty's per-platform jars are one section each, each held to its own hash.
- **Bill and classpath belong together:** every jar needs its bill entry, by coordinates, and its SHA-256 must equal
  the bill's where the bill carries one. One jar twice on the classpath, and one component named twice differently in
  the bill, are refused, naming both.
- **Every section holds the standard full text of each license and exception its bill entry names** - the effective
  POM's, a parent's included (logback's from logback-parent) - beside the jar's own texts, which "same text once"
  absorbs. `A OR B` and `A AND B` give every text, operators in any case; after `WITH` only a pinned exception counts.
  Refused when the bill names none, a license or exception the set lacks, or a license by URL alone.
- **The jar's own texts:** every file whose name has license(s), notice(s) or copying as a whole part (`LICENSE.txt`,
  `NOTICES`,
  `LICENSE-notice.md`, jackson-core's `FastDoubleParser-LICENSE`) and every file under a `license(s)/` directory
  (plexus-utils' `licenses/thoughtworks.TXT`), anywhere in the jar; never a class, source, properties file or image.
- **The pinned set:** Apache-2.0, BSD-2-Clause, BSD-3-Clause, EPL-1.0, EPL-2.0, GPL-2.0-only, LGPL-2.1-only, MIT
  (and GPL-2.0-with-classpath-exception as GPL-2.0-only with the exception), the exception Classpath-exception-2.0, as
  resources of `porelo-release`, unchanged from SPDX license-list-data tag v3.29.0, which `licenses/SOURCE.txt`
  names; a test holds each file to its SHA-256.
- **The GraalVM runtime is one more section:** the JDK's root license files and its whole `legal/` directory. Which
  modules an image reaches no build output says; all of them is never less than the image needs.
- **Same text once, stable, readable:** a text that stood once is named; the same input gives the same bytes,
  whatever the classpath's order; the file is written atomically with mode 0644, and nothing is written on a refusal.
- **Docs and changelog**; the shipping rule for adopters: the file goes to
  `/usr/share/doc/<package>/THIRD-PARTY-NOTICES`, named in `debian/copyright`, and as `%license` in the rpm.
- **Tests (unit, `ThirdPartyNoticesTest`, jars made in the test and jackson-core of the build's own)**, each seen red
  first: a jar's texts in full; texts under `licenses/`; only names for a license; standard texts beside a jar's own,
  dual and with an exception; the refusals (no bill entry, another SHA-256, a jar twice, a component twice, no license
  named, a license or exception not pinned, a URL alone, no CycloneDX bill, a classes directory, a JDK without
  `legal/`); a jar told by its hash, a fat jar, a version only beginning like the file's, a SNAPSHOT or empty tail
  taken for no classifier, two classifier jars of one artifact, the classifier read from the purl; plural names;
  operators in any case;
  the pinned set's digests and tag; same bytes in any order; the file's mode.

## Acceptance

- Run on porelo-planner's runtime classpath, its bill and the pinned GraalVM, the file holds a section for every
  third-party jar and the runtime, each with its full text, and nothing is refused.
- The tests above, each seen red first, pass in the Build.
