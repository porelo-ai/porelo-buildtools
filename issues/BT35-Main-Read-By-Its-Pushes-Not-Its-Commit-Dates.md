# BT35 — Main Read By Its Pushes, Not Its Commit Dates

**Status:** Idea.

**What must be true.** Every commit that reaches `main` is held to the shared rule on commit messages once, however long
before its push it was committed. Today the scheduled step *Main's commits of the last day* picks its range by committer
date (`git rev-list --before='26 hours ago'`), so a commit made more than 26 hours before a push the Build skips
(`[skip ci]`, documents only, `.github/` only) meets neither that step nor the push step, whose next range starts above
it.
