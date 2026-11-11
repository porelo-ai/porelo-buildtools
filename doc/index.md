# Build tools

The tooling every Porelo repository builds and tests with, published to Maven Central under `org.fuin.porelo`. None
of it ships to a person who runs Porelo: it rents the machines the acceptance tests run on, checks what a release
may publish, and checks the native images before they go out.

## The tools

| Artifact | What it does | Commands |
|---|---|---|
| `porelo-machines` | Rents test machines and the snapshots they boot from, runs the agent repositories' acceptance legs on them, and installs the pinned GraalVM and musl toolchain in a build. A rebuilt snapshot deletes the older ones of its system labelled for CI, keeping the one before it as a fallback, and says each. A leg whose build is cancelled ends its suite and gives its machines back at once. A leg installs Porelo from the repository's `releases` on a tag run and from its `snapshots` otherwise; `--channel` on `acceptance` and `lease` names one, and `acceptance --candidate` needs no `--package`. A snapshot proves itself by building `porelo` with the tree's own `ci/leg-build.sh`. Every server it rents carries the end of its job's time, and `sweep` deletes only servers past it or without one. | `sweep`, `snapshot`, `acceptance`, `lease`, `jdk`, `musl` |
| `porelo-release` | Checks that every workflow step is pinned by commit, that a repository's shared rules and its `.editorconfig` are copied byte for byte, that no page or source cites an issue in a way that goes stale, that every commit message of a range keeps the shared rule, that a release builds and packages with no snapshot, that a repository asks Sonatype only about the snapshot groups it lists, that a deploy dry run went through the publishing plugin in every module and sent nothing off the machine, that native binaries need no newer C library or zlib than their packages declare, that the built `.deb` and `.rpm` carry the project's version, writes a package's third-party notices from the jars compiled into it and its bill, that every page of a documentation chapter is in its navigation once and every link on it can be followed, that every Maven module has a README linking its submodules, that each issue's data stands in a head its index is generated from, and that an agent's pinned CLI, each artifact of it by its own pin, is the one its download names; records those artifacts in the package's bill; moves named pins; deletes old snapshot packages from Artifactory. | `check-actions`, `check-shared`, `check-citations`, `check-commits`, `issues`, `check-doc-site`, `check-readmes`, `check-releases`, `check-deploy`, `check-linkage`, `check-package-version`, `third-party-notices`, `prune-snapshots`, `check-pin`, `add-fetched-cli`, `compare-bills`, `update`, … |
| `porelo-ffm-check` | Checks that every downcall of the Foreign Function & Memory API a test made is registered for the native image. | run by `porelo`'s build |
| `porelo-cpu-check` | Checks that a native image asks for no more than x86-64 v1, so it runs on any x86-64 CPU. | run by `porelo`'s build |
| `porelo-json` | The JSON reader and writer the tools above share. | - |

See [test machines](machines.md) for requests by kind, credentials, and cleanup.

## Who uses which

| Repository | Uses |
|---|---|
| `porelo` | all of them: the machines its acceptance legs run on, the image checks, `check-actions` and `check-shared` |
| the agent repositories (`claude`, `pi`, `omp`) | `porelo-machines` for their acceptance legs and the GraalVM; `porelo-release` for the pinned CLI, the bill and the shared rules |
| `frontend` | `porelo-machines` to lease a machine its integration tests reach; `porelo-release` for the shared rules |
| `sluice`, `matrix` | `porelo-machines` for the GraalVM; `porelo-release` for the shared rules |

## What `check-citations` reads

Every committed text file: what `git ls-files` names in a checkout, every file in a tree exported from a commit. A
binary file - one holding a NUL byte or bytes that are not UTF-8 - is skipped and counted, and so is a file no person
writes: an SVG image's path data, a lock file's hashes. Build output is not read. Nothing outside the repository is
read - no other repository's index or `project.yml`, no checkout beside it, nothing over the network - so another
repository's state never decides this one's check.

- **Outside `issues/`**, no file names an issue number, and no link goes to an issue's file or a design document.
- **Among the issues**, a number of the repository's own prefixes - the prefixes its issue files carry - names an
  issue file or an index row that exists, and a number beside an `[index](…)` link into the repository names one too.
- **Every index row** under `issues/` opens with the anchor of its number, `<a id="<number in lower case>"></a>`.
- **Every issue number under `issues/`** is a link to its row, `[<NUMBER>](<index>#<number>)`, never to its file:
  the row outlives the file. Headings, code spans, fenced code and a row's own first cell are not read. A link into
  this repository's own indexes reaches an anchor that is there. A number of the repository's own prefixes that no
  index has a row for, an issue finished before rows were kept, is said apart: its text is rewritten, not linked.
  Which prefixes are issue numbers is read from the root's own issue files and index rows, so `SHA256` or a
  guideline's `LLM03` is none, and neither is a prefix a test's fixture deeper down carries.
- **An issue's head**, the JSON fenced right below its heading, is data: its `blockedBy` and `parent` ids are not
  asked to be links, and each must name an issue file or an index row of the repository, else it is refused on its
  line. Issue files and rows of a sub-issue, `<parent>.<n>`, count.
- **An issue's sub-agent briefs** lie beside its file in `issues/<group>/<ID>.sub/<name>.md`, `<name>` one of
  `builder`, `tester` and `reader`, or a numbered variant from `-2` on, such as `builder-2`. A brief is read as a page
  under `issues/`, never as an issue: it has no row and no stats file, its own issue's number is a link to the row,
  and it dies with its issue, so no lasting page links to it. A brief beside no issue file, a file of another name,
  a nested directory or a link in `<ID>.sub/` is refused, naming the path.
- **Left to a check across repositories:** a link into another repository's index is counted, not followed, and a
  citation of another repository's issue is not looked for. One `NOTE` line says so, with the count, and does not
  fail the check.
- **A line that holds an issue number's shape on purpose**, as test data for something that reads such text, says
  `not-a-citation` in its own text. A linter's codes after `noqa:` are not read.
- **A whole file of such text** is named in `citations-exempt.txt` at the repository's root, one path per line,
  relative to the root, with `#` starting a comment; a path there that is not a committed file is refused, so the
  list cannot outlive what it exempts.

## What `check-commits` reads

```
porelo-release check-commits RANGE [--outside]
```

The commits of a git range in the working directory, a repository's root: before a push over `origin/main..HEAD`,
in a Build over the pushed range. In this repository's own build it runs as
`./mvnw -B -s settings.xml -pl release exec:java@check-commits -Dcommits.range=<range>`, or `@check-commits-outside`.
Each commit is held to the shared rule, and each fault is one line with the commit's short hash:

- **A subject of at most 72 characters**, every character counted, `[skip ci]` among them.
- **Below it, only a blank line and trailers** - `Assisted-by:`, `Signed-off-by:` and the `Co-authored-by:` GitHub
  writes; any other line is a body and refused.
- **No requirement number**: a word made of an issue prefix and digits. The prefixes are the ones `check-citations`
  reads: those of the repository's own issue files and index rows, never another repository's. So `IPv4` or `x86_64`
  is none, and another repository's number is not caught here but left to a check across repositories; a repository
  with no issue prefix at all is not checked but refused, exit 2.
- **Nobody named as having asked for or made the change.** A heuristic: a match is refused, and a pass proves nothing.
  It matches a party named - `operator`, `coordinator`, `Smith`, `Agent` and a capitalized name, an `@handle`, or the
  commit's own author or committer name - and a phrase that gives credit, taken whole: "at X's word", "as X chose",
  "asked by", "found by", "decided by", "reported by", "accepted by", "per" and a name, "X's review", "X's test". The
  lists are in `Who`. Trailers are not read for it, since they name a person on purpose.
- **With `--outside`**, for a pull request from outside, every commit also carries a `Signed-off-by:` naming its
  author's address, the Developer Certificate of Origin. Without it a sign-off is neither asked for nor refused.
  The pull-request step passes `--outside` unless the request comes from a branch of the repository itself and its
  author is an `OWNER` or `MEMBER` there, or is `github-actions[bot]`. GitHub reports `MEMBER` only for a public
  membership of the organization: a member whose membership is private is reported `CONTRIBUTOR` and checked as
  from outside. On the host's forge, which names no author association at all, a pull request from a branch of the
  repository itself is from inside: the step tells that forge by `github.server_url`, which is not
  `https://github.com` there, and only a holder of write access pushes such a branch.

Exempt are the commits authored by `dependabot[bot]` or `github-actions[bot]`, and those GitHub committed in its web
interface (committer `GitHub`), told by the author and committer the commit records, never by its message. Exit 0 when
no commit breaks the rule, an empty range among them; 1 when one does; 2 when git or the issues cannot be read, or the
range starts with `-`, since git would read it as an option.

Stage records are checked in each nonexempt commit's tree. Every `stats/*.jsonl` file must be nonempty and
contain complete, closed records: the same field, value, issue-to-filename and timestamp validation used by
`stage` applies. A record whose `end` is null is refused with that commit's short hash. Generated stage JSONL
files are exempt from `check-citations`; handwritten notes under `stats/` are still checked.

When that tree contains `stats/seed.csv`, a commit that adds an issue in a state marked `measured: true` in that
tree's `issues/workflow.yml`, or changes an issue's state or size while it ends in such a state, requires
`stats/<ISSUE>.jsonl` in the tree. State and size are read as `stage` reads them, from the head where the file has
one and from its `**Status:**` and `**Size:**` lines otherwise, in the commit and in its parent; an edit that keeps
both, such as prose or moving the lines into a head, needs no stats. A parent version that cannot be read or has no
valid state or size is refused with the reason, never taken as unchanged; the parent is read with the commit's own
workflow, so a commit that renames a state refuses until each measured issue it touches has its stats. A file is
followed by its path: a renamed or moved issue in a measured state counts as added and needs its stats. The workflow
is read from the same commit as the
issue and records; editing the working tree cannot change a historical check. Issue files are found directly under `issues/` and in
its subgroups. Deleted issue files and index-only changes need no stats. Changes are compared with the first
parent of a merge, and every issue of a root commit counts as added. Bot and web commits exempt from the message rules
also skip the stats rules. A `NOTE` identifies nonexempt trees without the seed, where the issue-change rule is
not yet enforced. A later commit closing a stage does not repair an earlier open stage in the checked range.

## Recording issue stages

Run these commands from the repository root:

```
porelo-release stage begin <issue> <stage> [--actor agent|subagent] [--model MODEL] [--effort EFFORT] [--session FILE] [--wait operator|build|agent|vm]
porelo-release stage end <issue> <stage> [--actor agent|subagent] [--session FILE] [--result done|returned|refused|abandoned] [--status-after STATE]
porelo-release stage median <size>
porelo-release stage p80 <size>
```

The stages are `analyze`, `estimate`, `plan`, `implement`, `test`, `review`, `rework`, `handover`, and `wait`.
A wait needs one reason: `operator`, `build`, `agent`, or `vm`. An issue identifier has one to three uppercase
letters and two or three digits. Model and effort are single identifiers made of letters, digits, dots,
underscores, or hyphens. An option value cannot start with a hyphen. Invalid input is refused with exit 2 and a
reason; diagnostics do not echo session paths or filesystem exception details. `--result` and `--status-after`
are supplied on end. Model, effort and wait reason are set on begin; repeating them on end must match begin.

`begin` reads the current size and status from the issue file, including files in subgroups - from its head where it
has one, a size of `-` read as none, else from its `**Size:**` and `**Status:**` lines - and appends an open
record with `end: null` to `stats/<issue>.jsonl`. An open, unmeasured issue without a size can begin `analyze`
or `estimate`, recording `size: null` and no estimate. Issue statuses and record statuses must belong to the required
local `issues/workflow.yml`. Each later begin reads the size anew; end preserves the
size captured by its begin. An estimate with a size uses that size's median for `estimate_min`.

The session source, its Codex begin total and a reference to the public record stay under `porelo-stage/` in the
directory returned by `git rev-parse --git-dir`, including in a linked worktree. Repository-owned storage paths
must not be symlinks; a linked worktree's Git directory may be outside its tree. A second open own stage is
refused. Subagent stages may run beside it, one per session file, and need both `--actor subagent` and `--model`
on begin. Use the same `--actor subagent --session FILE` on end. A subagent session file may be absent at begin
but must be a readable regular file at end. No public record contains its source path, account, or machine
identity. The command serializes writers and refuses an invocation that cannot obtain the lock.

`end` replaces exactly the matching open line with its closed record and removes the private state. Neighboring
lines retain their content and order; the replacement is atomic. It refuses an end for a different issue or stage
and cannot add a second copy when end is repeated. Commit the stats file with the issue's next change after all
stages are closed. A record has these fields:

| Fields | Meaning |
|---|---|
| `issue`, `stage`, `n`, `actor`, `model`, `effort` | Work identity, ordinal of this stage, and optional model settings. Repeated stages and concurrent subagents get separate ordinals. |
| `size`, `status_before`, `status_after` | The size at this stage's begin, or null for unsized analysis or estimation, and the statuses before and after the stage. End defaults to the issue file's current status; `--status-after` supplies one explicitly and allows end after the finished issue's file is removed. |
| `start`, `end`, `wall_min`, `vm_min` | UTC instants, elapsed minutes, and minutes of a `wait` whose reason is `vm`; other stages have zero `vm_min`. |
| `estimate_min`, `result`, `wait_reason`, `note` | The current size median on a sized estimate stage, otherwise null; end result, default `done`; a wait reason or null; `no-session` when no session was supplied, `no-usage` for a refused or abandoned session without usage, otherwise null. |
| `claude_input_tokens`, `claude_output_tokens`, `claude_cache_read_tokens`, `claude_cache_write_tokens`, `claude_messages` | Usage counted once per assistant `message.id`, using its last line. Own stages include last-line timestamps at or after start and before end; subagent stages use the entire session. |
| `codex_input_tokens`, `codex_cached_tokens`, `codex_output_tokens`, `codex_reasoning_tokens` | The last cumulative `total_token_usage` at end minus the total read at begin, or zero at begin if the session did not exist or had no measured usage. Events with `info: null` are ignored; decreasing totals are refused. |
| `agy_input_tokens`, `agy_cache_read_tokens`, `agy_output_tokens` | The JSON result's `usage` fields `input`, `cacheRead`, and `output`, or the complete alternative set `input_tokens`, `cache_read_input_tokens`, and `output_tokens`. Mixed or incomplete sets are refused. |

The own session is supplied with `--session FILE` on begin or `PORELO_STAGE_CLAUDE_LOG`. A subagent's usage comes
from its own supplied session, regardless of the stage's time window. Session format is detected from content.
At end, missing session files, unknown formats, malformed usage, and negative counts fail closed. A refused or
abandoned session containing no usage records closes with every token field zero and `note: "no-usage"`;
malformed usage still refuses to close. No session means all token fields are null with `note: "no-session"`.
Otherwise token fields belonging to other session formats remain null. Format detection is independent of actor.

A person's move of an issue on the board is a line the planner writes, never one `stage begin` opens: `actor:
person` with `stage: move`, the states before and after, `start` equal to `end`, `size` the issue's or null, and
`wall_min` and `vm_min` null. No other actor may record a move, and a person no other stage.

`stats/seed.csv` starts with `size,minutes` and one row per seeded size: XS 8, S 27, M 53, L 80. `stage median`
combines that size's seed with one sum per finished issue of its own stages carrying that size: `plan`,
`implement`, `test`, `review`, `rework`, and `handover`, including repetitions. Analysis, estimation, waits,
subagents and moves are excluded. Work recorded under another size is excluded from that size's sum. An issue with work
under several sizes contributes one sum to each applicable size. An issue is finished only when every record is
closed and its last record's `status_after` is a closed workflow state other than Rejected.

`stage median` prints the median in minutes, averaging the middle two values for an even sample. `stage p80`
uses the same samples and prints the nearest-rank 80th percentile. Unfinished issues contribute nothing.
A missing seed, empty stats file, malformed record or unreadable file is an error.

## What `check-pushes` reads

```
porelo-release check-pushes --since HOURS
```

The pushes to `main` of the last HOURS, read from GitHub's repository activity
(`GET /repos/{owner}/{repo}/activity`), each held to the shared rule by its own range, `before..after`, as
`check-commits` holds a range. A commit made long before its push is read with that push, whatever its date; the push
that made `main` is read up to its head. Activity of another branch is not read. The
scheduled step *Main's pushes of the last day* runs it with `--since 26`, two hours over the schedule's day, against a
checkout of every commit.

It reads `GITHUB_REPOSITORY` and `GITHUB_TOKEN` from the environment, never from an argument, and sends the token to
GitHub's API alone, and takes it out of any message it prints. It names each push it reads, with its range and time, then that range's result. Exit 0 when every
push holds to the rule, a day without a push among them; 1 when a commit breaks it; 2 when the activity cannot be read,
the token is missing, a push's range is not in the checkout, or a full page of 100 is still inside the window, since a
second page is not followed. `GITHUB_API_URL` other than GitHub's own is refused with 2: another forge's activity is
not read.

## What `check-actions` reads about permissions

Besides the pins, `check-actions` reads each workflow's `permissions:`. A file under `.github/workflows` with a
`jobs:` line is held to three rules:

- **Its top says `permissions:`**: `read-all`, `{}`, or a map of `read` and `none` only. Without one, the token
  gets the repository's default, which may be write.
- **A job may write only what the check's list names for it**, by workflow file and job id. A write anywhere else
  is refused, naming the file, the job, the permission and what the list allows there.
- **`write-all` is refused everywhere.**

The list is in the check (`CheckActions.WRITES`), the same for every repository:

| Workflow file | Job | May write | Why |
|---|---|---|---|
| `delete-runs.yml` | `delete` | `actions` | deletes the repository's old runs |
| `build.yml` | `build` | `actions` | cancels the legs of a commit that failed its unit tests |
| `machines.yml` | `refresh` | `contents`, `pull-requests` | moves a pin on its own branch and opens the pull request |
| `update.yml` | `update` | `contents`, `pull-requests` | the same, for an agent's pinned CLI |
| `site.yml` | `publish` | `pages`, `id-token` | publishes the documentation site |
| `build.yml` | `windows` | `id-token`, `attestations` | the interface's Windows job attests its ZIP |
| `site.yml`, `shared-rules.yml`, `workflows.yml` | `prune` | `actions` | keeps only the newest entry of each cache, with the runner's `gh` alone |

A job that needs a write not in it is a change to the list, with its reason, in `porelo-buildtools`.

## What `check-actions` reads about checkouts

Every `actions/checkout` step sets `persist-credentials: false` under its own `with:`; one without it, or with
`true`, is refused, naming the workflow file, the line and the step. Otherwise the job's token stays in the
checkout's `.git/config`, where every later step can read it. No step is excepted: a step that pushes with git
hands the token to that one command from the environment, as `machines.yml` does, and a repository that cannot
names it in the channel.

## What `check-actions` reads about triggers

A commit is built once: `main` by its push, a task's branch by its pull request. A workflow under
`.github/workflows` that runs on `pull_request` and also on a `push` of every branch is refused, naming the file:
a push without a branch filter, with `branches-ignore`, or with branches other than `main`. Under `push`, give
`branches: [main]`, with `tags:` where a release is built from one; a push of tags alone builds no branch. A
workflow on a schedule and on pull requests, as the Shared rules check runs, is not touched.

## What `check-readmes` reads

```
porelo-release check-readmes [REPOSITORY ROOT, default .]
```

The reactor from the root `pom.xml` down, through every `<module>`, a profile's included, since a module only a
profile builds is a module all the same. Each module directory must hold a `README.md`, and a module with submodules
must link each of them in it - to its directory or to its `README.md`; a name in a code span is no link. Every fault is
named; a root without a `pom.xml` is refused, never passed as an empty walk.

## What `check-deploy` reads

The log of the deploy a tag runs, run on every push with both of the publishing plugin's upload URLs at a port on the
runner where nothing listens:

```
./mvnw -B -s settings.xml deploy -Pcentral-sonatype-release -Dgpg.skip -DskipTests \
    -DcentralBaseUrl=http://127.0.0.1:9 -DcentralSnapshotsUrl=http://127.0.0.1:9/ > target/deploy-dry-run.log 2>&1
porelo-release check-deploy target/deploy-dry-run.log
```

The upload failing against the dead port is expected. Refused, each named:

- **a module deployed by `default-deploy`**: the release profile's publishing plugin did not reach it as an extension,
  so the real deploy fails there with "repository element was not specified";
- **a module where `injected-central-publishing` never ran**;
- **an upload aimed at any host but the loopback address**: both URLs must be set, since a snapshot goes to
  `centralSnapshotsUrl` and a release to `centralBaseUrl`, and a dry run must not be able to publish;
- **a run offline**, where the plugin skips its goal and proves nothing.

So a tag's deploy repeats what `main` already did, rather than being the first time the release profile meets the
build.

## What `check-snapshot-filter` reads

A repository's snapshots come from Sonatype, and on a forge from its registry too, the newest winning. The registry of
a host holds only what was built there and has not yet gone through the central forge. So that a private group's name
never reaches Sonatype, Maven's remote repository filter asks it only of the groups named in
`.mvn/rrf/groupId-central.sonatype.snapshots.txt`. With no file the filter asks everything, so the check refuses:

- a `settings.xml` profile holding Sonatype's repository that does not activate on both the switch and that file;
- a `.mvn/maven.config` without the switch and the filter's directory, written with `${session.rootDirectory}`: only
  that file expands it, and a relative path or `.mvn/jvm.config` leaves the filter asking everything;
- a missing filter file;
- a `pom.xml` that reads Sonatype's snapshots itself, by that id or address, which no switch or file holds; deploying
  there stays free;
- a deploy step of `-P"$PORELO_DEPLOY_PROFILE"` not held to `main` and tags, by its job or by itself, since a task
  branch's snapshot would become the newest for every build on the host.

On a forge the registry is read with `PORELO_PACKAGES_READ_TOKEN`, which can only read, by the server
`porelo-forge-snapshots`; only the step that deploys holds `PORELO_PUBLISHER_TOKEN`. A job on a forge that names no
`MAVEN_SNAPSHOTS_URL` or holds no read token fails first, as Maven would only warn and read Sonatype alone.

A repository whose `settings.xml` names no snapshot repository has nothing to hold. An empty filter file pins a
repository to what the host built, while central's copy is newer than its own and not yet brought in.

## What `check-releases` reads

The effective pom Maven writes, taken on a release tag before the build:

```
./mvnw -B -s settings.xml help:effective-pom -Doutput=target/effective-pom.xml
porelo-release check-releases target/effective-pom.xml --requires org.fuin.porelo:porelo-release
```

So what is checked is what Maven resolved: every property interpolated, an imported BOM's versions merged in, plugins
and the dependencies they run with. A release depends only on releases, so every `-SNAPSHOT` it names is refused and
named with where it was found - parent, dependency, managed dependency, plugin, a plugin's dependency, extension. The
enforcer's rule for release dependencies does not look at a plugin's dependencies, and the Porelo tools are exactly
those.

- **The repository's own modules are outside the rule**: the same run builds them, and a repository whose Maven version
  is never published (`0-SNAPSHOT`, where another file carries the package version) is not refused for it. The tag's
  own version is checked where the release names its channel.
- **A profile that is not active is read too**: the effective pom keeps its declarations, and a snapshot named there is
  refused as well.
- **Write the effective pom with the profiles the release build uses**: a module a profile adds - a `.deb` or `.rpm`
  module under `dist` - is in the file only when the profile was active. A profile's module the file does not hold is
  refused, naming the profile to add.
- **`--requires GROUP:ARTIFACT`**, given once per artifact, refuses an effective pom that does not name it, and one that
  names no artifact at all is refused always - so a file written from the wrong directory fails rather than passes.
- **A published pom names no parent**: a module that opts in to Central - `skipPublishing` or `maven.deploy.skip`
  `false`, on `central-publishing-maven-plugin` or `maven-deploy-plugin` where the module sets it there, as a BOM does
  since its properties are published with it, else as the property - is refused unless `flatten-maven-plugin` flattens its pom in a mode that leaves the parent out (`oss`,
  `ossrh`, `defaults`, `bom`) and no `pomElements` keeps it. A consumer then needs the module alone, and the root and
  grouping modules stay unpublished.

## What `prune-snapshots` deletes

Every push to `main` of a repository that ships a package adds a snapshot to Artifactory's `porelo-dist-deb` and
`porelo-dist-rpm`, and nothing else removes one. The workflow *Prune snapshot packages* runs `prune-snapshots` by hand;
left unticked, it lists what would go and deletes nothing. It reads `JF_URL`, the platform's address, and
`JF_ACCESS_TOKEN` from its environment; the address must be `https`, and the token goes to it alone, as a header.

- **Which files:** `.deb` files for `amd64` or `all` under `pool/main/p/<name>/` whose `deb.distribution` is exactly
  `snapshots`, and `.rpm` files under `porelo-dist-rpm/snapshots/`; no other file there, such as a bill. The Debian
  packages of both channels share the pool, so a file the `releases` index lists is refused whatever its property says.
  A run deletes nothing when it cannot read that index, when the index misses a release the pool holds, or when a
  Debian file has no `deb.distribution`.
- **Which go:** per repository and package name, the three newest versions as dpkg orders them stay however old, and so
  do the three stored last, since an older naming such as `0.5.1-snapshot.92` sorts above `0.5.1~snapshot.93`; of the
  others, a version goes when every file of it was stored more than 28 days before the run.
- **First a probe:** a file put and deleted under `prune-probe/` of each repository, outside the pool and every
  channel's folder. A token that cannot put or delete stops the run before anything is read, naming the probe left.
- **Then the indexes:** after deleting, the run reads the `snapshots` Debian index and the RPM primary metadata until
  neither lists a deleted file, counting an index that is gone as one that has not followed, asks Artifactory to recalculate one that still does after two minutes, and fails naming
  each file still listed two minutes after that.

Exit 0 when done, 1 when a deletion failed or an index kept a deleted file, 2 when something could not be read, before
any deletion.

## How a repository takes them

`porelo` names the version in its root pom, `porelo.buildtools.version`, and publishes it in `porelo-bom`. Every other
repository imports `porelo-bom` at the `porelo` version it builds against, and so takes the build tools that `porelo`
release names, without a version of its own. A build tool runs as a plugin's dependency, for example:

```
./mvnw -B -s settings.xml -N exec:exec@machines -Dmachines.args=jdk
```

The tools depend on nothing of `porelo`'s. `porelo`'s build runs them, so a dependency the other way would make the
two impossible to release one after the other.

## What `check-linkage` reads

The native binaries a package carries, each with `readelf -d -V -W`, against what the packages declare by hand:

```
porelo-release check-linkage --declare libc.so.6=GLIBC:2.34 --declare libz.so.1=ZLIB:1.2.2 \
    --ceiling GLIBC=2.41 apps/app/target/porelo daemon/target/porelod
```

jdeb and the rpm plugin do not read a binary to find what it links against, so the floor is written in the packages
and this holds it to the binaries. Refused, each named:

- **a library a binary needs that no `--declare` names**;
- **a symbol version above the declared floor**: installable, then not startable on a system at the floor;
- **a declared floor above its `--ceiling`**, the oldest system supported, so dropping one is a decision;
- **`readelf` output that names no library and is not a static binary's**, a `readelf` that failed.

A statically linked binary, `static-pie` included, needs nothing and passes.

**`--declared-only`** is for a package that derives its dependencies from its binaries, as one built with
`dpkg-shlibdeps` does: a library no `--declare` names is left to the package, and only the declared ones are held to
their floor and their ceiling. The interface's bundle links twenty libraries besides the C library; naming each by hand
would undo the derivation, and its C library's floor is still the one thing a newer build host can raise unnoticed.

## What `check-package-version` reads

The newest `.deb` and `.rpm` of a name in a directory, with `dpkg-deb -f` and `rpm -qp`:

```
porelo-release check-package-version porelo-build-github 0.4.2-SNAPSHOT 7 target
```

Both must carry the project's version as the packaging promises: `0.4.2-SNAPSHOT` built as run 7 is
`0.4.2~snapshot.7`, below its release and above the run before; a release is its own version. The rpm's release is
`1`, so the two say the same. Refused, each named: a deb of another name or version, and an rpm whose name, version
or release differs, such as the timestamp release the rpm plugin gives a snapshot unless told not to.

## What `third-party-notices` writes

A package with third-party code compiled into a native executable ships the licenses of that code. One file holds
them, written from the jars of the runtime classpath, the build's CycloneDX bill and the JDK the executable is built
with:

```
porelo-release third-party-notices target/classpath.txt target/bom.json "$JAVA_HOME" target/THIRD-PARTY-NOTICES \
    --own 'org\.fuin\.porelo:porelo-planner.*'
```

The classpath is what `dependency:build-classpath -DincludeScope=runtime` writes, after `package`, so the reactor's
modules are jars; `--own` leaves out the project's own modules by `groupId:artifactId`.

- **Each jar is one section, headed by its coordinates**: from the `pom.properties` its file is named after,
  `<artifact>-<version>.jar` or `<artifact>-<version>-<classifier>.jar` with a classifier that begins with a letter
  and is not `SNAPSHOT`; otherwise - a fat jar, or one Gradle built without, as Spring Boot's - from the bill entry
  that carries its SHA-256. A classifier heads the section too, as the bill's purl names it, so netty's
  `linux-x86_64` and `linux-aarch_64` jars are two sections, each held to its own hash.
- **A section holds the standard text of every license and exception the jar's bill entry names**, the effective
  POM's, a parent's included, and in full every file of the jar named for a license, notice or copying (`LICENSE.txt`,
  `NOTICE`, `LICENSES.txt`, `LICENSE-notice.md`, `FastDoubleParser-LICENSE`) and every file under a `license` or `licenses` directory,
  anywhere in the jar: that is where a jar names the licenses of what it embeds, such as jackson-core's
  FastDoubleParser or plexus-utils' Extreme! Lab code. A class, a source, a properties file or an image is never read.
  `A OR B` and `A AND B` give every text named, in any case; `A WITH exception` gives the license and the exception.
  The standard texts are pinned in `porelo-release`, taken unchanged from one tag of the SPDX license list, and
  GPL-3.0-or-later's from gnu.org's `gpl-3.0.txt`, as `licenses/SOURCE.txt` names; a test holds each to its SHA-256.
  A license the set lacks is added there.
- **The runtime is one more section**: the JDK's own license files and its whole `legal/` directory. Which of its
  modules an image reaches no build output says, and every one is never less than the image needs.
- **A text that stood once is named, not repeated**, and the same input gives the same bytes. The file is readable by
  everyone.

Refused, each named, and nothing written: a jar the bill has no entry for, one whose SHA-256 differs from the bill's,
one jar twice on the classpath, a bill naming one component twice differently, a classes directory instead of a jar,
a jar whose bill entry names no license, a license or an exception the pinned set lacks, or a license by its URL
alone, and a GPL-3.0 component that `porelo-stories` reaches in the bill's dependencies: the library is LGPL so that
others may link it.

A package ships the file as `/usr/share/doc/<package>/THIRD-PARTY-NOTICES`, named in the Debian `copyright` file, and
as `%license` in the rpm.


## Issues

```
porelo-release issues index issues
porelo-release issues check issues
porelo-release issues convert issues
```

An issue's data stands in a JSON head right below the heading of its file, checked by `issue-1.json`; the index,
each directory's `README.md` under `issues/`, is generated from the heads and never edited by hand. A file reads:

````markdown
# <ID> — <Title>

```json
{
  "$schema": "issue-1.json",
  "id": "<ID>",
  "title": "<Title>",
  "state": "Review",
  "size": "M",
  "priority": "Medium",
  "rank": 1,
  "blockedBy": [],
  "covers": "What the index row says it covers."
}
```

**What must be true.** ...
````

- **The fields:** `id` is the prefix and the number as the file name writes it, a sub-issue's `<parent>.<n>`;
  `title` is what the heading repeats; `state` a state of the repository's `workflow.yml`; `size` one of `XS`, `S`,
  `M`, `L`, `XL` or `-`; `priority` Jira's, `Highest` to `Lowest`; `rank` a whole number from 1, the order within one
  priority; `blockedBy` the ids of the same repository it waits on, and in porelo-project alone also another
  repository's as `<repository>#<id>`, such as `<repository>#<ID>`, which no check reads against local files;
  `covers` the index row's text. Optional are
  `parent`, which a sub-issue requires and which is its id before the dot, `role` (`builder`, `tester` or `reader`),
  `severity` (`Blocker`, `Critical`, `Major`, `Minor`, `Trivial`), `kind` (`Feature`, `Bug`, `Task`),
  `openQuestions`, a count, and `worker` (`Agent`, `Manual`), who works the issue, written last; the library reads
  an absent `worker` as null and leaves its meaning to the planner. Nothing else is allowed. The schema ships as
  `org/fuin/porelo/stories/schemas/issue-1.json`; the check is hand-written and held to the schema by a test.
- **`issues index`** writes each directory's index: the text above the first state's table as it was, then one table
  for each state in the workflow's order, its rows by priority, then rank, then id, and a group with nothing in it left
  out; a state's heading twice is refused, naming the second's line. An open state's table is `| # | Size | Blocked by | What it covers |`, with `Open questions` after it where a
  head of that state counts them; a wait is a link to its row, another repository's a link to its row in that
  repository's index, after the repository's name. A prose section between the tables, such as *What was
  decided along the way*, stays after the state it followed. The rows of a closed state stay as the file has them,
  since their files are gone; a head in a closed state adds its row there, in the form of the table's header (the open
  form where the table keeps it), and a kept row of an issue that has a head goes. The workflow is
  `issues/workflow.yml`, or in porelo-project `stories/workflow.yml`.
- **`issues check`** writes nothing and refuses, each by file: a file without a head, a fence right below the heading
  other than exactly ```` ```json ````, a head the schema or the workflow refuses, a size of `-` in a state that is
  measured or done, a heading other than `# <id> — <title>`, a file not named after its id, an id twice, a wait or a
  parent that is no issue of the repository (a file, or a closed state's row), a sub-issue's parent other than its id
  before the dot, a parent on an issue that is no sub-issue, an open state's row with no file, a closed state's row
  of an issue without a head whose cells do not fit its table's header, and an index other than the one
  `issues index` writes, naming its first differing line. A misfit row of an issue with a head is not refused: index writes
  it anew. A bar in a code span must be escaped as `\|`, or GitHub splits the cell. Outside porelo-project a wait on
  another repository's issue is refused.
- **`issues convert`** reads, for each file without a head, the first paragraph below its heading, its `**Status:**`,
  `**Size:**`, `**Blocked by:**`, `**Priority:**`, `**Role:**` and `**Parent:**` lines, and its index row. Where
  they agree on state, size and waits, it writes the head and drops those lines; a size's reason stays as the
  paragraph `**Why this size.**`. The priority is `Medium` without a line, the rank the row's place in its table, and
  `covers` the row's text. A closed row of the finished form (`| # | Title | What was built |`) gives no size, so the
  file's stands; a closed table kept in the open form is compared by its Size column and gives `covers` from its
  "What it covers". Where they do not agree, where a line says more than ids, links, punctuation, `none` or
  `-`, where the heading is not the one the head gives, or where measured work has no size, it names every
  difference and leaves that file as it was; exit 1. A second run finds nothing to do. Then `issues index` writes the
  index.

Exit 0 when nothing is refused, 1 when something is, 2 when a file cannot be read or written or the call is mistyped.

Readers of an issue take the head where a file has one and today's lines or row otherwise: `stage` its state and size,
the stats rule of `check-commits` its state and size, the stories library's local issue source the whole row, and
`check-citations` the waits and parent as data.

## Stories

```
porelo-release stories index stories
porelo-release stories check stories
porelo-release stories cut 0.6.0 stories --after 2026-11-01
```

porelo-project's `stories/` holds one file a story: a function of value to a user, the test the person runs at its
end, and every issue it needs. Work follows the stories' priority; a release is a cut the person calls, holding every
story tested since the last one. A story's file reads, top to bottom:

1. the heading `# <id> — <title>`;
2. the part `stories index` writes from the head: the priority with the state or the tested day, what a user gains,
   the test, and the issues, one a line, each linked to its row in its index with its state;
3. whatever a person writes by hand;
4. `## Data` and the head, fenced `json`, by `story-2.json`; legacy heads also read as `story-1.json`.

A story whose story-2 head states a state has that state, with issues or without. Where its issues give another state by
the rule below, `stories index` and `stories check` print one line on standard output and still succeed:
`WARN  ST<n>: stated Backlog, its issues give Accepted (<repository>#<issue> Accepted, ...)`. A story without a stated
state takes the least advanced open issue state in the order of `stories/workflow.yml`. When all issues are closed, it
takes the first closed state other than Rejected; if every issue is Rejected, it is Rejected. A stated completed story
needs its `tested` day; a computed completed story may still await that test. A tested day belongs only to a completed
story other than Rejected. Issue indexes are read from the checkouts beside the project's, else from the forge.

`stories index` writes every story's part 2 and `stories/README.md`: the count in each state, then the stories by state
in the states' sequence, the higher priority first within one, each with its issues counted by prefix. `stories check`
refuses, naming the file and the field: a head the schema refuses, a heading other than the head's, a file not named
after its id, an id twice, a stated completed story without a tested day or a tested day on an open or rejected story,
an issue no index holds, a repository `project.yml` does not name, and a story's part 2 or the index other than `stories index` writes them. `stories cut` writes, for a release's
notes, the stories tested after the day of the last release, the earliest first. `check-citations` skips the root's own
stories and their index as generated, a story being made of issues, and takes `ST<n>` elsewhere as a
story's number where `stories/` holds that story.

### Workflows

`stories/workflow.yml` in porelo-project defines the states of stories and issues, their board order, their
closed and measured flags, and their display phases. There is no default workflow. Missing files are refused by
path. A workflow is one YAML document with this shape:

```yaml
states:
  - {name: Idea, closed: false}
  - {name: Implementing, closed: false, measured: true}
  - {name: Finished, closed: true, measured: true}
  - {name: Rejected, closed: true}
phases:
  - {name: Complete, states: [Finished, Rejected]}
  - {name: Working, states: [Implementing]}
  - {name: Early, states: [Idea]}
```

YAML `#` comments are accepted, both on their own lines and after values. `states` is a nonempty list in board
order, earliest first. Each state requires `name` and boolean `closed`; omitted `measured` is false. Phase names and state names are unique within their lists and each is one nonempty line without
surrounding whitespace. Unknown keys, duplicate mapping keys, invalid types, unknown phase members, and a state
repeated within or across phases are refused with file and line. `phases` is a required list in display order. Colors follow phase order using
the existing palette. The stories index and generated issue-source headings use state order. The shared phases are Ideas, Analysis, Estimation, Backlog, Build, and Done, earliest
first. Reading and Reworking belong to Build; Finishing belongs to Done but remains open. The done count uses the
closed flag, rather than the phase name. An index heading or story state absent from the file is refused, naming
the workflow. Reading a handwritten issue index preserves its rows and metadata; it does not rewrite that file.

The project admin places and signs the workflow file once in each repository, as with `project.yml`.
Each repository keeps its own signed copy: `stories/workflow.yml` in porelo-project and `issues/workflow.yml`
elsewhere. Its checks read only that local copy and refuse a missing or invalid file; changes are placed and
signed by the project admin. The stories library validates supplied issue indexes against the stories'
workflow, and its local source validates each checkout against that checkout's own copy. The repository's
own `check-citations` reads its issue index through
`IssueIndexes.read` with this workflow. An ATX heading at any level from one to six is a state section when
an issue-table header (`| # | ...`) follows it before the next heading or blank-separated prose, or when its
text names a workflow state. Empty or unknown headings with issue tables are refused with their file, heading
line and workflow path, even if the tables have no issue rows. Tables without a state heading are refused at
the table header. Other headings are prose and are left alone; known states may have empty sections without
tables. An unknown state in `check-citations` is reported as a validation fault with its original file and line.

`Workflow.read(Path)` returns the immutable workflow or throws `IOException` for missing, unreadable or invalid
YAML. Its other methods are `states()`, `closed(String)`, `measured(String)`, `rejected(String)`, `phases()`, and `order(String)`;
`Workflow.Phase` exposes `name()` and `states()`. Unknown state queries throw `IllegalArgumentException` naming the
workflow; `order` is zero based. `rejected` identifies Rejected, the one closed state that is not done work.

A new head declares `"$schema": "story-2.json"`. It requires `state` when `issues` is empty and allows it beside
issues. Legacy heads without `$schema` use story-1. The index command upgrades each legacy head by inserting
`  "$schema": "story-2.json",` as the object's first property line and removing the `state` line only when the
story has issues. The other JSON bytes keep their indentation, colon spacing, key order, array layout, nulls,
and escaping; heads already in story-2 retain their bytes. When a legacy stated state differs from computation,
the operation returns one NOTE
naming the story and both states; the command prints it. A subsequent operation on the migrated head has no
legacy state to compare. No tested day is invented during migration.

### Stories library

`org.fuin.porelo:porelo-stories` exposes `org.fuin.porelo.stories.StoriesIndex.check(Path, IssueSource)` and
`write(Path, IssueSource)`. Both return `StoriesIndex.Result` with `faults()`, `written()` and `notes()`; a check writes nothing,
and a write checks every input before changing files. Symlinked story directories, stories and README.md are
refused before any writes. Changed files are published through temporary files in the same directory and atomic
moves; unchanged files are untouched and absent from `written()`. Unreadable inputs or failed writes throw `IOException`.
Invalid project configuration or unknown issue states throw `IllegalArgumentException`.
The generated bytes and validation messages are the same as the stories commands.

A program that reads a board without the index takes the index's own readers, so there is no second parser.
`Workflow.read(String text)` reads the text of `workflow.yml` as `Workflow.read(Path)` reads the file, and refuses it
with `IllegalArgumentException` in the same words, the file named `workflow.yml`. `Project.read(String text,
Set<String> ownPrefixes)` reads the text of `project.yml` into a `Project`: `own()`, the project's own repository,
and `repositories()`, every repository it names with its issue prefixes, the own one with the prefixes given, since
`project.yml` names none for it, and `centrals()`, each repository's central address by name in the same order, as
`project.yml` writes it under `central` or a not yet renamed `upstream`; `central(String name)` gives one and throws
`IllegalArgumentException` for a name the project does not hold. `Workflow.storyState(String stated, List<String>
issueStates)` is the rule the index gives a story its state by: the stated one (which may be null) without issues,
else the earliest open issue state in board order, else the first issue's state when all are rejected, else the
workflow's first closed state that is not rejected. `Story.read(String text, Workflow workflow)` reads a story's file into a `Story`:
`id()`, `title()`, `state()` (null where a story-2 head leaves it to its issues), `priority()`, `issues()` and
`body()`, the text between the heading and `## Data` with its leading and trailing blank lines dropped and every other
line kept as it stands, indentation included. Both refuse with `IllegalArgumentException` in the words the index
reports its faults with; a story's message is each fault without the file name, joined by `; `. `Project.read` throws
`NullPointerException` for null prefixes. The values are immutable, and their constructors refuse a missing field and
a project whose repositories do not hold its own or whose centrals name other repositories.

`IssueSource.index(String repository)` supplies an issue index as Markdown. `IssueSource.local(Path projectRoot)`
reads only the project's checkout and checkouts beside it, including linked indexes, and fails with the local path
when one is missing. Where an issue file of a directory has a head, it supplies that directory's index as the heads
give it, a file without a head keeping its row, and fails on a head the schema refuses. `IssueHead.read(String,
Workflow)` reads an issue file's head, null without one; `IssuesIndex.write`, `check` and `convert` take an issues
directory and return `IssuesIndex.Result` with `faults()`, `written()` and `notes()`, as the commands do. Callers may supply another source; the library opens no connection. `IssueIndexes.read(String
index, String repository, Workflow workflow)` reads the text into public `IssueIndex` records: number, state, size, title, index path,
source and blocked issue numbers. `location()` supplies the row's original `anchor()`, optional issue-file
link `file()`, one-based `line()`, and the character offset `numberEnd()` after its first-cell number and link.
The original seven-argument constructor remains available for rows constructed without a source location.
Location data is excluded from metadata JSON. Citation checks use this same parser for every issue
row, anchor and file link; prose examples supply none of them. A source can bundle pages by preceding each with
`<!-- porelo-issue-index {"path":"issues/base/README.md","source":"...","length":N} -->` and a newline. Replace `N`
with the integer number of UTF-16 code units in that page after normalizing CRLF to LF. The parser consumes exactly
that body before the next header; there is no unframed text between pages other than LF separators. JSON keys must
be unique and each header must contain exactly one JSON value. Paths must match
`^issues(/[A-Za-z0-9_-][A-Za-z0-9_.-]*)*/README\.md$`, without `.` or `..` segments. A source must reject marker lines
in raw pages before framing; the parser also rejects them inside page bodies, even in code fences. A plain index
without framing cannot contain marker lines. `source` is only descriptive text for diagnostics and is never opened
or used in a link.

The head schemas ship as `org/fuin/porelo/stories/schemas/story-1.json` and `story-2.json`; validation is
hand-written and checked against both schemas in tests. State names are checked against the workflow after the
structural schema check. The only deliberate differences from the reference validator
are refusal of all undeclared properties, including hash-prefixed ones, and whole-value pattern matching that refuses
trailing line terminators. Jackson's reachability metadata ships with the jar.
`./mvnw -B -o -q -s settings.xml -pl stories -am -Pnative verify` builds a test executable with Native Build Tools
0.11.1 and compares every generated story and the index with the JVM, byte for byte; the plugin must be cached first.
The Build workflow enables this profile.

## Release plans

`porelo-release` had a `plan` command that rendered and checked release plans. The release plans were dropped
for the stories, and the command was removed with them; `porelo-release plan` now answers as an unknown command.

The release module runs on the JVM, with no native image build or FFM check.
