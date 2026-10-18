# BT14 — The Newest Release That Has Stood

**Status:** Review. Built 2026-10-18; the update runs after the push are its proof.
**Size:** S - one method per upstream source in `porelo-release`, the answer of `upstream-version` built on it, and
tests seen red first.

**What must be true.** A release tool that picks a version takes the newest release that is at least
`porelo.release.min-age` old, even where newer, younger ones exist. Prereleases stay out. A release public that long
without being withdrawn counts as standing.

## Why

Decided by the operator on 2026-10-18. Oh My Pi releases several times a day: 40 releases since 2026-09-28. So its
newest release was never three days old, and `upstream-version` answered `update=no` with a `waiting=` line every
time. The rule then was "never an older release instead of a newer one that is too young", and by it the pin stood at
18.4.1 for three weeks and would never have moved.

## As built

- `Release.Upstream.standing(web, channel, env, by)` answers the newest version published by a moment, with its
  date:
  - **`github-releases`** reads its list as before;
  - **`node-lts`** reads its index;
  - **`npm`** reads the document's `time`, up to the dist-tag's own version, so a tag never moves a pin past its line;
  - **`github-latest`** reads the list beside the newest release (`.../releases?per_page=100`), with drafts and
    pre-releases left out.
  - **A pointer and an image tag** list no history. They keep the rule as it was: the newest if it is old enough,
    and none otherwise.
- `UpstreamVersion.answer`: when the newest is too young, the standing version is the update if it is newer than the
  pin. It is answered as `upstream=`, with its `published=`, and the newer one as `newest=`. When nothing newer than
  the pin has stood long enough, the answer is `update=no` with `waiting=` for the newest, as before.
- `machines.yml`'s comment says the new rule. The agent repositories' `update` reads the same answer, so their
  `porelo.release.upstream` lines stay as they are.

## Acceptance

- [x] A young newest release over an older one old enough takes the older one: `github-latest`
  (`takesTheNewestReleaseOldEnoughWhenNewerOnesAreTooYoung`, with a prerelease left out) and `npm`
  (`takesTheNewestNpmVersionOldEnoughWhenTheDistTagIsTooYoung`). Both were seen red against the code before.
- [x] Nothing newer than the pin old enough waits, with `waiting=` for the newest
  (`waitsWhenNoReleaseNewerThanThePinIsOldEnough`).
- [ ] omp's next update run takes 18.10.4 or a newer release that has stood three days, and opens its pull request.
