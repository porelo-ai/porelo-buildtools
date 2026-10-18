# Contributing

Changes to porelo-buildtools follow the project's guide: how an issue is written, how a change is proposed and
reviewed, and what a commit carries. It is one page for every Porelo repository:
[Contributing to Porelo](https://porelo-ai.github.io/porelo-project/contributing/).

## In this repository

- **Building and testing:** as the README's [Building](README.md#building) section says. It needs JDK 25, the pinned
  GraalVM. `./mvnw -B verify` runs every module's tests, `./mvnw -B -Pdocuments test` checks the documents, and
  `./mvnw -B -N logchange:lint` checks the changelog entries.
- **A change to a tool is a change to every repository that uses it.** They take its published snapshot, so a change
  that alters a command or a class they call says which of them must move with it.
- **Renting test machines** needs a Hetzner token and the CI's ssh key, which only CI and the operator hold. Unit tests
  use a stand-in for the API and rent nothing.
- **Requirements** are in [issues/README.md](issues/README.md), and decisions in [doc/decisions.md](doc/decisions.md).
