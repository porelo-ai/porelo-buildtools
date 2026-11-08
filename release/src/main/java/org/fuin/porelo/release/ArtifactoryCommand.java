package org.fuin.porelo.release;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.fuin.porelo.pinned.PinnedRetry;

/**
 * The uploads and calls of a Publish job, the one helper every repository takes in place of a copy
 * of {@code artifactory.sh}: {@code put}, {@code api} and {@code ping}.
 *
 * <p>The token comes from the job's environment into this process and goes only into the {@code
 * Authorization} header of calls to the platform: never on a command line, into a file or into a
 * log. What did not answer, or answered a 5xx, is asked again by {@link PinnedRetry}'s pauses and
 * deadline; an upload sends the same bytes each time, so asking again changes nothing twice.
 * Anything else fails at once.
 */
final class ArtifactoryCommand {

    static final String USAGE =
            "artifactory put FILE REPOSITORY/PATH[/] [KEY=VALUE[;KEY=VALUE...]]"
                    + " | artifactory api GET|PUT|POST|DELETE|PATCH /PATH | artifactory ping";

    private static final Set<String> METHODS = Set.of("GET", "PUT", "POST", "DELETE", "PATCH");

    private static final String NAME = "[A-Za-z0-9][A-Za-z0-9._-]*";

    /** A segment of a target: what a file name holds, never a character a URI must escape. */
    private static final String SEGMENT = "[A-Za-z0-9._+~@,=-]+";

    private static final String PROPERTY = "[A-Za-z0-9._-]+=[A-Za-z0-9._+~:@,-]+";

    /** A segment of a REST path, percent escapes allowed but none of a dot or a slash. */
    private static final String API_SEGMENT =
            "(?:[A-Za-z0-9._~!$'()*+,:@=-]|%(?![2][EeFf])[0-9A-Fa-f]{2})+";

    private static final String API_QUERY = "(?:[A-Za-z0-9._~!$'()*+,:@=/?&-]|%[0-9A-Fa-f]{2})*";

    /** How a wait between attempts is spent, and the clock the deadline is kept by. */
    interface Pause {

        /**
         * Waits.
         *
         * @param time how long
         * @throws InterruptedException if interrupted
         */
        void of(Duration time) throws InterruptedException;

        /**
         * The time now, on a clock that only counts up.
         *
         * @return a point on it; only the time between two counts
         */
        default Instant now() {
            return Instant.EPOCH.plusNanos(System.nanoTime());
        }
    }

    /** A call that may be asked again. */
    private interface Call<T> {
        T run() throws IOException;
    }

    private final Artifactory artifactory;

    private final PrintStream out;

    private final PrintStream err;

    private final Pause pause;

    ArtifactoryCommand(Artifactory artifactory, PrintStream out, PrintStream err, Pause pause) {
        this.artifactory = artifactory;
        this.out = out;
        this.err = err;
        this.pause = pause;
    }

    /**
     * Whether the arguments are one of the three calls, before anything is asked.
     *
     * @param args the arguments after {@code artifactory}
     * @return whether they fit {@link #USAGE}
     */
    static boolean fits(List<String> args) {
        if (args.isEmpty()) {
            return false;
        }
        return switch (args.getFirst()) {
            case "put" ->
                    (args.size() == 3 || args.size() == 4)
                            && isTarget(args.get(2))
                            && (args.size() == 3
                                    || args.get(3).matches(PROPERTY + "(;" + PROPERTY + ")*"));
            case "api" ->
                    args.size() == 3 && METHODS.contains(args.get(1)) && isApiPath(args.get(2));
            case "ping" -> args.size() == 1;
            default -> false;
        };
    }

    /**
     * Whether a REST path stays under {@code /artifactory}: segments without {@code .} or {@code
     * ..}, no empty one, nothing a URI must escape, a query at the end.
     */
    private static boolean isApiPath(String path) {
        final int query = path.indexOf('?');
        final String segments = query < 0 ? path : path.substring(0, query);
        if (!segments.matches("(/" + API_SEGMENT + ")+/?")
                || (query >= 0 && !path.substring(query + 1).matches(API_QUERY))) {
            return false;
        }
        return noDotSegment(segments.substring(1));
    }

    /**
     * Whether a target names a file in a repository: segments of a file name's characters, no
     * {@code .} or {@code ..}, a trailing {@code /} for the file's own name.
     */
    static boolean isTarget(String target) {
        return target.matches(NAME + "(/" + SEGMENT + ")+/?") && noDotSegment(target);
    }

    private static boolean noDotSegment(String path) {
        for (String segment : path.split("/", -1)) {
            if (segment.equals("..") || segment.equals(".")) {
                return false;
            }
        }
        return true;
    }

    /**
     * Runs one call.
     *
     * @param args arguments that {@link #fits}
     * @return 0 when it was done; {@link Stop#REFUSED} when Artifactory refused it or a file is
     *     missing; {@link Stop#UNANSWERED} when Artifactory did not answer within the rule
     */
    int run(List<String> args) {
        try {
            switch (args.getFirst()) {
                case "put" -> put(args);
                case "api" -> {
                    final byte[] body =
                            retried(
                                    args.get(1) + " " + args.get(2),
                                    () -> artifactory.call(args.get(1), args.get(2)));
                    out.write(body);
                    out.flush();
                }
                default -> {
                    final String answer =
                            new String(
                                            retried(
                                                    "ping",
                                                    () ->
                                                            artifactory.call(
                                                                    "GET", "/api/system/ping")),
                                            StandardCharsets.UTF_8)
                                    .strip();
                    if (!answer.equals("OK")) {
                        err.println(
                                "::error::Artifactory's ping answered '" + answer + "', not OK");
                        return Stop.REFUSED;
                    }
                    out.println("  OK    Artifactory answers, and the token is accepted");
                }
            }
            return 0;
        } catch (Artifactory.Unanswered ex) {
            err.println("::error::" + ex.getMessage());
            return Stop.UNANSWERED;
        } catch (IOException ex) {
            err.println("::error::" + ex.getMessage());
            // Stopped from outside, not refused by Artifactory.
            return Thread.currentThread().isInterrupted() ? Stop.UNANSWERED : Stop.REFUSED;
        }
    }

    private void put(List<String> args) throws IOException {
        final Path file = Path.of(args.get(1));
        if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("no file '" + file + "' to upload");
        }
        final String target =
                args.get(2).endsWith("/")
                        ? args.get(2) + String.valueOf(file.getFileName())
                        : args.get(2);
        if (!isTarget(target) || target.endsWith("/")) {
            throw new IOException(
                    "'" + target + "' is no target: the file's name holds a character it cannot");
        }
        final String properties = args.size() == 4 ? args.get(3) : "";
        retried(
                "PUT " + target,
                () -> {
                    artifactory.upload(target, file, properties);
                    return Boolean.TRUE;
                });
        out.println("  put   " + target);
    }

    private <T> T retried(String what, Call<T> call) throws IOException {
        final Instant start = pause.now();
        for (int attempt = 1; ; attempt++) {
            try {
                return call.run();
            } catch (Artifactory.Unanswered ex) {
                final Duration elapsed = Duration.between(start, pause.now());
                final Optional<Duration> next = PinnedRetry.pauseAfter(attempt, elapsed);
                if (next.isEmpty()) {
                    throw new Artifactory.Unanswered(
                            ex.getMessage()
                                    + "; given up after "
                                    + attempt
                                    + " of at most "
                                    + PinnedRetry.attempts()
                                    + " attempts in "
                                    + Math.max(0, elapsed.toSeconds())
                                    + " s",
                            ex);
                }
                err.println(
                        "::warning::"
                                + ex.getMessage()
                                + " on attempt "
                                + attempt
                                + " of "
                                + PinnedRetry.attempts()
                                + "; trying again in "
                                + next.get().toSeconds()
                                + " s");
                try {
                    pause.of(next.get());
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new IOException(
                            "interrupted while waiting to ask again: " + what, interrupted);
                }
            }
        }
    }
}
