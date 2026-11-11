# Fixture issue index

## Finished

| <a id="cc24"></a>[CC24](README.md#cc24) | Prove OAuth Isolation End To End | An acceptance scenario signs in with a subscription against a stand-in token endpoint and shows the refresh token never reaches the task: only the host renews, and the broker refuses a renewal from inside it. |
| <a id="cc30"></a>[CC30](README.md#cc30) | Switch Off The Agent's Own Memory In A Task | A task's Claude Code keeps no memory of its own: auto memory is off in the settings the adapter writes, so what holds across tasks is only the repository's `AGENTS.md`. |
