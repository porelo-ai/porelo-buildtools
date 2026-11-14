# Issues

The open tasks of `porelo-buildtools`, grouped by state; the order within a group is the priority. The rules for an
issue, and who sets which state, are in [AGENTS.md](../AGENTS.md). *Open questions*, in *Analyzed* only, counts what
waits on the operator: the items under an issue's own *Open questions*. Its prefix is `BT`. A group with nothing in it is
left out.

## Idea

| # | Size | Blocked by | What it covers |
|---|---|---|---|
| <a id="bt50"></a>[BT50](BT50-A-Pin-Names-Fallback-Sources.md) | - | - | A pinned download names several sources, each checked against the same sha256, asked in order when the first cannot answer. |
| <a id="bt44"></a>[BT44](BT44-Old-Snapshot-Packages-Deleted-Daily.md) | M | - | A daily workflow deletes snapshot package files older than four weeks from `porelo-dist-deb` and `porelo-dist-rpm`, keeping the newest three versions of each package name and never touching `releases`; a dry run's list is read before the schedule goes live. |
| <a id="bt33"></a>[BT33](BT33-Machines-From-Providers-Behind-One-Interface.md) | - | - | One interface for asking for a machine, each source a provider behind it: clouds provided through OpenTofu, the host's VM pool and a fixed server lent; the providers per kind as one variable on both forges with different values. |
| <a id="bt18"></a>[BT18](BT18-A-Runner-Named-By-Its-Version.md) | - | - | `check-actions` refuses `runs-on: ubuntu-latest` and the other moving labels, naming file and job; a runner named by its version passes. |
| <a id="bt74"></a>[BT74](BT74-The-Citations-Check-Rules-Written-Down.md) | - | - | What `porelo-release check-citations` refuses today, rule by rule, with what it reads and what it skips, written down for ST321 (porelo-project) before anything of it is removed. |
| <a id="bt76"></a>[BT76](BT76-The-Musl-Installer-Names-Its-Pins.md) | XS | - | `machines musl --pins` prints the installer's two digests, `MUSL_SHA256=<hex>` and `ZLIB_SHA256=<hex>`, read from the installer it carries, so a workflow keys its cache by them without a second copy. |

## Accepted

| # | Size | Blocked by | What it covers |
|---|---|---|---|
| <a id="bt41"></a>[BT41](BT41-Mutation-Testing-With-PIT.md) | - | - | A `pit` profile run by hand, PIT and its JUnit 5 plugin pinned, on a VM where tests call real tools, and a report sorting survivors into real gaps and harmless ones. |
| <a id="bt30"></a>[BT30](BT30-Check-Actions-Reads-Workflows-As-YAML.md) | - | - | `check-actions` judges permissions, pins and `persist-credentials` on the parsed YAML, so a quoted or flow-style form meets the same refusals as the plain one. |
| <a id="bt09"></a>[BT09](BT09-A-Limit-Wait-Names-The-Limit.md) | - | - | The waiting line at `resource_limit_exceeded` says which limit the API names (dedicated vCPUs, shared vCPUs, primary IPs), not only the server limit. |

## Analyzed

| # | Size | Blocked by | What it covers |
|---|---|---|---|
| <a id="bt83"></a>[BT83](BT83-Providers-Named-Nowhere-Once-Every-Caller-Asks-By-Kind.md) | M | - | Once porelo asks for machines by kind, `check-providers` joins the shared rules of every repository, and `porelo-machines` drops the old call with a provider's server types and the `HETZNER_API` and `HETZNER_SSH` fallbacks (ST22). |

## Backlog

| # | Size | Blocked by | What it covers |
|---|---|---|---|
| <a id="bt48"></a>[BT48](BT48-Snapshots-Filtered-In-The-Agent-Repositories.md) | S | - | The five agent repositories read Sonatype's snapshots only for the groups in `.mvn/rrf/`, in the shape `check-snapshot-filter` holds, and their Build runs it; built by Agent Smith there. |
| <a id="bt37"></a>[BT37](BT37-The-Buildtools-Agent-Works-In-A-Porelo-Task.md) | S | - | porelo-buildtools' agent, `Agent Buildtools`, in a Porelo task on the move's host: first `machines.yml` and the scheduled `check-pushes` held to GitHub, then the trial move with Forgejo's build publishing `main`'s snapshots to its registry. |
| <a id="bt71"></a>[BT71](BT71-The-Work-Format-Handed-To-The-Planner.md) | M | - | Once porelo-planner publishes the work format and its checks, porelo-buildtools drops them: the porelo-stories module, the `stories`, `issues`, `stage`, `check-commits` and `check-pushes` commands with Stage, StageTokens, IssueLinks and Who, and `stats/`; every repository's workflows run the planner's native executable, downloaded from Artifactory pinned by version and sha256, instead. |

## Review

| # | Size | Blocked by | What it covers |
|---|---|---|---|
| <a id="bt73"></a>[BT73](BT73-A-Persons-Move-Passes-The-Stage-Check.md) | XS | - | `Stage.validate` takes a person's move on the board, `actor: person` with `stage: move`, `size` null or a size, `wall_min` and `vm_min` null, and the median leaves every move out; `stage begin` still refuses a move. |
| <a id="bt75"></a>[BT75](BT75-Who-Works-An-Issue-In-Its-Head.md) | XS | - | An issue head may say who works it, `"worker": "Agent"` or `"Manual"`: issue-1.json takes the field and refuses any other value, and IssueHead reads it and writes it last, after the optional fields it writes today. |
| <a id="bt77"></a>[BT77](BT77-How-Much-An-Issue-Risks-In-Its-Head.md) | XS | - | An issue head may say how much the issue risks, `"risk": "harm"`, `"ordinary"` or `"small"`: issue-1.json and the issues check take it and refuse any other value, and IssueHead reads it and writes it last, after `worker`. |
| <a id="bt57"></a>[BT57](BT57-The-Buildtools-Tests-Follow-The-Pyramid.md) | L | - | The buildtools' tests follow the pyramid (ST296): leases, snapshots and agent legs unit-tested behind seams, unit and integration run apart, no real network and no fixed sleeps in a test. |
| <a id="bt82"></a>[BT82](BT82-The-Commit-Check-Takes-Its-Range-From-The-Event.md) | M | - | The test pyramid (ST296) for the workflows' shared shell: `check-commits` decides a push's range and a pull request's origin from the event itself, and `check-workflows` checks that every workflow loads with a trigger and a job, each unit-tested, so a workflow only calls a command. |
| <a id="bt03"></a>[BT03](BT03-A-Rebuilt-Snapshot-Removes-Its-Predecessors.md) | S | - | A new CI snapshot, once available, deletes the older `porelo=ci` snapshots of its `os`, keeping one before it; nothing else is touched, each deletion said. |
| <a id="bt58"></a>[BT58](BT58-One-Build-Per-Commit.md) | S | - | A commit is built once: on a task branch by its pull request, on `main` by its push; the shared template says so and the shared check refuses a workflow that builds both. |
| <a id="bt87"></a>[BT87](BT87-The-Unit-Run-Under-Fifteen-Seconds.md) | S | - | `./mvnw test` takes under 15 s of test time, the last acceptance point the test pyramid's fixes did not reach: the slowest unit classes, which load a whole tree or a class graph per test, share it or shrink it. |
| <a id="bt64"></a>[BT64](BT64-Public-Readers-Of-A-Project-And-A-Story.md) | M | - | `porelo-stories` reads a project's repositories from the text of `project.yml` and a story from its file's text through public readers, the very code the stories index uses, so porelo-planner reads its board without a second parser. |
| <a id="bt88"></a>[BT88](BT88-The-Shell-Clock-Tested-Without-Sleeping.md) | S | - | `PinnedRetryTest.theShellsClockCountsUpWhateverTheCallerDoesWithSeconds` proves `pinned_now` without waiting 1.3 s of real time, so no test of the buildtools sleeps longer than 50 ms. |
| <a id="bt65"></a>[BT65](BT65-The-Public-Readers-Give-The-Centrals-And-A-Storys-State.md) | S | - | `porelo-stories`' public `Project` also gives each repository's central address from the same reading of `project.yml`, and the rule the stories index gives a story its state by is public, so porelo-planner clones every repository and shows the index's state without a second implementation. |
| <a id="bt45"></a>[BT45](BT45-Each-Issue-Counts-Its-Stages.md) | M | - | `porelo-release stage begin` and `end` record each stage of an issue with its time and tokens in `stats/<issue>.jsonl`, `check-commits` refuses an open stage, and `stage median` reads the estimate. |
| <a id="bt70"></a>[BT70](BT70-The-Release-Plan-Commands-Removed.md) | S | - | porelo-release has no `plan` command any more: Plan, PlanCheck, PlanRender, PlanStand and PlanTemplate, their tests, documentation and changelog entries are deleted, not moved. |
| <a id="bt72"></a>[BT72](BT72-The-Native-Image-Checks-Handed-To-Core.md) | S | - | Once porelo builds porelo-ffm-check and porelo-cpu-check itself, porelo-buildtools drops both modules; porelo-json stays here, where porelo-release and porelo-machines read it. |
| <a id="bt84"></a>[BT84](BT84-Acceptance-Takes-The-Pool-A-Job-Is-Told.md) | S | - | `porelo-machines acceptance` takes the host's VM pool from `PORELO_MACHINES_POOL` when `--pool` is not given, and `sweep --mine` passes where the job rented from the pool, so one workflow runs on the host's forge and on GitHub (porelo-host's HO27). |

## Finished

| # | Title | What was built |
|---|---|---|
| <a id="bt80"></a>BT80 | A Story Names Issues Of Any Repository | A story's `issues` and an issue's `blockedBy` name an issue of any repository, `<repository>#<id>`, by the planner's own pattern, and refuse a name no repository can have. |
| <a id="bt86"></a>BT86 | A Story Head May Name Its Size | A story head may name its `size`, one of XS, S, M, L and XL, and any other value is refused naming the field. |
| <a id="bt69"></a>BT69 | The Pinned GraalVM For Windows | `porelo-machines jdk` installs the pinned GraalVM on a Windows x64 runner from the zip the same pin names, checked by its digest before it is unpacked, and the pin's update writes both builds or neither. |
| <a id="bt85"></a>BT85 | The Third-Party Notices Written On Windows | `third-party-notices` writes its file on a file system without POSIX permissions too, as on Windows. |
| <a id="bt43"></a>BT43 | The JDK Download Tries Again | `porelo-machines jdk` tries the GraalVM download again on a 5xx or a failed or broken connection, and fails at once on a 4xx or a wrong digest. |
| <a id="bt47"></a>BT47 | Check Commits Takes Porelo's Own Trailers | `check-commits` takes Porelo's own trailers `Porelo-Change-Id` and `Porelo-Signs-Anew`, each only in its own form. |
| <a id="bt49"></a>BT49 | Pinned Downloads Cached And Tried Again By One Rule | Every pinned download of a Build is cached by its digest and checked again after a hit, and tried again by one rule in `PinnedRetry` and its shell twin `pinned.sh`. |
| <a id="bt51"></a>BT51 | The Pinned Rule Keeps A Deadline | A pinned download starts no attempt later than 15 minutes after its first, the attempts' own time counted, in Java and in `pinned.sh` alike. |
| <a id="bt52"></a>BT52 | A Pinned Download From github.com Goes With The Job's Token | A pinned download from github.com goes with the job's token to there alone, never along a redirect, and `check-pin` asks GitHub's API again by the pinned rule. |
| <a id="bt53"></a>BT53 | Check Shared Holds The Pinned Rule | `check-shared` refuses a repository's `pinned.sh` that differs from porelo-buildtools' own, named in the call or not, and a link in its place. |
| <a id="bt60"></a>BT60 | A Story Takes The State It Is Given | A story keeps the state its head states, and `stories index` and `stories check` warn where its issues give another. |
| <a id="bt78"></a>BT78 | Outside Downloads Cached, Newest Copy Kept | A Build keeps one cached copy of each download: the Maven repository saved with only what the build read, older cache keys pruned. |
| <a id="bt46"></a>BT46 | The Plan Pages Read Stories | A story's data stands in a JSON head at the end of its file, checked by its schema, and `porelo-release stories index`, `check` and `cut` write, hold and list porelo-project's stories from the heads. |
| <a id="bt54"></a>BT54 | The Stories Index As A Library | `porelo-stories` is a published library, native-image ready, that checks and writes the stories index and reads issue indexes byte for byte as `porelo-release stories` does, which calls it. |
| <a id="bt55"></a>BT55 | Stories Read Their Workflow From A File | The states, their board order and phases come from each repository's own signed `workflow.yml`, which stories, issue indexes, the plan and the stage rule read, and a story's state is computed from its issues. |
| <a id="bt61"></a>BT61 | The Stories Library Under The LGPL | `porelo-stories` is published under LGPL-3.0-or-later with its own license file, so a program under another license may link it. |
| <a id="bt62"></a>BT62 | An Issue's Data In A Head | An issue's data stands in a JSON head by `issue-1.json`, and `porelo-release issues index`, `check` and `convert` write, hold and convert the index from the heads. |
| <a id="bt63"></a>BT63 | Third-Party Notices From The Compiled-In Jars | `porelo-release third-party-notices` writes a package's notices from the jars compiled into it and its bill, with each jar's own texts, the pinned standard texts and the runtime's legal files. |
| <a id="bt66"></a>BT66 | The Notices Hold The LGPL-3.0 Text | `third-party-notices` takes a component under LGPL-3.0-or-later and writes the LGPL-3.0 and the GPL-3.0 text for it. |
| <a id="bt67"></a>BT67 | The Notices Read The Bouncy Castle Licence As MIT | `third-party-notices` reads the license named `Bouncy Castle Licence` with Bouncy Castle's URL as MIT and gives it the MIT text. |
| <a id="bt68"></a>BT68 | Project.read Takes Any Clone URL | `Project.read` takes an `https://`, `ssh://` or scp-like central on any host and under any owner, naming the repository by its path's last part, and refuses any other form. |
| <a id="bt79"></a>BT79 | The Commit Check Trusts No Commit's Own Word | `check-commits` gives no exemption by author or committer name to a pull request from outside, reads every trailer, refuses `Co-authored-by:` and a deleted stage record, and gives no answer on a log that does not split as `git rev-list` counts. |
| <a id="bt81"></a>BT81 | Every Repository's Issue Heads Checked Before A Push | `issues check issues` runs in this repository's daily and pull-request checks on the checked-out commit and in the prepush, naming a missing `issues/workflow.yml`. |
| <a id="bt89"></a>BT89 | The Citation Check And The Planner Read One Rule | `check-citations` refuses a settled issue's deleted file named in the index too and accepts a link to a story's index row, by the planner's own rule. |
| <a id="bt01"></a>BT01 | A Release Leg Installs The Release | A leg installs Porelo from the channel its run publishes to, `releases` on a tag and `snapshots` otherwise or as `--channel` names, and `acceptance --candidate` needs no package name. |
| <a id="bt24"></a>BT24 | An Account Rented From The Local Pool | `porelo-machines acceptance --pool` rents one run account of the host's VM pool per run from the pool file, waits or refuses when all are held, and has the account's VM reverted when it is given back, after a killed run too. |
| <a id="bt25"></a>BT25 | Machines Asked For By Kind | `porelo-machines` is asked for a machine by kind, system and account count, the setup choosing the local pool or a provider, the old call by server types routed onto it, and `check-providers` refuses a provider's name outside its implementation. |
| <a id="bt36"></a>BT36 | A Forge Pull Request From The Same Repository Counts As Inside | On a forge other than github.com, a pull request from a branch of the same repository is checked as from inside, with no sign-off asked, and the template carries it to every repository. |
| <a id="bt38"></a>BT38 | One Directory Per Release Plan | A release plan is `plan/<release>/plan.yml`, with its rendered page as `README.md` and its progress image beside it. |
| <a id="bt39"></a>BT39 | Snapshots Resolved With A Read-Only Token | Builds on a forge resolve snapshots with `PORELO_PACKAGES_READ_TOKEN`, only the deploying step holds the publisher's token, and a forge job without `MAVEN_SNAPSHOTS_URL` fails first. |
| <a id="bt40"></a>BT40 | Work May Wait On Work Begun | `plan check` lets work wait for an issue that is Implementing, Rework, Review or Finished, and refuses only a wait for one not yet begun or rejected. |
| <a id="bt42"></a>BT42 | Snapshots From Central And What The Host Built | A build on a host's forge reads snapshots from central and the host's registry, the newest winning, and asks central only about the groups the repository's filter file lists, which `check-snapshot-filter` holds. |
| <a id="bt23"></a>BT23 | One Upload Helper For Every Publish | `porelo-release artifactory` is the one Artifactory upload helper every Publish job takes, with the token from the environment and the pinned retry rule in its own module, `porelo-pinned-rule`, in place of each repository's own copy. |
| <a id="bt17"></a>BT17 | Commit Messages Checked Before And On A Push | `porelo-release check-commits` holds each commit of a range to the shared rule on commit messages; every repository runs it before a push, on its pushed range, on a pull request and on main's last day, from one template. |
| <a id="bt34"></a>BT34 | The Own Update Workflow's Pull Request Counts As Inside | A pull request the repository's own workflow opens as `github-actions[bot]` from a branch of the same repository is checked as from inside, with no sign-off asked. |
| <a id="bt35"></a>BT35 | Main Read By Its Pushes, Not Its Commit Dates | `porelo-release check-pushes` reads the day's pushes to `main` from the repository's activity and holds each by its own range, so an old commit in a push the Build skips is checked too; the scheduled step calls it, and each repository takes it from the template. |
| <a id="bt13"></a>BT13 | Action Moves Merged Without A Build | A push of only `.github/` starts no Build, and an action move is merged by hand on a light pull-request check that rents nothing and is not required; no auto-merge. |
| <a id="bt10"></a>BT10 | A Leg Takes No IPv6 Address | Every server the tools rent is created with an IPv4 address and no IPv6 one, so it holds one primary IP; measured on the project's primary IPs and green in every leg kind. |
| <a id="bt32"></a>BT32 | Snapshots On The Forge's Own Registry | The same workflows resolve and deploy snapshots on Sonatype on GitHub and on the forge's Maven registry on Forgejo, chosen by variables and secrets alone, with release tags published only to Sonatype. |
| <a id="bt28"></a>BT28 | A Release's Publish Job May Write Its Contents | `porelo-release check-actions` lets the job `publish` of `release.yml` write `contents`, so porelo-host's release can attach its tarball and digest, and holds that job to runner commands alone: no fetched action, image or shell template, only its listed env names, and expressions only in env values and if. |
| <a id="bt31"></a>BT31 | A Function Names The Parents It Covers | A plan function names under `covers:` the parent issues it covers with no work of their own; both pages show them as one line with their state, outside the sprint's bar and counts, and `plan check` refuses a parent missing from its index, named twice, or behind its parts. |
| <a id="bt29"></a>BT29 | The Plan Page Tells Ready From Undecided | Nine colour groups in the states' flow order, Backlog, Estimated and Idea each its own; the progress image named `progress-<8 hex of its SHA-256>.svg`, the release's older ones removed, so a preview and GitHub show the new one. |
| <a id="bt15"></a>BT15 | Legs Taken From A Proven Run | `porelo-release proven-legs` lets a Build on `main` take its legs from a run of this repository that proved the same tree, so a squash merge rents no machine twice. |
| <a id="bt04"></a>BT04 | Issue Numbers Link Their Index Row | `check-citations` holds every issue number in `issues/` to a link to its index row, in its own repository or another's, and refuses a link to a row that is not there. |
| <a id="bt22"></a>BT22 | A Pin With Several Artifacts | `check-pin` and `add-fetched-cli` take `--pin <name>` and check and record one artifact of a CLI downloaded as several by the digest its own pin holds. |
| <a id="bt21"></a>BT21 | Jackson Without Its Known Advisories | porelo-release reads its YAML and XML through Jackson 2.22.3, past the advisories published against the versions before. |
| <a id="bt20"></a>BT20 | Bills Read With A Current CycloneDX Library | porelo-release reads and writes bills with `cyclonedx-core-java` 11.0.1 and `commons-lang3` 3.18.0, past their known advisories. |
| <a id="bt16"></a>BT16 | A Snapshot Built Only From New Inputs | A push builds a system's snapshot only when the newest one's `inputs` label, a hash of what it is made from, differs; a dispatch always builds. |
| <a id="bt14"></a>BT14 | The Newest Release That Has Stood | A release tool takes the newest release at least `porelo.release.min-age` old, even where newer, younger ones exist, so a pin moves for an upstream that releases daily. |
| <a id="bt12"></a>BT12 | A Rented Machine Carries Its Expiry | Every lease labels its server `porelo-expires` from its job's time limit, and `sweep` deletes only servers past it or carrying none. |
| <a id="bt11"></a>BT11 | A Rented Machine Answers With Its Own Key | A rented machine gets a host key made per run in its `user_data`, pinned by every connection to it; a local VM's any key is an accepted risk. |
| <a id="bt27"></a>BT27 | The Release Plan Page Drawn From Templates | `plan render` draws the HTML page from precompiled jte templates and the Markdown page like it, with one self-contained progress image, sprints in boxes and the inputs' latest commit time as *Stand*; `plan check` compares both with a fresh render. |
| <a id="bt26"></a>BT26 | A Release Planned In Sprints Of Tested Functions | `porelo-release plan render` and `plan check` read a release's plan file of sprints and tested functions against every repository's issue index, write its page and refuse five faults, naming the issue or the function. |
| <a id="bt19"></a>BT19 | A Component Told Apart By Its Group | `add-component` matches a recorded component on group and name, so npm's `@types/node` and the Node runtime stand side by side in a tree's bill. |
| <a id="bt05"></a>BT05 | The Code Read Against The Skills It Names | Every module read against the seven skills `AGENTS.md` names, each finding fixed with a test seen red or declined, and its three questions decided. |
| <a id="bt02"></a>BT02 | Check Actions Reads Permissions | `check-actions` reads `permissions:`: one at the top of every workflow, read or nothing, `write` only in a job its list names, anything else refused by name. |
| <a id="bt07"></a>BT07 | Renamed Porelo Here | The tools are published as `org.fuin.porelo` `0.5.0`, and what they rent, label and install is named Porelo, the CI snapshots labelled `porelo=ci`; a documents test refuses the old word. |
| <a id="bt08"></a>BT08 | Parent Renamed Porelo Parent | `org.fuin.porelo:porelo-parent` `0.2.0`, naming the tools as `org.fuin.porelo` `0.5.0`. |

## Rejected

| # | Title | Why |
|---|---|---|
| <a id="bt59"></a>BT59 | The Stories Library Names No Host Of Its Own | Closed into [BT71](README.md#bt71) on the coordinator's word: the stories library leaves porelo-buildtools with [BT71](README.md#bt71), so its fixed hosts are a condition on the planner's replacement, not work here. |
| <a id="bt56"></a>BT56 | Sub-Agent Briefs Beside Their Issue | Rejected by the operator (2026-11-12): check-citations goes under ST321 with the work format's hand-over to the planner. |
| <a id="bt06"></a>BT06 | The Gathered Push Refuses A Moved Central Forge | Moved to porelo-host, which installs the command on the host, where there is no Java. |
