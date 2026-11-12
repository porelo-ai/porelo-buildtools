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


