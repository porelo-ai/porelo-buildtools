# BT80 — A Story Names Issues Of Any Repository

```json
{
  "$schema": "issue-1.json",
  "id": "BT80",
  "title": "A Story Names Issues Of Any Repository",
  "state": "Review",
  "size": "XS",
  "priority": "High",
  "rank": 1,
  "blockedBy": [],
  "covers": "The stories format's issue reference, in story-1.json, story-2.json and StorySchema, and an issue's `blockedBy`, in issue-1.json and IssueSchema, take any repository name `[a-z0-9][a-z0-9._-]{0,99}` before `#`, as porelo-planner's do, so a project whose repositories are not named porelo-* has stories and waits the check takes."
}
```

**Size XS.** Two patterns in three schema files, `StorySchema` and `IssueSchema`, and their tests; part of ST314 (porelo-project).

**What must be true.** A story head whose `issues` names `foo-1#TP01` passes `stories check`, as it does in the planner;
a name no repository can have, such as `Other/Repository#CD02`, is still refused; Porelo's own stories still pass. An
issue head's `blockedBy` takes `foo-1#TP01` alike; only a project repository's issues may wait on another
repository's, as before.

## Plan

- `stories/.../schemas/story-1.json` and `story-2.json`: the `issues` items' pattern becomes
  `^[a-z0-9][a-z0-9._-]{0,99}#[A-Z]{1,3}[0-9]{2,5}$`, the planner's exactly.
- `StorySchema.ISSUE`: the same pattern.
- `stories/.../schemas/issue-1.json`'s `blockedBy` items and `IssueSchema.WAIT`:
  `^([a-z0-9][a-z0-9._-]{0,99}#)?[A-Z]{1,3}[0-9]{2,3}(\.[1-9][0-9]{0,2})?$`, the id part unchanged.
- `StorySchemaTest`: a head naming `foo-1#TP01` is taken and one naming `Other/Repository#CD02` refused, by the schema
  and by `StorySchema`, in both formats; `IssueSchemaTest` takes `foo-1#TP01` in `blockedBy` and refuses
  `Other/Repository#CD02`; each red first against today's pattern.

## Acceptance

- The new test goes red on today's pattern and green after; the module's suite, which reads the fixture copy of
  Porelo's stories, passes.
