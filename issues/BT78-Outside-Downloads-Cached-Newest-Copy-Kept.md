# BT78 — Outside Downloads Cached, Newest Copy Kept

```json
{
  "$schema": "issue-1.json",
  "id": "BT78",
  "title": "Outside Downloads Cached, Newest Copy Kept",
  "state": "Review",
  "size": "S",
  "priority": "Medium",
  "rank": 27,
  "blockedBy": [],
  "covers": "Every download porelo-buildtools' CI takes from outside is cached or named as not cacheable, and of each cache only the newest copy is kept: the Maven repository's cache no longer grows by restore-keys, and a saved key removes the copies it replaces."
}
```

**Size S.** Only `.github/` changes: the composite action `pinned-jdk` and one clean-up step; no Java code, no
contract another repository reads.

**What must be true.** A Build takes nothing from outside twice that it could keep, and keeps one copy of each:

| Download | Where | Today | After |
|---|---|---|---|
| GraalVM archive | `pinned-jdk` | cached by digest, checked after a hit | unchanged |
| shfmt | `pinned-jdk` | cached by digest, checked after a hit | unchanged |
| The wrapper's Maven | `pinned-jdk` | cached by `maven-wrapper.properties` | unchanged; older keys removed |
| Maven dependencies and plugins (Central, Sonatype snapshots, the forge's registry) | `pinned-jdk`, every job | the local Maven repository, keyed by the poms' hash, restored by prefix, so old versions pile up in every new key | only what this build resolved is saved; this repository's own installed modules are not; older keys removed |
| `porelo-ai/porelo` checkout in the snapshot job | `build.yml` | fetched each time | not cacheable: the snapshot must build the current `main` |
| What a rented snapshot machine downloads | `machines snapshot` | on the rented machine | not cacheable here: it lives and dies with the machine |
| The actions named by `uses:` | runner | fetched by the runner | not cacheable: the runner fetches them before any step |

- After a cache is saved under a new key, the copies of the same prefix that it replaces are removed, so each kind holds
  one copy; a removal that fails is said and does not fail the Build.
- A cache restored by prefix carries no artifact this build did not resolve: before saving, the job removes from
  the local Maven repository this repository's own group and every file not read in this run.

## Plan

- `.github/actions/pinned-jdk/action.yml`: split `actions/cache` for the local Maven repository into restore and save; before the
  save, drop `org/fuin/porelo/` and files with an access time older than the job's start; save under the poms' hash.
- One step after each save, with `actions: write` in the jobs that save: list the caches of the same prefix through the
  forge's API and delete all but the key just saved; on a forge that has no such API, say so and go on.
- The wrapper's cache gets the same clean-up. GraalVM and shfmt keep their digest keys; a moved pin removes the
  old digest's copy.
- The test that goes red first: none, a workflow change; the proof is two Builds in a row, the second restoring every
  cache and fetching nothing, and the cache list showing one key per kind.

## Acceptance

- Two pushes with unchanged pins: the second Build's log shows a cache hit for every kind above and no download of
  GraalVM, shfmt, Maven or a dependency.
- After a pom change, the cache list holds one `maven-` key, the new one.
- A deliberately broken clean-up (a wrong token scope) is said in the log and leaves the Build green.

## To be checked

- Whether the host's forge (Forgejo Actions) offers a cache list and delete API; if not, its runner's cache ages out
  by its own rule, and the step says so.
- How [BT50](README.md#bt50) meets this: it lets a pinned download ask further sources when the first cannot answer; it helps a cache
  miss during an outage, this issue makes misses rarer. Neither waits on the other; both touch `pinned.sh`'s callers,
  so they are built one after the other.

## As built

- `pinned-jdk` restores the local Maven repository by its prefix with `actions/cache/restore` and never saves it. A
  step marks each restored file unread, at the epoch, so the runner's relatime records the first read of each.
- `.github/actions/maven-cache-keep`, the build job's last step, drops this repository's modules at the root pom's
  version, so porelo-parent and other porelo libraries stay. It also drops every version directory in which no file
  was read since the mark, then saves under the poms' hash. A directory is kept whole, as Maven only asks whether a
  jar exists. Nothing is saved after an exact hit. A runner whose probe file records no read keeps the repository
  whole, with a warning.
- Only build.yml's build job saves the Maven repository. The other jobs restore its entry and fetch what it lacks, so
  no two jobs race for one key.
- The wrapper's Maven is split into restore and save, saved once the wrapper ran. `pinned-jdk` hands each cache's key
  to the job as outputs, only when it was restored or saved.
- The clean-up is build.yml's `prune` job, after the pattern of the site's: its own job with `actions: write`, the
  runner's `gh` alone, `GH_TOKEN` and `KEEP`, on https://github.com only, for the Build's own ref only. It takes only
  the four prefixes with a key of each, and deletes nothing of a prefix whose key is not stored for that ref.
  check-actions lists `build.yml/prune` beside the others. Seen red first: `acceptsActionsWriteInTheCachePruneJob`
  for build.yml, and `thisRepositorysOwnWorkflowsFetchNothingByAName` over the workflows.
- The acceptance runs on GitHub: two Builds in a row, and the cache list after a pom change. Locally, check-actions,
  the release module's tests and the unread-file pruning on a relatime directory are green.
- Not done: machines.yml, prune-snapshots.yml, shared-rules.yml and workflows.yml do not save the Maven repository, and
  their GraalVM, shfmt and wrapper entries are pruned only by the next Build. The snapshots job's matrix shares the
  build's entry.
- Read by agy (2026-11-12). Eight findings, all taken: a missed key handed out, a save that did not reach the store,
  main's prune reaching pull requests and tags, the KEEP lines unchecked, a noatime runner seen as recording reads, jars
  only asked about, porelo-parent dropped, and the stage record. The keep step and the prune were run locally against
  a stand-in repository and a stand-in gh.
