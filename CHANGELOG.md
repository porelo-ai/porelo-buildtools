<!-- @formatter:off -->
<!-- noinspection -->
<!-- Prevents auto format, for JetBrains IDE File > Settings > Editor > Code Style (Formatter Tab) > Turn formatter on/off with markers in code comments  -->

<!-- This file is automatically generate by logchange tool 🌳 🪓 => 🪵 -->
<!-- Visit https://github.com/logchange/logchange and leave a star 🌟 -->
<!-- !!! ⚠️ DO NOT MODIFY THIS FILE, YOUR CHANGES WILL BE LOST ⚠️ !!! -->


[0.5.2] - 2026-11-12
--------------------

### Added (17 changes)

- `porelo-machines acceptance` takes the host's VM pool from `PORELO_MACHINES_POOL` when `--pool` is not given, as the host's forge tells its jobs, and `sweep --mine` there deletes nothing when no provider token is set. 
- porelo-release artifactory put, api and ping upload and call for a Publish job in place of each repository's artifactory.sh, the token from the job's environment to Artifactory alone, a 5xx or no answer asked again by the pinned rule, which moves into its own module, porelo-pinned-rule. 
- check-shared refuses a repository's .github/actions/pinned-jdk/pinned.sh that differs from porelo-buildtools' own, which porelo-release now carries, whether the call names the file or not. 
- An issue head may say how much the issue risks, `risk` of `harm`, `ordinary` or `small`: issue-1.json and the issues check take it, and `IssueHead` reads it and writes it last. 
- An issue head may say who works the issue, `"worker": "Agent"` or `"Manual"`: issue-1.json and the issues check take it, and `IssueHead` reads it and writes it last; its constructor without `worker` stays. 
- An issue's data stands in a JSON head right below its heading, checked by issue-1.json; porelo-release issues index writes each issue index from the heads, issues check refuses a faulty head or a hand-edited index, and issues convert writes the heads from today's lines where they agree with the index, while stage, check-commits, check-citations and the stories library read the head where a file has one. 
- `porelo-machines jdk` installs the pinned GraalVM on Windows x64 too, from the zip of the same release, checked against its own digest; the release tool's pin update moves both builds of a release together, or neither. 
- porelo-release prune-snapshots deletes snapshot package files older than four weeks from Artifactory's Debian and RPM repositories, keeping the newest three versions of each package name and never a file of the releases channel, after a probe shows the token can delete; the Prune snapshot packages workflow runs it by hand, listing only unless told to delete. 
- Record open and closed issue stages with session usage and per-stage sizes, calculate size medians and p80, and validate closed stats for nonexempt commits and active issue changes. 
- porelo-stories' `Project` gives each repository's central address as `project.yml` writes it, `centrals()` and `central(String)`, and `Workflow.storyState(String, List)` is the public rule the stories index gives a story its state by, so a program that links the library implements neither a second time. 
- porelo-release stories cut writes, for a release's notes, the stories tested after the day of the last release; releases are no longer planned, and the release plans stay as the record. 
- porelo-release stories index writes each story's readable part from its JSON head, its state as the head writes it and each issue linked to its index row, and stories/README.md listing the stories by state in the states' sequence, the higher priority first, each with Jira's priority icon and its issues counted by prefix; stories check refuses a story naming an issue no index holds, a story in Review while one of its issues stands earlier than Review, a Finished story without a tested day or a tested day on another, and a story or index that differs from what the heads give. 
- porelo-stories provides story checks and index generation as a library with caller-supplied issue indexes, a local source without network fallback, and native-image metadata. 
- porelo-stories reads a workflow, a project and a story from the text of their files through public readers, `Workflow.read(String)`, `Project.read(String, Set)` and `Story.read(String, Workflow)`, the very parsers the stories index uses, refusing in its words. 
- porelo-release stories check reads each story's JSON head at the end of its file in porelo-project's stories/ and refuses, by file and field, a head the story schema refuses, a heading that differs from its head, a file not named after its id and a repeated id. 
- `check-citations` takes `issues/<group>/<ID>.sub/<name>.md` as briefs of their issue, refusing one beside no issue file or of another name; `check-commits` asks no stats file for a brief. 
- porelo-release third-party-notices writes a package's notices from the jars compiled into its native executable, its CycloneDX bill and the JDK, each license in full, refusing a jar whose license it cannot tell. 

### Changed (22 changes)

- A rebuilt snapshot removes older ones of its system labelled for CI, keeping the one before it as a fallback, and says each. 
- The Build keeps one entry of each cache: the local Maven repository is saved by the build job alone with only what it read, and build.yml's `prune` job deletes the older entries of the Maven repository, the wrapper's Maven, shfmt and the GraalVM. 
- check-actions lets build.yml's `prune` job write `actions`, as it does the other workflows' `prune` jobs, so the Build can delete its older cache entries. 
- check-actions lets the `prune` job of site.yml, shared-rules.yml and workflows.yml write `actions`, so it can delete all but the newest entry of each cache; the job may hold only runner commands, with GH_TOKEN and KEEP as its environment. 
- `check-commits` gives no exemption from outside, reads every trailer for numbers and all but the sign-off for who, refuses `Co-authored-by:`, refuses a commit that deletes the seed or a stage record, and gives no answer when the log splits into another number of commits than git counts. 
- `check-snapshot-filter` also refuses a `pom.xml` that reads Sonatype's snapshots itself. 
- check-citations reads nothing outside its own repository - no other repository's index or project.yml, no checkout beside it, no --checkouts - and says in one NOTE line that citations of another repository's issue are left to a check across repositories; check-commits and check-pushes take only the repository's own issue prefixes, and a test fixture's issues lend it none 
- `third-party-notices` reads a bill's license named `Bouncy Castle Licence` with Bouncy Castle's own licence URL as MIT and gives it the MIT text; that name with another URL or none is refused as before. 
- `check-actions` refuses a workflow that builds a branch's commit on its push and its pull request both; the Build runs on `main`'s pushes and on pull requests. 
- porelo-machines jdk keeps the checked GraalVM archive in a cache by its digest (--cache), checks a cached one again before use, prints the pinned digest for a CI cache key (--print-sha256), and tries a download again for up to 15 minutes of growing pauses instead of two and a half. 
- porelo-machines jdk starts no download attempt later than 15 minutes after the first, the attempts' own time counted with the pauses, and says after how many attempts it gave up. 
- A pinned download from https://github.com goes with the job's token, to that origin alone and never along a redirect, and porelo-release's reader of GitHub's API asks again by the pinned rule on a 5xx or no answer. 
- `Project.read` takes any project's centrals: `https://`, `ssh://` and scp-like URLs on any host and under any owner, the name being the last part of the path without `.git`; anything else is refused naming the key and the value. 
- Snapshots come from Sonatype for the groups a repository lists in `.mvn/rrf/`, and on a forge from its registry too, the newest winning; `check-snapshot-filter` holds that shape. 
- On a forge snapshots are read with `PORELO_PACKAGES_READ_TOKEN`, only the deploying step holds the publisher's token, and a job there naming no `MAVEN_SNAPSHOTS_URL` or holding no read token fails first. 
- The stage check takes a person's move on the board, `actor: person` with `stage: move`, with no size needed and no minutes, and leaves it out of the median; `stage begin` still refuses a move. 
- check-commits asks for an issue's stats file only when a commit adds the issue in a measured state or changes its state or size, so moving an issue's lines into a head or editing its prose passes; a parent version that cannot be read is refused with the reason. 
- check-citations skips porelo-project's stories and their index as generated, a story being made of issues, and accepts a story's number ST<n> elsewhere where stories/ holds that story. 
- porelo-stories is published under LGPL-3.0-or-later, so a program under another license may link it; the rest of porelo-buildtools stays GPL-3.0-or-later. 
- Stories and issue indexes read their states and phases from each repository's required, locally signed workflow file, story states are computed from their issues with legacy heads migrated to story-2 without reformatting their other JSON, stage checks use measured states, issue citations and plans share one row parser. 
- A story's `issues` and an issue's `blockedBy` may name an issue of any repository, `<name>#<number>` with a name of `[a-z0-9][a-z0-9._-]{0,99}`, as in porelo-planner's stories format, so a project whose repositories are not named porelo-* has stories and waits the check takes. 
- porelo-release stories index and stories check keep a state stated in a story-2 head beside its issues, and warn where the issues give another state instead of refusing it. 

### Removed (1 change)

- `porelo-release plan` is removed with the release plans: `plan render` and `plan check` answer as an unknown command, and `check-citations` no longer skips a rendered plan page. 

### Fixed (10 changes)

- A leg on a tag run installs the released Porelo, `--channel` names the channel, and `acceptance --candidate` needs no `--package`. 
- check-commits takes the trailers Porelo writes on its own commits, `Porelo-Change-Id` (one token) and `Porelo-Signs-Anew` (a path inside the repository), so a Build that pushes such a commit is not red. 
- check-commits takes a trailer key such as `Co-Authored-By` in any case, as git does, instead of refusing the line as no trailer. 
- The deploy step refuses an empty or unknown channel, which skipped its guard on a release. 
- Outside porelo-project, an issues directory without its own `workflow.yml` names that missing file, not porelo-project's `stories/workflow.yml`; the shared-rules check runs `issues check issues`. 
- porelo-machines jdk tries the download of the pinned GraalVM again, five attempts with a growing wait, when the server answers a 5xx or the connection fails or breaks off; a 4xx or a wrong digest still fails at once. 
- porelo-release third-party-notices takes a component under LGPL-3.0-or-later, porelo-stories among them, and writes the LGPL-3.0 text with the GPL-3.0 text it builds on. 
- `third-party-notices` writes `THIRD-PARTY-NOTICES` on a file system without POSIX permissions, such as Windows' NTFS, instead of ending in `UnsupportedOperationException`; on Linux the file stays `rw-r--r--`. 
- A snapshot's proof build runs with swap and a larger heap for its native image, so the small machine a snapshot needs does not run out of memory; the swap is gone before the image is taken. 
- A release check that cannot reach an address names the address and the kind of failure instead of "null". 

### Security (1 change)

- The release tool no longer sends a forge's job token to GitHub's API; it sends `GITHUB_TOKEN` there only on GitHub or outside any job. 


[0.5.1] - 2026-10-29
--------------------

### Added (14 changes)

- porelo-release check-actions refuses an actions/checkout step that does not set persist-credentials false, naming the workflow and the step 
- porelo-release check-commits holds every commit of a range to one line of at most 72 characters with only trailers below it, no requirement number and nobody named, and on a pull request from outside to its author's sign-off 
- porelo-release check-providers refuses provider names and server types outside their implementation in git-tracked text 
- porelo-release check-pushes holds every push to main of a recent window to the shared rule on commit messages by the push's own range, read from GitHub's repository activity, and the shared-rules template's scheduled step runs it in place of a range by commit date 
- porelo-release check-shared compares a repository's .editorconfig, when named, with the shared code style it carries, and refuses one that differs or is missing 
- `porelo-machines acceptance --pool <file>` runs a leg in a run account of the host's local VM pool, as porelo-host's up writes it, instead of renting a server: one account per run, held by a file lock, the run key taken from `PORELO_POOL_SSH` or `--key <file>`, a run beyond the pool's size waits then is refused, and the account, giving itself back with the one root-owned command it may run, has its VM go back to its base snapshot before anyone gets it again 
- porelo-machines accepts machine kinds through a provider-neutral API and command line while preserving previous type-based calls and credential names 
- `check-pin` and `add-fetched-cli` take `--pin <name>` and pick the artifact whose digest that pin's `sha256` property holds, so a CLI downloaded as several artifacts is checked and recorded one artifact at a time 
- `plan check` and `plan render` read which issue waits for which from one `plan/after.yml` beside the release directories; the check refuses a wait without an index row, work started before what it waits for is in Review, a cycle and a wait on a later sprint or release, and reads the Blocked by column of each index as waits inside that repository, refusing one on an issue no release plans unless it is done, and a wait on a requirement a release covers; the page lists each waiting issue with the state of what it waits for 
- porelo-release proven-legs also takes a dispatched or scheduled run that made an artifact naming the tree, as an update run does for the commit it pushed 
- porelo-release proven-legs says whether a run of the same tree, from a push, a dispatch or a schedule, at most --max-age old, had every named job green, so a Build can take its legs from it 
- Release plan functions show covered parents outside progress bars and refuse parents that lag behind active parts 
- porelo-release plan render draws the HTML page from precompiled jte templates and writes a Markdown page built like it, with one progress image of each sprint's states and tested functions and each sprint in a box, marks the page as generated so check-citations skips it beside its plan, and plan check compares the image too 
- porelo-release plan render writes release plans as Markdown and self-contained HTML, and plan check refuses missing or duplicated issue references, stale pages, and untested progress claims 

### Changed (12 changes)

- porelo-release check-actions lets the job publish of release.yml write contents, so porelo-host's release can attach its tarball and digest, and holds that job to runner commands alone: no fetched action, image or shell template, only its listed env names, and expressions only in env values and if 
- `check-citations` refuses, outside the project's own repository, a citation under `issues/` of another repository's issue, as a link into its index or a bare number of its prefix, read from the project's `project.yml` and index; a repository whose `project.yml` names its project may still cite them 
- check-shared's .editorconfig gives Ansible's YAML in roles, playbooks, molecule, extensions and galaxy.yml two spaces, as that collection's own convention is 
- The pull-request step of the shared-rules template checks a pull request on the host's forge from a branch of the repository itself as one from inside, since that forge names no author association 
- porelo-release plan check lets work wait for an issue that is Implementing, Rework, Review or Finished, and refuses only a wait for one not yet begun or Rejected 
- `plan render` and `plan check` read a plan as `<release>/plan.yml`, and `plan render` writes its page as `README.md` and its progress image beside it, so a forge shows the page on opening the release's directory; a plan elsewhere is refused 
- Release plans distinguish nine state groups and name progress images by their content hash so previews refresh after rendering 
- porelo-machines creates every server with an IPv4 address and no IPv6 one, so a server holds one of the project's primary IPs instead of two 
- The code follows the shared code style, which validate checks - google-java-format in its AOSP style, shfmt for shell scripts, four spaces for YAML - and the reformatting commit is left out of git blame 
- porelo-machines labels a CI snapshot with what it is made from, and snapshot --unless-current builds none when the newest one carries the same inputs 
- porelo-release upstream-version takes the newest release at least porelo.release.min-age old, even where newer, younger ones exist, and names the newest as newest= 
- porelo-machines labels every server it creates porelo-expires with the end of its job's time (--for <minutes> on snapshot, acceptance and lease, 60 by default, plus 15), and sweep deletes only servers past it or without one, deletes those within it only with --including-in-use, and refuses --older-than 

### Fixed (10 changes)

- `add-component` records a runtime beside a package of the same name in another group, such as npm's `@types/node` beside Node, instead of refusing both as one thing 
- An agent leg hands the suite its OpenRouter key and model on the far shell's standard input, so neither is in that shell's arguments nor in the message a failed leg ends with 
- porelo-release check-actions reads a job's permissions in a workflow indented by four spaces, and a 'jobs:' that carries a comment, so a job granting a write the list does not name is refused there too 
- porelo-release check-deploy refuses an upload to a host whose name only ends in apache.org, excepting Apache's own domain alone 
- A key held in memory, and a machine's spec holding one, print as redacted rather than as the private key 
- porelo-ffm-check reads every record the tracing agent left under its output, not only the one whose path sorts last, so a downcall in any session that is not registered fails the check 
- Provision non-root pool leases with noninteractive sudo and copy the lease account’s authorized keys to acceptance accounts 
- porelo-machines sweep, and a run's clean-up, delete every other server when one cannot be deleted, then fail naming each one left 
- porelo-release update writes a pin's changelog line under Unreleased after a release, leaving the line the release shipped with as it was 
- porelo-release upstream-version counts a release dated by its day alone, as Node's index dates them, from the end of that day, so it is never taken before it is as old as the bound 

### Security (3 changes)

- porelo-release reads and writes bills with cyclonedx-core-java 11.0.1 and commons-lang3 3.18.0, past GHSA-6fhj-vr9j-g45r and GHSA-j288-q9x7-2f5v 
- porelo-release reads its YAML and XML through Jackson 2.22.3, past the advisories published against 2.21.2 and 2.22.1 
- porelo-machines creates a rented machine with a host key made for it and connects only when the machine shows that key, every caller of Ssh naming the host key it accepts; lease --write records it as hostkey=, and an agent leg hands it to its suite as porelo.acceptance.hostkey 


[0.5.0] - 2026-10-17
--------------------

### Added (5 changes)

- check-actions reads each workflow's permissions - read or nothing at its top, a write only in a job its list names, write-all nowhere 
- check-citations holds every issue number under issues/ to a link to its index row, every row to its anchor, and every such link to an anchor that is there, here or in another repository's index 
- check-linkage --declared-only holds only the declared libraries to their floor and ceiling, for a package that derives its dependencies from its binaries 
- porelo-release check-linkage holds native binaries to the C library and zlib their packages declare, and the declared floor to the oldest system supported 
- porelo-release check-package-version reads the built .deb and .rpm and holds both to the project's version as the packaging maps it 

### Changed (3 changes)

- An agent leg installs from Artifactory's porelo-dist-deb and porelo-dist-rpm and checks them with the key porelo-packages 
- The tools are Porelo's - org.fuin.porelo porelo-json, porelo-machines, porelo-release, porelo-ffm-check and porelo-cpu-check, on porelo-parent - and what they rent, label and install is named porelo 
- Every workflow run is titled by its workflow, its branch or tag and its commit's subject, and the workflows are named Shared rules check and Machine images 

### Fixed (1 change)

- A command run over ssh that writes much to standard error before it ends is read to its end instead of waiting for ever 


[0.4.4] - 2026-10-09
--------------------

### Changed (2 changes)

- machines waits up to 60 minutes at the project's server limit instead of 10, since acceptance legs run one after another 
- The Build rebuilds the machine images when their recipe changes, not only a pin, and when started on main by hand, to recover after a push whose snapshot failed 

### Fixed (1 change)

- The machine images install unzip, which the Maven wrapper needs to check its download against the pinned digest; without it the snapshot build failed 


[0.4.3] - 2026-10-08
--------------------

### Added (2 changes)

- release check-deploy reads a deploy dry run's log and refuses a module deployed without the publishing plugin, or an upload aimed off the machine 
- The build runs that deploy dry run on every push, so a tag's deploy repeats what main already did 

### Changed (1 change)

- machines Stopping and AgentLeg.quote are public, for the leg Porelo now runs from its own tree 

### Removed (2 changes)

- machines no longer has the leg and deploy commands; Porelo keeps them in its own tree, since they know its module layout 
- package-check is no longer published; Porelo checks its packages with a module of its own tree, which knows where its modules write them 

### Fixed (1 change)

- machines snapshot proves an image with the tree's own ci/leg-build.sh, and a tree from before it with the app where that tree keeps it 


[0.4.2] - 2026-10-08
--------------------

### Added (7 changes)

- release check-actions holds a Gradle Wrapper as it holds Maven's: a distributionUrl with a version and a distributionSha256Sum 
- release check-readmes refuses a Maven module without a README.md beside its pom.xml, and a README that does not link each of its submodules, walking the reactor from the root, profiles included 
- release check-releases refuses a release that builds or packages with a snapshot, reading what Maven resolved in the effective pom, a plugin's dependencies included 
- machines deploy --account installs the build readers a handover carries under builds/ into the account, beside the packages 
- release check-releases also reads a module's opt-in to Central where it sets it on the publishing plugins, as a BOM does 
- release check-releases refuses a module published to Central whose pom would still name its parent, since nothing flattens it or its flatten mode keeps the parent 
- A leg, a snapshot and a deploy build and install Porelo's stub build reader as the account's stub-forge when the tree has one, so the acceptance suite can follow builds without a forge 

### Changed (5 changes)

- A push that changes only documents - Markdown, mkdocs.yml, doc/ and issues/ - starts no build and leases no machine; the Shared rules workflow checks it 
- release check-releases accepts a published pom that has no parent, as the parent pom itself is 
- A release tag refuses every snapshot in the effective pom - a dependency, a plugin or a plugin's dependency - before it publishes, checked by the release tool that very tag builds 
- The NullAway compile comes from the parent pom, and exec-maven-plugin is 3.6.3 as it sets 
- The build takes a parent pom as its parent, with every version it builds with unchanged 

### Fixed (2 changes)

- machines runs with bcpkix and bcutil of bcprov's release line, and release with the Jackson dataformats of the Jackson it resolves, also where another repository resolves them 
- The build takes the parent pom 0.1.1, whose release profile a child inherits again, so the published modules reach Central 


[0.4.1] - 2026-10-06
--------------------

### Added (1 change)

- Check citations and documentation chapters: check-citations reads every committed text file and refuses a link to an issue's file, a pointer to an issue that is gone, a number among the issues that names none and an issue number anywhere else, with not-a-citation and citations-exempt.txt for text that holds the shape on purpose; check-doc-site refuses a page missing from the navigation or in it twice and a link a published page could not follow 

### Changed (1 change)

- The build checks this repository's own citations and documentation chapter on every push, beside its shared block 


[0.4.0] - 2026-10-05
--------------------

### Added (1 change)

- Initial public version 



