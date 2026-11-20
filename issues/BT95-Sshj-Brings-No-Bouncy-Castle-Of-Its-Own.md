# BT95 — Sshj Brings No Bouncy Castle Of Its Own

```json
{
  "$schema": "issue-1.json",
  "id": "BT95",
  "title": "Sshj Brings No Bouncy Castle Of Its Own",
  "state": "Review",
  "size": "XS",
  "priority": "High",
  "rank": 1,
  "blockedBy": [],
  "covers": "porelo-machines excludes sshj's bcprov and bcpkix, so a build resolving its published pom never reads bcpkix 1.80 and the version range it declares for bcutil, and a missing metadata fetch for that range no longer fails a caller's Build."
}
```

**Why this size.** One dependency's exclusions in `machines/pom.xml` and one test reading the flattened pom: XS.

**What must be true.** A repository that builds with `porelo-machines`, such as one using `-Pci-tools`, resolves only
the Bouncy Castle release porelo-machines declares itself. Today sshj 0.40.0 brings `bcpkix-jdk18on:1.80`, whose pom
declares a version range for `bcutil-jdk18on`. Maven resolves that range from the repositories' metadata before
porelo-machines' own 1.85 wins, so a Build fails when that one metadata fetch fails, though the range's answer is
never used.

## Plan

- `machines/pom.xml`: on `com.hierynomus:sshj`, exclude `org.bouncycastle:bcprov-jdk18on` and
  `org.bouncycastle:bcpkix-jdk18on`; porelo-machines declares both, bcprov at compile and bcpkix at runtime, and they
  stay.
- Red first, `LibrarySetsTest`: the flattened pom, the one a caller resolves, carries both exclusions on sshj.
  The existing test that bcprov, bcpkix and bcutil load from one release stays and guards the runtime.

## Acceptance

- The test above, seen red first, and `./mvnw -Pnative verify` pass.
- `dependency:tree -Dverbose -Dincludes=org.bouncycastle` of `machines` shows no Bouncy Castle under sshj.
