# BT32 — Snapshots On The Forge's Own Registry

**Status:** Implementing.
**Size:** S - build settings, four workflows and document guards in one module; no published tool changes.

**What must be true.** The same workflows run on GitHub and the local Forgejo, with only variables and secrets
selecting where snapshots are resolved and deployed: Sonatype on GitHub, the forge's Maven registry on Forgejo.
Release tags publish only to Sonatype, as required by porelo-project
[PJ60](https://github.com/porelo-ai/porelo-project/blob/main/issues/README.md#pj60), Sprint 4.

## Plan

- Add `WorkflowRegistryTest` and `RegistrySettingsTest` beside `WorkflowSigningTest` in `release`, tagged
  `documents`, before changing the configuration. The existing configuration must fail the environment,
  profile-activation and deployment-refusal guards; deployment runs against a temporary Maven stub.
- Give `settings.xml` mutually exclusive `standard` and `forge` profiles selected by `env.MAVEN_SNAPSHOTS_URL`,
  and the `porelo-forge` server using `env.PORELO_PUBLISHER_TOKEN`.
- Give every Maven job and every `pinned-jdk` caller in `.github/workflows/` the registry URL and token at job
  level. In `build.yml`, pass `vars.PORELO_DEPLOY_PROFILE` through the deployment step's environment, allow only
  `central-sonatype-release` and `forge-release`, and refuse the latter on the releases channel. Keep the
  Sonatype dry run and signing configuration.
- Use the `forge-release` profile supplied by `porelo-parent` `0.2.1-SNAPSHOT`; it deploys to
  `env.MAVEN_SNAPSHOTS_URL` under server id `porelo-forge`.

## Acceptance

- Without `MAVEN_SNAPSHOTS_URL`, only the Sonatype snapshot profile is active; with it, only the forge profile
  is active, using the supplied URL for both repositories and plugin repositories, snapshots only and always
  updated. The forge server takes its publisher token from the environment.
- All six Maven jobs, including their `pinned-jdk` bootstrap, inherit the URL and token from forge configuration.
- Both allowed profiles reach Maven for snapshots; only `central-sonatype-release` reaches Maven for releases.
  Empty, unknown and combined profile values fail with `::error::` before Maven runs.
- The dry run still selects `central-sonatype-release`; `CheckDeploy` and `WorkflowSigningTest` remain valid.
- No workflow branches on the forge, and no changelog entry is added for these CI and settings changes.

## To be checked

- Run the new guards against the previous configuration to observe red, then against the changes to observe green.
  Run the full build workflow checks and the documents profile; Maven builds are left to the build environment.
- Verify snapshot resolution and publication on both forges, including the registry's exact published version.
