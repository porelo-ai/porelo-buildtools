package org.fuin.porelo.release;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/** An agent repository on disk, shaped like the three that call these commands. */
final class AgentRepository {

    static final String OLD_DIGEST = "a".repeat(64);

    static final String CLAUDE_RELEASES = "https://downloads.example/claude-code-releases";

    static final String OMP_SUMS =
            "https://github.example/omp/releases/download/v{version}/SHA256SUMS.txt";

    static final String OMP_LATEST = "https://api.github.com/repos/omp/releases/latest";

    /** The list GitHub serves beside the newest release, which the age rule reads. */
    static final String OMP_RELEASES = "https://api.github.com/repos/omp/releases?per_page=100";

    static final String CODEX_SUMS =
            "https://github.example/codex/releases/download/rust-v{version}/SHA256SUMS.txt";

    static final String CODEX_LATEST = "https://api.github.com/repos/codex/releases/latest";

    /** Codex tags its releases {@code rust-v<version>}, so its pins follow the tagged releases. */
    static final String CODEX_RELEASES = "https://api.github.com/repos/codex/releases?per_page=100";

    static final String NPM_REGISTRY =
            "https://registry.npm.example/@earendil-works%2Fpi-coding-agent";

    final Path root;

    final String agent;

    final String definitionName;

    private AgentRepository(Path root, String agent, String definitionName) {
        this.root = root;
        this.agent = agent;
        this.definitionName = definitionName;
    }

    static AgentRepository claude(Path root, String pinned, String moduleVersion) {
        final Map<String, String> properties = new LinkedHashMap<>();
        properties.put("porelo.release.agent", "Claude Code");
        properties.put("porelo.release.definition", "src/main/resources/agent/claude.yaml");
        properties.put("porelo.release.upstream", "pointer " + CLAUDE_RELEASES);
        properties.put("porelo.release.channel", "stable");
        properties.put(
                "porelo.release.digest",
                "manifest " + CLAUDE_RELEASES + "/{version}/manifest.json linux-x64");
        final AgentRepository repository = new AgentRepository(root, "Claude Code", "claude.yaml");
        repository.write("pom.xml", pom("porelo-agent-claude", moduleVersion, pinned, properties));
        repository.write(
                "src/main/resources/agent/claude.yaml",
                source(CLAUDE_RELEASES + "/${agent.cli.version}/linux-x64/claude"));
        repository.write(
                "CHANGELOG.md", "# Changelog\n\n## [Unreleased]\n\n## [1.0.0] - 2026-09-01\n");
        return repository;
    }

    static AgentRepository omp(Path root, String pinned) {
        final Map<String, String> properties = new LinkedHashMap<>();
        properties.put("porelo.release.agent", "Oh My Pi");
        properties.put("porelo.release.definition", "src/main/resources/agent/omp.yaml");
        properties.put("porelo.release.upstream", "github-latest " + OMP_LATEST);
        properties.put("porelo.release.digest", "sums " + OMP_SUMS + " omp-linux-x64");
        final AgentRepository repository = new AgentRepository(root, "Oh My Pi", "omp.yaml");
        repository.write("pom.xml", pom("porelo-agent-omp", "1.0.0-SNAPSHOT", pinned, properties));
        repository.write(
                "src/main/resources/agent/omp.yaml",
                source(
                        "https://github.example/omp/releases/download/v${agent.cli.version}/omp-linux-x64"));
        repository.write("CHANGELOG.md", "# Changelog\n\n## [Unreleased]\n");
        return repository;
    }

    /**
     * An agent whose CLI is downloaded as two artifacts at one version, each a named pin with its
     * own digest property, as porelo-codex declares them.
     */
    static AgentRepository codex(Path root, String pinned, String cliDigest, String helperDigest) {
        return codex(root, pinned, cliDigest, helperDigest, true);
    }

    /**
     * The same, its pom declaring the agent's own release configuration or, without it, only the
     * two named pins, as porelo-codex's does.
     */
    static AgentRepository codex(
            Path root, String pinned, String cliDigest, String helperDigest, boolean release) {
        final Map<String, String> properties = new LinkedHashMap<>();
        if (release) {
            properties.put("porelo.release.agent", "Codex");
            properties.put("porelo.release.definition", "src/main/resources/agent/codex.yaml");
            properties.put("porelo.release.upstream", "github-latest " + CODEX_LATEST);
            properties.put("porelo.release.digest", "none");
        }
        properties.put("agent.cli.sha256", cliDigest);
        properties.put("agent.helper.sha256", helperDigest);
        for (final String[] pin :
                new String[][] {
                    {"codex", "codex-x86_64.tar.gz", "agent.cli.sha256"},
                    {"helper", "helper-x86_64.tar.gz", "agent.helper.sha256"}
                }) {
            final String prefix = "porelo.release.pin." + pin[0] + ".";
            properties.put(prefix + "property", "agent.cli.version");
            properties.put(prefix + "upstream", "github-releases " + CODEX_RELEASES + " rust-v");
            properties.put(prefix + "digest", "sums " + CODEX_SUMS + " " + pin[1]);
            properties.put(prefix + "sha256", pin[2]);
        }
        final AgentRepository repository = new AgentRepository(root, "Codex", "codex.yaml");
        repository.write(
                "pom.xml", pom("porelo-agent-codex", "1.0.0-SNAPSHOT", pinned, properties));
        repository.write("CHANGELOG.md", "# Changelog\n\n## [Unreleased]\n");
        return repository;
    }

    /** Writes a filtered definition pinning the codex fixture's two artifacts. */
    Path filteredTwo(String version, String cliDigest, String helperDigest) {
        final String base = "https://github.example/codex/releases/download/rust-v" + version + "/";
        return write(
                "target/classes/agent/" + definitionName,
                definition(version, base + "codex-x86_64.tar.gz", cliDigest)
                        + "    - url: "
                        + base
                        + "helper-x86_64.tar.gz\n"
                        + "      sha256: \""
                        + helperDigest
                        + "\"\n"
                        + "      target: /usr/local/bin/helper\n");
    }

    static AgentRepository pi(Path root, String pinned) {
        final Map<String, String> properties = new LinkedHashMap<>();
        properties.put("porelo.release.agent", "Pi");
        properties.put("porelo.release.definition", "src/main/resources/agent/pi.yaml");
        properties.put("porelo.release.upstream", "npm " + NPM_REGISTRY);
        properties.put("porelo.release.digest", "none");
        properties.put("porelo.release.npm.package", "@earendil-works/pi-coding-agent");
        properties.put("porelo.release.npm.image", "docker.io/library/node:22-slim");
        final AgentRepository repository = new AgentRepository(root, "Pi", "pi.yaml");
        repository.write("pom.xml", pom("porelo-agent-pi", "1.0.0-SNAPSHOT", pinned, properties));
        repository.write(
                "src/main/npm/package.json",
                "{\n  \"dependencies\": {\n    \"@earendil-works/pi-coding-agent\": \""
                        + pinned
                        + "\"\n  }\n}\n");
        repository.write("src/main/npm/package-lock.json", lockfile(pinned, 3));
        repository.write("CHANGELOG.md", "# Changelog\n\n## [Unreleased]\n");
        return repository;
    }

    static String lockfile(String version, int others) {
        final StringBuilder packages =
                new StringBuilder(
                        "\"\": {}, \"node_modules/@earendil-works/pi-coding-agent\": "
                                + "{\"version\": \""
                                + version
                                + "\"}");
        for (int i = 0; i < others; i++) {
            packages.append(", \"node_modules/dep-")
                    .append(i)
                    .append("\": {\"version\": \"1.0.")
                    .append(i)
                    .append("\"}");
        }
        return "{\"lockfileVersion\": 3, \"packages\": {" + packages + "}}";
    }

    /** Writes the filtered definition the build would have produced. */
    Path filtered(String version, String url, String digest) {
        return write("target/classes/agent/" + definitionName, definition(version, url, digest));
    }

    Path pom() {
        return root.resolve("pom.xml");
    }

    String read(String relative) {
        try {
            return Files.readString(root.resolve(relative));
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    Path write(String relative, String content) {
        try {
            final Path file = root.resolve(relative);
            Files.createDirectories(file.getParent());
            return Files.writeString(file, content);
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    /** Every file of the repository by path, to prove a refused update wrote nothing. */
    Map<String, String> snapshot() {
        final Map<String, String> files = new LinkedHashMap<>();
        try (var walk = Files.walk(root)) {
            for (final Path file : walk.filter(Files::isRegularFile).sorted().toList()) {
                files.put(root.relativize(file).toString(), Files.readString(file));
            }
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
        return files;
    }

    static String pom(
            String artifactId, String version, String pinned, Map<String, String> properties) {
        final StringBuilder declared = new StringBuilder();
        properties.forEach(
                (name, value) ->
                        declared.append("        <")
                                .append(name)
                                .append('>')
                                .append(value)
                                .append("</")
                                .append(name)
                                .append(">\n"));
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <project>
                    <modelVersion>4.0.0</modelVersion>
                    <parent>
                        <groupId>org.fuin.porelo</groupId>
                        <artifactId>porelo-agent-parent</artifactId>
                        <version>9.9.9</version>
                    </parent>
                    <artifactId>%s</artifactId>
                    <version>%s</version>
                    <properties>
                        <agent.cli.version>%s</agent.cli.version>
                %s    </properties>
                </project>
                """
                .formatted(artifactId, version, pinned, declared);
    }

    private static String source(String url) {
        return definition("${agent.cli.version}", url, OLD_DIGEST);
    }

    static String definition(String version, String url, String digest) {
        return """
                name: fake
                label: Fake Agent
                binary: fake-cli
                git_identity:
                  name: Fake
                  email: noreply@fake.invalid
                headless:
                  prompt_flag: "-p"
                install:
                  version: "%s"
                  artifacts:
                    - url: %s
                      sha256: "%s"
                      target: /usr/local/bin/fake-cli
                """
                .formatted(version, url, digest);
    }
}
