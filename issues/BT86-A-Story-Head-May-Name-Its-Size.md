# BT86 — A Story Head May Name Its Size

```json
{
  "$schema": "issue-1.json",
  "id": "BT86",
  "title": "A Story Head May Name Its Size",
  "state": "Review",
  "size": "XS",
  "priority": "Medium",
  "rank": 2,
  "blockedBy": [],
  "covers": "porelo-stories' story-2.json takes an optional `size`, one of XS, S, M, L and XL, as the planner's copy does under PL47, so a story estimated with a size passes `stories check` and `stories index` here too."
}
```

**Why this size.** one optional property in the schema, a test that a head with a size passes and one with another
word is refused, and a changelog line.

**What must be true.** A story head with `"size"` of XS, S, M, L or XL passes `stories check`; any other value is
refused naming the field; a head without it passes as today. No schema version changes: every head valid today stays
valid.

**Why.** The Agent Planner proposed the field for both copies of story-2.json (room, 2026-11-12 20:54Z); Machines
agreed. With `additionalProperties` false here, a story carrying a size would be refused until this copy takes it. It
leaves with the rest of the work format under [BT71](README.md#bt71).

## As built

story-2.json and the hand-written check take an optional `size` of XS, S, M, L or XL; StorySchemaTest proves each size
passes both and five other values are refused by both, and went red with the check's rule removed. `doc/index.md`
names the field.
