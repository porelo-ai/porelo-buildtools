package org.fuin.porelo.release;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Holds a repository's skills directory to the shared skills, as {@link CheckShared} holds a file's
 * shared blocks.
 *
 * <p>A shared skill is copied byte for byte into every repository that names it, and its markers
 * carry the hash of its frontmatter followed by the lines between them, so a description edited in
 * one copy shows as a rule would. A skill of the repository's own carries no block and is held only
 * to the frontmatter agentskills.io asks for. Claude Code finds the skills through {@code
 * .claude/skills}, which must be a link to {@code ../.agents/skills}.
 */
final class SharedSkills {

    /** Where a repository keeps its skills, from its root. */
    static final Path DIRECTORY = Path.of(".agents", "skills");

    /** Where Claude Code looks for them, from the root. */
    static final Path CLAUDE = Path.of(".claude", "skills");

    /** What {@link #CLAUDE} links to, exactly. */
    static final String LINK = "../.agents/skills";

    /** The file a skill directory holds. */
    static final String SKILL = "SKILL.md";

    /** The longest description agentskills.io allows. */
    static final int LONGEST_DESCRIPTION = 1024;

    /** The block name a shared skill's markers carry, before its name. */
    static final String PREFIX = "Shared Skill ";

    private static final Pattern BEGIN =
            Pattern.compile("^> \\*\\*BEGIN (.+?)\\*\\* · sha256 `([0-9a-f]{16})`.*$");

    private static final Pattern END =
            Pattern.compile("^> \\*\\*END (.+?)\\*\\* · sha256 `([0-9a-f]{16})`.*$");

    /** Any line that looks like a marker, so a malformed one is refused, not skipped. */
    private static final Pattern MARKER = Pattern.compile("^> \\*\\*(BEGIN|END) .*$");

    /** The head of a table of skills to load, inside a shared block. */
    private static final Pattern TABLE =
            Pattern.compile("^\\|\\s*Before you\\s*\\|\\s*Load\\s*\\|\\s*$");

    /** A skill's name as agentskills.io allows it. */
    private static final Pattern NAME = Pattern.compile("^[a-z0-9]+(-[a-z0-9]+)*$");

    /** A skill a table names: each in code in the row's last cell. */
    private static final Pattern NAMED = Pattern.compile("`([^`]+)`");

    /**
     * A top-level key of the frontmatter and its value on the same line, read as the harnesses read
     * it: the skills carry descriptions with a colon and a blank inside, which strict YAML refuses.
     */
    private static final Pattern KEY = Pattern.compile("^([A-Za-z][A-Za-z0-9_-]*):(?: (.*))?$");

    private SharedSkills() {}

    /**
     * Returns the skills the tables of skills to load name, read from a shared block's lines.
     *
     * @param file the file the block is in, for a refusal
     * @param block the lines strictly between a shared block's markers
     * @param faults receives a row whose last cell names no skill
     * @return each skill named in a row's last cell, in order
     */
    static Set<String> named(Path file, List<String> block, List<String> faults) {
        final Set<String> named = new LinkedHashSet<>();
        boolean inTable = false;
        for (final String line : block) {
            if (TABLE.matcher(line).matches()) {
                inTable = true;
                continue;
            }
            if (!inTable) {
                continue;
            }
            final String row = line.strip();
            if (!row.startsWith("|") || !row.endsWith("|") || row.length() < 2) {
                inTable = false;
                continue;
            }
            final String[] cells = row.substring(1, row.length() - 1).split("\\|", -1);
            if (cells[cells.length - 1].strip().matches("[:-]+")) {
                continue;
            }
            final Matcher skill = NAMED.matcher(cells[cells.length - 1]);
            boolean any = false;
            while (skill.find()) {
                any = true;
                if (!NAME.matcher(skill.group(1)).matches()) {
                    faults.add(file + ": a skill to load named `" + skill.group(1) + "`, no name");
                    continue;
                }
                named.add(skill.group(1));
            }
            if (!any) {
                faults.add(file + ": a row of skills to load that names none in code: " + row);
            }
        }
        return named;
    }

    /**
     * Checks a skills directory: each skill in it, and the link Claude Code finds it by.
     *
     * @param root the repository's root, where {@link #CLAUDE} is
     * @param directory the skills directory, usually {@link #DIRECTORY} under the root
     * @param shared receives the name of each shared skill whose copy is whole
     * @param faults receives one line per refusal, naming the file
     * @param fine receives one line per skill that passes
     * @throws IOException when a file cannot be read
     */
    static void check(
            Path root, Path directory, Set<String> shared, List<String> faults, List<String> fine)
            throws IOException {
        final Path parent = directory.toAbsolutePath().normalize().getParent();
        if (Files.isSymbolicLink(directory)
                || !Files.isDirectory(directory)
                || (parent != null && Files.isSymbolicLink(parent))) {
            faults.add(
                    directory
                            + ": not a directory under a directory; the skills are kept in one,"
                            + " not behind a link");
            return;
        }
        checkLink(root, faults);
        final List<Path> entries;
        try (Stream<Path> list = Files.list(directory)) {
            entries = list.sorted().toList();
        }
        for (final Path entry : entries) {
            if (Files.isSymbolicLink(entry) || !Files.isDirectory(entry)) {
                faults.add(entry + ": not a skill; the skills directory holds only directories");
                continue;
            }
            checkSkill(entry, shared, faults, fine);
        }
    }

    /**
     * Refuses each skill a table names that the repository does not carry whole.
     *
     * @param directory the skills directory
     * @param named the skills the checked tables name
     * @param shared the shared skills found whole
     * @param faults receives one line per skill missing
     */
    static void present(
            Path directory, Set<String> named, Set<String> shared, List<String> faults) {
        for (final String name : new TreeSet<>(named)) {
            if (shared.contains(name)) {
                continue;
            }
            final Path file = directory.resolve(name).resolve(SKILL);
            faults.add(
                    file
                            + (Files.exists(file, LinkOption.NOFOLLOW_LINKS)
                                    ? ": not the whole shared skill " + name
                                    : ": missing")
                            + "; the shared rules name the skill "
                            + name
                            + " to load, so every repository carries its shared copy");
        }
    }

    private static void checkLink(Path root, List<String> faults) throws IOException {
        final Path link = root.resolve(CLAUDE);
        final Path claude = link.getParent();
        if (claude != null && Files.isSymbolicLink(claude)) {
            faults.add(claude + ": a link; .claude is a directory holding the link skills");
            return;
        }
        if (!Files.isSymbolicLink(link)) {
            faults.add(
                    link
                            + ": "
                            + (Files.exists(link, LinkOption.NOFOLLOW_LINKS)
                                    ? "not a link"
                                    : "missing")
                            + "; it is a link to "
                            + LINK
                            + ", so Claude Code finds the same skills");
            return;
        }
        final String target = Files.readSymbolicLink(link).toString();
        if (!linksToSkills(target, link.getFileSystem().getSeparator())) {
            faults.add(link + ": links to " + target + ", not to " + LINK);
        }
    }

    /**
     * Tells whether a link's target, as its file system gives it back, is {@link #LINK}.
     *
     * @param target the target, as read from the link
     * @param separator the name separator of the link's file system
     * @return whether it names the shared skills directory beside {@code .claude}
     */
    static boolean linksToSkills(String target, String separator) {
        final List<String> names =
                new ArrayList<>(List.of(target.split(Pattern.quote(separator), -1)));
        // A trailing separator names the same directory.
        if (names.size() > 1 && names.get(names.size() - 1).isEmpty()) {
            names.remove(names.size() - 1);
        }
        return names.equals(List.of(LINK.split("/")));
    }

    /**
     * Checks one skill: its frontmatter, and its block when it carries one.
     *
     * @param skill the skill's directory
     * @param shared receives the skill's name when it is a shared skill copied whole
     * @param faults receives one line per refusal
     * @param fine receives one line when it passes
     * @throws IOException when its file cannot be read
     */
    static void checkSkill(Path skill, Set<String> shared, List<String> faults, List<String> fine)
            throws IOException {
        final Path file = skill.resolve(SKILL);
        if (Files.isSymbolicLink(file) || !Files.isRegularFile(file)) {
            faults.add(
                    file
                            + ": "
                            + (Files.exists(file, LinkOption.NOFOLLOW_LINKS)
                                    ? "a link or not a file"
                                    : "missing")
                            + "; a skill directory holds its own "
                            + SKILL);
            return;
        }
        final String dir = String.valueOf(skill.getFileName());
        final String text = Files.readString(file, StandardCharsets.UTF_8);
        if (text.indexOf('\r') >= 0) {
            faults.add(file + ": a carriage return; a skill's lines end in a newline alone");
            return;
        }
        final List<String> lines = List.of(text.split("\n", -1));
        if (lines.isEmpty() || !lines.getFirst().equals("---")) {
            faults.add(file + ":1: no frontmatter; a skill opens with a line '---'");
            return;
        }
        final int close = lines.subList(1, lines.size()).indexOf("---") + 1;
        if (close == 0) {
            faults.add(file + ":1: the frontmatter has no closing line '---'");
            return;
        }
        final List<String> frontmatter = lines.subList(1, close);
        final int before = faults.size();
        checkFrontmatter(file, dir, frontmatter, faults);
        final List<Integer> markers = new ArrayList<>();
        for (int at = 0; at < lines.size(); at++) {
            if (MARKER.matcher(lines.get(at)).matches()) {
                markers.add(at);
            }
        }
        if (markers.isEmpty()) {
            if (faults.size() == before) {
                fine.add("OK    " + file + ": a skill of the repository's own");
            }
            return;
        }
        checkBlock(file, dir, lines, close, markers, shared, faults, fine, before);
    }

    private static void checkFrontmatter(
            Path file, String dir, List<String> frontmatter, List<String> faults) {
        final Map<String, String> head = new LinkedHashMap<>();
        for (final String line : frontmatter) {
            final Matcher key = KEY.matcher(line);
            if (!key.matches()) {
                // A value folded over several lines would escape the length rule below.
                faults.add(
                        file + ": a frontmatter line that is no 'key: value' on one line: " + line);
                continue;
            }
            if (head.putIfAbsent(
                            key.group(1),
                            unquoted(java.util.Objects.requireNonNullElse(key.group(2), "")))
                    != null) {
                faults.add(file + ": the frontmatter names " + key.group(1) + " twice");
            }
        }
        final String name = head.get("name");
        if (name == null) {
            faults.add(file + ": the frontmatter has no name");
        } else if (!NAME.matcher(name).matches()) {
            faults.add(
                    file
                            + ": the frontmatter's name "
                            + name
                            + " is not lower-case letters and digits joined by single hyphens");
        } else if (!name.equals(dir)) {
            faults.add(
                    file
                            + ": the frontmatter's name is "
                            + name
                            + ", its directory's "
                            + dir
                            + "; they are the same");
        }
        final String description = head.get("description");
        if (description == null || description.isBlank()) {
            faults.add(file + ": the frontmatter has no description");
        } else if (description.length() > LONGEST_DESCRIPTION) {
            faults.add(
                    file
                            + ": the description is "
                            + description.length()
                            + " characters, at most "
                            + LONGEST_DESCRIPTION);
        }
    }

    /** A value without the quotes YAML may put around it. */
    private static String unquoted(String value) {
        final String strip = value.strip();
        final boolean quoted =
                strip.length() >= 2
                        && (strip.startsWith("\"") && strip.endsWith("\"")
                                || strip.startsWith("'") && strip.endsWith("'"));
        return quoted ? strip.substring(1, strip.length() - 1) : strip;
    }

    private static void checkBlock(
            Path file,
            String dir,
            List<String> lines,
            int close,
            List<Integer> markers,
            Set<String> shared,
            List<String> faults,
            List<String> fine,
            int before) {
        final int from = markers.getFirst();
        final int to = markers.getLast();
        final Matcher begin = BEGIN.matcher(lines.get(from));
        final Matcher end = END.matcher(lines.get(to));
        if (markers.size() != 2 || !begin.matches() || !end.matches()) {
            faults.add(
                    file
                            + ":"
                            + (from + 1)
                            + ": a skill carries one block, '> **BEGIN "
                            + PREFIX
                            + dir
                            + "** · sha256 `<hash>`' to its END, and no other marker");
            return;
        }
        final String name = begin.group(1);
        if (!name.equals(end.group(1))) {
            faults.add(file + ":" + (to + 1) + ": END " + end.group(1) + " closes BEGIN " + name);
            return;
        }
        if (!name.equals(PREFIX + dir)) {
            faults.add(
                    file
                            + ":"
                            + (from + 1)
                            + ": the block is "
                            + name
                            + ", the directory "
                            + dir
                            + "; a shared skill's block is named "
                            + PREFIX
                            + dir);
            return;
        }
        if (!begin.group(2).equals(end.group(2))) {
            faults.add(
                    file
                            + ":"
                            + (to + 1)
                            + ": "
                            + name
                            + ": BEGIN and END carry different hashes, "
                            + begin.group(2)
                            + " and "
                            + end.group(2));
            return;
        }
        for (int at = close + 1; at < lines.size(); at++) {
            if ((at < from || at > to) && !lines.get(at).isBlank()) {
                faults.add(
                        file
                                + ":"
                                + (at + 1)
                                + ": a line outside the block, which its hash does not hold;"
                                + " a shared skill is its frontmatter and its block alone");
                return;
            }
        }
        if (from < close) {
            faults.add(file + ":" + (from + 1) + ": the block begins inside the frontmatter");
            return;
        }
        final List<String> hashed = new ArrayList<>(lines.subList(1, close));
        hashed.addAll(lines.subList(from + 1, to));
        final String is = CheckShared.hashOf(hashed);
        if (!begin.group(2).equals(is)) {
            faults.add(
                    file
                            + ":"
                            + (from + 1)
                            + ": "
                            + name
                            + ": the markers say "
                            + begin.group(2)
                            + ", the frontmatter and text are "
                            + is);
            return;
        }
        if (faults.size() == before) {
            shared.add(dir);
            fine.add("OK    " + name + " in " + file + ", sha256 " + is);
        }
    }
}
