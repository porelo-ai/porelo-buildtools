# Fixture issue index

### Implementing

| # | Size | Blocked by | What it covers |
|---|---|---|---|
| <a id="f151"></a>[F151](F151-A-Stops-Survivors-Read-In-Either-Form.md) | XS | — | A stop's survivors read as a count or names; missing or invalid data is not reported. |
| <a id="f135"></a>[F135](F135-The-Interface-Brings-A-Host-Up-And-Down.md) | M | porelo-host [HO19](https://github.com/porelo-ai/porelo-host/blob/main/issues/README.md#ho19) | Putting a project on a server from the interface, answering its drive and vault stop, adding and removing its host machine. |
| <a id="f141"></a>[F141](F141-An-Agent-Signed-In-On-The-Host-At-The-Stop.md) | M | porelo-host [HO19](https://github.com/porelo-ai/porelo-host/blob/main/issues/README.md#ho19) | Agent sign-in at task start on a host machine; every SSH path uses the setup identity and the key pinned by `porelo-host`. |
| <a id="f140"></a>[F140](F140-The-Hosts-Files-Written-Into-The-Persons-Drive.md) | M | — | The interface writing the host's files, the machine file and the host's WireGuard key, into the project's tresor folder on the desktop, the person's own VPN key kept in the secret store. |
### Accepted

| # | Size | Blocked by | What it covers |
|---|---|---|---|
| <a id="f148"></a>[F148](F148-An-Agent-For-Each-Repository-At-One-Click.md) | — | — | At one click, a task for each of a project's repositories that names an agent, repositories left out and `issue` and `phase` given before the start, agents signed in on the way. |
### Finished

| # | Title | What was built |
|---|---|---|
| <a id="f131"></a>F131 | A Host Brought Up And Down By One Command | The pure-Dart package `packages/porelo_host/` and `porelo-host up` and `down`, which rent or find a host, run the collection's plays on it over ssh with its host key pinned, stop with 75 for the drive and the vault, resume with the answers on standard input, and give the host back after the down play. |
| <a id="f132"></a>F132 | A Host Rented From Scaleway Elastic Metal | `ScalewayElasticMetal` against Scaleway's Elastic Metal and IAM APIs, checking that the person's ssh key is registered before renting, passing a generated ed25519 host key through cloud-init and the server's tag, and finding or giving back a server by the project's tag alone. |
| <a id="f134"></a>F134 | The Host Command Built And Shipped For Linux And Windows | `porelo-host` compiled with the package's version and shipped at `/usr/lib/porelo-frontend/porelo-host` in the Linux packages and beside the interface in the Windows ZIP and MSIX, checked in every install proof. |
