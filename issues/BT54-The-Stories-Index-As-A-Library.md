# BT54 — The Stories Index As A Library

**Status:** Backlog on the operator's word of 2026-11-09.
**Size:** L - a new published module with a public API carved out of four package-private classes of
`porelo-release`, its native-image metadata and a native test, while `porelo-release stories …` keeps its behaviour.

**What must be true.** A program other than `porelo-release` can regenerate porelo-project's stories index after it
changes a story's state: read every story's head, check it against its schema, read each named issue's row from its
repository's index, and write `stories/README.md` and each story's generated part. It does so by calling one small
published library, byte for byte as `porelo-release stories index` writes it today, without Maven at run time and
without the network unless the caller hands it a way in. The library also reads an issue index (`IssueIndex`) on its
own. It builds into a GraalVM native image. `porelo-release` calls the same library, so the two can never write
different indexes.

## Why

A story's state is set by the same hands as an issue's. A program that sets it (an adapter of the issue tracker) must
leave the index as the index command would. Today that logic is package-private in `porelo-release`: `Stories`,
`Heads`, `IssueLinks` and `IssueIndex` in `org.fuin.porelo.release`. A second copy elsewhere would drift.

## Plan

Read in porelo-buildtools 7016154.

- **A module `stories`, artifact `porelo-stories`,** beside `json` and `machines`, published as they are. Its public
  package `org.fuin.porelo.stories` holds:
  - `StoriesIndex` with `check(Path dir)` and `write(Path dir)`, returning the faults or the files written;
  - `IssueIndexes` reading one repository's `issues/README.md` into `IssueIndex` rows (number, state, size, title,
    path, blocked by);
  - `IssueSource`, the one way to a repository's index. The library ships `IssueSource.local(Path root)`, which reads
    checkouts beside porelo-project as `IssueLinks.Sources.LOCAL_FIRST` does today without its forge fallback. A caller
    that wants the forge passes its own source; the library opens no connection itself.
- **What moves:**
  - `Stories`' reading, checking and generating;
  - `Heads`, the JSON head at a story's end and its schema check;
  - the part of `IssueLinks` that parses an index row and resolves a repository from `project.yml`
    (`Plan.project`);
  - `IssueIndex`;
  - the schema `schemas/story-1.json`.

  `porelo-release` keeps the commands `stories index|check|cut`, the citation checks and the plan, and calls the
  library. Its `IssueLinks` keeps the forge and the citation sources, wrapping them as an `IssueSource`.
- **Native image.** Jackson reads classes by reflection; networknt is not in the library (decided below). The
  module ships its reachability metadata under `META-INF/native-image/org.fuin.porelo/porelo-stories/`. A native
  test in its build writes the index of a fixture `stories/` and compares it byte for byte with the JVM's.
- **Tests that go red first:**
  - `StoriesIndexTest.writesWhatTheCommandWrote`: the library over a copy of porelo-project's `stories/` and its
    issue indexes, compared with `porelo-release stories index` on the same copy. Red while the module does not exist.
  - `IssueSourceTest.opensNoConnection`: `IssueSource.local` with a missing checkout fails naming the path, and
    reaches no `Web`.
  - The existing `StoriesTest` and `IssueLinksTest` stay green unchanged, so the command's behaviour holds.
- **Docs:** `doc/index.md` gets a section on the library and its one entry point; changelog under *Added*.

## Decided by the operator (2026-11-09)

- **A hand-written head check replaces networknt's validator** in the library, so it carries no reflection-heavy
  dependency. The schema file stays as the head's documentation. A test runs both the hand-written check and the
  schema (through networknt, in the test scope only) over every existing story head and over the faulty fixtures, and
  requires the same verdict from both.

## Settled with Agent Adapters (2026-11-09)

- **The native build:** Java 25 on the GraalVM that porelo-machines' jdk pins at the `porelo.buildtools.version` the
  adapter's root pom names (here `graalvm-25.4.4.1.1`). The metadata is tested with that pin.
- **How it is taken:** as an ordinary Maven dependency at build time, as `porelo-build-api` is taken, from Sonatype
  snapshots while developed and from Central once released. native-image compiles it in, so nothing is fetched at run
  time and no separate jar is needed. Its own dependencies stay few and native-image friendly: no reflection without
  metadata, and no `ServiceLoader` lookups.
- **Sources:** the adapter passes its own `IssueSource` over its clones.

## Acceptance

- The two red-first tests above, seen red, then green. `StoriesTest` and `IssueLinksTest` unchanged and green.
- The native test passes in the Build, and its output equals the JVM's byte for byte.
- porelo-project's `stories/` regenerated by the library and by the command give the same tree (`git diff` empty).
