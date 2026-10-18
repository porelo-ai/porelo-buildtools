package org.fuin.porelo.release;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * Says whether upstream has moved on from the version the module pins.
 *
 * <p>Every agent answers in the same lines, so the job that calls it does not know which source was
 * read. {@code update=yes} is not a failing exit: a scheduled job that goes red whenever there is
 * work teaches whoever watches it to ignore red.
 */
public final class UpstreamVersion {

    /** The question was answered, whatever the answer. */
    public static final int ANSWERED = 0;

    private final PrintStream out;

    private final PrintStream err;

    private final Web web;

    private final Map<String, String> env;

    private final Clock clock;

    /**
     * A lookup that reports on two streams.
     *
     * @param out where the answer goes
     * @param err where an unanswered question is explained
     * @param web where upstream is read
     * @param env the environment: {@code GITHUB_OUTPUT} and {@code GITHUB_TOKEN}
     */
    public UpstreamVersion(PrintStream out, PrintStream err, Web web, Map<String, String> env) {
        this(out, err, web, env, Clock.systemUTC());
    }

    /**
     * A lookup that reports on two streams, telling a release's age by a given clock.
     *
     * @param out where the answer goes
     * @param err where an unanswered question is explained
     * @param web where upstream is read
     * @param env the environment: {@code GITHUB_OUTPUT} and {@code GITHUB_TOKEN}
     * @param clock what "now" is when a release's age is told
     */
    public UpstreamVersion(
            PrintStream out, PrintStream err, Web web, Map<String, String> env, Clock clock) {
        this.out = Objects.requireNonNull(out, "out");
        this.err = Objects.requireNonNull(err, "err");
        this.web = Objects.requireNonNull(web, "web");
        this.env = Map.copyOf(env);
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /**
     * Answers for the agent's CLI, taking a release of any age.
     *
     * @param pom the module's pom
     * @param channel the channel or dist-tag named on the command line, or null for the pom's
     * @param named a version a person named, answered as if upstream offered it, or null
     * @return {@link #ANSWERED} or {@link Stop#UNANSWERED}
     */
    public int answer(Path pom, @Nullable String channel, @Nullable String named) {
        return answer(pom, channel, named, null, null);
    }

    /**
     * Answers.
     *
     * <p><strong>The newest release that is at least the age asked for is the update</strong>, even
     * where newer, younger ones exist: it says {@code upstream=} that one and {@code newest=} the
     * newer one. When none newer than the pin is old enough, the answer is {@code update=no} with a
     * {@code waiting=} line saying when the newest becomes old enough. A version a person named is
     * theirs to judge and waits for nothing.
     *
     * @param pom the module's pom
     * @param channel the channel or dist-tag named on the command line, or null for the pom's
     * @param named a version a person named, answered as if upstream offered it, or null
     * @param pin a pin beside the CLI, as {@code porelo.release.pin.<name>} declares it, or null
     *     for the CLI
     * @param minAge how old a release has to be, {@code 3d} or {@code 72h}, or null for the pom's
     *     {@code porelo.release.min-age}, or any age when neither says
     * @return {@link #ANSWERED} or {@link Stop#UNANSWERED}
     */
    public int answer(
            Path pom,
            @Nullable String channel,
            @Nullable String named,
            @Nullable String pin,
            @Nullable String minAge) {
        final Map<String, String> answer = new LinkedHashMap<>();
        try {
            final Pom read = Pom.read(pom);
            final Tracked tracked = pin == null ? Release.of(read) : Pin.of(read, pin);
            // Read before upstream is asked anything: a bound nobody can read must not pass as "no
            // bound".
            final String bound =
                    minAge != null ? minAge : read.optional(Release.PREFIX + "min-age");
            final Duration age = bound == null ? null : Age.bound(bound);
            final String have = tracked.pinned();
            final String followed = channel != null ? channel : tracked.channel();
            final boolean byDigest = tracked.upstream().byDigest();
            if (named != null
                    && !(tracked instanceof Pin one
                            ? one.accepts(named)
                            : Versions.isVersion(named))) {
                throw Stop.unanswered(
                        "'" + named + "' is not a " + (byDigest ? "digest" : "version"));
            }
            final String there =
                    named != null ? named : tracked.upstream().version(web, followed, env);
            answer.put("pinned", have);
            answer.put("upstream", there);
            answer.put(
                    "source",
                    named != null
                            ? "a version named by hand"
                            : tracked.upstream().source(followed));
            // A digest has no order: a different one is the tag rebuilt, never a rollback.
            final Versions.Verdict verdict =
                    byDigest
                            ? (have.equals(there) ? Versions.Verdict.NO : Versions.Verdict.YES)
                            : Versions.verdict(have, there, named != null);
            answer.put("update", verdict.word());
            answer.put("major", !byDigest && Versions.majorMoved(have, there) ? "moved" : "same");
            if (age != null && named == null && verdict == Versions.Verdict.YES) {
                // The newest release that has stood the age, even where newer, younger ones exist:
                // a project that releases more often than the age would otherwise never move.
                final Instant newestPublished =
                        tracked.upstream().published(web, there, followed, env);
                final java.util.Optional<Release.Standing> standing =
                        clock.instant().isBefore(newestPublished.plus(age))
                                ? tracked.upstream()
                                        .standing(web, followed, env, clock.instant().minus(age))
                                : java.util.Optional.of(
                                        new Release.Standing(there, newestPublished));
                if (standing.isPresent()
                        && (byDigest
                                ? !standing.get().version().equals(have)
                                : Versions.compare(standing.get().version(), have) > 0)) {
                    final String taken = standing.get().version();
                    answer.put("upstream", taken);
                    answer.put(
                            "major",
                            !byDigest && Versions.majorMoved(have, taken) ? "moved" : "same");
                    answer.put("published", standing.get().published().toString());
                    if (!taken.equals(there)) {
                        answer.put("newest", there);
                    }
                } else {
                    answer.put("update", Versions.Verdict.NO.word());
                    answer.put("published", newestPublished.toString());
                    answer.put("waiting", newestPublished.plus(age).toString());
                }
            }
        } catch (Stop stop) {
            // Every stop is "could not tell" here: a pom that pins nothing is not "up to date".
            err.println("could not tell whether there is a new version - " + stop.getMessage());
            return Stop.UNANSWERED;
        }
        answer.forEach((key, value) -> out.println(key + "=" + value));
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
}
