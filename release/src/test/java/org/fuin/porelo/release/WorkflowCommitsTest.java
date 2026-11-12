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
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The Build checks the commit messages of every push, so a direct push to {@code main} cannot pass
 * the rule around the pull request's check.
 */
@Tag("documents")
@Tag("integration")
class WorkflowCommitsTest {

    private static final Path BUILD = Path.of("../.github/workflows/build.yml");
    private static final Path SHARED_RULES = Path.of("../.github/workflows/shared-rules.yml");
    private static final ObjectMapper YAML =
            new ObjectMapper(
                    new YAMLFactory().enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION));
    private static final String STEP = "The pushed commits' messages";
    private static final String CLASSPATH = "/stub/a.jar:/stub/b.jar";

    /** The host's forge as its runner names it. */
    private static final String FORGE = "https://192.0.2.1:3000";

    @TempDir Path directory;

    // Prevents the check from being skipped on a push, or fed a range a workflow expression or the
    // step's own shell built: the command reads the event itself.
    @Test
    void theBuildChecksThePushFromItsEvent() throws IOException, InterruptedException {
        final JsonNode step = step();
        assertThat(step.path("if").asText())
                .as("the step runs on a push")
                .isEqualTo("github.event_name == 'push'");
        assertThat(step.path("env").isMissingNode())
                .as("nothing of the event reaches the step but the runner's own variables")
                .isTrue();
        assertThat(step.path("run").asText())
                .as("the script holds no expression")
                .doesNotContain("${{");

        final Run passed = execute(step, 0);
        assertThat(passed.exit).as("a clean push: %s", passed.log).isZero();
        assertThat(passed.java)
                .as("check-commits reads the event, with release and its classpath")
                .containsExactly(
                        "-cp",
                        "release/target/classes:" + CLASSPATH,
                        "org.fuin.porelo.release.Main",
                        "check-commits",
                        "--event");
        assertThat(passed.maven)
                .as(
                        "release is installed and its classpath read, each with the repository's settings")
                .containsExactly(
                        "-B -q -s settings.xml -pl release -am install -DskipTests",
                        "-B -q -s settings.xml -pl release dependency:build-classpath"
                                + " -Dmdep.outputFile=target/commits-cp.txt");
        assertThat(execute(step, 1).exit).as("a refused push fails the step").isNotZero();
        assertThat(execute(step, 2).exit)
                .as("an event that could not be read fails the step")
                .isNotZero();
    }

    // Prevents a range the checkout cannot read, and a step that runs before release is compiled.
    @Test
    void theBuildJobChecksOutTheWholeHistoryAndChecksAfterItsBuild() throws IOException {
        final List<String> names = new ArrayList<>();
        JsonNode checkout = null;
        for (final JsonNode candidate : buildJob().path("steps")) {
            if (candidate.path("uses").asText().startsWith("actions/checkout@")) {
                assertThat(checkout).as("the build job checks out once").isNull();
                checkout = candidate;
                names.add("checkout");
            } else {
                names.add(candidate.path("name").asText());
            }
        }
        assertThat(checkout).as("the build job checks out the repository").isNotNull();
        final JsonNode depth = checkout.path("with").path("fetch-depth");
        assertThat(depth.isInt() && depth.intValue() == 0)
                .as("the checkout holds every commit of a pushed range, fetch-depth %s", depth)
                .isTrue();
        assertThat(names.indexOf(STEP))
                .as("the check runs after the checkout and after the build that compiles release")
                .isGreaterThan(names.indexOf("Build and unit tests"))
                .isGreaterThan(names.indexOf("checkout"));
        assertThat(names.indexOf("Build and unit tests"))
                .as("the build job builds")
                .isNotNegative();
    }

    // Prevents a push the Build never starts from going unchecked: the schedule reads each push of
    // main's last day by its own range, however long before it its commits were made.
    @Test
    void theScheduleChecksMainsPushesOfTheLastDay() throws IOException, InterruptedException {
        final JsonNode step = scheduledStep();
        assertThat(step.path("if").asText())
                .as("the step runs on the schedule")
                .isEqualTo("github.event_name == 'schedule'");
        assertThat(step.path("env").path("GITHUB_TOKEN").asText())
                .as("the run's own token reads the repository's activity")
                .isEqualTo("${{ github.token }}");
        assertThat(step.path("run").asText())
                .as("the script reads its environment alone")
                .doesNotContain("${{");
        final JsonNode depth =
                job(SHARED_RULES, "check").path("steps").get(1).path("with").path("fetch-depth");
        assertThat(depth.isInt() && depth.intValue() == 0)
                .as("the checkout holds every push's commits, fetch-depth %s", depth)
                .isTrue();

        final Run run = execute(step, 0);
        assertThat(run.exit).as("a clean day: %s", run.log).isZero();
        assertThat(run.java)
                .as("check-pushes reads the pushes of the last 26 hours")
                .containsExactly(
                        "-cp",
                        "release/target/classes:" + CLASSPATH,
                        "org.fuin.porelo.release.Main",
                        "check-pushes",
                        "--since",
                        "26");
        assertThat(run.maven)
                .as(
                        "release is installed and its classpath read, each with the repository's settings")
                .containsExactly(
                        "-B -q -s settings.xml -pl release -am install -DskipTests",
                        "-B -q -s settings.xml -pl release dependency:build-classpath"
                                + " -Dmdep.outputFile=target/commits-cp.txt");
        assertThat(execute(step, 1).exit).as("a refused day fails the step").isNotZero();
        assertThat(execute(step, 2).exit)
                .as("a day that could not be read fails the step")
                .isNotZero();
    }

    // Prevents the pull request's facts from reaching the check any way but the event file, where a
    // workflow expression in the script would run what a pull request's title or branch carries,
    // and
    // a second fetch, which a private forge copy refuses to a checkout without credentials.
    @Test
    void thePullRequestIsCheckedFromItsEvent() throws IOException, InterruptedException {
        final JsonNode step =
                named(job(SHARED_RULES, "check"), "The pull request's commit messages");
        assertThat(step.path("if").asText())
                .as("the step runs on a pull request")
                .isEqualTo("github.event_name == 'pull_request'");
        assertThat(step.path("env").isMissingNode())
                .as("nothing of the pull request reaches the step but the event file")
                .isTrue();
        assertThat(step.path("run").asText())
                .as("the script holds no expression")
                .doesNotContain("${{");

        final Run passed = execute(step, 0);
        assertThat(passed.exit)
                .as("a clean pull request, nothing fetched: %s", passed.log)
                .isZero();
        assertThat(passed.java)
                .as("check-commits reads the event, with release and its classpath")
                .containsExactly(
                        "-cp",
                        "release/target/classes:" + CLASSPATH,
                        "org.fuin.porelo.release.Main",
                        "check-commits",
                        "--event");
        assertThat(passed.maven)
                .as(
                        "release is installed and its classpath read, each with the repository's settings")
                .containsExactly(
                        "-B -q -s settings.xml -pl release -am install -DskipTests",
                        "-B -q -s settings.xml -pl release dependency:build-classpath"
                                + " -Dmdep.outputFile=target/cp.txt");
        assertThat(execute(step, 1).exit).as("a refused pull request fails the step").isNotZero();
    }

    private record Run(int exit, String log, List<String> java, List<String> maven) {}

    /** Runs a step's own script with git, Maven and java stubbed, in an empty environment. */
    private Run execute(final JsonNode step, final int checkExit)
            throws IOException, InterruptedException {
        final Path bin = Files.createDirectories(directory.resolve("bin"));
        final Path javaArguments = directory.resolve("java-arguments");
        final Path mavenCalls = directory.resolve("maven-calls");
        Files.writeString(javaArguments, "");
        Files.writeString(mavenCalls, "");
        // Writes the classpath file each call names, below the module, as the dependency plugin
        // does.
        stub(
                directory.resolve("mvnw"),
                "printf '%s\\n' \"$*\" >> \"$MAVEN_CALLS\"\n"
                        + "for argument in \"$@\"; do case \"$argument\" in -Dmdep.outputFile=*)"
                        + " file=release/${argument#*=}; mkdir -p \"${file%/*}\"; printf '"
                        + CLASSPATH
                        + "' > \"$file\" ;; esac; done\n");
        stub(
                bin.resolve("java"),
                "printf '%s\\n' \"$@\" >> \"$JAVA_ARGUMENTS\"\nexit " + checkExit + "\n");
        // The steps call git through the command alone; a call of their own fails.
        stub(bin.resolve("git"), "echo \"unexpected git $*\" >&2\nexit 99\n");
        final Path output = directory.resolve("output");
        final ProcessBuilder command =
                new ProcessBuilder(
                                "bash",
                                "--noprofile",
                                "--norc",
                                "-e",
                                "-o",
                                "pipefail",
                                "-c",
                                step.path("run").asText())
                        .directory(directory.toFile())
                        .redirectErrorStream(true)
                        .redirectOutput(output.toFile());
        command.environment().clear();
        command.environment().put("PATH", bin + ":/usr/bin:/bin");
        command.environment().put("JAVA_ARGUMENTS", javaArguments.toString());
        command.environment().put("MAVEN_CALLS", mavenCalls.toString());
        final Process process = command.start();
        try {
            assertThat(process.waitFor(10, TimeUnit.SECONDS)).as("the step ends").isTrue();
            return new Run(
                    process.exitValue(),
                    Files.readString(output),
                    Files.readAllLines(javaArguments),
                    Files.readAllLines(mavenCalls));
        } finally {
            process.destroyForcibly();
        }
    }

    private static void stub(final Path file, final String body) throws IOException {
        Files.writeString(file, "#!/bin/sh\n" + body);
        Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("rwx------"));
    }

    private static JsonNode job(final Path workflow, final String name) throws IOException {
        final JsonNode job = YAML.readTree(workflow.toFile()).path("jobs").path(name);
        assertThat(job.isMissingNode())
                .as("%s has a job '%s'", workflow.getFileName(), name)
                .isFalse();
        return job;
    }

    private static JsonNode buildJob() throws IOException {
        return job(BUILD, "build");
    }

    private static JsonNode step() throws IOException {
        return named(buildJob(), STEP);
    }

    private static JsonNode scheduledStep() throws IOException {
        return named(job(SHARED_RULES, "check"), "Main's pushes of the last day");
    }

    private static JsonNode named(final JsonNode job, final String name) {
        for (final JsonNode step : job.path("steps")) {
            if (step.path("name").asText().equals(name)) {
                return step;
            }
        }
        throw new AssertionError("no step '" + name + "' in its job");
    }
}
