# BT09 — A Limit Wait Names The Limit

```json
{
  "$schema": "issue-1.json",
  "id": "BT09",
  "title": "A Limit Wait Names The Limit",
  "state": "Accepted",
  "size": "-",
  "priority": "Medium",
  "rank": 4,
  "blockedBy": [],
  "covers": "The waiting line at `resource_limit_exceeded` says which limit the API names (dedicated vCPUs, shared vCPUs, primary IPs), not only the server limit."
}
```

**What must be true.** When the cloud provider refuses a server create with `resource_limit_exceeded`, the waiting
line says which limit the API names - its own `message`, such as dedicated vCPUs, shared vCPUs or primary IPs - and
not only "at the project's server limit". The line after the last attempt says it too.

**Why.** On 2026-10-17 a release's leg waited at a limit, and the log could not say whether it waited on vCPUs or on
primary IPs, so nobody could tell whether raising the vCPU limit would help.
