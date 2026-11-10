# BT68 — Project.read Takes Any Clone URL

```json
{
  "$schema": "issue-1.json",
  "id": "BT68",
  "title": "Project.read Takes Any Clone URL",
  "state": "Backlog",
  "size": "S",
  "priority": "Medium",
  "rank": 3,
  "blockedBy": [],
  "covers": "`Project.read` in porelo-stories reads the project.yml of any project, wherever its repositories live: `https://`, `ssh://` and scp-like centrals on any host and under any owner, the name being the last part of the path without `.git`; anything else is refused naming the key and the value."
}
```

**Why this size.** one reading of a clone URL in `Project`, its refusals and their tests; no other reader changes.

**What must be true.** `Project.read` reads the project.yml of any project, wherever its repositories live: the
project's own `central` and every repository's `central` (or `upstream`) may be an `https://` URL, an `ssh://` URL or a
scp-like `user@host:path`, on any host and under any owner, and the repository's name is the last part of its path
without `.git`. A value that is no such URL (another scheme such as `file://` or `ext::`, a local path, a leading `-`,
user info with a password, a control character or white space, an empty last part, or a name that is no repository name
`[A-Za-z0-9][A-Za-z0-9_.-]*`) is refused, naming the key and the value as today. Two repositories whose names come out
the same are refused as today.

**Why.** A planner that serves any project's board reads its project.yml with this library. A project on its own Git
server, or under another GitHub owner, is refused today with "unsupported repository central", although the planner
checks each clone URL itself before Git sees it. Proposed by Agent Planner on 2026-11-10 and accepted by Agent
Coordinator.

## Plan

- **The reading:** `Project` reads the three URL forms and takes the name from the last part of the path; the refusals
  above name the key and the value.
- **Red first:** a project.yml whose centrals are `https://git.example.org/team/app.git`,
  `ssh://git@git.example.org:2222/team/lib.git` and `git@example.org:team/docs` reads with the names `app`, `lib` and
  `docs`; each refused form is refused with its key named.
- **Docs:** a changelog entry.

## Acceptance

- A project.yml with the three centrals above reads with the names `app`, `lib` and `docs`.
- Each refused form above is refused with its key named; the test for one of them seen red first.
- porelo-project's own project.yml reads as today, with the same names.
- `./mvnw verify` green.
