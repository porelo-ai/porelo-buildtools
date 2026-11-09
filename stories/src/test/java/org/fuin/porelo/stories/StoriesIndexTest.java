package org.fuin.porelo.stories;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class StoriesIndexTest {
    @TempDir Path work;
    private static final IssueSource SOURCE = repository -> "# Issues\n";

    private Path fixture(String name) throws IOException {
        final Path root = Files.createDirectories(work.resolve(name));
        final Path dir = Files.createDirectory(root.resolve("stories"));
        Files.writeString(
                root.resolve("project.yml"),
                "project:\n  central: git@github.com:porelo-ai/porelo-project.git\nrepositories: {}\n");
        Files.writeString(
                dir.resolve("ST1.md"),
                """
                # ST1 — A title

                ## Data

                ```json
                {"id":"ST1","title":"A title","state":"Idea","priority":"High","gain":"A gain.","test":"A test.","issues":[],"tested":null}
                ```
                """);
        return dir;
    }

    // Prevents a writer from truncating content held by an existing reader or hard link.
    @Test
    void replacesChangedFilesWithoutChangingTheirPreviousInodes() throws IOException {
        final Path dir = fixture("atomic");
        assertThat(StoriesIndex.write(dir, SOURCE).faults()).as("fixture is valid").isEmpty();
        final Path story = dir.resolve("ST1.md");
        Files.writeString(
                story,
                Files.readString(story)
                        .replace("\"gain\":\"A gain.\"", "\"gain\":\"A changed gain.\""));
        final Path oldStory = Files.createLink(work.resolve("old-story"), story);
        final Path index = dir.resolve("README.md");
        final Path oldIndex = Files.createLink(work.resolve("old-index"), index);
        final byte[] storyBefore = Files.readAllBytes(story);
        final byte[] indexBefore = Files.readAllBytes(index);
        final var result = StoriesIndex.write(dir, SOURCE);
        assertThat(result.written())
                .as("only both changed files are published")
                .containsExactly(story, index);
        assertThat(Files.readAllBytes(oldStory))
                .as("the previous story inode is intact")
                .isEqualTo(storyBefore);
        assertThat(Files.readAllBytes(oldIndex))
                .as("the previous index inode is intact")
                .isEqualTo(indexBefore);
        assertThat(Files.isSameFile(oldStory, story))
                .as("the story path now names a replacement inode")
                .isFalse();
        assertThat(Files.isSameFile(oldIndex, index))
                .as("the index path now names a replacement inode")
                .isFalse();
        assertThat(StoriesIndex.check(dir, SOURCE).faults())
                .as("both published files are complete")
                .isEmpty();
        try (var files = Files.list(dir)) {
            assertThat(files.map(file -> file.getFileName().toString()).sorted().toList())
                    .as("no temporary files remain")
                    .containsExactly("README.md", "ST1.md");
        }
    }

    // Prevents a no-op regeneration from touching outputs or falsely reporting changed files.
    @Test
    void leavesUnchangedFilesAndTheirTimestampsAlone() throws IOException {
        final Path dir = fixture("unchanged");
        StoriesIndex.write(dir, SOURCE);
        final FileTime sentinel = FileTime.fromMillis(1234000L);
        for (final String name : new String[] {"README.md", "ST1.md"})
            Files.setLastModifiedTime(dir.resolve(name), sentinel);
        assertThat(StoriesIndex.write(dir, SOURCE).written())
                .as("an unchanged tree writes nothing")
                .isEmpty();
        for (final String name : new String[] {"README.md", "ST1.md"})
            assertThat(Files.getLastModifiedTime(dir.resolve(name)))
                    .as("unchanged timestamp for %s", name)
                    .isEqualTo(sentinel);
    }

    // Prevents generated files from escaping the requested directory through any symlink.
    @ParameterizedTest
    @CsvSource({
        "write,story",
        "check,story",
        "write,index",
        "check,index",
        "write,directory",
        "check,directory"
    })
    void refusesSymlinksBeforeChangingAnyFile(String operation, String kind) throws IOException {
        Path dir = fixture(operation + kind);
        final Path story = dir.resolve("ST1.md");
        final byte[] before = Files.readAllBytes(story);
        final Path external = work.resolve(operation + kind + "-outside");
        Files.write(external, before);
        final Path link;
        if (kind.equals("directory")) {
            link = dir.resolveSibling("alias");
            Files.createSymbolicLink(link, dir);
            dir = link;
        } else {
            link = dir.resolve(kind.equals("story") ? "ST1.md" : "README.md");
            Files.deleteIfExists(link);
            Files.createSymbolicLink(link, external);
        }
        final Path checked = dir;
        assertThatThrownBy(
                        () -> {
                            if (operation.equals("write")) StoriesIndex.write(checked, SOURCE);
                            else StoriesIndex.check(checked, SOURCE);
                        })
                .as("%s refuses a symlinked %s", operation, kind)
                .isInstanceOf(IOException.class)
                .hasMessageContaining(link.toString());
        assertThat(Files.readAllBytes(external))
                .as("the external target was not changed")
                .isEqualTo(before);
        assertThat(Files.readAllBytes(story))
                .as("no story changed before refusal")
                .isEqualTo(before);
    }
}
