# BT08 — Sokar Parent Renamed Porelo Parent

**Status:** Analyzed.
**Size:** XS - one pom, a README and two workflow files in `sokar-parent`: 46 occurrences in 4 of 12 files, no path.

**What must be true.** `sokar-parent` is `porelo-parent`: `org.fuin.porelo:porelo-parent` at `0.2.0`, naming the
build tools as `org.fuin.porelo` at `0.5.0`, and no file in it says `sokar` but what says the old name. From [PJ41](https://github.com/sokar-ai/sokar-project/blob/main/issues/README.md#pj41).

Written here because `sokar-parent` holds only the parent pom and has no issues of its own; its rename is the first
commit of the window and this repository's ([BT07](README.md#bt07)) needs it published.

## Plan

**How:** one commit, made by a script this repository's agent runs and reviews, never by hand: `git mv` for every
path naming `sokar`, then `sokar`/`Sokar`/`SOKAR` replaced by `porelo`/`Porelo`/`PORELO`, then the exceptions below
put back. Nothing is renamed before the window is announced.

**What carries the name:** `groupId`, `artifactId`, `name`, `description`, the URLs and SCM of the pom, the
property `sokar.buildtools.version` (read by every repository that takes the tools) and the dependency
`sokar-machines`; the README's title.

**Exceptions:** none in the repository; Maven Central keeps `org.fuin.sokar:sokar-parent` as it is.

**How it is tested:** a search of the tracked files for `sokar` finds nothing, the pom validates and installs, and
`0.2.0` is published before [BT07](README.md#bt07) builds against it.

## Acceptance

- `org.fuin.porelo:porelo-parent:0.2.0` is on Maven Central; a search of the repository finds no `sokar`.
