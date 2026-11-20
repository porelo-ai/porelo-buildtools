# BT92 — The Tools Recognize The Cut-Over

```json
{
  "$schema": "issue-1.json",
  "id": "BT92",
  "title": "The Tools Recognize The Cut-Over",
  "state": "Review",
  "size": "M",
  "priority": "High",
  "rank": 1,
  "blockedBy": [],
  "covers": "Once the project's `project.yml` names GitHub Issues as its tracker, `issues check`, `stories check` and `stage` pass on purpose with one line, and `check-commits` reads no `issues/` and tells an issue number by its shape; without a tracker every check stays as it is, and `check-citations` refuses a reference to a project's GitHub issue either way."
}
```

**Why this size.** One new reader, the tracker of the project, wired into four commands, `check-commits`' number rule
told by shape, and `check-citations`' number rule extended, each with its tests, the documents and a changelog entry:
M.

**What must be true.** Projects move from Markdown issues to GitHub Issues on one cut-over day, and on that day only
the project's `project.yml` changes. No repository's workflow, `issues/` or `stats/` has to change with it, and every
Build stays green; the owners drop the then idle steps later.

- **The tracker is the project's.** It stands in the project's `project.yml`, in the shape porelo reads: a top-level
  `tracker:` mapping, `kind: github` for GitHub Issues. Without `tracker` the issues are Markdown under each
  repository's `issues/`, as today. Another kind, or a `tracker` of another shape, is refused: this reads no other.
- **Where it is read.** The nearest `project.yml` above the directory a command is given, so porelo-project and a
  test's fixture each answer for themselves; where none stands above, porelo-project's `project.yml` on GitHub `main`,
  read without a token, as `stories check` already reads porelo-ai's repositories. A file that cannot be read stops the
  command unanswered (exit 2), never a guess either way; so does a `project.yml` that is no regular file, a dangling
  link among them, rather than being passed over for one further up.
- **With the tracker,** `issues check`, `stories check` and `stage` pass on purpose with one line naming the file
  that says so; `stage` writes nothing. `check-commits` and `check-pushes` read no stage record and no prefix from
  `issues/`: an issue number is told by its shape, an old id (`[A-Z]{1,2}\d{2,3}` but `S256`, as `check-citations`
  reads one) or a GitHub reference (`#<n>`, quoted or after a dash too, `porelo-ai/<repository>#<n>`, `GH-<n>`, the
  issue's address). That holds for every number: the repository's own, another repository's and a story's `ST<n>`,
  in porelo-project too. Every other message rule stays: the subject's length, the trailers, who, the sign-off from
  outside.
- **Either way,** `check-citations` refuses `porelo-ai/<repository>#<n>`, `GH-<n>` and the address of a porelo-ai
  issue in code and shipped text, the owner in any case, the address with or without its scheme and `www.`. A bare `#<n>` is not refused in a file: over today's
  repositories it hits CSS colors, stack frames, Markdown anchors and a filter's field paths, never an issue. An
  upstream project's issue address stays allowed. Measured over all repositories' committed files: these forms hit
  none today.
- **Who else calls these commands** keeps working unchanged until its owner drops the idle step: the step `issues
  check issues` in the shared-rules workflow of most repositories and in porelo-parent's `workflows.yml`, the tests in
  the agent adapters that hold that step, porelo-project's `stories-check.sh`, and porelo-planner's tests. A
  repository on porelo-release `0.5.2-SNAPSHOT` keeps today's checks on its unchanged `issues/`; it moves to the
  snapshot holding this before it deletes `issues/`, since 0.5.2's `check-commits` stops without it.

## Plan

- `release/src/main/java/org/fuin/porelo/release/Tracker.java`: reads `tracker.kind` from the nearest `project.yml`
  above a directory, else from porelo-project's on GitHub through `Web`; refuses another kind or shape, stops
  unanswered when the file cannot be read.
- `Main.java`: `issues check`, `stories check` and `stage` run through `unlessTracked`; `check-commits` and
  `check-pushes` read the tracker and hand it to `CheckCommits`.
- `CheckCommits.java`: a constructor taking `tracked`; tracked, no stage record and no prefix is read, and the number
  rule takes `CheckCitations.SHAPE`, `CheckCitations.GITHUB_REFERENCE` and a bare `#<n>`.
- `CheckCitations.java`: `GITHUB_REFERENCE`, matched in every file the number rule reads.
- Tests, red first: `check-commits` as a process in a repository without `issues/` whose `project.yml` names the
  tracker refuses `HO12` and passes a clean commit (today it stops unanswered); `issues check` and `stories check` on
  a broken issue pass on purpose under a tracked `project.yml`; `stage` writes nothing under the project's file read
  from GitHub; `TrackerTest` over the shapes, the nearest file, the fallback and an unreadable file;
  `check-citations` refuses the project's GitHub references and leaves an upstream one and a bare `#12` alone.
- `doc/index.md` and a changelog entry under *Added*.

## Acceptance

- The tests above pass, each seen red first.
- `./mvnw -Pnative verify` and `-Pdocuments test` pass.
- Measured over the last 300 commits of each of the 20 repositories, this build with the tracker named against the
  current version: what only the current version refuses is the stage rule; what only this build refuses is a
  number of another repository or a story in a subject, which the current version cannot see. That is the shared
  rule on commit messages, enforced from the cut-over on new commits only, since a push and the daily check read new
  commits alone.
- It is pushed and its snapshot published before the cut-over: without a tracker named nothing changes, and on the
  cut-over day only the project's `project.yml` changes.

## Declined from the code reading

- The lookup does not stop at the repository's root: the nearest `project.yml` above is the agreed contract, a
  fixture and porelo-project each answer for themselves by it, and a file above a checkout is placed by whoever owns
  the machine.
- A one-digit or four-digit id, or one of three capitals, is not told by its shape in a commit: the shape is the one
  `check-citations` reads, and every id the project has used fits it.
- A mapping without `project` is read as no tracker, as porelo reads it; the project file's own shape is porelo's
  check, not this one.
- `check-commits` is tested as a process in a repository without `issues/`, since it reads the working directory,
  which only a process of its own can set.
