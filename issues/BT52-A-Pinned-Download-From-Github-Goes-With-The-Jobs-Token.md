# BT52 — A Pinned Download From github.com Goes With The Job's Token

**Status:** Review.
**Size:** S - the token in `PinnedJdk` and `pinned.sh`, the action's steps, the pinned rule in porelo-release's reader,
their tests, the doc and the text the copies take.

**What must be true.** On GitHub, a pinned download from `https://github.com` goes with the job's token, and the token
goes nowhere else. `check-pin` asks GitHub's API again by the pinned rule when it does not answer or answers a 5xx.

## Why

GitHub limits unauthenticated downloads by the runner's address, which every job on a shared runner spends. With the
job's token, a download counts against the job's own limit. The rule of [BT49](README.md#bt49) and
[BT51](README.md#bt51) waits out a GitHub that does not answer; the token keeps a download from being refused for the
address's limit in the first place. The release commands read GitHub's API once, and a single 5xx turned a check red.

## Shape

- **The token, to `https://github.com` alone:**
  - only when `GITHUB_SERVER_URL` is exactly `https://github.com`: on a host's forge the job's token is the forge's
    own;
  - only to the address asked for, when its scheme, host and port are `https://github.com`'s; a host that only
    begins like it, another of GitHub's hosts or plain http get none;
  - only a token of a token's form, `[A-Za-z0-9_]{20,255}`, so nothing else reaches a header;
  - never along a redirect, even one back to github.com: `PinnedJdk` follows redirects itself, at most 10 and never
    from https to anything else; `pinned.sh` asks the first address without following and fetches where it leads
    without the token;
  - never in a log, a message or an argument: `pinned.sh` hands it to curl on standard input (`-K -`), and
    `Credential` hides it in its text.
- **401 and 403 fail at once**, as every 4xx.
- **The action's steps** that download set
  `PORELO_PINNED_GITHUB_TOKEN: ${{ github.server_url == 'https://github.com' && github.token || '' }}`.
- **check-pin's reader** asks again on no answer or a 5xx (`Web.askedAgain`), by `PinnedRetry`'s pauses and
  deadline, each attempt said on standard error. A 4xx, a 404 among them, a refused certificate and a local file that
  cannot be read are answered at once. The other commands read once (`Web.overHttp`): a plan rendered or an image's
  upstream asked must not wait 15 minutes an address. The token to `https://api.github.com` stays as `githubHeaders`
  sends it, not on a host's forge.
- **A redirect** is named by the address asked for and the host it led to, never the signed address's query. One to
  no address, without a host or not over http(s) is refused; from https only to https, its scheme in any case.
- **The digest step** of the action and of the steps a repository copies says `::error::` when porelo-machines names no
  digest, where `set -e` ended it without a word.

## Reading

Read once by agy `gemini-4-argon-high` (800 s), with eight findings, all taken:
1. (grave) `grep -x` held the token line by line, so a second line reached curl's configuration. Now the whole value
   is held by a bash pattern; a test gives a token with a second line.
2. (grave) A failure after a redirect named the signed address, its signature included. Now it names the address
   asked for and the host it led to. A malformed `Location` and a scheme in capitals are handled.
3. and 4. (grave) Every release command read with 15 minutes of asking again: the image refresh's four pins and `plan`'s
   optional issue files would have waited past their jobs. Now only check-pin asks again.
5. (grave) Nothing tested that the download's client follows no redirect, nor the https guard. Both are tested.
6. Ten redirects were one too few. Now ten are followed and the eleventh refused.
7. The tokens refused by their form were also too short. Long ones are tested.
8. The deadline was never reached by the attempts' own time in a test. It is now.

Six mutations, one for each fix, each turn one test red.

## Tests

- `PinnedJdkTest`, against two local servers as two origins:
  - the token with the address asked for, not along a redirect back to its own origin, not to the signed address;
  - none to another port;
  - 401 and 403 refused, not asked again, the token not in the message;
  - ten redirects followed, the eleventh refused;
  - a redirect named by its host, never its signature; one to no address, to http from https, or to ftp refused;
  - the download's client follows no redirect itself;
  - `githubToken`: on github.com only, exactly `https://github.com`, of a token's form, however long;
  - `Credential.isFor`: github.com's download, not a look-alike host, not http, not api.github.com.
- `PinnedRetryTest` for `pinned.sh`, with a stand-in curl:
  - the token on curl's standard input for the github.com address, following nothing; the signed address without it;
  - none off github.com, on a host's forge, or of another form, a second line included;
  - a 403 with the token fails at once.
- `WebTest`: two 5xx then read, with the rule's pauses; eight 504s given up; no answer given up after eight; attempts
  of 200 s each given up after four at the deadline; a 403 and a 404 at once.

## Acceptance

- A repository's Build on GitHub fetches the GraalVM and shfmt with the job's token, and its log shows no token.
- A Build on a host's forge sends no token.
- `check-pin` survives a 5xx of GitHub's API.
