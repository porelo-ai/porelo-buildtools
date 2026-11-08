# BT50 — A Pin Names Fallback Sources

**Status:** Idea.

**What must be true.** A pinned download names more than one source, each checked against the same sha256: the
GraalVM archive, shfmt and the like. When the first source cannot answer after the retry rule of
[BT49](README.md#bt49), the next one is asked, and the file is taken only when its digest matches.

## Why

A cache ([BT49](README.md#bt49)) helps once an archive was fetched once, and the retry rule outlasts a short outage.
Neither helps when GitHub's release download fails for longer, or when a new pin is fetched for the first time during
an outage. The digest makes any source as good as the first, since nothing is taken that does not match.

## The shape

- The pin in `machines/pom.xml` gains `graalvm.urls`, an ordered list beside the digest; the shell's pins take a list
  the same way.
- Possible second sources:
  - the organization's own Artifactory, where a Build puts the archive once;
  - the vendor's own download page where there is one.
- Asked in order, each with the retry rule, and the log names which source answered.

## Open questions

- Which second source: the organization's Artifactory (under our control, filled by a Build) or a vendor mirror?
- Does the rule's 15 min apply to each source, or to all together?
