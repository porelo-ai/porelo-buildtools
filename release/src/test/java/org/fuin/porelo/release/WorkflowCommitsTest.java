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
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The Build checks the commit messages of every push, so a direct push to {@code main} cannot pass
 * the rule around the pull request's check.
 */
@Tag("documents")
class WorkflowCommitsTest {

    private static final Path BUILD = Path.of("../.github/workflows/build.yml");
    private static final Path SHARED_RULES = Path.of("../.github/workflows/shared-rules.yml");
    private static final ObjectMapper YAML =
            new ObjectMapper(
                    new YAMLFactory().enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION));
    private static final String STEP = "The pushed commits' messages";
    private static final String CLASSPATH = "/stub/a.jar:/stub/b.jar";

    @TempDir Path directory;

    // Prevents the check from being skipped on a push or fed a range a workflow expression built.
    @Test
    void theBuildChecksThePushedRangeFromItsOwnEnvironment() throws IOException {
        final JsonNode step = step();
        assertThat(step.path("if").asText())
                .as("the step runs on a push")
                .isEqualTo("github.event_name == 'push'");
        assertThat(step.path("env").path("PUSH_BEFORE").asText())
                .as("the range starts where the push began")
                .isEqualTo("${{ github.event.before }}");
        assertThat(step.path("run").asText())
                .as("the range reaches the script through its environment alone")
                .doesNotContain("${{");
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

    // Prevents a pushed range from going unchecked, and a refusal from passing.
    @Test
    void aPushedRangeReachesCheckCommitsAndItsRefusalFailsTheStep()
            throws IOException, InterruptedException {
        final Run passed = run("refs/heads/main", "1111111", "1111111", 0);
        assertThat(passed.exit).as("a clean range: %s", passed.log).isZero();
        assertThat(passed.java)
                .as("check-commits reads exactly the pushed range, with release and its classpath")
                .containsExactly(
                        "-cp",
                        "release/target/classes:" + CLASSPATH,
                        "org.fuin.porelo.release.Main",
                        "check-commits",
                        "1111111..2222222");
        assertThat(passed.maven)
                .as(
                        "release is installed and its classpath read, each with the repository's settings")
                .containsExactly(
                        "-B -q -s settings.xml -pl release -am install -DskipTests",
                        "-B -q -s settings.xml -pl release dependency:build-classpath"
                                + " -Dmdep.outputFile=target/commits-cp.txt");

        final Run refused = run("refs/heads/main", "1111111", "1111111", 1);
        assertThat(refused.exit).as("a refused range fails the step: %s", refused.log).isNotZero();
    }

    // Prevents a new branch, whose start is all zeros, from checking nothing.
    @Test
    void aNewBranchIsCheckedFromMain() throws IOException, InterruptedException {
        final Run run = run("refs/heads/topic", "0000000000000000000000000000000000000000", "", 0);
        assertThat(run.exit).as("a new branch: %s", run.log).isZero();
        assertThat(run.java).as("its range starts at main").endsWith("origin/main..2222222");
    }

    // Prevents a branch pushed over, whose old tip the checkout lacks, from failing a clean push.
    @Test
    void aBranchPushedOverIsCheckedFromMain() throws IOException, InterruptedException {
        final Run run = run("refs/heads/topic", "3333333", "1111111", 0);
        assertThat(run.exit).as("a branch pushed over: %s", run.log).isZero();
        assertThat(run.java).as("its range starts at main").endsWith("origin/main..2222222");
    }

    // Prevents a tag, whose range from main is empty, from checking nothing.
    @Test
    void aTagChecksTheCommitItNames() throws IOException, InterruptedException {
        final Run run = run("refs/tags/v1.2.3", "0000000000000000000000000000000000000000", "", 0);
        assertThat(run.exit).as("a tag: %s", run.log).isZero();
        assertThat(run.java).as("its range is the commit it names").endsWith("2222222~1..2222222");
    }

    // Prevents main moved from a commit nobody holds from passing as an empty range.
    @Test
    void mainFromACommitTheCheckoutLacksFailsLoudly() throws IOException, InterruptedException {
        final Run run = run("refs/heads/main", "3333333", "1111111", 0);
        assertThat(run.exit).as("main from an unknown commit: %s", run.log).isNotZero();
        assertThat(run.log).as("the refusal is a visible workflow error").contains("::error::");
        assertThat(run.java).as("check-commits is not started for it").isEmpty();
    }

    // Prevents a missing start of the range from silently checking nothing.
    @Test
    void aMissingStartFailsLoudly() throws IOException, InterruptedException {
        final Run run = run("refs/heads/main", "", "1111111", 0);
        assertThat(run.exit).as("no start of the range: %s", run.log).isNotZero();
        assertThat(run.log).as("the refusal is a visible workflow error").contains("::error::");
        assertThat(run.java).as("check-commits is not started without a range").isEmpty();
    }

    // Prevents a push the Build never starts from going unchecked: the schedule reads main's last
    // day.
    @Test
    void theScheduleChecksMainsLastDay() throws IOException, InterruptedException {
        final JsonNode step = scheduledStep();
        assertThat(step.path("if").asText())
                .as("the step runs on the schedule")
                .isEqualTo("github.event_name == 'schedule'");
        assertThat(step.path("run").asText())
                .as("the range comes from git, not a workflow expression")
                .doesNotContain("${{");
        final JsonNode depth =
                job(SHARED_RULES, "check").path("steps").get(0).path("with").path("fetch-depth");
        assertThat(depth.isInt() && depth.intValue() == 0)
                .as("the scheduled checkout holds main's last day, fetch-depth %s", depth)
                .isTrue();

        final Run run = execute(step, Map.of("DAY_START", "4444444"), 0);
        assertThat(run.exit).as("a clean day: %s", run.log).isZero();
        assertThat(run.java)
                .as("check-commits reads main's commits since the last day began")
                .containsExactly(
                        "-cp",
                        "release/target/classes:" + CLASSPATH,
                        "org.fuin.porelo.release.Main",
                        "check-commits",
                        "4444444..HEAD");
        assertThat(run.maven)
                .as(
                        "release is installed and its classpath read, each with the repository's settings")
                .containsExactly(
                        "-B -q -s settings.xml -pl release -am install -DskipTests",
                        "-B -q -s settings.xml -pl release dependency:build-classpath"
                                + " -Dmdep.outputFile=target/commits-cp.txt");
        assertThat(execute(step, Map.of("DAY_START", "4444444"), 1).exit)
                .as("a refused day fails the step")
                .isNotZero();
    }

    // Prevents a repository younger than a day from checking nothing, or leaving out its first
    // commit.
    @Test
    void aRepositoryYoungerThanADayIsReadWhole() throws IOException, InterruptedException {
        final Run run = execute(scheduledStep(), Map.of("DAY_START", ""), 0);
        assertThat(run.exit).as("a young repository: %s", run.log).isZero();
        assertThat(run.java).as("it is read whole").endsWith("check-commits", "HEAD");
    }

    // Prevents a second fetch, which a private forge copy refuses to a checkout without
    // credentials.
    @Test
    void aMembersPullRequestIsCheckedFromTheCheckoutAlone()
            throws IOException, InterruptedException {
        final Run run = pullRequest("porelo-ai/porelo-buildtools", "MEMBER", "someone");
        assertThat(run.exit).as("a member's pull request, nothing fetched: %s", run.log).isZero();
        assertThat(run.java)
                .as("its own commits are read, as a member's, with release and its classpath")
                .containsExactly(
                        "-cp",
                        "release/target/classes:" + CLASSPATH,
                        "org.fuin.porelo.release.Main",
                        "check-commits",
                        "6666666..7777777");
        assertThat(run.maven)
                .as(
                        "release is installed and its classpath read, each with the repository's settings")
                .containsExactly(
                        "-B -q -s settings.xml -pl release -am install -DskipTests",
                        "-B -q -s settings.xml -pl release dependency:build-classpath"
                                + " -Dmdep.outputFile=target/cp.txt");
    }

    // Prevents the pull request's facts from reaching the script any way but its environment, where
    // a
    // workflow expression in the script would run what a pull request's title or branch carries.
    @Test
    void thePullRequestsFactsReachTheScriptThroughItsEnvironment() throws IOException {
        final JsonNode step =
                named(job(SHARED_RULES, "check"), "The pull request's commit messages");
        assertThat(step.path("env").path("PR_AUTHOR").asText())
                .as("the author is the pull request's own")
                .isEqualTo("${{ github.event.pull_request.user.login }}");
        assertThat(step.path("env").path("PR_REPOSITORY").asText())
                .as("the repository is the pull request's head's")
                .isEqualTo("${{ github.event.pull_request.head.repo.full_name }}");
        assertThat(step.path("env").path("PR_ASSOCIATION").asText())
                .as("the association is the pull request's author's")
                .isEqualTo("${{ github.event.pull_request.author_association }}");
        assertThat(step.path("run").asText())
                .as("the script reads its environment alone")
                .doesNotContain("${{");
    }

    // Prevents the repository's own update workflow's pull request from being refused for want of a
    // sign-off, though GitHub names its author a contributor.
    @Test
    void theOwnWorkflowsPullRequestIsFromInside() throws IOException, InterruptedException {
        final Run run =
                pullRequest("porelo-ai/porelo-buildtools", "CONTRIBUTOR", "github-actions[bot]");
        assertThat(run.exit).as("the own workflow's pull request: %s", run.log).isZero();
        assertThat(run.java).as("its commits need no sign-off").endsWith("6666666..7777777");
    }

    // Prevents code from outside from passing without its author's sign-off.
    @Test
    void aPullRequestFromElsewhereStaysFromOutside() throws IOException, InterruptedException {
        assertThat(
                        pullRequest(
                                        "someone/porelo-buildtools",
                                        "CONTRIBUTOR",
                                        "github-actions[bot]")
                                .java)
                .as("the bot's pull request from another repository")
                .endsWith("6666666..7777777", "--outside");
        assertThat(pullRequest("porelo-ai/porelo-buildtools", "CONTRIBUTOR", "someone").java)
                .as("a contributor's pull request from this repository")
                .endsWith("6666666..7777777", "--outside");
        assertThat(pullRequest("someone/porelo-buildtools", "MEMBER", "someone").java)
                .as("a member's pull request from another repository")
                .endsWith("6666666..7777777", "--outside");
    }

    private Run pullRequest(final String repository, final String association, final String author)
            throws IOException, InterruptedException {
        return execute(
                named(job(SHARED_RULES, "check"), "The pull request's commit messages"),
                Map.of(
                        "PR_BASE",
                        "6666666",
                        "PR_HEAD",
                        "7777777",
                        "PR_REPOSITORY",
                        repository,
                        "GITHUB_REPOSITORY",
                        "porelo-ai/porelo-buildtools",
                        "PR_ASSOCIATION",
                        association,
                        "PR_AUTHOR",
                        author),
                0);
    }

    private record Run(int exit, String log, List<String> java, List<String> maven) {}

    /**
     * Runs the Build's step for a push of {@code ref} from {@code before}; git holds {@code known}.
     */
    private Run run(final String ref, final String before, final String known, final int checkExit)
            throws IOException, InterruptedException {
        return execute(
                step(),
                Map.of(
                        "PUSH_BEFORE",
                        before,
                        "GITHUB_SHA",
                        "2222222",
                        "GITHUB_REF",
                        ref,
                        "GITHUB_REF_TYPE",
                        ref.startsWith("refs/tags/") ? "tag" : "branch",
                        "KNOWN",
                        known.isEmpty() ? "none" : known),
                checkExit);
    }

    /** Runs a step's own script with git, Maven and java stubbed, in the given environment. */
    private Run execute(
            final JsonNode step, final Map<String, String> environment, final int checkExit)
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
        // Answers only the calls the steps make, as git would; any other call fails.
        stub(
                bin.resolve("git"),
                "case \"$*\" in\n"
                        + "'cat-file -e '*) [ \"$#\" -eq 3 ] && [ \"$3\" = \"$KNOWN^{commit}\" ] ;;\n"
                        + "'rev-list -1 --before=26 hours ago HEAD')"
                        + " [ -z \"$DAY_START\" ] || printf '%s\\n' \"$DAY_START\" ;;\n"
                        + "*) echo \"unexpected git $*\" >&2; exit 99 ;;\n"
                        + "esac\n");
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
        command.environment().put("KNOWN", "none");
        command.environment().put("DAY_START", "");
        command.environment().putAll(environment);
        command.environment().put("JAVA_ARGUMENTS", javaArguments.toString());
        command.environment().put("MAVEN_CALLS", mavenCalls.toString());
        final Process process = command.start();
        try {
            assertThat(process.waitFor(10, TimeUnit.SECONDS))
                    .as("the step ends in %s", environment)
                    .isTrue();
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
        return named(job(SHARED_RULES, "check"), "Main's commits of the last day");
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
