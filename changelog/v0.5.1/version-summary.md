<!-- @formatter:off -->
<!-- noinspection -->
<!-- Prevents auto format, for JetBrains IDE File > Settings > Editor > Code Style (Formatter Tab) > Turn formatter on/off with markers in code comments  -->

<!-- This file is automatically generate by logchange tool 🌳 🪓 => 🪵 -->
<!-- Visit https://github.com/logchange/logchange and leave a star 🌟 -->
<!-- !!! ⚠️ DO NOT MODIFY THIS FILE, YOUR CHANGES WILL BE LOST ⚠️ !!! -->


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


