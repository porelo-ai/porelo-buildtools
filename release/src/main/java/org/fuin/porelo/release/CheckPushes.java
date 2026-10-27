package org.fuin.porelo.release;

import java.io.IOException;
import java.io.PrintStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import org.fuin.porelo.json.Json;
import org.fuin.porelo.json.JsonException;
import org.jspecify.annotations.Nullable;

/**
 * Holds every push to {@code main} of a recent window to the shared rule on commit messages, by the
 * push's own range.
 *
 * <p>A range picked by committer date misses a commit made long before its push, when that push
 * starts no Build of its own. GitHub's repository activity names each push with its {@code before}
 * and {@code after}, so the push is read as it happened.
 */
final class CheckPushes {

    /** The one API this command knows how to ask. */
    static final String GITHUB_API = "https://api.github.com";

    /** The most activity one page holds; a full page may hide more. */
    static final int PAGE = 100;

    /** The kinds of activity that move {@code main} to a new commit, its creation among them. */
    private static final Set<String> PUSHES =
            Set.of("push", "force_push", "pr_merge", "merge_queue_merge", "branch_creation");

    /** The branch this command reads. */
    private static final String MAIN = "refs/heads/main";

    private static final Pattern HASH = Pattern.compile("[0-9a-f]{40}");

    private static final Pattern REPOSITORY = Pattern.compile("[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+");

    /**
     * Checks the commits of one range, as {@code check-commits} does, and answers its exit code.
     */
    @FunctionalInterface
    interface Commits {

        /**
         * Checks a range.
         *
         * @param range {@code before..after}
         * @return {@code check-commits}' exit code
         */
        int check(String range);
    }

    /**
     * One push to {@code main}.
     *
     * @param range what {@code check-commits} reads: {@code before..after}, or {@code after} alone
     *     for the push that made {@code main}, whose every commit is new
     */
    private record Push(String range, String after, Instant when) {}

    private final PrintStream out;

    private final PrintStream err;

    private final Web web;

    private final Map<String, String> env;

    private final Clock clock;

    private final Commits commits;

    /**
     * A checker that reports on two streams.
     *
     * @param out where each push and its result go
     * @param err where an unanswered question is explained
     * @param web where GitHub is read
     * @param env the environment: {@code GITHUB_API_URL}, {@code GITHUB_REPOSITORY} and {@code
     *     GITHUB_TOKEN}; the token goes to GitHub's API alone
     * @param clock what "now" is
     * @param commits checks one push's range
     */
    CheckPushes(
            PrintStream out,
            PrintStream err,
            Web web,
            Map<String, String> env,
            Clock clock,
            Commits commits) {
        this.out = Objects.requireNonNull(out, "out");
        this.err = Objects.requireNonNull(err, "err");
        this.web = Objects.requireNonNull(web, "web");
        this.env = Map.copyOf(env);
        this.clock = Objects.requireNonNull(clock, "clock");
        this.commits = Objects.requireNonNull(commits, "commits");
    }

    /**
     * Checks every push to {@code main} younger than a window.
     *
     * @param window how far back pushes are read
     * @return 0 when every push holds to the rule, an empty window among them; 1 when a commit
     *     breaks it; 2 when a push or the activity could not be read
     */
    int check(Duration window) {
        final List<Push> pushes;
        try {
            pushes = pushes(clock.instant().minus(window));
        } catch (Stop stop) {
            err.println(redacted(Objects.requireNonNullElse(stop.getMessage(), "")));
            return stop.code();
        }
        if (pushes.isEmpty()) {
            out.println("no push to main in the last " + window.toHours() + " hours");
            return 0;
        }
        int worst = 0;
        for (final Push push : pushes) {
            out.println("push " + push.range() + " to " + push.after() + " at " + push.when());
            worst = Math.max(worst, commits.check(push.range()));
        }
        return worst;
    }

    /** The pushes to {@code main} since a moment, the oldest first. */
    private List<Push> pushes(Instant since) throws Stop {
        final String api = env.getOrDefault("GITHUB_API_URL", GITHUB_API);
        if (!GITHUB_API.equals(api)) {
            throw Stop.unanswered(
                    "check-pushes reads GitHub's repository activity, and "
                            + api
                            + " is not GitHub's API; this forge's pushes are not read");
        }
        final String repository = env.getOrDefault("GITHUB_REPOSITORY", "");
        if (!REPOSITORY.matcher(repository).matches()) {
            throw Stop.unanswered("GITHUB_REPOSITORY names no owner/name: '" + repository + "'");
        }
        final String token = env.get("GITHUB_TOKEN");
        if (token == null || token.isBlank()) {
            throw Stop.unanswered(
                    "GITHUB_TOKEN is not set, and the repository's activity needs it");
        }
        // A space or control character makes the header unsendable, and the client's refusal would
        // repeat the value.
        if (token.chars().anyMatch(c -> c <= ' ' || c == 0x7f)) {
            throw Stop.unanswered("GITHUB_TOKEN holds a character no header can carry");
        }
        final URI address =
                URI.create(
                        GITHUB_API
                                + "/repos/"
                                + repository
                                + "/activity?ref=refs%2Fheads%2Fmain&per_page="
                                + PAGE);
        if (!(read(address) instanceof List<?> listed)) {
            throw Stop.unanswered(address + " did not list the repository's activity");
        }
        final List<Push> pushes = new ArrayList<>();
        @Nullable Instant oldest = null;
        for (final Object each : listed) {
            if (!(each instanceof Map<?, ?> activity)) {
                throw Stop.unanswered(address + " listed something that is no activity");
            }
            final Instant when = when(address, activity.get("timestamp"));
            oldest = oldest == null || when.isBefore(oldest) ? when : oldest;
            if (when.isBefore(since)
                    || !MAIN.equals(activity.get("ref"))
                    || !PUSHES.contains(activity.get("activity_type"))) {
                continue;
            }
            final String before = hash(address, activity.get("before"));
            final String after = hash(address, activity.get("after"));
            // The push that made main has no commit before it: every commit up to after is new.
            pushes.add(
                    new Push(
                            before.chars().allMatch(c -> c == '0') ? after : before + ".." + after,
                            after,
                            when));
        }
        // Only the first page is read: a full one whose oldest entry is still inside the window may
        // hide pushes on the next.
        if (listed.size() >= PAGE && oldest != null && !oldest.isBefore(since)) {
            throw Stop.unanswered(
                    address
                            + " answered a full page of "
                            + PAGE
                            + " still inside the window, so pushes may be missing");
        }
        pushes.sort((a, b) -> a.when().compareTo(b.when()));
        return pushes;
    }

    /** A message with the token taken out, whatever put it there. */
    private String redacted(String message) {
        final String token = env.get("GITHUB_TOKEN");
        return token == null || token.isBlank() ? message : message.replace(token, "<token>");
    }

    private static Instant when(URI address, @Nullable Object value) throws Stop {
        if (value instanceof String text) {
            try {
                return Instant.parse(text);
            } catch (DateTimeParseException ex) {
                throw Stop.unanswered(address + " gave a time that is none: '" + text + "'", ex);
            }
        }
        throw Stop.unanswered(address + " gave an activity without a time");
    }

    private static String hash(URI address, @Nullable Object value) throws Stop {
        if (value instanceof String text && HASH.matcher(text).matches()) {
            return text;
        }
        throw Stop.unanswered(address + " gave a push without a full commit hash");
    }

    private @Nullable Object read(URI address) throws Stop {
        final String body;
        try {
            body =
                    web.read(address, Release.githubHeaders(address, env))
                            .map(bytes -> new String(bytes, StandardCharsets.UTF_8))
                            .orElseThrow(() -> Stop.unanswered(address + ": HTTP 404"));
        } catch (IOException | RuntimeException ex) {
            throw Stop.unanswered(
                    address
                            + " could not be read: "
                            + Objects.requireNonNullElse(ex.getMessage(), ""));
        }
        try {
            return Json.parse(body);
        } catch (JsonException ex) {
            throw Stop.unanswered(address + " did not answer with JSON: " + ex.getMessage(), ex);
        }
    }
}
