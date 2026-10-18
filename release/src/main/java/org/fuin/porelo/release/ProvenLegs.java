package org.fuin.porelo.release;

import java.io.IOException;
import java.io.PrintStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.fuin.porelo.json.Json;
import org.fuin.porelo.json.JsonException;
import org.jspecify.annotations.Nullable;

/**
 * Says whether a Build may take its legs from a run that already proved the same tree, rather than
 * renting machines for it again.
 *
 * <p>A run counts when it is of this repository and was started by a push, a dispatch or a schedule
 * - never by a pull request, whose code may come from anyone - when its commit has the same tree,
 * when it is younger than the bound, since images, snapshots and downloaded programs move under an
 * unchanged tree, and when every job of one of the named groups in it concluded {@code success}. A
 * tag always runs its own legs. The answer is {@code proven=yes} with the run, or {@code
 * proven=no}; it is never a failing exit, since running the legs is always a safe answer.
 */
public final class ProvenLegs {

    /** The question was answered, whatever the answer. */
    public static final int ANSWERED = 0;

    /** The name of the artifact a run makes to say which tree it verified other than its head's. */
    public static final String VERIFIED_TREE = "porelo-verified-tree-";

    /** The events whose code is the repository's own. */
    private static final Set<String> TRUSTED = Set.of("push", "workflow_dispatch", "schedule");

    private final PrintStream out;

    private final PrintStream err;

    private final Web web;

    private final Map<String, String> env;

    private final Clock clock;

    /**
     * A finder that reports on two streams.
     *
     * @param out where the answer goes
     * @param err where an unanswered question is explained
     * @param web where GitHub is read
     * @param env the environment: {@code GITHUB_TOKEN}, {@code GITHUB_RUN_ID}, {@code
     *     GITHUB_REF_TYPE} and {@code GITHUB_OUTPUT}
     * @param clock what "now" is when a run's age is told
     */
    public ProvenLegs(
            PrintStream out, PrintStream err, Web web, Map<String, String> env, Clock clock) {
        this.out = Objects.requireNonNull(out, "out");
        this.err = Objects.requireNonNull(err, "err");
        this.web = Objects.requireNonNull(web, "web");
        this.env = Map.copyOf(env);
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /**
     * Looks for a run that proved the tree.
     *
     * @param repository the repository, {@code owner/name}
     * @param tree the tree hash of the commit being built
     * @param groups the jobs that prove it, each group on its own: a run counts when every job of
     *     one group in it concluded {@code success}
     * @param maxAge how old the proving run may be, {@code 24h} or {@code 1d}
     * @return {@link #ANSWERED}; {@link Stop#UNANSWERED} only when the answer cannot be written
     */
    public int find(String repository, String tree, List<List<String>> groups, String maxAge) {
        final Map<String, String> answer = new LinkedHashMap<>();
        try {
            if ("tag".equals(env.get("GITHUB_REF_TYPE"))) {
                answer.put("proven", "no");
                answer.put("why", "a tag always runs its own legs");
            } else {
                final Duration age = Age.bound(maxAge);
                final Instant since = clock.instant().minus(age).truncatedTo(ChronoUnit.SECONDS);
                final Proof found = proving(repository, tree, groups, since);
                if (found == null) {
                    answer.put("proven", "no");
                    answer.put(
                            "why",
                            "no run of tree "
                                    + tree
                                    + " in the last "
                                    + maxAge
                                    + " from a push, a dispatch or a schedule with every job of "
                                    + groups
                                    + " green");
                } else {
                    answer.put("proven", "yes");
                    answer.put("run", found.run());
                    answer.put(
                            "why",
                            "legs taken from run "
                                    + found.run()
                                    + ", tree "
                                    + tree
                                    + ", "
                                    + old(Duration.between(found.created(), clock.instant())));
                }
            }
        } catch (Stop stop) {
            // Not knowing is no proof: the legs run.
            answer.put("proven", "no");
            answer.put("why", "could not tell - " + stop.getMessage());
        }
        answer.forEach((key, value) -> out.println(key + "=" + value));
        out.println(answer.get("why"));
        final String output = env.get("GITHUB_OUTPUT");
        if (output != null && !output.isBlank()) {
            final StringBuilder lines = new StringBuilder();
            answer.forEach(
                    (key, value) -> lines.append(key).append('=').append(value).append('\n'));
            try {
                Files.writeString(
                        Path.of(output),
                        lines,
                        StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE,
                        StandardOpenOption.APPEND);
            } catch (IOException ex) {
                err.println("could not append to GITHUB_OUTPUT " + output + ": " + ex.getMessage());
                return Stop.UNANSWERED;
            }
        }
        return ANSWERED;
    }

    private static String old(Duration age) {
        return age.toHours() + "h " + age.toMinutesPart() + "m old";
    }

    /** A run that proved the tree, and when it was started. */
    private record Proof(String run, Instant created) {}

    /** The newest run that proves the tree, or null. */
    private @Nullable Proof proving(
            String repository, String tree, List<List<String>> groups, Instant since) throws Stop {
        final URI runs =
                URI.create(
                        "https://api.github.com/repos/"
                                + repository
                                + "/actions/runs?per_page=100&created=%3E%3D"
                                + since);
        final Object listed = field(read(runs), "workflow_runs");
        if (!(listed instanceof List<?> all)) {
            throw Stop.unanswered(runs + " did not list workflow runs");
        }
        final String self = env.getOrDefault("GITHUB_RUN_ID", "");
        for (final Object each : all) {
            if (!(each instanceof Map<?, ?> run)
                    || !(run.get("id") instanceof Number id)
                    || !(run.get("created_at") instanceof String when)) {
                continue;
            }
            final String number = Long.toString(id.longValue());
            // Each of these is read before anything of the run is asked for: a pull request's run
            // is not looked at, whatever its jobs say.
            final String event = String.valueOf(run.get("event"));
            if (number.equals(self)
                    || !TRUSTED.contains(event)
                    || !repository.equals(field(run.get("repository"), "full_name"))
                    || !repository.equals(field(run.get("head_repository"), "full_name"))
                    || Instant.parse(when).isBefore(since)) {
                continue;
            }
            // An update run starts on main and verifies the commit it pushes afterwards, so its
            // head's
            // tree is not the one merged later. It names that tree in an artifact, which only the
            // run
            // itself can make - unlike a pull request's text, which anybody editing it can change.
            if (!tree.equals(field(run.get("head_commit"), "tree_id"))
                    && ("push".equals(event) || !verified(repository, number, tree))) {
                continue;
            }
            if (proves(repository, number, groups)) {
                return new Proof(number, Instant.parse(when));
            }
        }
        return null;
    }

    private boolean verified(String repository, String run, String tree) throws Stop {
        final URI address =
                URI.create(
                        "https://api.github.com/repos/"
                                + repository
                                + "/actions/runs/"
                                + run
                                + "/artifacts?per_page=100");
        if (field(read(address), "artifacts") instanceof List<?> all) {
            for (final Object each : all) {
                if (each instanceof Map<?, ?> artifact
                        && (VERIFIED_TREE + tree).equals(artifact.get("name"))) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean proves(String repository, String run, List<List<String>> groups) throws Stop {
        final URI address =
                URI.create(
                        "https://api.github.com/repos/"
                                + repository
                                + "/actions/runs/"
                                + run
                                + "/jobs?per_page=100");
        final Map<String, String> concluded = new LinkedHashMap<>();
        if (field(read(address), "jobs") instanceof List<?> all) {
            for (final Object each : all) {
                if (each instanceof Map<?, ?> job && job.get("name") instanceof String name) {
                    concluded.put(name, String.valueOf(job.get("conclusion")));
                }
            }
        }
        for (final List<String> group : groups) {
            if (!group.isEmpty()
                    && group.stream().allMatch(name -> "success".equals(concluded.get(name)))) {
                return true;
            }
        }
        return false;
    }

    private @Nullable Object read(URI address) throws Stop {
        final String body;
        try {
            body =
                    web.read(address, Release.githubHeaders(address, env))
                            .map(bytes -> new String(bytes, StandardCharsets.UTF_8))
                            .orElseThrow(() -> Stop.unanswered(address + ": HTTP 404"));
        } catch (IOException ex) {
            throw Stop.unanswered(Objects.requireNonNullElse(ex.getMessage(), ex.toString()), ex);
        }
        try {
            return Json.parse(body);
        } catch (JsonException ex) {
            throw Stop.unanswered(address + " did not answer with JSON: " + ex.getMessage(), ex);
        }
    }

    private static @Nullable Object field(@Nullable Object parsed, String name) {
        return parsed instanceof Map<?, ?> map ? map.get(name) : null;
    }
}
