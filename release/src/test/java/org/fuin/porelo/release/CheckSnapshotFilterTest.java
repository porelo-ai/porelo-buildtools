package org.fuin.porelo.release;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Guards a private group's names and a task branch's snapshots against a repository's own copy. */
class CheckSnapshotFilterTest {

    private static final String SWITCH =
            "-Daether.remoteRepositoryFilter.groupId=true\n"
                    + "-Daether.remoteRepositoryFilter.groupId.basedir="
                    + "${session.rootDirectory}/.mvn/rrf\n";

    private static final String GUARDED =
            """
            <settings><profiles><profile><id>central</id><activation>
            <property><name>aether.remoteRepositoryFilter.groupId</name><value>true</value></property>
            <file><exists>${maven.multiModuleProjectDirectory}/.mvn/rrf/\
            groupId-central.sonatype.snapshots.txt</exists></file>
            </activation><repositories><repository><id>central.sonatype.snapshots</id></repository>
            </repositories></profile></profiles></settings>
            """;

    private static final String DEPLOY =
            """
            jobs:
                deploy:
                    if: github.ref == 'refs/heads/main' || startsWith(github.ref, 'refs/tags/v')
                    steps:
                        - run: ./mvnw deploy -P"$PORELO_DEPLOY_PROFILE"
            """;

    @TempDir Path root;

    private final ByteArrayOutputStream out = new ByteArrayOutputStream();

    private final ByteArrayOutputStream err = new ByteArrayOutputStream();

    @BeforeEach
    void aRepositoryInTheShape() throws IOException {
        write("settings.xml", GUARDED);
        write(".mvn/maven.config", SWITCH);
        write(".mvn/rrf/groupId-central.sonatype.snapshots.txt", "org.fuin.porelo\n");
        write(".github/workflows/build.yml", DEPLOY);
    }

    @Test
    void acceptsARepositoryInTheShape() throws IOException {
        assertThat(check()).as("the shape itself: %s", err).isZero();
    }

    @Test
    void acceptsARepositoryThatResolvesNoSnapshots() throws IOException {
        write("settings.xml", "<settings><servers/></settings>\n");
        Files.delete(root.resolve(".mvn/maven.config"));
        Files.delete(root.resolve(".mvn/rrf/groupId-central.sonatype.snapshots.txt"));
        assertThat(check()).as("nothing to guard: %s", err).isZero();
    }

    // Prevents every name being asked of the central source, as a filter with no file asks
    // everything.
    @Test
    void refusesAMissingFiltersFile() throws IOException {
        Files.delete(root.resolve(".mvn/rrf/groupId-central.sonatype.snapshots.txt"));
        assertRefused("groupId-central.sonatype.snapshots.txt");
    }

    // Prevents a repository believing it filters while the switch is off or its file is not read.
    @Test
    void refusesASwitchThatIsOffOrReadsNoFile() throws IOException {
        write(".mvn/maven.config", "-Daether.remoteRepositoryFilter.groupId=true\n");
        assertRefused(".mvn/maven.config");
        Files.delete(root.resolve(".mvn/maven.config"));
        assertRefused(".mvn/maven.config");
    }

    // Prevents the central repository being read whether or not the filter is on.
    @Test
    void refusesACentralRepositoryOutsideTheGuardedProfile() throws IOException {
        write(
                "settings.xml",
                """
                <settings><profiles><profile><id>standard</id><activation>
                <property><name>!env.MAVEN_SNAPSHOTS_URL</name></property></activation>
                <repositories><repository><id>central.sonatype.snapshots</id></repository>
                </repositories></profile></profiles></settings>
                """);
        assertRefused("standard");
    }

    // Prevents a pom reading Sonatype beside the guarded profile, where no filter or switch holds
    // it.
    @Test
    void refusesAPomThatNamesSonatypesSnapshots() throws IOException {
        write(
                "build/pom.xml",
                """
                <project><repositories><repository><id>any</id>
                <url>https://central.sonatype.com/repository/maven-snapshots/</url>
                </repository></repositories></project>
                """);
        assertRefused("build/pom.xml");
    }

    @Test
    void refusesAPomThatNamesItAsAPluginRepository() throws IOException {
        write(
                "pom.xml",
                """
                <project><pluginRepositories><pluginRepository><id>central.sonatype.snapshots</id>
                <url>https://elsewhere.example/</url>
                </pluginRepository></pluginRepositories></project>
                """);
        assertRefused("pom.xml");
    }

    // A pom deploying to Sonatype reads nothing from it, so it stays free to name it.
    @Test
    void acceptsAPomThatOnlyDeploysThere() throws IOException {
        write(
                "pom.xml",
                """
                <project><distributionManagement><snapshotRepository><id>central.sonatype.snapshots</id>
                <url>https://central.sonatype.com/repository/maven-snapshots/</url>
                </snapshotRepository></distributionManagement></project>
                """);
        assertThat(check()).as("only deploying: %s", err).isZero();
    }

    @Test
    void readsNoPomABuildMade() throws IOException {
        write(
                "target/pom.xml",
                """
                <project><repositories><repository><id>central.sonatype.snapshots</id>
                </repository></repositories></project>
                """);
        assertThat(check()).as("a build's output: %s", err).isZero();
    }

    // Prevents a task branch's snapshot becoming the newest for every build on the host.
    @Test
    void refusesADeployThatIsNotHeldToMainAndTags() throws IOException {
        write(
                ".github/workflows/build.yml",
                """
                jobs:
                    deploy:
                        steps:
                            - run: ./mvnw deploy -P"$PORELO_DEPLOY_PROFILE"
                """);
        assertRefused("build.yml");
    }

    @Test
    void acceptsADeployHeldOnItsOwnStep() throws IOException {
        write(
                ".github/workflows/build.yml",
                """
                jobs:
                    deploy:
                        steps:
                            - if: github.ref == 'refs/heads/main' || startsWith(github.ref, 'refs/tags/v')
                              run: ./mvnw deploy -P"$PORELO_DEPLOY_PROFILE"
                """);
        assertThat(check()).as("held on the step: %s", err).isZero();
    }

    // The repository's own settings hold the rule they set for every other.
    @Tag("documents")
    @Test
    void thisRepositoryHoldsTheShape() throws IOException {
        assertThat(
                        new CheckSnapshotFilter(new PrintStream(out), new PrintStream(err))
                                .check(Path.of("..")))
                .as("this repository: %s", err)
                .isZero();
    }

    private void assertRefused(String named) throws IOException {
        err.reset();
        assertThat(check()).as("refused for %s", named).isEqualTo(Stop.REFUSED);
        assertThat(err.toString(StandardCharsets.UTF_8)).as("the refusal names it").contains(named);
    }

    private int check() throws IOException {
        return new CheckSnapshotFilter(new PrintStream(out), new PrintStream(err)).check(root);
    }

    private void write(String path, String text) throws IOException {
        final Path file = root.resolve(path);
        Files.createDirectories(file.getParent());
        Files.writeString(file, text);
    }
}
