# porelo-buildtools

<img src="doc/images/early-bird.svg" width="350" alt="Early bird - work in progress">

> **Early bird - work in progress.** Porelo is not stable yet: until release 1.0.0, its code, commands
> and file formats can change without notice.

The build and test tooling shared by the Porelo repositories, published to Maven Central under
`org.fuin.porelo`.

**Documentation: [porelo-ai.github.io/buildtools](https://porelo-ai.github.io/buildtools/)**, one chapter of
[all of Porelo's documentation](https://porelo-ai.github.io).

| Artifact | Directory | What it does |
|---|---|---|
| `porelo-machines` | [`hetzner/`](hetzner/README.md) | Rents test machines, builds the snapshots they boot from, runs the acceptance legs on them, and installs the pinned GraalVM in CI. |
| `porelo-release` | [`release/`](release/README.md) | Decides what an agent package may publish without a person, moves named pins, and checks that every workflow step is pinned by commit. |
| `porelo-ffm-check` | [`ffm-check/`](ffm-check/README.md) | Checks that every FFM downcall a test makes is registered for the native image. |
| `porelo-cpu-check` | [`cpu-check/`](cpu-check/README.md) | Checks that a native image runs on any x86-64 CPU. |
| `porelo-json` | [`json/`](json/README.md) | The JSON reader and writer the tools above share. |

## Building

```
JAVA_HOME=~/.sdkman/candidates/java/25.3.4+1.r25-graalce ./mvnw -B clean verify
```

JDK 25; the pinned GraalVM is the one CI uses. The tools depend on nothing of porelo's; `porelo-json` is
their own JSON reader. See [AGENTS.md](AGENTS.md) for the conventions.

[Contributing](CONTRIBUTING.md), and [reporting a vulnerability](SECURITY.md).

## License

GPL-3.0-or-later, see [LICENSE](LICENSE).
