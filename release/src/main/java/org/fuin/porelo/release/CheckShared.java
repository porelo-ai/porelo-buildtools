package org.fuin.porelo.release;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Refuses a shared block whose text no longer fits the hash its markers carry, and an {@code
 * .editorconfig} that is not the shared one.
 *
 * <p>A block shared by several repositories is changed in one place first and copied byte for byte;
 * its markers carry the first 16 hex digits of the SHA-256 of the lines strictly between them, each
 * ending in a newline, so a copy edited on its own shows. Every block a file holds is checked, each
 * name may appear once, and a file with none is refused.
 *
 * <p>A file named {@code .editorconfig} is compared line for line with the copy this tool carries,
 * the code style every repository shares; a repository requires it by naming it, and one that is
 * named but missing is refused.
 */
final class CheckShared {

    private static final Pattern BEGIN =
            Pattern.compile("^> \\*\\*BEGIN (.+?)\\*\\* · sha256 `([0-9a-f]{16})`.*$");

    private static final Pattern END =
            Pattern.compile("^> \\*\\*END (.+?)\\*\\* · sha256 `([0-9a-f]{16})`.*$");

    /**
     * The name a repository's code style file has, compared whole with {@link
     * #SHARED_EDITORCONFIG}.
     */
    static final String EDITORCONFIG = ".editorconfig";

    /** Where on the class path the shared {@code .editorconfig} is. */
    static final String SHARED_EDITORCONFIG = "/org/fuin/porelo/release/.editorconfig";

    private final PrintStream out;

    private final PrintStream err;

    /**
     * Constructor.
     *
     * @param out where the result goes
     * @param err where a refusal goes
     */
    CheckShared(PrintStream out, PrintStream err) {
        this.out = out;
        this.err = err;
    }

    /**
     * Checks every shared block in each file.
     *
     * @param files usually {@code AGENTS.md}, {@code .AGENTS.md} where it exists, and {@code
     *     .editorconfig}
     * @return 0 when every block fits its hash and every {@code .editorconfig} is the shared one,
     *     {@link Stop#REFUSED} when one does not, a marker is missing or a named {@code
     *     .editorconfig} is missing, {@link Stop#UNANSWERED} when a file cannot be read
     */
    int check(List<Path> files) {
        final List<String> faults = new ArrayList<>();
        final List<String> fine = new ArrayList<>();
        for (final Path file : files) {
            if (isEditorconfig(file) && !Files.exists(file)) {
                faults.add(
                        file
                                + ": missing; the repository carries the shared "
                                + EDITORCONFIG
                                + " at its root, as porelo-release has it");
                continue;
            }
            final List<String> lines;
            try {
                lines = List.of(Files.readString(file, StandardCharsets.UTF_8).split("\n", -1));
            } catch (IOException ex) {
                err.println("could not read " + file + ": " + ex.getMessage());
                return Stop.UNANSWERED;
            }
            if (isEditorconfig(file)) {
                final List<String> shared;
                try {
                    shared = sharedEditorconfig();
                } catch (IOException ex) {
                    err.println(
                            "could not read the shared "
                                    + EDITORCONFIG
                                    + " "
                                    + SHARED_EDITORCONFIG
                                    + ": "
                                    + ex.getMessage());
                    return Stop.UNANSWERED;
                }
                compare(file, lines, shared, faults, fine);
            } else {
                check(file, lines, faults, fine);
            }
        }
        if (!faults.isEmpty()) {
            faults.forEach(err::println);
            err.println(
                    faults.size()
                            + " fault(s). A shared block or file is changed in the channel first and copied"
                            + " byte for byte; a block's markers carry the hash of the lines between them:");
            err.println(
                    "    sed -n '/^> \\*\\*BEGIN <name>\\*\\*/,/^> \\*\\*END <name>\\*\\*/p' FILE | sed '1d;$d'"
                            + " | sha256sum | cut -c1-16");
            return Stop.REFUSED;
        }
        fine.forEach(out::println);
        return 0;
    }

    private static boolean isEditorconfig(Path file) {
        final Path name = file.getFileName();
        return name != null && name.toString().equals(EDITORCONFIG);
    }

    /**
     * Returns the shared {@code .editorconfig} this tool carries, split into lines as a checked
     * file is.
     *
     * @return its lines, the last one empty when the file ends in a newline
     * @throws IOException when it is not on the class path or cannot be read
     */
    static List<String> sharedEditorconfig() throws IOException {
        try (InputStream in = CheckShared.class.getResourceAsStream(SHARED_EDITORCONFIG)) {
            if (in == null) {
                throw new IOException("not on the class path");
            }
            return List.of(new String(in.readAllBytes(), StandardCharsets.UTF_8).split("\n", -1));
        }
    }

    private static void compare(
            Path file,
            List<String> lines,
            List<String> shared,
            List<String> faults,
            List<String> fine) {
        final int common = Math.min(lines.size(), shared.size());
        for (int at = 0; at < common; at++) {
            if (!lines.get(at).equals(shared.get(at))) {
                faults.add(
                        differs(file, at, "'" + shared.get(at) + "'", "'" + lines.get(at) + "'"));
                return;
            }
        }
        if (lines.size() != shared.size()) {
            faults.add(
                    differs(
                            file,
                            common,
                            common < shared.size()
                                    ? "'" + shared.get(common) + "'"
                                    : "the end of the file",
                            common < lines.size()
                                    ? "'" + lines.get(common) + "'"
                                    : "the end of the file"));
            return;
        }
        fine.add("OK    " + file + " is the shared " + EDITORCONFIG);
    }

    private static String differs(Path file, int at, String shared, String got) {
        return file
                + ":"
                + (at + 1)
                + ": differs from the shared "
                + EDITORCONFIG
                + " porelo-release carries:"
                + " shared "
                + shared
                + ", got "
                + got
                + "; the file is copied whole, never changed in one copy";
    }

    private static void check(
            Path file, List<String> lines, List<String> faults, List<String> fine) {
        final Map<String, Integer> begun = new LinkedHashMap<>();
        final Map<String, String> declared = new LinkedHashMap<>();
        final List<String> closed = new ArrayList<>();
        for (int at = 0; at < lines.size(); at++) {
            final Matcher begin = BEGIN.matcher(lines.get(at));
            final Matcher end = END.matcher(lines.get(at));
            if (begin.matches()) {
                final String name = begin.group(1);
                if (begun.containsKey(name)) {
                    faults.add(
                            file
                                    + ":"
                                    + (at + 1)
                                    + ": "
                                    + name
                                    + " begins twice; a block appears once");
                    continue;
                }
                begun.put(name, at);
                declared.put(name, begin.group(2));
            } else if (end.matches()) {
                final String name = end.group(1);
                final Integer from = begun.get(name);
                if (from == null || closed.contains(name)) {
                    faults.add(file + ":" + (at + 1) + ": END " + name + " without its BEGIN");
                    continue;
                }
                closed.add(name);
                final String said =
                        java.util.Objects.requireNonNull(
                                declared.get(name), "declared with its BEGIN");
                final String is = hashOf(lines.subList(from + 1, at));
                if (!said.equals(end.group(2))) {
                    faults.add(
                            file
                                    + ":"
                                    + (at + 1)
                                    + ": "
                                    + name
                                    + ": BEGIN and END carry different hashes, "
                                    + said
                                    + " and "
                                    + end.group(2));
                } else if (!said.equals(is)) {
                    faults.add(
                            file
                                    + ":"
                                    + (from + 1)
                                    + ": "
                                    + name
                                    + ": the markers say "
                                    + said
                                    + ", the text is "
                                    + is);
                } else {
                    fine.add("OK    " + name + " in " + file + ", sha256 " + is);
                }
            }
        }
        for (final String name : begun.keySet()) {
            if (!closed.contains(name)) {
                faults.add(file + ":" + (begun.get(name) + 1) + ": " + name + " has no END");
            }
        }
        if (begun.isEmpty() && faults.stream().noneMatch(fault -> fault.startsWith(file + ":"))) {
            faults.add(
                    file
                            + ": no shared block, so nothing in it is checked; it carries one between"
                            + " '> **BEGIN <name>** · sha256 `<hash>`' and '> **END <name>** · sha256 `<hash>`'");
        }
    }

    /**
     * Returns the hash a block's markers carry for its text.
     *
     * @param lines the lines strictly between the markers
     * @return the first 16 hex digits of the SHA-256 of the lines, each ending in a newline
     */
    static String hashOf(List<String> lines) {
        final StringBuilder text = new StringBuilder();
        lines.forEach(line -> text.append(line).append('\n'));
        try {
            return HexFormat.of()
                    .formatHex(
                            MessageDigest.getInstance("SHA-256")
                                    .digest(text.toString().getBytes(StandardCharsets.UTF_8)))
                    .substring(0, 16);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("every Java has SHA-256", ex);
        }
    }
}
