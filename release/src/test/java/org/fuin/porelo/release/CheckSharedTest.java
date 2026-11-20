package org.fuin.porelo.release;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CheckSharedTest {

    private static final String TEXT =
            "Identical in every repository.\n\n- **A rule.** Its reason.\n";

    @TempDir Path directory;

    private final ByteArrayOutputStream out = new ByteArrayOutputStream();

    private final ByteArrayOutputStream err = new ByteArrayOutputStream();

    @Test
    void acceptsABlockWhoseMarkersCarryTheHashOfTheLinesBetweenThem() throws IOException {
        final Path file =
                file("# Rules\n\n" + block("Shared Area", hash(TEXT), TEXT) + "\n## Mine\n");

        assertThat(check(file)).as(stderr()).isEqualTo(0);
        assertThat(stdout())
                .as("the block named, with its hash")
                .contains("Shared Area")
                .contains(hash(TEXT));
    }

    @Test
    void acceptsTheHashTheSedCommandPrintsForTheSameText() throws IOException {
        // The hash agents compute by hand: sed -n '/BEGIN/,/END/p' | sed '1d;$d' | sha256sum | cut
        // -c1-16.
        final String text = "line one\nline two\n";
        assertThat(hash(text))
                .as("the first 16 hex digits of the SHA-256 of the lines, each ending in a newline")
                .isEqualTo(CheckShared.hashOf(java.util.List.of("line one", "line two")));
    }

    @Test
    void refusesABlockWhoseTextChangedAndSaysBothHashes() throws IOException {
        final Path file =
                file(block("Shared Area", hash(TEXT), TEXT.replace("A rule", "A changed rule")));

        assertThat(check(file)).isEqualTo(Stop.REFUSED);
        assertThat(stderr())
                .as("what the markers say and what the text is")
                .contains("Shared Area")
                .contains("markers say " + hash(TEXT))
                .contains("the text is " + hash(TEXT.replace("A rule", "A changed rule")));
    }

    @Test
    void refusesMarkersThatDisagree() throws IOException {
        final String text =
                "> **BEGIN Shared Area** · sha256 `"
                        + hash(TEXT)
                        + "` · changed 2026-10-05T06:23Z\n"
                        + TEXT
                        + "> **END Shared Area** · sha256 `0000000000000000`\n";

        assertThat(check(file(text))).isEqualTo(Stop.REFUSED);
        assertThat(stderr()).contains("BEGIN and END carry different hashes");
    }

    @Test
    void refusesAMissingEnd() throws IOException {
        final String text =
                "> **BEGIN Shared Area** · sha256 `"
                        + hash(TEXT)
                        + "` · changed 2026-10-05T06:23Z\n"
                        + TEXT;

        assertThat(check(file(text))).isEqualTo(Stop.REFUSED);
        assertThat(stderr()).contains("Shared Area").contains("no END");
    }

    @Test
    void refusesAnEndWithoutItsBegin() throws IOException {
        assertThat(check(file(TEXT + "> **END Shared Area** · sha256 `" + hash(TEXT) + "`\n")))
                .isEqualTo(Stop.REFUSED);
        assertThat(stderr()).contains("END Shared Area").contains("without its BEGIN");
    }

    @Test
    void refusesABlockThatAppearsTwice() throws IOException {
        final String one = block("Shared Area", hash(TEXT), TEXT);

        assertThat(check(file(one + "\n" + one))).isEqualTo(Stop.REFUSED);
        assertThat(stderr()).contains("Shared Area").contains("twice");
    }

    @Test
    void refusesAFileWithNoBlockAtAll() throws IOException {
        assertThat(check(file("# Rules\n\nNothing shared here.\n"))).isEqualTo(Stop.REFUSED);
        assertThat(stderr()).contains("no shared block");
    }

    @Test
    void checksSeveralBlocksInOneFileEachOnItsOwn() throws IOException {
        final String other = "Only on this machine.\n";
        final String adapters = "Only in the agent adapters.\n";
        final Path file =
                file(
                        block("Local Shared Area", hash(other), other)
                                + "\n"
                                + block("Shared Area", hash(TEXT), TEXT)
                                + "\n"
                                + block("Agent Adapter Area", hash(adapters), adapters));

        assertThat(check(file)).as(stderr()).isEqualTo(0);
        assertThat(stdout())
                .as("every block a file has, whatever its name")
                .contains("Local Shared Area")
                .contains("OK    Shared Area")
                .contains("Agent Adapter Area");
    }

    @Test
    void saysSoWhenTheFileIsMissing() {
        assertThat(check(directory.resolve("AGENTS.md"))).isEqualTo(Stop.UNANSWERED);
        assertThat(stderr()).contains("AGENTS.md");
    }

    @Test
    @Tag("documents")
    void thisRepositorysOwnRulesCarryTheirTrueHash() {
        assertThat(check(Path.of("../AGENTS.md"))).as(stderr()).isEqualTo(0);
    }

    @Test
    void acceptsAnEditorconfigIdenticalToTheSharedOne() throws IOException {
        final Path file =
                Files.writeString(
                        directory.resolve(".editorconfig"), shared(), StandardCharsets.UTF_8);

        assertThat(check(file)).as(stderr()).isEqualTo(0);
        assertThat(stdout())
                .as("the file named as the shared copy")
                .contains("OK    " + file + " is the shared");
    }

    @Test
    void refusesAnEditorconfigThatDiffersAndNamesTheFileAndTheLine() throws IOException {
        final String changed = shared().replace("max_line_length = 100", "max_line_length = 120");
        final Path file =
                Files.writeString(
                        directory.resolve(".editorconfig"), changed, StandardCharsets.UTF_8);
        final int line = List.of(shared().split("\n", -1)).indexOf("max_line_length = 100") + 1;

        assertThat(check(file)).isEqualTo(Stop.REFUSED);
        assertThat(stderr())
                .as(
                        "the file, the first line that differs, what the shared copy says and what it got")
                .contains(file + ":" + line + ": differs from the shared .editorconfig")
                .contains("shared 'max_line_length = 100'")
                .contains("got 'max_line_length = 120'");
    }

    @Test
    void refusesAnEditorconfigThatEndsEarlier() throws IOException {
        final String shorter = shared().substring(0, shared().indexOf("[*.java]"));
        final Path file =
                Files.writeString(
                        directory.resolve(".editorconfig"), shorter, StandardCharsets.UTF_8);

        assertThat(check(file)).isEqualTo(Stop.REFUSED);
        assertThat(stderr())
                .contains(file + ":")
                .contains("differs from the shared .editorconfig")
                .contains("shared '[*.java]'");
    }

    @Test
    void acceptsAPinnedShellIdenticalToTheSharedOneNamedOrNot() throws IOException {
        final Path pinned = pinned(sharedPinned());

        assertThat(check(List.of(), directory)).as(stderr()).isEqualTo(0);
        assertThat(stdout())
                .as("the copy found without being named, once")
                .containsOnlyOnce(pinned + " is the shared .github/actions/pinned-jdk/pinned.sh");

        out.reset();
        assertThat(check(List.of(pinned), directory)).as(stderr()).isEqualTo(0);
        assertThat(stdout())
                .as("named, it is checked once, not again as found")
                .containsOnlyOnce("is the shared .github/actions/pinned-jdk/pinned.sh");
    }

    @Test
    void namesAPinnedShellOnceHoweverItsPathIsWritten() throws IOException {
        final Path pinned =
                pinned(sharedPinned().replace("pinned_attempts=8", "pinned_attempts=3"));
        final Path roundabout =
                directory.resolve(".github/actions/../actions/./pinned-jdk/pinned.sh");

        assertThat(check(List.of(roundabout, pinned), directory)).isEqualTo(Stop.REFUSED);
        assertThat(stderr())
                .as("one fault for the one file, its path written roundabout")
                .containsOnlyOnce("differs from the shared .github/actions/pinned-jdk/pinned.sh")
                .contains("1 fault(s)");
    }

    @Test
    void checksTheRepositorysOwnPinnedShellBesideAnotherOneNamed() throws IOException {
        final Path own = pinned(sharedPinned().replace("pinned_attempts=8", "pinned_attempts=3"));
        final Path other = directory.resolve("other");
        Files.createDirectories(other.resolve(CheckShared.PINNED).getParent());
        Files.writeString(
                other.resolve(CheckShared.PINNED), sharedPinned(), StandardCharsets.UTF_8);

        assertThat(check(List.of(other.resolve(CheckShared.PINNED)), directory))
                .isEqualTo(Stop.REFUSED);
        assertThat(stderr()).as("the root's own copy still checked").contains(own + ":");
    }

    @Test
    void refusesAPinnedShellThatDiffersEvenWhenNotNamed() throws IOException {
        final Path agents = file(block("Shared Area", hash(TEXT), TEXT));
        final String shared = sharedPinned();
        final Path pinned = pinned(shared.replace("pinned_attempts=8", "pinned_attempts=3"));
        final int line =
                shared.substring(0, shared.indexOf("pinned_attempts=8")).split("\n", -1).length;

        assertThat(check(List.of(agents), directory)).isEqualTo(Stop.REFUSED);
        assertThat(stderr())
                .as("the copy in the repository, held to porelo-buildtools' own, by its line")
                .contains(
                        pinned
                                + ":"
                                + line
                                + ": differs from the shared .github/actions/pinned-jdk/pinned.sh")
                .contains("shared 'pinned_attempts=8', got 'pinned_attempts=3'");
    }

    @Test
    void refusesALinkInPlaceOfACopyNamedOrNotDanglingOrNot() throws IOException {
        final Path elsewhere = Files.writeString(directory.resolve("elsewhere.sh"), sharedPinned());
        final Path pinned = directory.resolve(CheckShared.PINNED);
        Files.createDirectories(pinned.getParent());
        Files.createSymbolicLink(pinned, elsewhere);

        assertThat(check(List.of(), directory)).as("a link to a true copy").isEqualTo(Stop.REFUSED);
        Files.delete(elsewhere);
        assertThat(check(List.of(pinned), directory)).as("a dangling link").isEqualTo(Stop.REFUSED);
        assertThat(stderr()).as("what it is").contains(pinned + ": a link");
    }

    @Test
    void refusesAMissingPinnedShellWhenNamed() {
        final Path pinned = directory.resolve(CheckShared.PINNED);

        assertThat(check(List.of(pinned), directory)).isEqualTo(Stop.REFUSED);
        assertThat(stderr()).as("the file asked for").contains(pinned + ": missing");
    }

    @Test
    void leavesARepositoryWithoutAPinnedShellAlone() throws IOException {
        final Path agents = file(block("Shared Area", hash(TEXT), TEXT));

        assertThat(check(List.of(agents), directory)).as(stderr()).isEqualTo(0);
        assertThat(stdout())
                .as("nothing said of a file it does not hold")
                .doesNotContain("pinned.sh");
    }

    @Test
    @Tag("documents")
    void thisRepositorysOwnPinnedShellIsTheOneTheReleaseCarries() throws IOException {
        assertThat(sharedPinned())
                .as("the release carries the pinned.sh porelo-buildtools' own action runs")
                .isEqualTo(
                        Files.readString(
                                Path.of("..").resolve(CheckShared.PINNED), StandardCharsets.UTF_8));
        assertThat(check(List.of(Path.of("../AGENTS.md")), Path.of("..")))
                .as("this repository's rules and pinned.sh, from its root: " + stderr())
                .isEqualTo(0);
        assertThat(stdout())
                .as("pinned.sh checked from the root")
                .contains("is the shared .github/actions/pinned-jdk/pinned.sh");
    }

    @Test
    void refusesAMissingEditorconfigNamingIt() {
        final Path file = directory.resolve(".editorconfig");

        assertThat(check(file)).isEqualTo(Stop.REFUSED);
        assertThat(stderr())
                .as("the file asked for")
                .contains(file + ": missing")
                .contains("the shared .editorconfig");
    }

    @Test
    void theSharedEditorconfigStatesTheJavaStyleAndTheCommonRules() {
        // Google Java Style in its AOSP variant, and what every file shares.
        assertThat(shared())
                .as("the copy porelo-release carries")
                .startsWith("# ")
                .contains("root = true")
                .contains(
                        "[*]\ncharset = utf-8\nend_of_line = lf\ninsert_final_newline = true\n"
                                + "trim_trailing_whitespace = true\nindent_style = space\nindent_size = 4\n")
                .contains("[*.java]\nmax_line_length = 100\n")
                .contains("[*.dart]\nindent_size = 2\nmax_line_length = 120\n")
                .contains("[*.py]\nmax_line_length = 100\n")
                .doesNotContain("indent_style = tab");
    }

    @Test
    @Tag("documents")
    void thisRepositorysOwnEditorconfigIsTheSharedOne() {
        assertThat(check(Path.of("../.editorconfig"))).as(stderr()).isEqualTo(0);
    }

    // A shared skill edited in one copy steers that repository's agents apart from the rest.
    @Test
    void refusesASharedSkillWhoseDescriptionWasEditedInOneCopy() throws IOException {
        sharedSkill("porelo-code", "Rules for code.");
        final Path skill = skills().resolve("porelo-code").resolve("SKILL.md");
        Files.writeString(
                skill,
                Files.readString(skill).replace("Rules for code.", "Rules for code, loosened."));

        assertThat(check(List.of(rules("porelo-code")), directory))
                .as("refused, never passed")
                .isEqualTo(Stop.REFUSED);
        assertThat(stderr())
                .as("the skill's file and both hashes")
                .contains(skill.toString())
                .contains("Shared Skill porelo-code: the markers say");
    }

    @Test
    void acceptsSharedSkillsCopiedWholeAndOwnSkillsWithTheirFrontmatter() throws IOException {
        sharedSkill("porelo-code", "Rules for code.");
        ownSkill("host-build", "description: The host's own build.\n");

        assertThat(check(List.of(rules("porelo-code")), directory)).as(stderr()).isEqualTo(0);
        assertThat(stdout())
                .as("the shared skill with its hash, and the own skill")
                .contains("OK    Shared Skill porelo-code in")
                .contains("host-build/SKILL.md: a skill of the repository's own");
    }

    @Test
    void refusesASharedSkillWhoseRulesWereEditedInOneCopy() throws IOException {
        sharedSkill("porelo-code", "Rules for code.");
        final Path skill = skills().resolve("porelo-code").resolve("SKILL.md");
        Files.writeString(skill, Files.readString(skill).replace("A rule", "A loosened rule"));

        assertThat(check(List.of(rules("porelo-code")), directory))
                .as("refused, never passed")
                .isEqualTo(Stop.REFUSED);
        assertThat(stderr())
                .as("the refusal names what is wrong")
                .contains("Shared Skill porelo-code: the markers say");
    }

    @Test
    void refusesASharedSkillInADirectoryOfAnotherName() throws IOException {
        sharedSkill("porelo-code", "Rules for code.");
        Files.move(skills().resolve("porelo-code"), skills().resolve("porelo-kode"));

        assertThat(check(List.of(rules()), directory))
                .as("refused, never passed")
                .isEqualTo(Stop.REFUSED);
        assertThat(stderr())
                .as("the frontmatter's name and the block's against the directory's")
                .contains("the frontmatter's name is porelo-code, its directory's porelo-kode")
                .contains("the block is Shared Skill porelo-code, the directory porelo-kode");
    }

    @Test
    void refusesASkillTheSharedTableNamesThatIsMissing() throws IOException {
        sharedSkill("porelo-code", "Rules for code.");

        assertThat(check(List.of(rules("porelo-code", "porelo-testing")), directory))
                .as("refused, never passed")
                .isEqualTo(Stop.REFUSED);
        assertThat(stderr())
                .as("the missing skill's file")
                .contains(skills().resolve("porelo-testing").resolve("SKILL.md") + ": missing");
    }

    @Test
    void refusesASkillTheSharedTableNamesThatIsTheRepositorysOwn() throws IOException {
        ownSkill("porelo-code", "description: Rules for code, my way.\n");

        assertThat(check(List.of(rules("porelo-code")), directory))
                .as("refused, never passed")
                .isEqualTo(Stop.REFUSED);
        assertThat(stderr())
                .as("the refusal names what is wrong")
                .contains("not the whole shared skill porelo-code");
    }

    @Test
    void refusesSkillsClaudeCodeCannotFindTheSameWay() throws IOException {
        sharedSkill("porelo-code", "Rules for code.");
        final Path link = directory.resolve(".claude").resolve("skills");
        Files.delete(link);
        Files.createDirectories(link);

        assertThat(check(List.of(rules("porelo-code")), directory))
                .as("refused, never passed")
                .isEqualTo(Stop.REFUSED);
        assertThat(stderr())
                .as("the refusal names what is wrong")
                .contains(".claude/skills: not a link");

        Files.delete(link);
        Files.createSymbolicLink(link, skills().toAbsolutePath());
        err.reset();
        assertThat(check(List.of(rules("porelo-code")), directory))
                .as("refused, never passed")
                .isEqualTo(Stop.REFUSED);
        assertThat(stderr())
                .as("the refusal names what is wrong")
                .contains("not to ../.agents/skills");

        Files.delete(link);
        err.reset();
        assertThat(check(List.of(rules("porelo-code")), directory))
                .as("refused, never passed")
                .isEqualTo(Stop.REFUSED);
        assertThat(stderr())
                .as("the refusal names what is wrong")
                .contains(".claude/skills: missing");
    }

    @Test
    void readsTheSkillsLinkByItsNamesAsItsFileSystemSeparatesThem() {
        assertThat(SharedSkills.linksToSkills("..\\.agents\\skills", "\\"))
                .as("Windows gives the right link back with backslashes")
                .isTrue();
        assertThat(SharedSkills.linksToSkills("../.agents/skills", "/"))
                .as("as Linux gives it")
                .isTrue();
        assertThat(SharedSkills.linksToSkills("..\\.agents\\skills", "/"))
                .as("backslashes are part of one name where the separator is a slash")
                .isFalse();
        assertThat(SharedSkills.linksToSkills("/repository/.agents/skills", "/"))
                .as("an absolute link")
                .isFalse();
        assertThat(SharedSkills.linksToSkills("..\\other\\skills", "\\"))
                .as("another directory")
                .isFalse();
        assertThat(SharedSkills.linksToSkills("../.agents/skills/", "/"))
                .as("a trailing separator")
                .isTrue();
    }

    @Test
    void refusesAnOwnSkillWithoutTheFrontmatterAgentskillsAsksFor() throws IOException {
        ownSkill("host-build", "");
        ownSkill("host-notes", "description: " + "x".repeat(1025) + "\n");

        assertThat(check(List.of(rules()), directory))
                .as("refused, never passed")
                .isEqualTo(Stop.REFUSED);
        assertThat(stderr())
                .as("the refusal names what is wrong")
                .contains("host-build/SKILL.md: the frontmatter has no description")
                .contains("host-notes/SKILL.md: the description is 1025 characters, at most 1024");
    }

    @Test
    void refusesAShapeThatCouldHideASkillFromTheCheck() throws IOException {
        sharedSkill("porelo-code", "Rules for code.");
        final Path elsewhere = Files.createDirectories(directory.resolve("elsewhere"));
        final Path linked = Files.createDirectories(skills().resolve("linked"));
        Files.createSymbolicLink(
                linked.resolve("SKILL.md"),
                Files.writeString(elsewhere.resolve("SKILL.md"), "---\nname: linked\n---\n"));
        Files.createDirectories(skills().resolve("empty"));
        Files.writeString(skills().resolve("README.md"), "Not a skill.\n");
        final Path twice = skills().resolve("porelo-code").resolve("SKILL.md");
        Files.writeString(
                twice,
                Files.readString(twice) + block("Shared Skill porelo-code", hash(TEXT), TEXT));

        assertThat(check(List.of(rules("porelo-code")), directory))
                .as("refused, never passed")
                .isEqualTo(Stop.REFUSED);
        assertThat(stderr())
                .as("a linked SKILL.md, a directory without one, a file beside them, two blocks")
                .contains("linked/SKILL.md: a link or not a file")
                .contains("empty/SKILL.md: missing")
                .contains("README.md: not a skill")
                .contains("a skill carries one block");
    }

    @Test
    void refusesWhatTheHashOfASharedSkillDoesNotHold() throws IOException {
        sharedSkill("porelo-code", "Rules for code.");
        sharedSkill("porelo-testing", "Rules for tests.");
        final Path code = skills().resolve("porelo-code").resolve("SKILL.md");
        Files.writeString(code, Files.readString(code) + "\n- **A rule of one copy.**\n");
        final Path testing = skills().resolve("porelo-testing").resolve("SKILL.md");
        Files.writeString(testing, Files.readString(testing).replace("\n", "\r\n"));

        assertThat(check(List.of(rules("porelo-code", "porelo-testing")), directory))
                .as("refused, never passed")
                .isEqualTo(Stop.REFUSED);
        assertThat(stderr())
                .as("a rule after the block, and line ends a copy changed")
                .contains("porelo-code/SKILL.md:")
                .contains("a line outside the block")
                .contains("porelo-testing/SKILL.md: a carriage return");
    }

    @Test
    void refusesARowOfTheSharedTableThatNamesNoSkill() throws IOException {
        sharedSkill("porelo-code", "Rules for code.");
        final String text = TEXT + "\n| Before you | Load |\n|---|---|\n| work | porelo-code |\n";

        assertThat(check(List.of(file(block("Shared Area", hash(text), text))), directory))
                .as("refused, never passed")
                .isEqualTo(Stop.REFUSED);
        assertThat(stderr())
                .as("the refusal names what is wrong")
                .contains("a row of skills to load that names none in code");
    }

    @Test
    void checksASkillsOwnFileNamedAloneWithItsFrontmatter() throws IOException {
        sharedSkill("porelo-code", "Rules for code.");
        final Path skill = skills().resolve("porelo-code").resolve("SKILL.md");

        assertThat(check(List.of(skill), directory.resolve("elsewhere")))
                .as("a whole copy named alone, its frontmatter in the hash: " + stderr())
                .isEqualTo(0);

        Files.writeString(
                skill,
                Files.readString(skill).replace("Rules for code.", "Rules for code, loosened."));
        assertThat(check(List.of(skill), directory.resolve("elsewhere")))
                .as("the same file with its description edited")
                .isEqualTo(Stop.REFUSED);
        assertThat(stderr())
                .as("the refusal names what is wrong")
                .contains("Shared Skill porelo-code: the markers say");
    }

    @Test
    void checksTheSkillsOfTheRepositoryWhoseRulesAreNamedNotThoseOfTheCaller() throws IOException {
        sharedSkill("porelo-code", "Rules for code.");
        sharedSkill("porelo-testing", "Rules for tests.");
        final Path caller = Files.createDirectories(directory.resolve("caller"));
        final Path rules = rules("porelo-code", "porelo-testing");

        assertThat(check(List.of(rules), caller))
                .as("a whole repository checked from a directory without skills: " + stderr())
                .isEqualTo(0);

        final Path theirs = caller.resolve(".agents").resolve("skills");
        Files.createDirectories(theirs);
        Files.createDirectories(caller.resolve(".claude"));
        Files.createSymbolicLink(
                caller.resolve(".claude").resolve("skills"), Path.of("../.agents/skills"));
        Files.move(skills().resolve("porelo-testing"), theirs.resolve("porelo-testing"));
        assertThat(check(List.of(rules), caller))
                .as("the caller's copy of a skill never stands in for the named repository's")
                .isEqualTo(Stop.REFUSED);
        assertThat(stderr())
                .as("the named repository's missing skill")
                .contains(skills().resolve("porelo-testing").resolve("SKILL.md") + ": missing");
    }

    @Test
    void refusesSkillsNamedThroughALinkToAFile() throws IOException {
        final String text = TEXT;
        final Path held =
                Files.writeString(
                        directory.resolve("held.md"), block("Shared Area", hash(text), text));
        Files.createDirectories(directory.resolve(".agents"));
        Files.createSymbolicLink(skills(), held);

        assertThat(check(List.of(skills()), directory))
                .as("a link in the skills directory's place is no skills directory")
                .isEqualTo(Stop.REFUSED);
        assertThat(stderr()).as("the link named").contains(skills() + ": not a directory");
    }

    @Test
    void refusesASharedSkillStrippedOfItsMarkersWhenOnlyTheSkillsAreNamed() throws IOException {
        sharedSkill("porelo-code", "Rules for code.");
        rules("porelo-code");
        final Path skill = skills().resolve("porelo-code").resolve("SKILL.md");
        Files.writeString(
                skill,
                Files.readString(skill)
                        .lines()
                        .filter(line -> !line.startsWith("> **"))
                        .map(line -> line.replace("A rule", "A loosened rule") + "\n")
                        .reduce("", String::concat));

        assertThat(check(List.of(skills()), directory))
                .as("the rules beside the skills are read for the skills they name")
                .isEqualTo(Stop.REFUSED);
        assertThat(stderr())
                .as("the skill carried as if it were the repository's own")
                .contains("not the whole shared skill porelo-code");
    }

    @Test
    void refusesADescriptionFoldedOverSeveralLines() throws IOException {
        ownSkill("host-notes", "description: >\n  " + "x".repeat(1025) + "\n");

        assertThat(check(List.of(rules()), directory))
                .as("a folded value would escape the length rule")
                .isEqualTo(Stop.REFUSED);
        assertThat(stderr())
                .as("the line that is no key and value")
                .contains("a frontmatter line that is no 'key: value' on one line");
    }

    @Test
    void checksTheSkillsDirectoryEvenWhenTheCallNamesOnlyTheRules() throws IOException {
        sharedSkill("porelo-code", "Rules for code.");
        ownSkill("host-build", "");

        assertThat(check(List.of(rules("porelo-code")), directory))
                .as("the skills under the root are checked unnamed, as pinned.sh is")
                .isEqualTo(Stop.REFUSED);
        assertThat(stderr())
                .as("the refusal names what is wrong")
                .contains("host-build/SKILL.md: the frontmatter has no description");
    }

    @Test
    @Tag("documents")
    void thisRepositorysOwnSkillsAreTheSharedOnes() {
        assertThat(check(List.of(Path.of("../AGENTS.md")), Path.of("..")))
                .as(stderr())
                .isEqualTo(0);
        assertThat(stdout())
                .as("every shared skill the rules name, checked whole")
                .contains("OK    Shared Skill porelo-code in")
                .contains("OK    Shared Skill porelo-review in");
    }

    /** Writes AGENTS.md with a shared block whose table names the given skills. */
    private Path rules(String... names) throws IOException {
        final StringBuilder text =
                new StringBuilder(TEXT).append("\n| Before you | Load |\n|---|---|\n");
        for (final String name : names) {
            text.append("| work | `").append(name).append("` |\n");
        }
        return file(block("Shared Area", hash(text.toString()), text.toString()));
    }

    private Path skills() {
        return directory.resolve(".agents").resolve("skills");
    }

    /** Writes a shared skill as the shared rules carry it, and the link Claude Code reads. */
    private void sharedSkill(String name, String description) throws IOException {
        final String frontmatter = "name: " + name + "\ndescription: " + description + "\n";
        final String body = "\n# " + name + "\n\n" + TEXT;
        write(
                name,
                "---\n"
                        + frontmatter
                        + "---\n\n"
                        + block("Shared Skill " + name, hash(frontmatter + body), body));
    }

    private void ownSkill(String name, String more) throws IOException {
        write(name, "---\nname: " + name + "\n" + more + "---\n\n# " + name + "\n");
    }

    private void write(String name, String text) throws IOException {
        final Path skill = Files.createDirectories(skills().resolve(name));
        Files.writeString(skill.resolve("SKILL.md"), text, StandardCharsets.UTF_8);
        final Path link = directory.resolve(".claude").resolve("skills");
        if (!Files.exists(link, java.nio.file.LinkOption.NOFOLLOW_LINKS)) {
            Files.createDirectories(link.getParent());
            Files.createSymbolicLink(link, Path.of("../.agents/skills"));
        }
    }

    private static String shared() {
        try (InputStream in =
                CheckSharedTest.class.getResourceAsStream(
                        "/org/fuin/porelo/release/.editorconfig")) {
            assertThat(in)
                    .as("the shared .editorconfig on porelo-release's class path")
                    .isNotNull();
            return new String(
                    java.util.Objects.requireNonNull(in).readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    private static String block(String name, String hash, String text) {
        return "> **BEGIN "
                + name
                + "** · sha256 `"
                + hash
                + "` · changed 2026-10-05T06:23Z\n"
                + text
                + "> **END "
                + name
                + "** · sha256 `"
                + hash
                + "`\n";
    }

    private static String hash(String text) {
        try {
            return HexFormat.of()
                    .formatHex(
                            MessageDigest.getInstance("SHA-256")
                                    .digest(text.getBytes(StandardCharsets.UTF_8)))
                    .substring(0, 16);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private Path file(String text) throws IOException {
        return Files.writeString(directory.resolve("AGENTS.md"), text, StandardCharsets.UTF_8);
    }

    private int check(Path file) {
        return new CheckShared(
                        new PrintStream(out, true, StandardCharsets.UTF_8),
                        new PrintStream(err, true, StandardCharsets.UTF_8))
                .check(java.util.List.of(file), file.startsWith("..") ? Path.of("..") : directory);
    }

    private int check(List<Path> files, Path root) {
        return new CheckShared(
                        new PrintStream(out, true, StandardCharsets.UTF_8),
                        new PrintStream(err, true, StandardCharsets.UTF_8))
                .check(files, root);
    }

    private Path pinned(String text) throws IOException {
        final Path pinned = directory.resolve(CheckShared.PINNED);
        Files.createDirectories(pinned.getParent());
        return Files.writeString(pinned, text, StandardCharsets.UTF_8);
    }

    private static String sharedPinned() {
        try (InputStream in = CheckShared.class.getResourceAsStream(CheckShared.SHARED_PINNED)) {
            return new String(
                    java.util.Objects.requireNonNull(in, "pinned.sh on the class path")
                            .readAllBytes(),
                    StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    private String stdout() {
        return out.toString(StandardCharsets.UTF_8);
    }

    private String stderr() {
        return err.toString(StandardCharsets.UTF_8);
    }
}
