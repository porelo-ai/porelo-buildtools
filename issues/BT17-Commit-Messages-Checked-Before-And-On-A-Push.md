# BT17 — Commit Messages Checked Before And On A Push

**Status:** Implementing. Built in porelo-buildtools; each repository's pre-push routine and Build take it.
**Size:** M - one check in `porelo-release` with its tests, the sign-off of an outside pull request among them (S on
its own), and then every repository takes it into its pre-push routine, its Build and its pull-request check, which is
what makes it M.

**What must be true.** A commit message that breaks the shared rule is found by a command before the push, and by the
Build after one. The rule: one line, at most 72 characters with any `[skip ci]`, saying in words what changed, never by
a requirement number, and never saying who asked for the change or made it.

## Why

On 2026-10-18 every agent measured its own commits on `main` since the day before. Each repository had broken the rule,
which has been in the Shared Area since 2026-10-13:
- subjects of up to 105 characters, often pushed over 72 by `[skip ci]`;
- issue numbers in an issue's state change ("Set X and Y Finished");
- bodies;
- "at the operator's word".

Rewording by hand before a push catches some of these. A check that runs every time catches all of them.

## The shape

- **`porelo-release check-commits <range>`**, in `release/`, beside `check-shared`. It reads `git log --format` over the
  range: an agent runs it over `origin/main..HEAD` before naming commits for a push, and the Build runs it over the
  pushed range, `github.event.before..github.sha`. It prints each breaking commit with what it breaks, and exits `1` on
  any.
- **What it holds each commit to:**
  - **one line,** and below a blank line only trailers (`Assisted-by:`, `Signed-off-by:`, and the `Co-authored-by:`
    GitHub writes): no prose body;
  - **at most 72 characters**, counting `[skip ci]`;
  - **no requirement number:** a word made of an issue prefix and digits (`B102`, `BT17`, `MX22`). The prefixes are
    read from the `issues/` indexes `check-citations` already reads. So `IPv4`, `UTF8` or `x86_64` are not caught, and a
    new repository's prefix is known once its index is published;
  - **no who:** see below;
  - **on a pull request from outside, its author's `Signed-off-by:`** on every commit, the Developer Certificate of
    Origin ([PJ27](https://github.com/porelo-ai/porelo-project/blob/main/issues/README.md#pj27)'s step 3, decided
    2026-10-18). An agent never writes one: the operator vouches for the project's own
    commits by pushing them, and each carries `Assisted-by: <tool>, <model>` instead. "From outside" is a pull request
    whose head repository is not the base, or whose author is not the operator; the bots' commits and GitHub's merges
    are exempt as below.
- **Exempt:** commits GitHub makes or Dependabot authors. Only their author and committer, as the commit records them,
  are taken as the sign: author `dependabot[bot]`, or committer `GitHub` with the commit made by a merge in the web
  interface. A subject saying it was merged is never trusted.
- **In the Build:** a step of the `build` job on a push to `main`, which needs no machine. On a pull request, the
  range is the request's own commits. A red step does not undo the push. It makes the next wave's commits repair what
  they can (a pushed commit stays, as the shared rule on rewriting says), and it shows at once who to ask.
- **Taken by every repository** with the same two lines, through the shared template; each agent names its commit.

**How "who asked or made it" is caught.** A word list alone is crude: "operator" also names an operator of a machine,
and "asked" is a plain verb. It matches two things, and a match is refused while a pass proves nothing:
1. **A party named:** the operator, any agent's name from the agents' list in the Shared Area ("Coordinator", "Smith",
   "Agent Core", ...), an `@handle`, or the name of the commit's own author or committer.
2. **A phrase that gives credit**, matched as a whole and not as single words: "at X's word", "as X chose", "asked by",
   "found by", "decided by", "reported by", "per X", "X's review", "X's test", "accepted by".

A message can still say who without either; that stays with the reading of each hand-over. The check makes the
common case impossible to miss and claims nothing more. Its tests hold both sides: each subject found on 2026-10-18
is refused ("the operator's hand merge" among them), and each clean subject from the same days passes.

## Not chosen

- **A GitHub ruleset's commit message pattern:** it can hold one regular expression on a push, but not the read
  prefixes, the exemptions or a message that says why. Whether the organisation's plan offers it was not checked.
- **A local `commit-msg` hook:** agents commit from several worktrees and machines, and a hook not installed checks
  nothing. The pre-push command is part of the hand-over instead, where the checks already are.

## Acceptance

- [ ] `check-commits` refuses each breaking subject measured on 2026-10-18 in porelo and porelo-buildtools, and the
  failing test is watched first. It passes their clean subjects and GitHub's and Dependabot's merges.
- [ ] Over `origin/main..HEAD` it runs as one command, and it exits `0` on a range with nothing in it.
- [ ] In this repository's Build, a pushed range with a breaking subject turns the step red, seen once on a branch.
- [ ] A pull request from outside whose commit lacks `Signed-off-by:` is refused, seen to pass against today's tooling
  first; one with it passes, and an agent's commit with `Assisted-by:` and no sign-off passes on a push.
- [ ] Every repository has taken it; the agents name their commits.
