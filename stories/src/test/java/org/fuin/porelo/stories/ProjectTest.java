package org.fuin.porelo.stories;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ProjectTest {
    @TempDir Path work;

    private static final String PROJECT =
            "project:\n  central: git@github.com:porelo-ai/porelo-project.git\nrepositories: {}\n";

    private Path write(byte[]... parts) throws IOException {
        final Path file = work.resolve("project.yml");
        try (var out = Files.newOutputStream(file)) {
            for (final byte[] part : parts) out.write(part);
        }
        return work.resolve("stories/README.md");
    }

    // Prevents a project.yml saved by an editor that writes a byte order mark from being refused.
    @Test
    void aProjectFileOpeningWithAByteOrderMarkIsRead() throws IOException {
        final Path plan =
                write(
                        new byte[] {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF},
                        PROJECT.getBytes(StandardCharsets.UTF_8));

        assertThat(Project.read(plan, repository -> Map.of()).own())
                .as("the own repository read past the mark")
                .isEqualTo("porelo-project");
    }

    // Prevents a project.yml that is no UTF-8 from escaping the refusal a malformed project gets.
    @Test
    void aProjectFileThatIsNoUtf8IsRefusedAsInvalidYaml() throws IOException {
        final Path plan =
                write(
                        "project:\n  central: \"git@github.com:porelo-ai/porelo-"
                                .getBytes(StandardCharsets.UTF_8),
                        new byte[] {(byte) 0xFF},
                        ".git\"\nrepositories: {}\n".getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> Project.read(plan, repository -> Map.of()))
                .as("the configuration refusal it got before, naming the file")
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageStartingWith(work.resolve("project.yml") + ": invalid YAML: ")
                .hasMessageContaining("Invalid UTF-8");
    }

    private static String project(String central, String app, String lib) {
        return "project:\n  central: "
                + central
                + "\nrepositories:\n  a:\n    central: "
                + app
                + "\n    issues: [A]\n  b:\n    upstream: "
                + lib
                + "\n    issues: [B]\n";
    }

    // Prevents a project whose repositories live on its own Git server or under another owner from
    // being refused.
    @Test
    void readsTheCentralsOfAnyHostAndOwnerInEachForm() {
        final Project read =
                Project.read(
                        project(
                                "git@example.org:team/docs",
                                "https://git.example.org/team/app.git",
                                "ssh://git@git.example.org:2222/team/lib.git"),
                        Set.of());

        assertThat(read.own()).isEqualTo("docs");
        assertThat(read.repositories()).containsOnlyKeys("docs", "app", "lib");
        assertThat(read.central("lib")).isEqualTo("ssh://git@git.example.org:2222/team/lib.git");
    }

    // Prevents porelo-project's own centrals from reading under other names than before.
    @Test
    void readsPoreloAisCentralsAsBefore() {
        final Project read =
                Project.read(
                        project(
                                "git@github.com:porelo-ai/porelo-project.git",
                                "https://github.com/porelo-ai/porelo",
                                "git@github.com:porelo-ai/porelo-host.git"),
                        Set.of());

        assertThat(read.repositories()).containsOnlyKeys("porelo-project", "porelo", "porelo-host");
    }

    // Prevents a central Git would read as a command, a local path or a secret from being taken.
    @Test
    void refusesWhatIsNoCloneUrlNamingTheKeyAndTheValue() {
        for (final String central :
                List.of(
                        "file:///srv/git/app.git",
                        "ext::sh",
                        "user:secret@example.org:team/app.git",
                        "C:team/app.git",
                        "git@example.org:-oProxy/app.git",
                        "-example.org:team/app",
                        "https://example.org/team\\x01x/app.git",
                        "/srv/git/app.git",
                        "../app.git",
                        "-uploadpack=x:app",
                        "-oProxy@example.org:team/app",
                        "https://user:secret@example.org/team/app.git",
                        "https://example.org/team/a\tpp.git",
                        "https://example.org/team/ app.git",
                        "https://example.org/team/",
                        "https://example.org/team/.git",
                        "https://example.org/team/_app.git",
                        "git@example.org:",
                        "http://example.org/team/app.git",
                        "https://example.org")) {
            assertThatThrownBy(
                            () ->
                                    Project.read(
                                            project(
                                                    "git@example.org:team/docs",
                                                    "\"" + central + "\"",
                                                    "git@example.org:team/lib"),
                                            Set.of()))
                    .as(central)
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage(
                            "project.yml repositories.a: unsupported repository central "
                                    + central.replace("\\t", "\t").replace("\\x01", "\u0001"));
        }
    }

    // Prevents two repositories from being read under one name, wherever they live.
    @Test
    void refusesTwoRepositoriesOfOneNameOnDifferentHosts() {
        assertThatThrownBy(
                        () ->
                                Project.read(
                                        project(
                                                "git@example.org:team/docs",
                                                "https://one.example.org/team/app.git",
                                                "git@two.example.org:other/app"),
                                        Set.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("project.yml repositories.b: duplicate repository app");
    }
}
