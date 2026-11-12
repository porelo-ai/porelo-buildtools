# BT80 — A Story Names Issues Of Any Repository

```json
{
  "$schema": "issue-1.json",
  "id": "BT80",
  "title": "A Story Names Issues Of Any Repository",
  "state": "Implementing",
  "size": "XS",
  "priority": "High",
  "rank": 1,
  "blockedBy": [],
  "covers": "The stories format's issue reference, in story-1.json, story-2.json and StorySchema, takes any repository name `[a-z0-9][a-z0-9._-]{0,99}` before `#`, as porelo-planner's does, so a project whose repositories are not named porelo-* has stories the check takes."
}
```

**Size XS.** One pattern in two schema files and `StorySchema`, and one test; part of ST314 (porelo-project).

**What must be true.** A story head whose `issues` names `foo-1#TP01` passes `stories check`, as it does in the planner;
a name no repository can have, such as `Other/Repository#CD02`, is still refused; Porelo's own stories still pass.

## Plan

- `stories/.../schemas/story-1.json` and `story-2.json`: the `issues` items' pattern becomes
  `^[a-z0-9][a-z0-9._-]{0,99}#[A-Z]{1,3}[0-9]{2,5}$`, the planner's exactly.
- `StorySchema.ISSUE`: the same pattern.
- `StorySchemaTest`: a head naming `foo-1#TP01` is taken and one naming `Other/Repository#CD02` refused, by the schema
  and by `StorySchema`, in both formats; red first against today's pattern.

## Acceptance

- The new test goes red on today's pattern and green after; the module's suite, which reads the fixture copy of
  Porelo's stories, passes.
