# BT85 — The Third-Party Notices Written On Windows

```json
{
  "$schema": "issue-1.json",
  "id": "BT85",
  "title": "The Third-Party Notices Written On Windows",
  "state": "Review",
  "size": "XS",
  "priority": "High",
  "rank": 1,
  "blockedBy": [],
  "covers": "`third-party-notices` writes `THIRD-PARTY-NOTICES` on a file system without POSIX permissions, such as Windows' NTFS, as it does on Linux, so the planner's Windows executable carries its notices."
}
```

**Why this size.** one guard in `ThirdPartyNotices.write` and its test.

**What must be true.** `third-party-notices` writes the file on Windows as on Linux. On Linux the file stays
`rw-r--r--`; where the file system has no POSIX view, the file keeps the access its directory gives.

**Why.** `ThirdPartyNotices.write` set `rw-r--r--` with `Files.setPosixFilePermissions` unguarded; on NTFS that throws
`UnsupportedOperationException`, so the run ended with a stack trace and no notices. porelo-planner's Windows package
(its PL29, under ST334 in porelo-project) needs the notices. Proposed by Agent Planner, filed on the coordinator's word.

## As built

- The permissions are set only where `Files.getFileAttributeView(temporary, PosixFileAttributeView.class)` is not
  null.
- Red first: `writesTheFileWhereTheFileSystemHasNoPosixPermissions` writes the notices into a ZIP file system, which
  offers no POSIX view, as NTFS offers none; before the guard it ended in `UnsupportedOperationException`.
- The Linux test still finds `rw-r--r--`.
