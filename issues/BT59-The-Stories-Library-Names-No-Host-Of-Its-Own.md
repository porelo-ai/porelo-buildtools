# BT59 — The Stories Library Names No Host Of Its Own

```json
{
  "$schema": "issue-1.json",
  "id": "BT59",
  "title": "The Stories Library Names No Host Of Its Own",
  "state": "Backlog",
  "size": "-",
  "priority": "Medium",
  "rank": 4,
  "blockedBy": [],
  "covers": "The stories library takes its link pattern and the central's host from its caller, so a program that is not porelo-ai's own, stories-git among them, writes story texts and indexes naming its own host."
}
```

**What must be true.** A program that is not porelo-ai's own, the planning adapter stories-git among them, regenerates
a project's story texts and index with the stories library, and every link and repository it names comes from that
program. The caller passes the link pattern, with `{repository}` and `{path}`, and the central's host beside the issue
source; porelo-ai's values become arguments of the `porelo-release` command line.

Today the library holds porelo-ai's values itself:
- `Project` accepts only a central at `github.com/porelo-ai/...`;
- `IssueIndex` links to `https://github.com/porelo-ai/<repository>/blob/main/...`;
- `IssueIndexes` falls back to `https://raw.githubusercontent.com/porelo-ai/<repository>/...`;
- `StoriesIndex` takes only story files named `ST<n>.md`.

Until it changes, stories-git writes the stories index only for a planning repository that is a porelo-stories project.
Proposed by Agent Adapters; filed for ST294 on the coordinator's word.

## Acceptance

- A project whose central is on another host, with another link pattern, gets a stories index and issue links naming
  that host and pattern only; no `porelo-ai` appears in them.
- The library's sources name no `porelo-ai` and no `github.com`; the `porelo-release` command line passes them, and its
  output for porelo-ai's repositories is unchanged.
- A central that does not fit the given host is refused, naming the central and the host.
