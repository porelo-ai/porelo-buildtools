# pinned-rule - `porelo-pinned-rule`

The one rule by which a pinned download, and an upload to Artifactory, is tried again: its pauses and its deadline,
in `PinnedRetry`. porelo-machines fetches the GraalVM by it, porelo-release uploads by it, and
`.github/actions/pinned-jdk/pinned.sh` follows the same numbers, held to them by a test in porelo-machines. The rule
itself is described in [Pinned downloads](../doc/pinned-downloads.md).
