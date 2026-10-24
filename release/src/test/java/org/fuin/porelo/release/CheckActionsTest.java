package org.fuin.porelo.release;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CheckActionsTest {

    private static final String COMMIT = "11bd71901bbe5b1630ceea73d27597364c9af683";

    @TempDir Path directory;

    private final ByteArrayOutputStream out = new ByteArrayOutputStream();

    private final ByteArrayOutputStream err = new ByteArrayOutputStream();

    @Test
    void thisRepositorysOwnWorkflowsFetchNothingByAName() {
        // The build that fails on a tag: a new workflow step that names one turns this red.
        assertThat(check(Path.of("../.github"))).as(stderr()).isEqualTo(0);
    }

    @Test
    void acceptsACommitWithItsVersionBesideIt() throws IOException {
        workflow(
                "      - uses: actions/checkout@"
                        + COMMIT
                        + " # v7.0.1\n"
                        + "        with:\n          persist-credentials: false\n");

        assertThat(check(directory)).as(stderr()).isEqualTo(0);
        assertThat(stdout()).contains("OK    1 step(s)");
    }

    @Test
    void refusesATagAndSaysWhatToDoAboutIt() throws IOException {
        workflow("      - uses: actions/checkout@v7\n");

        assertThat(check(directory)).isEqualTo(Stop.REFUSED);
        assertThat(stderr())
                .contains("ci.yml:1: actions/checkout@v7 names a tag or a branch, not a commit")
                .contains("uses: owner/action@<40-digit commit> # v1.2.3");
    }

    @Test
    void refusesABranch() throws IOException {
        workflow("        uses: some/action@main\n");

        assertThat(check(directory)).isEqualTo(Stop.REFUSED);
    }

    @Test
    void refusesACommitNobodyCanRead() throws IOException {
        // A bare hash is a pin nobody updates, because nobody can tell what it is.
        workflow("      - uses: actions/checkout@" + COMMIT + "\n");

        assertThat(check(directory)).isEqualTo(Stop.REFUSED);
        assertThat(stderr()).contains("carries no exact version beside it");
    }

    @Test
    void refusesAVersionThatNamesALineOfReleasesRatherThanOne() throws IOException {
        // Measured: '# v7' passed, and it does not say which release the commit is.
        workflow("      - uses: actions/checkout@" + COMMIT + " # v7\n");

        assertThat(check(directory)).isEqualTo(Stop.REFUSED);
        assertThat(stderr()).contains("no exact version beside it");
    }

    @Test
    void holdsGithubsOwnActionsToTheSameRule() throws IOException {
        workflow("      - uses: actions/upload-artifact@v7\n");

        assertThat(check(directory)).isEqualTo(Stop.REFUSED);
    }

    @Test
    void exemptsOnlyAStepInTheRepositoryItself() throws IOException {
        workflow("      - uses: ./.github/actions/pinned-jdk\n");

        assertThat(check(directory)).as(stderr()).isEqualTo(0);
    }

    @Test
    void holdsAContainerImageToItsDigest() throws IOException {
        workflow("      - uses: docker://alpine:3.20\n");
        assertThat(check(directory)).isEqualTo(Stop.REFUSED);

        workflow("      - uses: docker://alpine@sha256:" + "a".repeat(64) + "\n");
        assertThat(check(directory)).isEqualTo(0);
    }

    @Test
    void readsACompositeActionAndAQuotedReference() throws IOException {
        Files.createDirectories(directory.resolve("actions/x"));
        Files.writeString(
                directory.resolve("actions/x/action.yaml"),
                "    - uses: 'jfrog/setup-jfrog-cli@v5'\n");

        assertThat(check(directory)).isEqualTo(Stop.REFUSED);
        assertThat(stderr()).contains("action.yaml:1: jfrog/setup-jfrog-cli@v5");
    }

    @Test
    void aDirectoryThatIsNotThereIsNoAnswer() {
        assertThat(check(directory.resolve("missing"))).isEqualTo(Stop.UNANSWERED);
    }

    @Test
    void refusesAnActionThatFetchesAJdkByItsVersionNameEvenAtACommit() throws IOException {
        workflow("      - uses: graalvm/setup-graalvm@" + COMMIT + " # v1.3.0\n");

        assertThat(check(directory)).isEqualTo(Stop.REFUSED);
        assertThat(stderr())
                .contains("fetches a JDK by its version name")
                .contains("porelo-machines jdk --github");
    }

    @Test
    void refusesWhenNothingMovesThePins() throws IOException {
        workflow("      - uses: ./.github/actions/pinned-jdk\n");
        Files.delete(directory.resolve("dependabot.yml"));

        assertThat(check(directory)).isEqualTo(Stop.REFUSED);
        assertThat(stderr()).contains("dependabot.yml: missing");
    }

    @Test
    void refusesDependabotThatDoesNotWatchTheLocalActions() throws IOException {
        workflow("      - uses: ./.github/actions/pinned-jdk\n");
        Files.createDirectories(directory.resolve("actions/pinned-jdk"));
        Files.writeString(
                directory.resolve("dependabot.yml"),
                "updates:\n  - package-ecosystem: github-actions\n"
                        + "    cooldown:\n      default-days: 3\n    groups:\n      a:\n        patterns: [\"*\"]\n");

        assertThat(check(directory)).isEqualTo(Stop.REFUSED);
        assertThat(stderr()).contains("does not watch /.github/actions/*");
    }

    @Test
    void refusesADependabotThatTakesAReleaseTheDayItAppearsOrMovesEachActionAlone()
            throws IOException {
        // Measured against the published check: both passed.
        workflow("      - uses: ./.github/actions/pinned-jdk\n");
        Files.writeString(
                directory.resolve("dependabot.yml"),
                "updates:\n  - package-ecosystem: github-actions\n    cooldown:\n      default-days: 0\n");

        assertThat(check(directory)).isEqualTo(Stop.REFUSED);
        assertThat(stderr()).contains("default-days: 3").contains("no 'groups:'");
    }

    @Test
    void aGroupedEntryOfAnotherEcosystemDoesNotCoverAnUngroupedGithubActionsOne()
            throws IOException {
        // Measured: a docker entry's groups and cooldown answered for the github-actions entry.
        workflow("      - uses: ./.github/actions/pinned-jdk\n");
        Files.writeString(
                directory.resolve("dependabot.yml"),
                "updates:\n  - package-ecosystem: github-actions\n"
                        + "    directory: /\n  - package-ecosystem: docker\n    directory: /\n    cooldown:\n"
                        + "      default-days: 3\n    groups:\n      images:\n        patterns: [\"*\"]\n");

        assertThat(check(directory)).isEqualTo(Stop.REFUSED);
        assertThat(stderr())
                .contains("github-actions entry has no 'cooldown")
                .contains("github-actions entry has no 'groups:'");
    }

    @Test
    void readsNothingADependabotConfigurationOnlySaysInAComment() throws IOException {
        // A comment that names what is missing is not what is missing; Dependabot reads no comment.
        workflow("      - uses: ./.github/actions/pinned-jdk\n");
        Files.writeString(
                directory.resolve("dependabot.yml"),
                "updates:\n"
                        + "  # - package-ecosystem: github-actions\n"
                        + "  - package-ecosystem: maven # github-actions to follow\n"
                        + "    directory: /\n");

        assertThat(check(directory)).isEqualTo(Stop.REFUSED);
        assertThat(stderr()).contains("no github-actions ecosystem");

        err.reset();
        Files.writeString(
                directory.resolve("dependabot.yml"),
                "updates:\n"
                        + "  - package-ecosystem: github-actions # cooldown: default-days: 7\n"
                        + "    directories: [\"/\", \"/.github/actions/*\"]\n"
                        + "    # cooldown:\n"
                        + "    #   default-days: 7\n"
                        + "    groups:\n      actions:\n        patterns: [\"*\"]\n");

        assertThat(check(directory)).isEqualTo(Stop.REFUSED);
        assertThat(stderr()).contains("github-actions entry has no 'cooldown");
    }

    @Test
    void aHashInsideQuotesIsNotAComment() throws IOException {
        workflow("      - uses: ./.github/actions/pinned-jdk\n");
        Files.writeString(
                directory.resolve("dependabot.yml"),
                "updates:\n"
                        + "  - package-ecosystem: \"github-actions\"\n"
                        + "    directories: [\"/\", \"/.github/actions/*\"]\n"
                        + "    cooldown:\n      default-days: 3\n"
                        + "    groups:\n      \"actions #1\":\n        patterns: [\"*\"]\n");

        assertThat(check(directory)).as(stderr()).isEqualTo(0);
    }

    @Test
    void refusesAMavenWrapperWithoutItsDigest() throws IOException {
        final Path repository = directory.resolve("repo");
        final Path github = Files.createDirectories(repository.resolve(".github/workflows"));
        Files.writeString(github.resolve("ci.yml"), "      - uses: ./.github/actions/x\n");
        Files.writeString(
                repository.resolve(".github/dependabot.yml"),
                "updates:\n  - package-ecosystem: github-actions\n"
                        + "    cooldown:\n      default-days: 3\n    groups:\n      a:\n        patterns: [\"*\"]\n");
        Files.createDirectories(repository.resolve(".mvn/wrapper"));
        Files.writeString(
                repository.resolve(".mvn/wrapper/maven-wrapper.properties"),
                "distributionUrl=https://x/maven.zip\n");

        assertThat(check(repository.resolve(".github"))).isEqualTo(Stop.REFUSED);
        assertThat(stderr()).contains("no distributionSha256Sum");

        Files.writeString(
                repository.resolve(".mvn/wrapper/maven-wrapper.properties"),
                "distributionUrl=https://x/maven.zip\ndistributionSha256Sum="
                        + "a".repeat(64)
                        + "\n");
        err.reset();
        assertThat(check(repository.resolve(".github"))).as(stderr()).isEqualTo(0);
    }

    @Test
    void refusesAGradleWrapperWithoutItsDigestOrAVersion() throws IOException {

        // The one repository built with Gradle downloads its build tool as Maven's wrapper does,
        // and is held to the same pin.
        final Path repository = directory.resolve("repo");
        final Path github = Files.createDirectories(repository.resolve(".github/workflows"));
        Files.writeString(github.resolve("ci.yml"), "      - uses: ./.github/actions/x\n");
        Files.writeString(
                repository.resolve(".github/dependabot.yml"),
                "updates:\n  - package-ecosystem: github-actions\n"
                        + "    cooldown:\n      default-days: 3\n    groups:\n      a:\n        patterns: [\"*\"]\n");
        final Path properties =
                Files.createDirectories(repository.resolve("gradle/wrapper"))
                        .resolve("gradle-wrapper.properties");
        Files.writeString(
                properties,
                "distributionUrl=https\\://services.gradle.org/distributions/gradle-9.8.0-bin.zip\n");

        assertThat(check(repository.resolve(".github"))).isEqualTo(Stop.REFUSED);
        assertThat(stderr())
                .contains("gradle-wrapper.properties")
                .contains("no distributionSha256Sum");

        Files.writeString(
                properties,
                "distributionUrl=https\\://services.gradle.org/distributions/gradle-latest.zip\n"
                        + "distributionSha256Sum="
                        + "a".repeat(64)
                        + "\n");
        err.reset();
        assertThat(check(repository.resolve(".github"))).isEqualTo(Stop.REFUSED);
        assertThat(stderr()).contains("names no version");

        Files.writeString(
                properties,
                "distributionUrl=https\\://services.gradle.org/distributions/gradle-9.8.0-bin.zip\n"
                        + "distributionSha256Sum="
                        + "a".repeat(64)
                        + "\n");
        err.reset();
        assertThat(check(repository.resolve(".github"))).as(stderr()).isEqualTo(0);
    }

    @Test
    void refusesAWorkflowThatAForkCanTriggerWithTheSecrets() throws IOException {
        // pull_request_target runs with this repository's secrets on an event a fork's pull request
        // raises.
        workflow(
                "on:\n  push:\n  pull_request_target:\n    types: [opened]\njobs:\n  a:\n    runs-on: ubuntu-latest\n"
                        + "    steps:\n      - uses: actions/checkout@"
                        + COMMIT
                        + " # v7.0.1\n");

        assertThat(check(directory)).isEqualTo(Stop.REFUSED);
        assertThat(stderr()).contains("workflows/ci.yml:3").contains("pull_request_target");
    }

    @Test
    void refusesItInTheShortFormOfTheTriggers() throws IOException {
        workflow("on: [push, pull_request_target]\n");

        assertThat(check(directory)).isEqualTo(Stop.REFUSED);
        assertThat(stderr()).contains("workflows/ci.yml:1").contains("pull_request_target");
    }

    @Test
    void refusesARunAfterAnotherThatChecksOutThePullRequestsCode() throws IOException {
        workflow(
                "on:\n  workflow_run:\n    workflows: [Build]\njobs:\n  a:\n    runs-on: ubuntu-latest\n    steps:\n"
                        + "      - uses: actions/checkout@"
                        + COMMIT
                        + " # v7.0.1\n"
                        + "        with:\n          ref: ${{ github.event.workflow_run.head_sha }}\n");

        assertThat(check(directory)).isEqualTo(Stop.REFUSED);
        assertThat(stderr()).contains("workflows/ci.yml:10").contains("workflow_run");
    }

    @Test
    void acceptsARunAfterAnotherOnItsOwnCodeAndTheWordInAnExpression() throws IOException {
        workflow(
                "on:\n  workflow_run:\n    workflows: [Build]\npermissions: {}\njobs:\n  a:\n"
                        + "    if: github.event_name != 'pull_request_target'\n    runs-on: ubuntu-latest\n    steps:\n"
                        + "      - uses: actions/checkout@"
                        + COMMIT
                        + " # v7.0.1\n"
                        + "        with:\n          persist-credentials: false\n");

        assertThat(check(directory)).as(stderr()).isEqualTo(0);
    }

    @Test
    void refusesACheckoutThatKeepsItsTokenNamingTheFileAndTheStep() throws IOException {
        // Without the option, the token stays in .git/config for every later step to read.
        workflow(
                "permissions: {}\njobs:\n  build:\n    steps:\n      - name: Check out\n        uses: actions/checkout@"
                        + COMMIT
                        + " # v7.0.1\n        with:\n          fetch-depth: 0\n      - name: Build\n        run: ./mvnw verify\n"
                        + "        with:\n          persist-credentials: false\n");

        assertThat(check(directory)).isEqualTo(Stop.REFUSED);
        assertThat(stderr())
                .as("the file, the step and what it lacks")
                .contains(
                        "workflows/ci.yml:6: step 'Check out' checks out without 'persist-credentials: false'")
                .contains("persist-credentials: false")
                .doesNotContain("Pin each step");
    }

    @Test
    void refusesACheckoutWithPersistCredentialsTrue() throws IOException {
        workflow(
                "      - uses: actions/checkout@"
                        + COMMIT
                        + " # v7.0.1\n        with:\n"
                        + "          persist-credentials: true\n");

        assertThat(check(directory)).isEqualTo(Stop.REFUSED);
        assertThat(stderr())
                .contains(
                        "workflows/ci.yml:1: step at line 1 checks out without"
                                + " 'persist-credentials: false'");
    }

    @Test
    void acceptsACheckoutWithPersistCredentialsFalseBesideItsOtherOptions() throws IOException {
        workflow(
                "permissions: {}\njobs:\n  build:\n    steps:\n      - name: Check out\n"
                        + "        uses: actions/checkout@"
                        + COMMIT
                        + " # v7.0.1\n        with:\n          fetch-depth: 0\n"
                        + "          persist-credentials: 'false' # no token for later steps\n"
                        + "          path: porelo\n      - run: ./mvnw verify\n");

        assertThat(check(directory)).as(stderr()).isEqualTo(0);
    }

    private static final String STEP =
            "    steps:\n      - uses: actions/checkout@"
                    + COMMIT
                    + " # v7.0.1\n"
                    + "        with:\n          persist-credentials: false\n";

    private void named(String file, String text) throws IOException {
        workflow("");
        Files.writeString(directory.resolve("workflows/" + file), text);
        Files.delete(directory.resolve("workflows/ci.yml"));
    }

    @Test
    void refusesAWorkflowWithoutPermissionsAtItsTop() throws IOException {
        // Without one, its token gets the repository's default, which may be write.
        named("build.yml", "on: push\njobs:\n  build:\n    runs-on: ubuntu-latest\n" + STEP);

        assertThat(check(directory)).isEqualTo(Stop.REFUSED);
        assertThat(stderr())
                .contains("workflows/build.yml")
                .contains("no 'permissions:' at its top");
    }

    @Test
    void refusesAWriteAtTheTopByName() throws IOException {
        named(
                "build.yml",
                "on: push\npermissions:\n  contents: write\njobs:\n  build:\n    runs-on: ubuntu-latest\n"
                        + STEP);

        assertThat(check(directory)).isEqualTo(Stop.REFUSED);
        assertThat(stderr())
                .contains("workflows/build.yml")
                .contains("'contents: write' at its top")
                .contains("only the jobs check-actions' list names may write")
                .doesNotContain("Pin each step");
    }

    @Test
    void refusesWriteAllWherever() throws IOException {
        named(
                "build.yml",
                "on: push\npermissions: read-all\njobs:\n  build:\n    permissions: write-all\n"
                        + "    runs-on: ubuntu-latest\n"
                        + STEP);

        assertThat(check(directory)).isEqualTo(Stop.REFUSED);
        assertThat(stderr()).contains("job 'build'").contains("write-all");
    }

    @Test
    void refusesAWriteInAJobTheListDoesNotName() throws IOException {
        named(
                "build.yml",
                "on: push\npermissions:\n  contents: read\njobs:\n  test:\n    permissions:\n"
                        + "      id-token: write\n      contents: read\n    runs-on: ubuntu-latest\n"
                        + STEP);

        assertThat(check(directory)).isEqualTo(Stop.REFUSED);
        assertThat(stderr())
                .contains("workflows/build.yml")
                .contains("job 'test'")
                .contains("'id-token: write'")
                .contains("allows no write there");
    }

    @Test
    void refusesAWriteInAJobOfAWorkflowIndentedByFourSpaces() throws IOException {
        // The shared code style indents YAML by four: a job there is no less able to write.
        named(
                "build.yml",
                "on: push\npermissions:\n    contents: read\njobs:\n    test:\n        permissions:\n"
                        + "            contents: write\n        runs-on: ubuntu-latest\n"
                        + "        steps:\n            - run: make\n");

        assertThat(check(directory)).as(stderr()).isEqualTo(Stop.REFUSED);
        assertThat(stderr())
                .as("the job and the write it is refused")
                .contains("job 'test'")
                .contains("'contents: write'");
    }

    @Test
    void acceptsTheWritesTheListNamesInAWorkflowIndentedByFourSpaces() throws IOException {
        named(
                "update.yml",
                "on: workflow_dispatch\npermissions:\n    contents: read\njobs:\n    update:\n"
                        + "        permissions:\n            contents: write\n"
                        + "            pull-requests: write\n        runs-on: ubuntu-latest\n"
                        + "        steps:\n            - run: make\n");

        assertThat(check(directory)).as(stderr()).isEqualTo(0);
    }

    @Test
    void acceptsContentsWriteInTheReleasesPublishJob() throws IOException {
        named(
                "release.yml",
                "on: workflow_dispatch\npermissions: {}\njobs:\n    publish:\n"
                        + "        permissions:\n            contents: write\n"
                        + "        runs-on: ubuntu-latest\n"
                        + "        steps:\n            - run: gh --version\n");

        assertThat(check(directory)).as(stderr()).isEqualTo(0);
    }

    @Test
    void refusesAShellCommandTemplateInAProtectedStep() throws IOException {
        named(
                "release.yml",
                "permissions: {}\njobs:\n  publish:\n    steps:\n"
                        + "      - run: gh --version\n        shell: docker run x sh {0}\n");

        assertThat(check(directory))
                .as("a shell template can run an image beside the token: %s", stderr())
                .isEqualTo(Stop.REFUSED);
        assertThat(stderr())
                .as("shell must be refused at the step's line")
                .contains(
                        "release.yml:5: job 'publish' writes and so may hold only runner commands; 'shell' is not one");
    }

    @Test
    void refusesShellDefaultsInTheProtectedJob() throws IOException {
        named(
                "release.yml",
                "permissions: {}\njobs:\n  publish:\n"
                        + "    defaults:\n      run:\n        shell: docker run x sh {0}\n"
                        + "    steps:\n      - run: gh --version\n");

        assertThat(check(directory))
                .as("job defaults can replace every runner command's shell: %s", stderr())
                .isEqualTo(Stop.REFUSED);
        assertThat(stderr())
                .as("job defaults must be outside the command allowlist")
                .contains(
                        "release.yml:4: job 'publish' writes and so may hold only runner commands; 'defaults' is not one");
    }

    @Test
    void refusesWorkflowDefaultsInheritedByTheProtectedJob() throws IOException {
        named(
                "release.yml",
                "permissions: {}\ndefaults:\n  run:\n    shell: docker run x sh {0}\n"
                        + "jobs:\n  publish:\n    steps:\n      - run: gh --version\n");

        assertThat(check(directory))
                .as("workflow defaults also change the protected job's shell: %s", stderr())
                .isEqualTo(Stop.REFUSED);
        assertThat(stderr())
                .as("the inherited defaults must name the affected job and their line")
                .contains(
                        "release.yml:2: job 'publish' writes and so may hold only runner commands; 'defaults' is not one");
    }

    @Test
    void refusesUnzipOptionsInAProtectedStepsEnv() throws IOException {
        named(
                "release.yml",
                "permissions: {}\njobs:\n  publish:\n    steps:\n"
                        + "      - run: unzip release.zip\n        env: {UNZIP: -o}\n");

        assertThat(check(directory))
                .as("an env name read by a command can change what it runs: %s", stderr())
                .isEqualTo(Stop.REFUSED);
        assertThat(stderr())
                .as("step env must use only the job's explicitly allowed names")
                .contains(
                        "release.yml:6: job 'publish' writes and so may set only ARTIFACT, GH_TOKEN, TAG, VERSION in env, not 'UNZIP'");
    }

    @Test
    void refusesAProxyInTheProtectedJobsEnv() throws IOException {
        named(
                "release.yml",
                "permissions: {}\njobs:\n  publish:\n    env: {HTTPS_PROXY: 'https://proxy.invalid'}\n"
                        + "    steps:\n      - run: gh --version\n");

        assertThat(check(directory))
                .as("job env must not redirect gh's traffic: %s", stderr())
                .isEqualTo(Stop.REFUSED);
        assertThat(stderr())
                .as("unknown job environment names must fail closed")
                .contains(
                        "release.yml:4: job 'publish' writes and so may set only ARTIFACT, GH_TOKEN, TAG, VERSION in env, not 'HTTPS_PROXY'");
    }

    @Test
    void refusesACdSearchPathInTheProtectedJobsEnv() throws IOException {
        named(
                "release.yml",
                "permissions: {}\njobs:\n  publish:\n    env: {CDPATH: release}\n"
                        + "    steps:\n      - run: gh --version\n");

        assertThat(check(directory))
                .as("an env name must not move commands to another directory: %s", stderr())
                .isEqualTo(Stop.REFUSED);
        assertThat(stderr())
                .as("the env allowlist must also exclude previously unlisted shell settings")
                .contains(
                        "release.yml:4: job 'publish' writes and so may set only ARTIFACT, GH_TOKEN, TAG, VERSION in env, not 'CDPATH'");
    }

    @Test
    void refusesAnyWorkflowEnvInheritedByTheProtectedJob() throws IOException {
        named(
                "release.yml",
                "permissions: {}\nenv: {FOO: bar}\njobs:\n  publish:\n"
                        + "    steps:\n      - run: gh --version\n");

        assertThat(check(directory))
                .as(
                        "even an ordinary workflow env name must not reach the protected job: %s",
                        stderr())
                .isEqualTo(Stop.REFUSED);
        assertThat(stderr())
                .as("the inherited env itself must be refused at its source")
                .contains("release.yml:2: job 'publish' writes and so may inherit no workflow env");
    }

    @Test
    void refusesAnEmptyWorkflowEnvForTheProtectedJob() throws IOException {
        named(
                "release.yml",
                "permissions: {}\nenv: {}\njobs:\n  publish:\n"
                        + "    steps:\n      - run: gh --version\n");

        assertThat(check(directory))
                .as("the presence of workflow env must fail closed even when empty: %s", stderr())
                .isEqualTo(Stop.REFUSED);
        assertThat(stderr())
                .as("refusing inherited env must not depend on its current entries")
                .contains("release.yml:2: job 'publish' writes and so may inherit no workflow env");
    }

    @Test
    void refusesAnAllowedNameInWorkflowEnvForTheProtectedJob() throws IOException {
        named(
                "release.yml",
                "permissions: {}\nenv: {TAG: stable}\njobs:\n  publish:\n"
                        + "    steps:\n      - run: gh --version\n");

        assertThat(check(directory))
                .as(
                        "the job's env allowlist must not authorize inherited workflow env: %s",
                        stderr())
                .isEqualTo(Stop.REFUSED);
        assertThat(stderr())
                .as("workflow env must be refused independently of name validation")
                .contains("release.yml:2: job 'publish' writes and so may inherit no workflow env");
    }

    @Test
    void refusesAShellStartupFileInTheProtectedJobsEnv() throws IOException {
        named(
                "release.yml",
                "permissions: {}\njobs:\n  publish:\n    env: {BASH_ENV: release/x}\n"
                        + "    steps:\n      - run: gh --version\n");

        assertThat(check(directory))
                .as("BASH_ENV can source fetched code before a command: %s", stderr())
                .isEqualTo(Stop.REFUSED);
        assertThat(stderr())
                .as("the forbidden job environment name must be explicit")
                .contains(
                        "release.yml:4: job 'publish' writes and so may set only ARTIFACT, GH_TOKEN, TAG, VERSION in env, not 'BASH_ENV'");
    }

    @Test
    void refusesAnExportedShellFunctionInAProtectedStepsEnv() throws IOException {
        named(
                "release.yml",
                "permissions: {}\njobs:\n  publish:\n    steps:\n"
                        + "      - run: gh --version\n        env: {BASH_FUNC_gh%%: '() { echo replaced; }'}\n");

        assertThat(check(directory))
                .as("an exported function can replace a runner command: %s", stderr())
                .isEqualTo(Stop.REFUSED);
        assertThat(stderr())
                .as("step environment names must obey the same restrictions")
                .contains(
                        "release.yml:6: job 'publish' writes and so may set only ARTIFACT, GH_TOKEN, TAG, VERSION in env, not 'BASH_FUNC_gh%%'");
    }

    @Test
    void refusesWorkflowEnvThatPreloadsCodeIntoTheProtectedJob() throws IOException {
        named(
                "release.yml",
                "permissions: {}\nenv: {LD_PRELOAD: x}\njobs:\n  publish:\n"
                        + "    steps:\n      - run: gh --version\n");

        assertThat(check(directory))
                .as("inherited env can load code beside the writing token: %s", stderr())
                .isEqualTo(Stop.REFUSED);
        assertThat(stderr())
                .as("workflow env must be checked for the protected job")
                .contains("release.yml:2: job 'publish' writes and so may inherit no workflow env");
    }

    @Test
    void refusesAnExpressionSubstitutedIntoAProtectedRun() throws IOException {
        named(
                "release.yml",
                "permissions: {}\njobs:\n  publish:\n    steps:\n"
                        + "      - run: echo ${{ needs.build.outputs.v }}\n");

        assertThat(check(directory))
                .as("expression substitution can turn data into shell code: %s", stderr())
                .isEqualTo(Stop.REFUSED);
        assertThat(stderr())
                .as("run expressions must be passed through env instead")
                .contains(
                        "release.yml:5: job 'publish' writes and so may take no expression in 'run'; pass it through env");
    }

    @Test
    void refusesAnExpressionSelectingTheProtectedRunner() throws IOException {
        named(
                "release.yml",
                "permissions: {}\njobs:\n  publish:\n"
                        + "    runs-on: ${{ needs.build.outputs.r }}\n"
                        + "    steps:\n      - run: gh --version\n");

        assertThat(check(directory))
                .as("an expression must not choose where the writing token runs: %s", stderr())
                .isEqualTo(Stop.REFUSED);
        assertThat(stderr())
                .as("expression checks must cover job keys as well as commands")
                .contains(
                        "release.yml:4: job 'publish' writes and so may take no expression in 'runs-on'; pass it through env");
    }

    @Test
    void refusesANonMappingProtectedEnv() throws IOException {
        named(
                "release.yml",
                "permissions: {}\njobs:\n  publish:\n    env: []\n"
                        + "    steps:\n      - run: gh --version\n");

        assertThat(check(directory))
                .as("environment names must be inspectable as a mapping: %s", stderr())
                .isEqualTo(Stop.REFUSED);
        assertThat(stderr())
                .as("a malformed env must fail closed at its location")
                .contains("release.yml:4: job 'publish'", "env must be a mapping");
    }

    @Test
    void refusesAnExpressionInANestedProtectedKey() throws IOException {
        named(
                "release.yml",
                "permissions: {}\njobs:\n  publish:\n"
                        + "    outputs:\n      '${{ needs.build.outputs.key }}': literal\n"
                        + "    steps:\n      - run: gh --version\n");

        assertThat(check(directory))
                .as("expression checks must cover nested keys too: %s", stderr())
                .isEqualTo(Stop.REFUSED);
        assertThat(stderr())
                .as("a nested key must not become an expression channel")
                .contains(
                        "release.yml:5: job 'publish' writes and so may take no expression in '${{ needs.build.outputs.key }}'; pass it through env");
    }

    @Test
    void refusesAnExpressionInANestedProtectedSequence() throws IOException {
        named(
                "release.yml",
                "permissions: {}\njobs:\n  publish:\n"
                        + "    runs-on: [self-hosted, '${{ needs.build.outputs.r }}']\n"
                        + "    steps:\n      - run: gh --version\n");

        assertThat(check(directory))
                .as("a sequence must not hide expression values: %s", stderr())
                .isEqualTo(Stop.REFUSED);
        assertThat(stderr())
                .as("nested expression values must retain their owning key")
                .contains(
                        "release.yml:4: job 'publish' writes and so may take no expression in 'runs-on'; pass it through env");
    }

    @Test
    void acceptsReleaseDataPassedThroughTheProtectedJobsEnv() throws IOException {
        named(
                "release.yml",
                """
                permissions: {}
                jobs:
                  build:
                    runs-on: ubuntu-latest
                    steps: []
                  publish:
                    needs: build
                    permissions:
                      contents: write
                    timeout-minutes: 10
                    runs-on: ubuntu-latest
                    env:
                      GH_TOKEN: ${{ github.token }}
                      TAG: ${{ needs.build.outputs.tag }}
                      VERSION: ${{ needs.build.outputs.version }}
                      ARTIFACT: ${{ needs.build.outputs.artifact }}
                    steps:
                      - name: Create release
                        env:
                          ARTIFACT: ${{ needs.build.outputs.artifact }}
                        run: gh release create "$TAG" "$ARTIFACT" --title "$VERSION"
                """);

        assertThat(check(directory))
                .as("release data must remain usable through ordinary env values: %s", stderr())
                .isEqualTo(0);
    }

    @Test
    void refusesAContainerImageStringInTheReleasesPublishJob() throws IOException {
        named(
                "release.yml",
                "permissions: {}\njobs:\n  publish:\n    container: attacker/image:latest\n"
                        + "    steps:\n      - run: gh --version\n");

        assertThat(check(directory))
                .as("a job container runs an image beside the writing token: %s", stderr())
                .isEqualTo(Stop.REFUSED);
        assertThat(stderr())
                .as("only known runner-command job keys may pass")
                .contains(
                        "release.yml:4: job 'publish' writes and so may hold only runner commands; 'container' is not one");
    }

    @Test
    void refusesAContainerImageMappingInTheReleasesPublishJob() throws IOException {
        named(
                "release.yml",
                "permissions: {}\njobs:\n  publish:\n    container: {image: attacker/image:latest}\n"
                        + "    steps:\n      - run: gh --version\n");

        assertThat(check(directory))
                .as("a mapped container still runs an image beside the writing token: %s", stderr())
                .isEqualTo(Stop.REFUSED);
        assertThat(stderr())
                .as("the container key must be refused regardless of its value's shape")
                .contains(
                        "release.yml:4: job 'publish' writes and so may hold only runner commands; 'container' is not one");
    }

    @Test
    void refusesServiceImagesInTheReleasesPublishJob() throws IOException {
        named(
                "release.yml",
                """
                permissions: {}
                jobs:
                  publish:
                    services:
                      database:
                        image: attacker/database:latest
                    steps:
                      - run: gh --version
                """);

        assertThat(check(directory))
                .as("service images run beside the writing token too: %s", stderr())
                .isEqualTo(Stop.REFUSED);
        assertThat(stderr())
                .as("services are outside the runner-command job allowlist")
                .contains(
                        "release.yml:4: job 'publish' writes and so may hold only runner commands; 'services' is not one");
    }

    @Test
    void refusesParallelStepsInTheReleasesPublishJob() throws IOException {
        named(
                "release.yml",
                "permissions: {}\njobs:\n  publish:\n    steps:\n"
                        + "      - parallel: [{uses: attacker/action@main}]\n");

        assertThat(check(directory))
                .as("nested action steps must not escape the runner-command guard: %s", stderr())
                .isEqualTo(Stop.REFUSED);
        assertThat(stderr())
                .as("unknown step keys must be refused at the step's line")
                .contains(
                        "release.yml:5: job 'publish' writes and so may hold only runner commands; 'parallel' is not one");
    }

    @Test
    void refusesAStepWithOnlyANameInTheReleasesPublishJob() throws IOException {
        named(
                "release.yml",
                "permissions: {}\njobs:\n  publish:\n    steps:\n      - name: Publish\n");

        assertThat(check(directory))
                .as("each protected step must explicitly name its runner command: %s", stderr())
                .isEqualTo(Stop.REFUSED);
        assertThat(stderr())
                .as("a missing run must name the step's line and the required key")
                .contains(
                        "release.yml:5: job 'publish' writes and so may hold only runner commands; step 'run' must be a string");
    }

    @Test
    void refusesAStrategyInTheReleasesPublishJob() throws IOException {
        named(
                "release.yml",
                "permissions: {}\njobs:\n  publish:\n    strategy: {matrix: {os: [ubuntu-latest]}}\n"
                        + "    steps:\n      - run: gh --version\n");

        assertThat(check(directory))
                .as("job execution features outside the allowlist must fail closed: %s", stderr())
                .isEqualTo(Stop.REFUSED);
        assertThat(stderr())
                .as("strategy must be refused even when every step is a runner command")
                .contains(
                        "release.yml:4: job 'publish' writes and so may hold only runner commands; 'strategy' is not one");
    }

    @Test
    void refusesANonStringRunInTheReleasesPublishJob() throws IOException {
        named("release.yml", "permissions: {}\njobs:\n  publish:\n    steps:\n      - run: 42\n");

        assertThat(check(directory))
                .as("a run key alone must not pass without a command string: %s", stderr())
                .isEqualTo(Stop.REFUSED);
        assertThat(stderr())
                .as("the malformed run must name the step's line and the key")
                .contains(
                        "release.yml:5: job 'publish' writes and so may hold only runner commands; step 'run' must be a string");
    }

    @Test
    void refusesAProtectedJobWithoutSteps() throws IOException {
        named("release.yml", "permissions: {}\njobs:\n  publish:\n    runs-on: ubuntu-latest\n");

        assertThat(check(directory))
                .as("the protected job must have an inspectable sequence of commands: %s", stderr())
                .isEqualTo(Stop.REFUSED);
        assertThat(stderr())
                .as("missing steps must fail closed at the job's location")
                .contains("release.yml:3: job 'publish'", "steps", "sequence of mappings");
    }

    @Test
    void acceptsEveryKnownRunnerCommandKeyInTheReleasesPublishJob() throws IOException {
        named(
                "release.yml",
                """
                permissions: {}
                jobs:
                  build:
                    runs-on: ubuntu-latest
                    steps: []
                  publish:
                    name: Publish release
                    runs-on: ubuntu-latest
                    timeout-minutes: 10
                    needs: build
                    if: ${{ success() }}
                    permissions:
                      contents: write
                    env:
                      TAG: stable
                    concurrency:
                      group: release
                      cancel-in-progress: false
                    outputs:
                      release: stable
                    continue-on-error: false
                    steps:
                      - name: Create release
                        id: publish
                        if: ${{ success() }}
                        run: gh --version
                        env:
                          GH_TOKEN: ${{ github.token }}
                        working-directory: .
                        timeout-minutes: 5
                        continue-on-error: false
                """);

        assertThat(check(directory))
                .as(
                        "every specified job and step key must remain usable for runner commands: %s",
                        stderr())
                .isEqualTo(0);
    }

    @Test
    void refusesACheckoutBesideTheReleasesWritingToken() throws IOException {
        named(
                "release.yml",
                "on: workflow_dispatch\npermissions: {}\njobs: # release jobs\n"
                        + "    publish: # creates the release\n"
                        + "        permissions:\n            contents: write\n"
                        + "        runs-on: ubuntu-latest\n        steps: # runner commands only\n"
                        + "            - uses: actions/checkout@"
                        + COMMIT
                        + " # v7.0.1\n"
                        + "              with:\n                  persist-credentials: false\n");

        assertThat(check(directory)).as(stderr()).isEqualTo(Stop.REFUSED);
        assertThat(stderr())
                .as("a checkout must not run beside the release publish job's writing token")
                .contains(
                        "workflows/release.yml:9: job 'publish' writes and so may hold only runner commands; 'uses' is not one");
    }

    @Test
    void refusesAFetchedActionBesideTheReleasesWritingToken() throws IOException {
        named(
                "release.yml",
                "on: workflow_dispatch\npermissions: {}\njobs: # release jobs\n"
                        + "  publish: # creates the release\n"
                        + "    permissions:\n      contents: write\n"
                        + "    runs-on: ubuntu-latest\n    steps: # runner commands only\n"
                        + "      - name: Upload files\n        uses: actions/upload-artifact@"
                        + COMMIT
                        + " # v7.0.1\n");

        assertThat(check(directory)).as(stderr()).isEqualTo(Stop.REFUSED);
        assertThat(stderr())
                .as("a pinned action must not run beside the release publish job's writing token")
                .contains(
                        "workflows/release.yml:9: job 'publish' writes and so may hold only runner commands; 'uses' is not one");
    }

    @Test
    void refusesFlowStyleStepsBesideTheReleasesWritingToken() throws IOException {
        named(
                "release.yml",
                """
                permissions: {}
                jobs:
                  publish:
                    permissions:
                      contents: write
                    steps: [{uses: attacker/action@main}]
                """);

        assertThat(check(directory))
                .as("flow-style steps can run fetched code beside the writing token: %s", stderr())
                .isEqualTo(Stop.REFUSED);
        assertThat(stderr())
                .as("the structural rule must identify the forbidden action key")
                .contains(
                        "job 'publish' writes and so may hold only runner commands; 'uses' is not one");
    }

    @Test
    void refusesAQuotedUsesKeyBesideTheReleasesWritingToken() throws IOException {
        named(
                "release.yml",
                """
                permissions: {}
                jobs:
                  publish:
                    permissions:
                      contents: write
                    steps:
                      - 'uses': attacker/action@main
                """);

        assertThat(check(directory))
                .as("a quoted key still runs fetched code beside the writing token: %s", stderr())
                .isEqualTo(Stop.REFUSED);
        assertThat(stderr())
                .as("quoting uses must not hide the fetched action")
                .contains(
                        "release.yml:7: job 'publish' writes and so may hold only runner commands; 'uses' is not one");
    }

    @Test
    void refusesStepsAliasedFromAnotherJobBesideTheReleasesWritingToken() throws IOException {
        named(
                "release.yml",
                "permissions: {}\njobs:\n  build:\n    steps: &shared\n"
                        + "      - uses: actions/checkout@"
                        + COMMIT
                        + " # v7.0.1\n"
                        + "        with:\n          persist-credentials: false\n"
                        + "  publish:\n    permissions:\n      contents: write\n    steps: *shared\n");

        assertThat(check(directory))
                .as("aliased steps run in the writing job too: %s", stderr())
                .isEqualTo(Stop.REFUSED);
        assertThat(stderr())
                .as("the alias itself must be refused without resolving its steps")
                .contains(
                        "release.yml:11: job 'publish' writes and so may use no YAML alias ('*shared')")
                .doesNotContain("release.yml:0:");
    }

    @Test
    void refusesAStepAliasedFromAnotherJobBesideTheReleasesWritingToken() throws IOException {
        named(
                "release.yml",
                """
                permissions: {}
                jobs:
                  build:
                    steps:
                      - &step
                        run: echo build
                  publish:
                    steps:
                      - *step
                """);

        assertThat(check(directory))
                .as("an aliased step can hide fetched code from inspection: %s", stderr())
                .isEqualTo(Stop.REFUSED);
        assertThat(stderr())
                .as("even an alias to a run step must be refused directly at its use")
                .contains(
                        "release.yml:9: job 'publish' writes and so may use no YAML alias ('*step')");
    }

    @Test
    void refusesAMergeKeyInTheReleasesPublishJob() throws IOException {
        named(
                "release.yml",
                "permissions: {}\njobs:\n  build:\n    steps:\n"
                        + "      - &base\n        uses: actions/checkout@"
                        + COMMIT
                        + " # v7.0.1\n"
                        + "        with:\n          persist-credentials: false\n"
                        + "  publish:\n    steps:\n      - <<: *base\n");

        assertThat(check(directory))
                .as("a merge can hide fetched code from inspection: %s", stderr())
                .isEqualTo(Stop.REFUSED);
        assertThat(stderr())
                .as("both the merge key and the alias must be refused in the protected job")
                .contains("release.yml:11: job 'publish' writes and so may use no YAML merge key")
                .contains(
                        "release.yml:11: job 'publish' writes and so may use no YAML alias ('*base')");
    }

    @Test
    void acceptsAliasesUsedOnlyInTheReleasesBuildJob() throws IOException {
        named(
                "release.yml",
                """
                permissions: {}
                jobs:
                  build:
                    steps:
                      - &step
                        run: echo build
                      - *step
                  publish:
                    permissions:
                      contents: write
                    steps:
                      - run: gh --version
                """);

        assertThat(check(directory))
                .as("aliases outside the protected job must remain accepted: %s", stderr())
                .isEqualTo(0);
    }

    @Test
    void refusesAnAliasAtTheJobsMapping() throws IOException {
        named(
                "release.yml",
                """
                permissions: {}
                template: &all_jobs
                  publish:
                    steps: []
                jobs: *all_jobs
                """);

        assertThat(check(directory))
                .as("aliasing jobs must not hide the protected job: %s", stderr())
                .isEqualTo(Stop.REFUSED);
        assertThat(stderr())
                .as("the alias at jobs must be refused for the protected job")
                .contains(
                        "release.yml:5: job 'publish' writes and so may use no YAML alias ('*all_jobs')");
    }

    @Test
    void refusesAnAliasAtTheReleasesPublishJob() throws IOException {
        named(
                "release.yml",
                """
                permissions: {}
                jobs:
                  build: &base
                    steps: []
                  publish: *base
                """);

        assertThat(check(directory))
                .as("aliasing the job itself must fail closed: %s", stderr())
                .isEqualTo(Stop.REFUSED);
        assertThat(stderr())
                .as("the alias at the protected job must be refused directly")
                .contains(
                        "release.yml:5: job 'publish' writes and so may use no YAML alias ('*base')");
    }

    @Test
    void refusesAnAliasNestedInsideTheReleasesPublishJob() throws IOException {
        named(
                "release.yml",
                """
                permissions: {}
                jobs:
                  build:
                    env:
                      MESSAGE: &message hello
                    steps: []
                  publish:
                    steps:
                      - run: echo "$MESSAGE"
                        env:
                          MESSAGE: *message
                """);

        assertThat(check(directory))
                .as("every position inside the protected job must reject aliases: %s", stderr())
                .isEqualTo(Stop.REFUSED);
        assertThat(stderr())
                .as("a nested alias must carry its actual source location")
                .contains(
                        "release.yml:11: job 'publish' writes and so may use no YAML alias ('*message')");
    }

    @Test
    void refusesANestedMergeKeyWithoutAnAliasInTheReleasesPublishJob() throws IOException {
        named(
                "release.yml",
                """
                permissions: {}
                jobs:
                  publish:
                    steps:
                      - run: echo "$MESSAGE"
                        env:
                          '<<': {MESSAGE: hello}
                """);

        assertThat(check(directory))
                .as("a merge key must fail closed even without an alias: %s", stderr())
                .isEqualTo(Stop.REFUSED);
        assertThat(stderr())
                .as("quoted and nested merge keys must be refused independently of aliases")
                .contains("release.yml:7: job 'publish' writes and so may use no YAML merge key");
    }

    @Test
    void refusesACheckoutInAQuotedPublishJob() throws IOException {
        named(
                "release.yml",
                "permissions: {}\njobs:\n  'publish':\n    permissions:\n      contents: write\n"
                        + "    steps:\n      - uses: actions/checkout@"
                        + COMMIT
                        + " # v7.0.1\n"
                        + "        with:\n          persist-credentials: false\n");

        assertThat(check(directory))
                .as("quoting the job id must not allow fetched code: %s", stderr())
                .isEqualTo(Stop.REFUSED);
        assertThat(stderr())
                .as("a permissions refusal alone does not enforce the fetched-code rule")
                .contains(
                        "release.yml:7: job 'publish' writes and so may hold only runner commands; 'uses' is not one");
    }

    @Test
    void refusesAnUnreadableReleaseWorkflowAndNamesTheDuplicateKey() throws IOException {
        named(
                "release.yml",
                """
                permissions: {}
                jobs:
                  publish:
                    permissions:
                      contents: write
                    steps: []
                    steps: []
                """);

        assertThat(check(directory))
                .as("an unreadable protected workflow must fail closed: %s", stderr())
                .isEqualTo(Stop.REFUSED);
        assertThat(stderr())
                .as("the refusal must identify the file and the parser's failure")
                .contains("workflows/release.yml:", "invalid YAML", "Duplicate field 'steps'");
    }

    @Test
    void refusesFetchedCodeUnderAQuotedJobsKey() throws IOException {
        named(
                "release.yml",
                """
                permissions: {}
                'jobs':
                  publish:
                    steps: [{'uses': attacker/action@main}]
                """);

        assertThat(check(directory))
                .as("quoted jobs must be checked independently of permissions: %s", stderr())
                .isEqualTo(Stop.REFUSED);
        assertThat(stderr())
                .as("the quoted jobs key must lead to the protected job")
                .contains(
                        "job 'publish' writes and so may hold only runner commands; 'uses' is not one");
    }

    @Test
    void refusesFetchedCodeAfterAQuotedScalarContainingAFakeJobsKey() throws IOException {
        named(
                "release.yml",
                """
                name: 'a workflow with
                jobs:
                                fake: text'
                permissions: {}
                jobs:
                  publish:
                    steps:
                      - uses: ./.github/actions/fetched
                """);

        assertThat(check(directory))
                .as("scalar content must not change which jobs are protected: %s", stderr())
                .isEqualTo(Stop.REFUSED);
        assertThat(stderr())
                .as("the real publish job must be checked despite the fake jobs key")
                .contains(
                        "job 'publish' writes and so may hold only runner commands; 'uses' is not one");
    }

    @Test
    void refusesAJobLevelUsesBesideTheReleasesWritingToken() throws IOException {
        named(
                "release.yml",
                """
                permissions: {}
                jobs:
                  publish: {uses: 'attacker/repo/.github/workflows/release.yml@main'}
                """);

        assertThat(check(directory))
                .as("a reusable workflow also runs fetched code: %s", stderr())
                .isEqualTo(Stop.REFUSED);
        assertThat(stderr())
                .as("the job-level uses must be refused by the fetched-code rule")
                .contains(
                        "job 'publish' writes and so may hold only runner commands; 'uses' is not one");
    }

    @Test
    void refusesStepsThatAreNotASequence() throws IOException {
        named("release.yml", "permissions: {}\njobs:\n  publish:\n    steps: {}\n");

        assertThat(check(directory))
                .as("an uninspectable steps value must fail closed: %s", stderr())
                .isEqualTo(Stop.REFUSED);
        assertThat(stderr())
                .as("the refusal must name the invalid steps shape")
                .contains("job 'publish'", "steps", "sequence of mappings");
    }

    @Test
    void refusesStepsContainingANonMapping() throws IOException {
        named("release.yml", "permissions: {}\njobs:\n  publish:\n    steps: [42]\n");

        assertThat(check(directory))
                .as("every step must be inspectable as a mapping: %s", stderr())
                .isEqualTo(Stop.REFUSED);
        assertThat(stderr())
                .as("the refusal must name the invalid sequence member")
                .contains("job 'publish'", "steps", "sequence of mappings");
    }

    @Test
    void acceptsACheckoutInAnotherReleaseJobThatWritesNothing() throws IOException {
        named(
                "release.yml",
                "on: workflow_dispatch\npermissions: {}\njobs:\n    publish:\n"
                        + "        permissions:\n            contents: write\n"
                        + "        runs-on: ubuntu-latest\n"
                        + "        steps:\n            - run: gh --version\n"
                        + "    build:\n        runs-on: ubuntu-latest\n"
                        + "        steps:\n            - uses: actions/checkout@"
                        + COMMIT
                        + " # v7.0.1\n"
                        + "              with:\n                  persist-credentials: false\n");

        assertThat(check(directory)).as(stderr()).isEqualTo(0);
    }

    @Test
    void refusesContentsWriteInTheReleasesBuildJob() throws IOException {
        named(
                "release.yml",
                "on: workflow_dispatch\npermissions: {}\njobs:\n    build:\n"
                        + "        permissions:\n            contents: write\n"
                        + "        runs-on: ubuntu-latest\n"
                        + "        steps:\n            - run: gh --version\n");

        assertThat(check(directory)).as(stderr()).isEqualTo(Stop.REFUSED);
        assertThat(stderr())
                .as("the release build job has no listed write")
                .contains("workflows/release.yml")
                .contains("job 'build'")
                .contains("'contents: write'")
                .contains("allows no write there");
    }

    @Test
    void refusesActionsWriteInTheReleasesPublishJob() throws IOException {
        named(
                "release.yml",
                "on: workflow_dispatch\npermissions: {}\njobs:\n    publish:\n"
                        + "        permissions:\n            actions: write\n"
                        + "        runs-on: ubuntu-latest\n"
                        + "        steps:\n            - run: gh --version\n");

        assertThat(check(directory)).as(stderr()).isEqualTo(Stop.REFUSED);
        assertThat(stderr())
                .as("the release publish job cannot write actions")
                .contains("workflows/release.yml")
                .contains("job 'publish'")
                .contains("'actions: write'");
    }

    @Test
    void readsTheJobsOfAWorkflowWhoseJobsKeyCarriesAComment() throws IOException {
        // Read as GitHub reads it: a comment after 'jobs:' does not make the file no workflow.
        named(
                "build.yml",
                "on: push\njobs:   # the one job\n  build:\n    permissions:\n"
                        + "      contents: write\n    runs-on: ubuntu-latest\n"
                        + STEP);

        assertThat(check(directory)).as(stderr()).isEqualTo(Stop.REFUSED);
        assertThat(stderr())
                .as("both the missing top and the job's write")
                .contains("no 'permissions:' at its top")
                .contains("'contents: write' in job 'build'");
    }

    @Test
    void acceptsTheWritesTheListNamesInTheirJobs() throws IOException {
        named(
                "delete-runs.yml",
                "on: workflow_dispatch\npermissions: {}\njobs:\n  delete:\n    permissions:\n"
                        + "      actions: write\n    runs-on: ubuntu-latest\n"
                        + STEP);
        Files.writeString(
                directory.resolve("workflows/update.yml"),
                "on: workflow_dispatch\npermissions:\n"
                        + "  contents: read\njobs:\n  update:\n    permissions:\n      contents: write\n"
                        + "      pull-requests: write\n    runs-on: ubuntu-latest\n"
                        + STEP);

        assertThat(check(directory)).as(stderr()).isEqualTo(0);
    }

    @Test
    void acceptsTheAttestationInTheBuildsWindowsJobOnly() throws IOException {
        named(
                "build.yml",
                "on: push\npermissions:\n  contents: read\njobs:\n  windows:\n    permissions:\n"
                        + "      contents: read\n      id-token: write\n      attestations: write\n    runs-on: windows-latest\n"
                        + STEP
                        + "  test:\n    permissions:\n      id-token: write\n    runs-on: windows-latest\n"
                        + STEP);

        assertThat(check(directory)).isEqualTo(Stop.REFUSED);
        assertThat(stderr()).contains("job 'test'").doesNotContain("job 'windows'");
    }

    @Test
    void refusesAWriteTheListNamesForAnotherPermission() throws IOException {
        named(
                "delete-runs.yml",
                "on: workflow_dispatch\npermissions: {}\njobs:\n  delete:\n    permissions:\n"
                        + "      actions: write\n      contents: write\n    runs-on: ubuntu-latest\n"
                        + STEP);

        assertThat(check(directory)).isEqualTo(Stop.REFUSED);
        assertThat(stderr()).contains("'contents: write'").contains("allows only actions there");
    }

    private void workflow(String text) throws IOException {
        if (!Files.exists(directory.resolve("dependabot.yml"))) {
            Files.writeString(
                    directory.resolve("dependabot.yml"),
                    "updates:\n  - package-ecosystem: github-actions\n    directories: [\"/\", \"/.github/actions/*\"]\n"
                            + "    cooldown:\n      default-days: 3\n    groups:\n      actions:\n        patterns: [\"*\"]\n");
        }
        Files.createDirectories(directory.resolve("workflows"));
        Files.writeString(directory.resolve("workflows/ci.yml"), text);
    }

    private int check(Path under) {
        return new CheckActions(
                        new PrintStream(out, true, StandardCharsets.UTF_8),
                        new PrintStream(err, true, StandardCharsets.UTF_8))
                .check(under);
    }

    private String stdout() {
        return out.toString(StandardCharsets.UTF_8);
    }

    private String stderr() {
        return err.toString(StandardCharsets.UTF_8);
    }
}
