package org.fuin.porelo.release;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Guards the registry selection shared by both forges' workflows. */
@Tag("documents")
class WorkflowRegistryTest {

    private static final Path WORKFLOWS = Path.of("../.github/workflows");
    private static final ObjectMapper YAML =
            new ObjectMapper(
                    new YAMLFactory().enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION));

    @TempDir Path directory;

    // Prevents a deploy to the wrong registry through a hardcoded or interpolated profile.
    @Test
    void theDeployProfileComesFromTheForgeVariableThroughItsEnvironment() throws IOException {
        final JsonNode deploy = deployStep();
        assertThat(deploy.path("env").path("PORELO_DEPLOY_PROFILE").asText())
                .as("the forge selects the profile through the step environment")
                .isEqualTo("${{ vars.PORELO_DEPLOY_PROFILE }}");
        assertThat(deploy.path("env").path("PORELO_DEPLOY_CHANNEL").asText())
                .as("the release guard uses the channel checked from the tag and version")
                .isEqualTo("${{ steps.channel.outputs.channel }}");
        assertThat(deploy.path("run").asText())
                .as("only an allowlisted environment value reaches Maven as one profile")
                .contains("case \"$PORELO_DEPLOY_PROFILE\" in", "-P\"$PORELO_DEPLOY_PROFILE\"")
                .doesNotContain("${{");
    }

    // Prevents a deploy to the wrong registry, including a release sent to the forge.
    @ParameterizedTest
    @CsvSource({
        "central-sonatype-release, snapshots, true",
        "forge-release, snapshots, true",
        "central-sonatype-release, releases, true",
        "forge-release, releases, false",
        "unknown, snapshots, false",
        "unknown, releases, false",
        "'', snapshots, false",
        "'forge-release,central-sonatype-release', snapshots, false",
        "'forge-release -DaltDeploymentRepository=elsewhere', snapshots, false"
    })
    void onlyAllowedProfilesAndChannelsReachMaven(String profile, String channel, boolean allowed)
            throws IOException, InterruptedException {
        final Path arguments = directory.resolve("arguments");
        final Path output = directory.resolve("output");
        final Path maven = directory.resolve("mvnw");
        Files.writeString(maven, "#!/bin/sh\nprintf '%s\\n' \"$@\" > \"$DEPLOY_ARGUMENTS\"\n");
        Files.setPosixFilePermissions(maven, PosixFilePermissions.fromString("rwx------"));

        final ProcessBuilder command =
                new ProcessBuilder(
                                "bash",
                                "--noprofile",
                                "--norc",
                                "-e",
                                "-o",
                                "pipefail",
                                "-c",
                                deployStep().path("run").asText())
                        .directory(directory.toFile())
                        .redirectErrorStream(true)
                        .redirectOutput(output.toFile());
        command.environment().clear();
        command.environment().put("PATH", "/usr/bin:/bin");
        command.environment().put("PORELO_DEPLOY_PROFILE", profile);
        command.environment().put("PORELO_DEPLOY_CHANNEL", channel);
        command.environment().put("DEPLOY_ARGUMENTS", arguments.toString());
        final Process process = command.start();
        try {
            assertThat(process.waitFor(10, TimeUnit.SECONDS))
                    .as("deploy selection terminates for profile %s, channel %s", profile, channel)
                    .isTrue();
            final String log = Files.readString(output);
            if (allowed) {
                assertThat(process.exitValue()).as("allowed deploy: %s", log).isZero();
                assertThat(Files.readAllLines(arguments))
                        .as("Maven receives the selected profile and still signs with BouncyCastle")
                        .contains("deploy", "-P" + profile, "-Dgpg.signer=bc");
            } else {
                assertThat(process.exitValue()).as("refused deploy: %s", log).isNotZero();
                assertThat(log).as("the refusal is a visible workflow error").contains("::error::");
                assertThat(arguments).as("a refused deploy never invokes Maven").doesNotExist();
            }
        } finally {
            process.destroyForcibly();
        }
    }

    // Prevents a forge build from resolving its parent from Sonatype instead of the forge.
    @Test
    void everyMavenJobReceivesTheSnapshotRegistryAndItsCredentials() throws IOException {
        final List<String> checked = new ArrayList<>();
        try (Stream<Path> files = Files.list(WORKFLOWS)) {
            for (final Path file :
                    files.filter(WorkflowRegistryTest::isWorkflow).sorted().toList()) {
                final JsonNode jobs = YAML.readTree(file.toFile()).path("jobs");
                for (final var entry : jobs.properties()) {
                    final JsonNode job = entry.getValue();
                    boolean runsMaven = false;
                    for (final JsonNode step : job.path("steps")) {
                        runsMaven |=
                                step.path("run").asText().contains("./mvnw")
                                        || step.path("uses")
                                                .asText()
                                                .equals("./.github/actions/pinned-jdk");
                    }
                    if (runsMaven) {
                        final String name = file.getFileName() + ":" + entry.getKey();
                        checked.add(name);
                        assertThat(job.path("env").path("MAVEN_SNAPSHOTS_URL").asText())
                                .as("%s selects its snapshot registry at job level", name)
                                .isEqualTo("${{ vars.MAVEN_SNAPSHOTS_URL }}");
                        assertThat(job.path("env").path("PORELO_PUBLISHER_TOKEN").asText())
                                .as(
                                        "%s authenticates parent resolution and the pinned JDK"
                                                + " build",
                                        name)
                                .isEqualTo("${{ secrets.PORELO_PUBLISHER_TOKEN }}");
                    }
                }
            }
        }
        assertThat(checked)
                .as("the guard covers the build, publishing and maintenance Maven jobs")
                .contains(
                        "build.yml:build",
                        "build.yml:snapshots",
                        "build.yml:deploy",
                        "shared-rules.yml:check",
                        "workflows.yml:check",
                        "machines.yml:refresh");
    }

    private static boolean isWorkflow(Path path) {
        final String name = path.getFileName().toString();
        return name.endsWith(".yml") || name.endsWith(".yaml");
    }

    private static JsonNode deployStep() throws IOException {
        final JsonNode steps =
                YAML.readTree(WORKFLOWS.resolve("build.yml").toFile())
                        .path("jobs")
                        .path("deploy")
                        .path("steps");
        final List<JsonNode> deploys = new ArrayList<>();
        for (final JsonNode step : steps) {
            if (step.path("name").asText().equals("Deploy the jars")) {
                deploys.add(step);
            }
        }
        assertThat(deploys).as("the Publish job has exactly one jar deployment").hasSize(1);
        return deploys.getFirst();
    }
}
