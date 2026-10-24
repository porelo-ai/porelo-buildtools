# Build tools

The tooling every Porelo repository builds and tests with, published to Maven Central under `org.fuin.porelo`. None
of it ships to a person who runs Porelo: it rents the machines the acceptance tests run on, checks what a release
may publish, and checks the native images before they go out.

## The tools

| Artifact | What it does | Commands |
|---|---|---|
| `porelo-machines` | Rents test machines and the snapshots they boot from, runs the agent repositories' acceptance legs on them, and installs the pinned GraalVM and musl toolchain in a build. A leg whose build is cancelled ends its suite and gives its machines back at once. A snapshot proves itself by building `porelo` with the tree's own `ci/leg-build.sh`. Every server it rents carries the end of its job's time, and `sweep` deletes only servers past it or without one. | `sweep`, `snapshot`, `acceptance`, `lease`, `jdk`, `musl` |
| `porelo-release` | Checks that every workflow step is pinned by commit, that a repository's shared rules and its `.editorconfig` are copied byte for byte, that no page or source cites an issue in a way that goes stale, that every commit message of a range keeps the shared rule, that a release builds and packages with no snapshot, that a deploy dry run went through the publishing plugin in every module and sent nothing off the machine, that native binaries need no newer C library or zlib than their packages declare, that the built `.deb` and `.rpm` carry the project's version, that every page of a documentation chapter is in its navigation once and every link on it can be followed, that every Maven module has a README linking its submodules, and that an agent's pinned CLI, each artifact of it by its own pin, is the one its download names; records those artifacts in the package's bill; moves named pins. | `check-actions`, `check-shared`, `check-citations`, `check-commits`, `check-doc-site`, `check-readmes`, `check-releases`, `check-deploy`, `check-linkage`, `check-package-version`, `check-pin`, `add-fetched-cli`, `compare-bills`, `update`, … |
| `porelo-ffm-check` | Checks that every downcall of the Foreign Function & Memory API a test made is registered for the native image. | run by `porelo`'s build |
| `porelo-cpu-check` | Checks that a native image asks for no more than x86-64 v1, so it runs on any x86-64 CPU. | run by `porelo`'s build |
| `porelo-json` | The JSON reader and writer the tools above share. | - |

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
writes: an SVG image's path data, a lock file's hashes. Build output is not read.

- **Outside `issues/`**, no file names an issue number, and no link goes to an issue's file or a design document.
- **Among the issues**, a number of the repository's own prefixes - the prefixes its issue files carry - names an
  issue file or an index row that exists, and a number beside an `[index](…)` link into the repository names one too.
- **Every index row** under `issues/` opens with the anchor of its number, `<a id="<number in lower case>"></a>`.
- **Every issue number under `issues/`** is a link to its row, `[<NUMBER>](<index>#<number>)`, never to its file:
  the row outlives the file. Headings, code spans, fenced code and a row's own first cell are not read. A link reaches
  an anchor that is there - in this repository's index read from the tree, in another's read from
  `raw.githubusercontent.com` on `main`, or with `--checkouts <dir>` from `<dir>/<repository>/`. A number that no
  index read has a row for, an issue finished before rows were kept, is said apart: its text is rewritten, not linked.
  Which prefixes are issue numbers is read from the indexes, so `SHA256` or a guideline's `LLM03` is none.
- **An index that cannot be read** - a repository not published yet, or no network - is named in a `NOTE` line with
  the count of links into it that went unchecked, and does not fail the check.
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
  reads: those of the repository's own issue files and index rows, and of every other repository's index its issues
  link to. So `IPv4` or `x86_64` is none, and a prefix no index read names is not caught. An index that cannot be read
  is named in a `NOTE` line; a repository with no issue prefix at all is not checked but refused, exit 2.
- **Nobody named as having asked for or made the change.** A heuristic: a match is refused, and a pass proves nothing.
  It matches a party named - `operator`, `coordinator`, `Smith`, `Agent` and a capitalized name, an `@handle`, or the
  commit's own author or committer name - and a phrase that gives credit, taken whole: "at X's word", "as X chose",
  "asked by", "found by", "decided by", "reported by", "accepted by", "per" and a name, "X's review", "X's test". The
  lists are in `Who`. Trailers are not read for it, since they name a person on purpose.
- **With `--outside`**, for a pull request from outside, every commit also carries a `Signed-off-by:` naming its
  author's address, the Developer Certificate of Origin. Without it a sign-off is neither asked for nor refused.

Exempt are the commits authored by `dependabot[bot]` or `github-actions[bot]`, and those GitHub committed in its web
interface (committer `GitHub`), told by the author and committer the commit records, never by its message. Exit 0 when
no commit breaks the rule, an empty range among them; 1 when one does; 2 when git or the issues cannot be read, or the
range starts with `-`, since git would read it as an option.

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

A job that needs a write not in it is a change to the list, with its reason, in `porelo-buildtools`.

## What `check-actions` reads about checkouts

Every `actions/checkout` step sets `persist-credentials: false` under its own `with:`; one without it, or with
`true`, is refused, naming the workflow file, the line and the step. Otherwise the job's token stays in the
checkout's `.git/config`, where every later step can read it. No step is excepted: a step that pushes with git
hands the token to that one command from the environment, as `machines.yml` does, and a repository that cannot
names it in the channel.

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


## Release plans

```
porelo-release plan render plan/0.6.yml
porelo-release plan render plan/0.6.yml --html plan/0.6.html
porelo-release plan check plan/0.6.yml
porelo-release plan render plan/0.6.yml --indexes=beside
porelo-release plan check plan/0.6.yml --indexes=beside
```

A plan belongs below the project's `project.yml`. Repository names are the last component of each declared
`upstream`, with `.git` removed; each repository's `issues` lists its permitted prefixes. The project's own
upstream is included, with its prefixes read from its issue index. Both verbs read every index from `main` on
GitHub by default, including the project's own index, so the page reflects the central forge. With
`--indexes=beside`, indexes come from the owning tree or a checkout beside it, falling back to `main` when no
checkout exists. Linked subindexes and readable issue headings use the same source. Refusals identify the index
file or URL read; an unavailable index ends the command with exit 2.

```yaml
release: "0.6"
goal: "A release people can try"
sprints:
  - id: "1"
    title: "A usable function"
    goal: "A person can complete the first task"
    functions:
      - title: "The first task works"
        test: "Try the task and observe its result"
        tested: "2026-10-23"
        issues: []
        notes: ["An improvement still to file"]
```

`release`, `goal`, and `sprints` are required. Every sprint requires string `id`, `title`, `goal`, and `functions`;
its function titles are unique. Every function requires `title` and `test`; `tested` is an optional valid date
string in `yyyy-MM-dd` form, and `issues` and `notes` are optional lists of strings. Function issue references
use `<repository>#<number>`. A release-level `issue` is an unknown field. Unknown fields, repositories,
mismatched prefixes, duplicate sprint IDs or function titles, and invalid dates are refused with their location.

Rendering writes `<release>.md` beside the YAML file. Markdown follows the HTML page's section order: the
release heading and goal, italic Stand line, Sprint progress, the introduction, and Issues Details. The progress
section links one self-contained jte-generated image, `<release>/progress-<hash>.svg`, with the HTML legend's colored
boxes, one labeled bar and done count per sprint, and a tested or untested mark per function. Its alt text names
each sprint and lists each nonzero state separately in legend order. The nine groups, from left to right, are
Finished / Rejected, Review, Implementing / Rework, Backlog, Estimated, Analyzed, Accepted, Idea, and
Not filed yet / Missing. The hash is the first eight lowercase hex digits of the image bytes' SHA-256;
rendering removes older `progress-<hash>.svg` files and the legacy `progress.svg` in that release's directory,
leaving other names alone.

Details keep sprint and function order, each sprint framed as on the HTML page by a one-cell table that holds its
Markdown: a sprint heading and goal, a bold function title with ✅ Tested and its
date or ⚪ Not tested, test instructions, and one line of issue chips. Each linked chip uses the HTML chip's
text, index-row target, and tooltip as its link title, preceded by its state's colored square; the fixed emoji
mapping is documented in `PlanTemplate.Chip.emoji()`. Notes and missing rows use the unfiled square and their
HTML chip text without a link. Each sprint counts distinct issues by index state; notes never count as done.
A readable issue heading supplies the title; otherwise the index row supplies it. Finished and Rejected rows
retain their title after the issue file is gone.

The Markdown page starts with `<!-- Generated by porelo-release plan render; do not edit. -->`. `check-citations`
skips a Markdown file only when that exact comment is its first line and a plan of the same name beside it, a YAML
mapping naming its `release` and `sprints`, and counts it among the generated files. HTML uses precompiled jte
templates with contextual escaping and inlines its bars, which take the page's light or dark palette; the progress
SVG is the only image file written. SVG text is escaped by jte, Markdown alt text and link titles escape their
delimiters, and plan text escapes image starts (with any backslash before them) and backtick or tilde runs of three
or more, keeping inline code spans and emphasis. HTML and SVG hold no script and no external reference. Each bar
segment starts after the preceding counts and spans its share of the bar. The progress SVG writes the light palette's
colours into its own attributes, since a CSS variable does not reach the attributes of an SVG shown as an image, and
switches them in its own dark-mode style. Unchanged inputs give identical bytes.

The Stand line is the latest committer time of the plan and the issue metadata sources actually read: local
files and the plan use their own Git history. For forge sources, one request per repository reads the latest
commit on `main` touching `issues`, covering its indexes and issue files. A source without
commit metadata is named and excluded, with no fallback to file modification time or the render clock. An
unreadable or malformed commit response, including trailing tokens after its JSON value, fails the command.
The release module runs on the JVM, with no native image build or FFM check; jte's Maven plugin generates Java
templates that compile into its JAR. A plan contains exactly one YAML document. Index states come from Markdown
headings, fenced examples are ignored, and table headers identify the title column. Issue headings accept an em
dash, an en dash, or a hyphen between the number and title; subgroup index links may include a fragment.

The Markdown and SVG destinations must resolve inside the plan file's directory, including through symlinks.
Release names and sprint IDs must be safe file-name components. The HTML option may name a file elsewhere.
Every destination must differ from every local input read (the plan, `project.yml`, indexes, and issue files)
and every other destination, including aliases; all destinations are checked before any output is written.
Repeating an issue within one function is refused as a duplicate in that function.

`plan check` refuses these five faults, naming the issue or the sprint and function:

- An issue has no row in its repository's index.
- An issue belongs to more than one function.
- The Markdown page or its progress SVG is missing or differs from a fresh render, including after an index changes.
- An issue is Finished but its function has no `tested` date.
- A function has no issue, no note and no `tested` date.

Exit 0 means the command succeeded, 1 means the plan or page was refused, and 2 means the command was malformed or
its inputs or outputs could not be read or written. Checking never rewrites the page.
