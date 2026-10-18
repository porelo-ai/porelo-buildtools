# BT15 — Legs Taken From A Proven Run

**Status:** Implementing. The shared command is built; each repository's Build adopts it.
**Size:** M - one command in `porelo-release` with tests seen red, and a job in each Build that rents (porelo's legs,
the agents' acceptance, the interface's leased leg) that asks it before renting.

**What must be true.** A Build on `main` takes its legs from a run that already proved the same tree, instead of
renting machines for them again. Build, unit tests and Publish always run.

## Why

Decided by the operator on 2026-10-18. A squash merge of a branch that is up to date with `main` gives `main` the very
tree the branch's run, or an agent's update run, has just proved on rented machines. The Build of that merge then rents
the same machines a second time for nothing.

## The shape

- **`porelo-release proven-legs --repository OWNER/NAME --tree HASH --jobs "A,B;C" [--max-age 24h]`** answers
  `proven=yes` with `run=` and a line "legs taken from run <id>, tree <hash>, <age> old", or `proven=no` with why.
  It also appends the answer to `GITHUB_OUTPUT`.
- **A run counts** only when all of these hold:
  - it is in the same repository, and its head is in the same repository too, so a fork's run never counts;
  - it was started by a push, a dispatch or a schedule, never by a pull request;
  - its commit has the same tree (`head_commit.tree_id`), or, for a dispatched or scheduled run, it made an artifact
    named `porelo-verified-tree-<tree>`. An update run starts on `main` and verifies the pin commit it pushes
    afterwards, whose tree a squash merge of its request puts on `main`, so it names that tree this way. An
    artifact only the run itself can make; a pull request's text could be changed by anyone editing it;
  - it is no older than `--max-age`, because images, snapshots and downloaded programs move under an unchanged tree;
  - it is not the asking run itself;
  - every job of one of the named groups in it concluded `success`. A `,` joins jobs that must all be green, and a `;`
    separates groups, so an adapter can name its Build's legs and its update run's one job ("Follow upstream"),
    which fails on any red suite.
- **A tag always runs its own legs.** A question that cannot be answered is no proof, so the legs run then too.
- **Each Build** gets a cheap first job: `git rev-parse HEAD^{tree}`, then the command. The legs job runs only when the
  answer is `no`. Publish runs when the legs passed, or were taken from a proof.

## Acceptance

- [x] The command answers yes for a run of the same tree with every job of a group green. It answers no for these:
  another tree, a red leg, a run older than the bound, a pull request's or a fork's run (their jobs are never read),
  its own run, a tag, and an artifact naming another tree. It answers yes for an update run's one job, and for an
  update run by the tree its artifact names. `ProvenLegsTest`, all seen red first.
- [x] Asked against porelo itself on 2026-10-18: the tree of `c31a94bc` is proven by Build 39103478961, and an
  unknown tree is not.
- [ ] porelo's Build adopts it, and a push of an already proven tree shows "legs taken from run ..." with no machine
  rented, while Publish runs.
- [ ] The agent repositories and the interface adopt it (Smith, Frontend), each naming its own jobs.
