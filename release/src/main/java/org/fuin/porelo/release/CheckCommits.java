package org.fuin.porelo.release;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.DateTimeException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.fuin.porelo.json.JsonException;
import org.fuin.porelo.stories.Workflow;
import org.jspecify.annotations.Nullable;

/**
 * Holds each commit of a range to the shared rules on commit messages and stage records.
 *
 * <p>A subject of one line of at most 72 characters, {@code [skip ci]} counted; below it only a
 * blank line and trailers; no requirement number, since the issue is deleted once it is done; and
 * nobody named as having asked for or made the change, as far as {@link Who} can tell. A commit
 * Dependabot or GitHub's Actions bot authored, or GitHub committed in its web interface, is exempt
 * from these checks: told by the author and committer the commit records, never by what its message
 * says. Other trees' stage records must be closed, and a tree with a seed must hold stats for each
 * issue it adds in a workflow state marked measured, and for each whose state or size it changes
 * while that issue ends in such a state; both are read as {@link Stage#issue} reads them, in the
 * commit and in its first parent, and a parent's version that cannot be read is refused, never
 * taken as unchanged.
 */
final class CheckCommits {

    /** The longest subject, every character counted. */
    static final int LONGEST_SUBJECT = 72;

    /** The authors whose commits are exempt: the bots GitHub runs. */
    static final Set<String> EXEMPT_AUTHORS = Set.of("dependabot[bot]", "github-actions[bot]");

    /** The committer of a merge made in GitHub's web interface. */
    static final String WEB_COMMITTER = "GitHub";

    /**
     * The one line kind allowed below the subject: a trailer of a key the shared rules name, its
     * key in any case, as git reads one. Porelo's own commits carry two more, each held to its
     * form: its change id, one token, and the file a commit signs anew, a path inside the
     * repository: no leading {@code /}, no {@code ..} part, and only letters, digits and {@code . _
     * - /}.
     */
    private static final Pattern TRAILER =
            Pattern.compile(
                    "^(?:(?:Assisted-by|Signed-off-by|Co-authored-by): \\S.*"
                            + "|Porelo-Change-Id: \\S+"
                            + "|Porelo-Signs-Anew: (?!/)(?!(?:.*/)?\\.\\.(?:/|$))[A-Za-z0-9._/-]+)$",
                    Pattern.CASE_INSENSITIVE);

    /** A sign-off, its key in any case, and the address it names. */
    private static final Pattern SIGN_OFF =
            Pattern.compile("^Signed-off-by: .*<([^<>\\s]+)>$", Pattern.CASE_INSENSITIVE);

    /** A word of an issue number's shape, its prefix apart; whether it is one the prefixes say. */
    private static final Pattern NUMBER = Pattern.compile("\\b([A-Z]{1,3})\\d+\\b");

    /** What git writes ahead of each commit, and between its fields. */
    private static final char RECORD = '\u001e';

    private static final char FIELD = '\u001f';

    private final PrintStream out;

    private final PrintStream err;

    private final Git git;

    private final Prefixes prefixes;

    /** Reads the log of a range. */
    @FunctionalInterface
    interface Git {

        /**
         * Per commit: the record separator, then the short hash, author name and address, committer
         * name and address, and the raw message, apart by the unit separator.
         */
        String FORMAT = "%x1e%h%x1f%an%x1f%ae%x1f%cn%x1f%ce%x1f%B";

        /**
         * Returns git's log of a range in {@link #FORMAT}.
         *
         * @param range the range, never an option
         * @return the log, empty when the range holds nothing
         * @throws IOException when git cannot be run or refuses the range
         */
        String log(String range) throws IOException;

        /** Lists the paths in a commit's tree. */
        default List<String> files(String hash) throws IOException {
            return List.of();
        }

        /** Lists paths changed against the first parent, or the empty tree for a root. */
        default List<String> changed(String hash) throws IOException {
            return List.of();
        }

        /**
         * Names the parent {@link #changed} compares against: a merge's first, none for a root.
         * Without one, every issue file of the commit counts as new.
         */
        default Optional<String> parent(String hash) throws IOException {
            return Optional.empty();
        }

        /** Reads a file from that commit, never from the working tree. */
        default String read(String hash, String path) throws IOException {
            throw new IOException("commit tree contents are unavailable");
        }

        /**
         * Runs git in a directory.
         *
         * @param directory the checkout
         * @param environment set for git on top of this process's own
         * @return the runner
         */
        static Git in(Path directory, Map<String, String> environment) {
            return new Git() {
                /** Reads the requested range's log. */
                @Override
                public String log(String range) throws IOException {
                    return run(directory, environment, range);
                }

                /** Lists that commit's files. */
                @Override
                public List<String> files(String hash) throws IOException {
                    return paths(
                            execute(
                                    directory,
                                    environment,
                                    List.of("ls-tree", "-rz", "--name-only", hash, "--")));
                }

                /** Lists that commit's changed paths. */
                @Override
                public List<String> changed(String hash) throws IOException {
                    final Optional<String> parent = parent(hash);
                    final List<String> arguments =
                            new ArrayList<>(
                                    List.of(
                                            "diff-tree",
                                            "--root",
                                            "--no-commit-id",
                                            "--name-only",
                                            "-r",
                                            "-z"));
                    parent.ifPresent(arguments::add);
                    arguments.add(hash);
                    arguments.add("--");
                    return paths(execute(directory, environment, arguments));
                }

                /** Names that commit's first parent. */
                @Override
                public Optional<String> parent(String hash) throws IOException {
                    final String[] ancestry =
                            execute(
                                            directory,
                                            environment,
                                            List.of("rev-list", "--parents", "-n", "1", hash, "--"))
                                    .strip()
                                    .split("\\s+");
                    return ancestry.length > 1 ? Optional.of(ancestry[1]) : Optional.empty();
                }

                /** Reads a file from that commit's tree. */
                @Override
                public String read(String hash, String path) throws IOException {
                    return execute(directory, environment, List.of("show", hash + ":" + path));
                }
            };
        }

        private static List<String> paths(String output) {
            return output.isEmpty() ? List.of() : List.of(output.split("\u0000"));
        }

        private static String run(Path directory, Map<String, String> environment, String range)
                throws IOException {
            return execute(
                    directory,
                    environment,
                    List.of(
                            "-c",
                            "log.showSignature=false",
                            "log",
                            "--no-color",
                            "--format=" + FORMAT,
                            "--end-of-options",
                            range,
                            "--"));
        }

        private static String execute(
                Path directory, Map<String, String> environment, List<String> arguments)
                throws IOException {
            final List<String> command =
                    new ArrayList<>(List.of("git", "-C", directory.toString()));
            command.addAll(arguments);
            final ProcessBuilder builder = new ProcessBuilder(command);
            builder.environment().putAll(environment);
            builder.environment().put("LC_ALL", "C");
            // To files rather than pipes: reading a pipe to its end would wait out a hang, not the
            // timeout.
            final Path output = Files.createTempFile("check-commits", ".out");
            final Path errors = Files.createTempFile("check-commits", ".err");
            try {
                final Process process =
                        builder.redirectOutput(output.toFile())
                                .redirectError(errors.toFile())
                                .start();
                if (!process.waitFor(60, TimeUnit.SECONDS)) {
                    process.destroyForcibly();
                    throw new IOException(
                            "git " + arguments.getFirst() + " did not finish within 60 seconds");
                }
                if (process.exitValue() != 0) {
                    throw new IOException(
                            "git "
                                    + arguments.getFirst()
                                    + " exited with "
                                    + process.exitValue()
                                    + ": "
                                    + Files.readString(errors, StandardCharsets.UTF_8).strip());
                }
                return Files.readString(output, StandardCharsets.UTF_8);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new IOException("interrupted while git read the log", ex);
            } finally {
                Files.deleteIfExists(output);
                Files.deleteIfExists(errors);
            }
        }
    }

    /** Reads the issue prefixes a requirement number is made of. */
    @FunctionalInterface
    interface Prefixes {

        /**
         * Reads them.
         *
         * @return the prefixes, such as {@code BT}
         * @throws IOException when the issues cannot be read
         */
        Set<String> read() throws IOException;
    }

    /** One commit as git recorded it. */
    private record Commit(
            String hash,
            String author,
            String authorMail,
            String committer,
            String committerMail,
            String message) {

        boolean exempt() {
            return EXEMPT_AUTHORS.contains(author) || WEB_COMMITTER.equals(committer);
        }
    }

    /**
     * Constructor.
     *
     * @param out where the result goes
     * @param err where a refusal goes
     * @param git where the log is read
     * @param prefixes where the issue prefixes are read, once a commit needs them
     */
    CheckCommits(PrintStream out, PrintStream err, Git git, Prefixes prefixes) {
        this.out = out;
        this.err = err;
        this.git = git;
        this.prefixes = prefixes;
    }

    /**
     * Checks every commit of a range.
     *
     * @param range a git range, such as {@code origin/main..HEAD}
     * @param outside true for a pull request from outside, whose every commit that is not exempt
     *     carries its author's {@code Signed-off-by:}
     * @return 0 when no commit breaks the rule, an empty range among them; {@link Stop#REFUSED}
     *     when one does; {@link Stop#UNANSWERED} when the log or the prefixes cannot be read
     */
    int check(String range, boolean outside) {
        if (range.isBlank() || range.startsWith("-")) {
            err.println("a range names revisions, never an option: '" + range + "'");
            return Stop.UNANSWERED;
        }
        final List<Commit> commits;
        try {
            commits = parse(git.log(range));
        } catch (IOException ex) {
            err.println("could not read the commits of " + range + ": " + ex.getMessage());
            return Stop.UNANSWERED;
        }
        final List<String> faults = new ArrayList<>();
        Set<String> known = null;
        int exempt = 0;
        int breaking = 0;
        boolean rolloutNote = false;
        for (final Commit commit : commits) {
            if (commit.exempt()) {
                exempt++;
                continue;
            }
            final List<String> broken;
            try {
                final Set<String> files = Set.copyOf(git.files(commit.hash()));
                if (!files.contains("stats/seed.csv") && !rolloutNote) {
                    out.println(
                            "NOTE  no stats/seed.csv in a commit tree; the issue stats rule is not"
                                    + " rolled out there");
                    rolloutNote = true;
                }
                broken = stageFaults(commit.hash(), files);
            } catch (IOException | IllegalArgumentException ex) {
                err.println(commit.hash() + "  could not check stage records: " + ex.getMessage());
                return Stop.UNANSWERED;
            }
            if (known == null) {
                try {
                    known = prefixes.read();
                } catch (IOException ex) {
                    err.println("could not read the issue prefixes: " + ex.getMessage());
                    return Stop.UNANSWERED;
                }
                if (known.isEmpty()) {
                    err.println(
                            "no issue prefix found under issues/, so a requirement number"
                                    + " cannot be told - is the working directory a repository's"
                                    + " root?");
                    return Stop.UNANSWERED;
                }
            }
            broken.addAll(faults(commit, known, outside));
            if (!broken.isEmpty()) {
                breaking++;
            }
            broken.forEach(fault -> faults.add(commit.hash() + "  " + fault));
        }
        if (faults.isEmpty()) {
            out.println(
                    "OK    "
                            + commits.size()
                            + " commit(s) in "
                            + range
                            + ", "
                            + exempt
                            + " exempt");
            return 0;
        }
        faults.forEach(err::println);
        err.println(
                faults.size()
                        + " fault(s) in "
                        + breaking
                        + " of "
                        + commits.size()
                        + " commit(s) in "
                        + range
                        + ": commit messages and stage records must follow the shared rules");
        return Stop.REFUSED;
    }

    private List<String> stageFaults(String hash, Set<String> files) throws IOException {
        final List<String> faults = new ArrayList<>();
        if (!files.contains("stats/seed.csv")
                && files.stream().noneMatch(path -> path.matches("stats/[^/]+\\.jsonl")))
            return faults;
        final Workflow workflow;
        if (!files.contains("issues/workflow.yml")) {
            faults.add("issues/workflow.yml is missing from the commit tree");
            return faults;
        }
        final Path temporary = Files.createTempFile("commit-workflow-", ".yml");
        try {
            Files.writeString(temporary, git.read(hash, "issues/workflow.yml"));
            workflow = Workflow.read(temporary);
        } catch (IOException ex) {
            faults.add(
                    "issues/workflow.yml: "
                            + String.valueOf(ex.getMessage())
                                    .replace(temporary.toString(), "issues/workflow.yml"));
            return faults;
        } finally {
            Files.deleteIfExists(temporary);
        }
        for (final String path : files) {
            if (!path.matches("stats/[^/]+\\.jsonl")) {
                continue;
            }
            try {
                Stage.records(
                        git.read(hash, path), path.substring("stats/".length()), true, workflow);
            } catch (JsonException ex) {
                faults.add(path + " contains invalid JSON");
            } catch (DateTimeException ex) {
                faults.add(path + " contains an invalid stage timestamp");
            } catch (IllegalArgumentException ex) {
                faults.add(path + ": " + ex.getMessage());
            }
        }
        if (files.contains("stats/seed.csv")) {
            final Pattern issue =
                    Pattern.compile("issues/(?:[^/]+/)*([A-Z]{1,3}\\d{2,3})-[^/]+\\.md");
            Optional<String> parent = null;
            Set<String> before = Set.of();
            for (final String path : git.changed(hash)) {
                final Matcher match = issue.matcher(path);
                if (!match.matches() || !files.contains(path)) {
                    continue;
                }
                final String text = git.read(hash, path);
                final String status = Stage.status(text, workflow);
                final String stats = "stats/" + match.group(1) + ".jsonl";
                if (!workflow.measured(status) || files.contains(stats)) {
                    continue;
                }
                if (parent == null) {
                    parent = git.parent(hash);
                    if (parent.isPresent()) {
                        before = Set.copyOf(git.files(parent.get()));
                    }
                }
                if (parent.isEmpty() || !before.contains(path)) {
                    faults.add(
                            path + " is new in " + status + " without " + stats + " in its tree");
                    continue;
                }
                final String change = change(parent.get(), path, text, workflow);
                if (change != null) {
                    faults.add(path + " " + change + " without " + stats + " in its tree");
                }
            }
        }
        return faults;
    }

    /**
     * Compares an issue's state and size with its first parent's, as {@link Stage#issue} reads
     * both.
     *
     * @return null when both are the same, else what changed or why it cannot be told
     */
    private @Nullable String change(String parent, String path, String text, Workflow workflow) {
        final String before;
        try {
            before = git.read(parent, path);
        } catch (IOException ex) {
            return "cannot be compared with its parent's version, which cannot be read: "
                    + ex.getMessage();
        }
        final Stage.Issue earlier;
        try {
            earlier = Stage.issue(before, workflow);
        } catch (IllegalArgumentException ex) {
            return "cannot be compared with its parent's version, which is no valid issue under"
                    + " this commit's workflow: "
                    + ex.getMessage();
        }
        final Stage.Issue now;
        try {
            now = Stage.issue(text, workflow);
        } catch (IllegalArgumentException ex) {
            return "cannot be compared with its parent's version: " + ex.getMessage();
        }
        final List<String> changes = new ArrayList<>();
        if (!earlier.status().equals(now.status())) {
            changes.add("its state from " + earlier.status() + " to " + now.status());
        }
        if (!Objects.equals(earlier.size(), now.size())) {
            changes.add(
                    "its size from "
                            + Objects.requireNonNullElse(earlier.size(), "none")
                            + " to "
                            + Objects.requireNonNullElse(now.size(), "none"));
        }
        return changes.isEmpty() ? null : "changes " + String.join(" and ", changes);
    }

    private static List<String> faults(Commit commit, Set<String> known, boolean outside) {
        final List<String> faults = new ArrayList<>();
        final String[] lines = commit.message().split("\n", -1);
        final String subject = lines[0];
        final int length = subject.codePointCount(0, subject.length());
        if (length > LONGEST_SUBJECT) {
            faults.add("subject is " + length + " characters, at most " + LONGEST_SUBJECT);
        }
        if (lines.length > 1 && !lines[1].isEmpty()) {
            faults.add("the subject is not followed by a blank line");
        }
        final StringBuilder words = new StringBuilder(subject);
        final List<String> trailers = new ArrayList<>();
        final int first = lines.length > 1 && lines[1].isEmpty() ? 2 : 1;
        for (int i = first; i < lines.length; i++) {
            if (TRAILER.matcher(lines[i]).matches()) {
                trailers.add(lines[i]);
            } else {
                faults.add(
                        "below the subject, a line that is not a trailer: "
                                + (lines[i].isBlank() ? "(a blank line)" : lines[i]));
                words.append('\n').append(lines[i]);
            }
        }
        final Matcher number = NUMBER.matcher(words);
        while (number.find()) {
            if (known.contains(number.group(1))) {
                faults.add("names a requirement number: " + number.group());
            }
        }
        Who.named(words.toString(), List.of(commit.author(), commit.committer()))
                .forEach(party -> faults.add("says who: " + party));
        if (outside && !signedOffByAuthor(trailers, commit.authorMail())) {
            faults.add(
                    "no Signed-off-by: naming its author <"
                            + commit.authorMail()
                            + ">, which a pull request from outside carries on every commit");
        }
        return faults;
    }

    private static boolean signedOffByAuthor(List<String> trailers, String mail) {
        for (final String trailer : trailers) {
            final Matcher signOff = SIGN_OFF.matcher(trailer);
            if (signOff.matches()
                    && signOff.group(1)
                            .toLowerCase(Locale.ROOT)
                            .equals(mail.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private static List<Commit> parse(String log) throws IOException {
        final List<Commit> commits = new ArrayList<>();
        for (final String record : log.split(String.valueOf(RECORD), -1)) {
            if (record.isBlank()) {
                continue;
            }
            final String[] fields = record.split(String.valueOf(FIELD), 6);
            if (fields.length != 6) {
                throw new IOException(
                        "git wrote a commit of "
                                + fields.length
                                + " field(s), not 6: "
                                + abbreviated(record));
            }
            commits.add(
                    new Commit(
                            fields[0],
                            fields[1],
                            fields[2],
                            fields[3],
                            fields[4],
                            fields[5].stripTrailing()));
        }
        return commits;
    }

    private static String abbreviated(String text) {
        return text.length() > 80 ? text.substring(0, 80) + "..." : text;
    }
}
