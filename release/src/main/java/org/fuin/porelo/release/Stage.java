package org.fuin.porelo.release;

import java.io.IOException;
import java.io.PrintStream;
import java.math.BigDecimal;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.fuin.porelo.json.Json;
import org.fuin.porelo.json.JsonException;
import org.jspecify.annotations.Nullable;

/** Records closed issue stages while keeping open work in the repository's git directory. */
final class Stage {
    private static final Set<String> STAGES =
            Set.of(
                    "analyze",
                    "estimate",
                    "plan",
                    "implement",
                    "test",
                    "review",
                    "rework",
                    "handover",
                    "wait");
    private static final Set<String> MEASURED =
            Set.of("plan", "implement", "test", "review", "rework", "handover");
    private static final Set<String> SIZES = Set.of("XS", "S", "M", "L", "XL");
    private static final Set<String> STATES =
            Set.of(
                    "Idea",
                    "Accepted",
                    "Rejected",
                    "Analyzed",
                    "Estimated",
                    "Backlog",
                    "Implementing",
                    "Review",
                    "Rework",
                    "Finished");
    private static final Set<String> REASONS = Set.of("operator", "build", "agent", "vm");
    private static final Set<String> RESULTS = Set.of("done", "returned", "refused", "abandoned");
    private static final Set<String> OPTIONS =
            Set.of(
                    "--actor",
                    "--model",
                    "--effort",
                    "--session",
                    "--result",
                    "--status-after",
                    "--wait");
    private static final String USAGE =
            "stage begin|end <issue> <stage> [--actor agent|subagent] [--model MODEL] [--effort"
                    + " EFFORT] [--session FILE] [--result done|returned|refused|abandoned]"
                    + " [--status-after STATE] [--wait operator|build|agent|vm] | stage median <size>";

    private final Path root;
    private final Clock clock;
    private final PrintStream out;
    private final PrintStream err;
    private final Map<String, String> environment;

    Stage(
            Path root,
            Clock clock,
            PrintStream out,
            PrintStream err,
            Map<String, String> environment) {
        this.root = root.toAbsolutePath().normalize();
        this.clock = clock;
        this.out = out;
        this.err = err;
        this.environment = environment;
    }

    /** Runs a stage operation, returning 2 with a reason on any refusal or unreadable input. */
    int run(List<String> arguments) {
        try {
            if (arguments.size() == 2 && arguments.getFirst().equals("median")) {
                out.println(decimal(median(arguments.get(1))));
                return 0;
            }
            final Main.Options options = Main.Options.parse(arguments, OPTIONS, Set.of());
            if (options == null) {
                throw new IllegalArgumentException(USAGE);
            }
            final Set<String> seen = new java.util.HashSet<>();
            for (final String argument : arguments) {
                if (argument.startsWith("--")) {
                    require(seen.add(argument.split("=", 2)[0]), "a stage option was repeated");
                }
            }
            final List<String> positional = options.positional();
            require(positional.size() == 3, USAGE);
            final String action = positional.getFirst();
            require(action.equals("begin") || action.equals("end"), USAGE);
            final String issue = positional.get(1);
            final String stage = positional.get(2);
            require(
                    issue.matches("[A-Z]{1,3}\\d{2,3}"),
                    "issue must match [A-Z]{1,3} followed by two or three digits");
            require(STAGES.contains(stage), "unknown stage");
            final String actor = options.values().getOrDefault("--actor", "agent");
            require(Set.of("agent", "subagent").contains(actor), "actor must be agent or subagent");
            validateOptions(options, stage);
            require(
                    !action.equals("begin") || options.value("--status-after") == null,
                    "status-after is supplied on end");
            require(
                    !action.equals("begin")
                            || !stage.equals("wait")
                            || options.value("--wait") != null,
                    "a wait stage needs --wait");
            final String session = session(options, actor, action.equals("begin"));
            require(!actor.equals("subagent") || session != null, "a subagent needs --session");
            require(
                    !action.equals("begin")
                            || !actor.equals("subagent")
                            || options.value("--model") != null,
                    "a subagent begin needs --model");
            if (action.equals("begin")) {
                issue(issue);
            }
            final Path stateDirectory = gitDirectory().resolve("porelo-stage");
            safe(stateDirectory);
            Files.createDirectories(stateDirectory);
            final Path lockPath = stateDirectory.resolve("lock");
            safe(lockPath);
            // All actors share the lock so concurrent ends cannot interleave JSON lines or
            // ordinals.
            try (FileChannel channel =
                            FileChannel.open(
                                    lockPath, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
                    FileLock lock = channel.tryLock()) {
                require(lock != null, "another stage command is running");
                final Path state =
                        stateDirectory.resolve(
                                actor.equals("agent")
                                        ? "agent.json"
                                        : "subagent-"
                                                + digest(session == null ? "" : session)
                                                + ".json");
                safe(state);
                if (action.equals("begin")) {
                    begin(issue, stage, actor, session, options, state, stateDirectory);
                } else {
                    end(issue, stage, actor, session, options, state);
                }
            }
            return 0;
        } catch (IOException
                | IllegalArgumentException
                | JsonException
                | DateTimeException
                | ArithmeticException
                | OverlappingFileLockException ex) {
            err.println("stage: " + ex.getMessage());
            return 2;
        }
    }

    private static void validateOptions(Main.Options options, String stage) {
        for (final String key : List.of("--model", "--effort")) {
            final String value = options.value(key);
            require(
                    value == null || value.matches("[A-Za-z0-9._-]+"),
                    key + " must be one identifier");
        }
        final String result = options.value("--result");
        require(result == null || RESULTS.contains(result), "unknown result");
        final String status = options.value("--status-after");
        require(status == null || STATES.contains(status), "unknown status-after");
        final String reason = options.value("--wait");
        require(
                reason == null || (stage.equals("wait") && REASONS.contains(reason)),
                "wait reason must be operator, build, agent or vm on a wait stage");
        final String session = options.value("--session");
        require(session == null || !session.isBlank(), "session must name a file");
    }

    private @Nullable String session(Main.Options options, String actor, boolean beginning)
            throws IOException {
        final String given = options.value("--session");
        final String value =
                given != null
                        ? given
                        : beginning && actor.equals("agent")
                                ? environment.get("PORELO_STAGE_CLAUDE_LOG")
                                : null;
        if (value == null) {
            return null;
        }
        require(!value.isBlank(), "session must name a file");
        final Path path = root.resolve(value).normalize();
        require(
                Files.isRegularFile(path) && Files.isReadable(path),
                "session is not a readable regular file");
        try {
            return path.toRealPath().toString();
        } catch (IOException ex) {
            throw new IOException("could not resolve the session file");
        }
    }

    private void begin(
            String issue,
            String stage,
            String actor,
            @Nullable String session,
            Main.Options options,
            Path state,
            Path stateDirectory)
            throws IOException {
        require(!Files.exists(state), "a stage is already open for this actor/session");
        final Issue metadata = issue(issue);
        final List<Map<String, @Nullable Object>> previous = records(stats(issue));
        int ordinal = 1;
        for (final Map<String, @Nullable Object> record : previous) {
            if (stage.equals(record.get("stage"))) {
                ordinal = Math.max(ordinal, Math.toIntExact(integer(record.get("n"), "n")) + 1);
            }
        }
        // Reserve ordinals across open subagents as well as stages already appended.
        try (var paths = Files.list(stateDirectory)) {
            for (final Path open :
                    paths.filter(p -> p.getFileName().toString().endsWith(".json")).toList()) {
                safe(open);
                final Map<String, @Nullable Object> record =
                        object(object(Json.parse(Files.readString(open))).get("record"));
                if (issue.equals(record.get("issue")) && stage.equals(record.get("stage"))) {
                    ordinal = Math.max(ordinal, Math.toIntExact(integer(record.get("n"), "n")) + 1);
                }
            }
        }
        final String reason = options.value("--wait");
        require(!stage.equals("wait") || reason != null, "a wait stage needs --wait");
        final Map<String, @Nullable Object> record = new LinkedHashMap<>();
        record.put("issue", issue);
        record.put("stage", stage);
        record.put("n", ordinal);
        record.put("actor", actor);
        record.put("model", options.value("--model"));
        record.put("effort", options.value("--effort"));
        record.put("size", previous.isEmpty() ? metadata.size() : previous.getFirst().get("size"));
        record.put("status_before", metadata.status());
        record.put("status_after", null);
        record.put("start", clock.instant().toString());
        record.put("end", null);
        record.put("wall_min", null);
        record.put("vm_min", null);
        record.put("estimate_min", stage.equals("estimate") ? median(metadata.size()) : null);
        record.put("result", options.value("--result"));
        record.put("wait_reason", reason);
        record.put("note", session == null ? "no-session" : null);
        StageTokens.empty(record);
        validate(record, false);
        final Map<String, @Nullable Object> saved = new LinkedHashMap<>();
        saved.put("record", record);
        // Only private git state retains the source needed by end; committed records never do.
        saved.put("session", session);
        Files.writeString(
                state, Json.write(saved), StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        out.println("stage opened");
    }

    private void end(
            String issue,
            String stage,
            String actor,
            @Nullable String session,
            Main.Options options,
            Path state)
            throws IOException {
        require(Files.isRegularFile(state), "no open stage for this actor/session");
        final Map<String, @Nullable Object> saved = object(Json.parse(Files.readString(state)));
        final Map<String, @Nullable Object> record = object(saved.get("record"));
        validate(record, false);
        require(
                issue.equals(record.get("issue"))
                        && stage.equals(record.get("stage"))
                        && actor.equals(record.get("actor")),
                "end does not match the open stage");
        for (final String field : List.of("model", "effort", "wait_reason")) {
            final String value =
                    options.value(field.equals("wait_reason") ? "--wait" : "--" + field);
            require(value == null || value.equals(record.get(field)), "end cannot change " + field);
        }
        final Object source = saved.get("session");
        require(source == null || source instanceof String, "invalid private session state");
        require(session == null || session.equals(source), "end names a different session");
        final Instant start = Instant.parse(string(record, "start"));
        final Instant end = clock.instant();
        require(!end.isBefore(start), "end precedes start");
        final String statusAfter = options.value("--status-after");
        record.put("status_after", statusAfter == null ? issue(issue).status() : statusAfter);
        record.put(
                "result",
                options.values()
                        .getOrDefault(
                                "--result",
                                record.get("result") instanceof String result ? result : "done"));
        record.put("end", end.toString());
        final double minutes = Duration.between(start, end).toMillis() / 60000.0;
        record.put("wall_min", minutes);
        record.put("vm_min", "vm".equals(record.get("wait_reason")) ? minutes : 0);
        if (source instanceof String path) {
            StageTokens.read(root.resolve(path), actor, start, end, record);
        }
        validate(record, true);
        final Path output = stats(issue);
        final List<Map<String, @Nullable Object>> earlier = records(output);
        final List<Map<String, @Nullable Object>> same =
                earlier.stream()
                        .filter(
                                r ->
                                        Objects.equals(record.get("n"), r.get("n"))
                                                && stage.equals(r.get("stage")))
                        .toList();
        require(
                same.isEmpty()
                        || same.size() == 1
                                && Objects.equals(same.getFirst().get("start"), record.get("start"))
                                && actor.equals(same.getFirst().get("actor")),
                "stage ordinal already belongs to another record");
        Files.createDirectories(output.getParent());
        // A retry after append succeeded but state removal failed must not append the stage twice.
        if (same.isEmpty()) {
            Files.writeString(
                    output,
                    Json.write(record) + "\n",
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND);
        }
        Files.delete(state);
        out.println("stage closed");
    }

    private double median(String size) throws IOException {
        require(SIZES.contains(size), "unknown size");
        final Path stats = root.resolve("stats");
        final Path seed = stats.resolve("seed.csv");
        safe(stats);
        safe(seed);
        final List<String> lines = Files.readAllLines(seed);
        require(
                !lines.isEmpty() && lines.getFirst().equals("size,minutes"),
                "seed header must be size,minutes");
        final Map<String, Double> seeds = new LinkedHashMap<>();
        for (final String line : lines.subList(1, lines.size())) {
            final String[] cells = line.split(",", -1);
            require(cells.length == 2 && SIZES.contains(cells[0]), "invalid seed row");
            final double minutes = Double.parseDouble(cells[1]);
            require(
                    Double.isFinite(minutes)
                            && minutes >= 0
                            && seeds.putIfAbsent(cells[0], minutes) == null,
                    "invalid or duplicate seed size");
        }
        final Double seedMinutes = seeds.get(size);
        if (seedMinutes == null) {
            throw new IllegalArgumentException("no seed for size " + size);
        }
        final List<Double> samples = new ArrayList<>();
        samples.add(seedMinutes);
        try (var paths = Files.list(seed.getParent())) {
            for (final Path file :
                    paths.filter(p -> p.getFileName().toString().endsWith(".jsonl")).toList()) {
                require(
                        file.getFileName().toString().matches("[A-Z]{1,3}\\d{2,3}\\.jsonl"),
                        "invalid stats filename");
                final List<Map<String, @Nullable Object>> records = records(file);
                require(!records.isEmpty(), "empty stats file");
                double total = 0;
                for (final Map<String, @Nullable Object> record : records) {
                    require(
                            file.getFileName()
                                    .toString()
                                    .equals(string(record, "issue") + ".jsonl"),
                            "record issue disagrees with filename");
                    require(
                            STAGES.contains(string(record, "stage"))
                                    && Set.of("agent", "subagent")
                                            .contains(string(record, "actor")),
                            "unknown stage or actor in stats");
                    require(
                            SIZES.contains(string(record, "size"))
                                    && STATES.contains(string(record, "status_after")),
                            "unknown size or status in stats");
                    Instant.parse(string(record, "end"));
                    final double minutes = number(record.get("wall_min"), "wall_min");
                    if ("agent".equals(record.get("actor"))
                            && MEASURED.contains(record.get("stage"))) {
                        total += minutes;
                    }
                }
                require(Double.isFinite(total), "stage minutes overflow");
                if (size.equals(records.getFirst().get("size"))
                        && "Finished".equals(records.getLast().get("status_after"))) {
                    samples.add(total);
                }
            }
        }
        samples.sort(Double::compare);
        final int middle = samples.size() / 2;
        return samples.size() % 2 == 1
                ? samples.get(middle)
                : samples.get(middle - 1) / 2 + samples.get(middle) / 2;
    }

    private record Issue(String size, String status) {}

    private Issue issue(String issue) throws IOException {
        final Path directory = root.resolve("issues");
        safe(directory);
        final List<Path> matches;
        try (var files = Files.list(directory)) {
            matches =
                    files.filter(
                                    p ->
                                            p.getFileName()
                                                    .toString()
                                                    .matches(Pattern.quote(issue) + "-[^/]+\\.md"))
                            .toList();
        }
        require(matches.size() == 1, "expected exactly one issue file");
        final Path file = matches.getFirst();
        safe(file);
        final String text = Files.readString(file);
        final String size = field(text, "Size", "XS|S|M|L|XL");
        final String status = field(text, "Status", String.join("|", STATES));
        return new Issue(size, status);
    }

    private static String field(String text, String name, String allowed) {
        final Matcher matcher =
                Pattern.compile("(?m)^\\*\\*" + name + ":\\*\\*\\s+(" + allowed + ")(?=[.;\\s]|$)")
                        .matcher(text);
        require(matcher.find(), "issue has no valid " + name);
        final String value = matcher.group(1);
        require(!matcher.find(), "issue has duplicate " + name);
        return value;
    }

    private Path stats(String issue) throws IOException {
        final Path directory = root.resolve("stats");
        safe(directory);
        final Path file = directory.resolve(issue + ".jsonl");
        safe(file);
        return file;
    }

    private static List<Map<String, @Nullable Object>> records(Path file) throws IOException {
        safe(file);
        if (!Files.exists(file)) {
            return List.of();
        }
        final String contents = Files.readString(file);
        require(
                contents.isEmpty() || contents.endsWith("\n"),
                "stats file has an incomplete final line");
        final List<Map<String, @Nullable Object>> records = new ArrayList<>();
        for (final String line : contents.lines().toList()) {
            final Map<String, @Nullable Object> record = object(Json.parse(line));
            require(
                    record.get("end") instanceof String end && !end.isBlank(),
                    "stats file contains a stage without end");
            records.add(record);
        }
        return records;
    }

    private Path gitDirectory() throws IOException {
        final ProcessBuilder builder =
                new ProcessBuilder("git", "-C", root.toString(), "rev-parse", "--git-dir");
        builder.environment().putAll(environment);
        builder.environment().put("LC_ALL", "C");
        final Process process = builder.start();
        try {
            if (!process.waitFor(10, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                throw new IOException("git rev-parse did not finish within 10 seconds");
            }
            require(
                    process.exitValue() == 0,
                    "git rev-parse --git-dir failed; run in a repository root");
            final String directory =
                    new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8)
                            .strip();
            require(
                    !directory.isEmpty() && !directory.contains("\n"),
                    "git returned no single directory");
            final Path path = root.resolve(directory).normalize();
            require(Files.isDirectory(path), "git directory is unavailable");
            return path;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IOException("interrupted while reading the git directory", ex);
        }
    }

    private static void validate(Map<String, @Nullable Object> record, boolean closed) {
        final Set<String> fields =
                new java.util.HashSet<>(
                        List.of(
                                "issue",
                                "stage",
                                "n",
                                "actor",
                                "model",
                                "effort",
                                "size",
                                "status_before",
                                "status_after",
                                "start",
                                "end",
                                "wall_min",
                                "vm_min",
                                "estimate_min",
                                "result",
                                "wait_reason",
                                "note"));
        fields.addAll(StageTokens.FIELDS);
        require(record.keySet().equals(fields), "unknown or missing record fields");
        require(string(record, "issue").matches("[A-Z]{1,3}\\d{2,3}"), "invalid record issue");
        final String stage = string(record, "stage");
        require(STAGES.contains(stage), "invalid record stage");
        require(
                Set.of("agent", "subagent").contains(string(record, "actor")),
                "invalid record actor");
        require(SIZES.contains(string(record, "size")), "invalid record size");
        require(STATES.contains(string(record, "status_before")), "invalid record status_before");
        require(integer(record.get("n"), "n") > 0, "invalid stage ordinal");
        for (final String key : List.of("model", "effort")) {
            final Object value = record.get(key);
            require(
                    value == null
                            || value instanceof String text && text.matches("[A-Za-z0-9._-]+"),
                    "invalid record " + key);
        }
        final Object reason = record.get("wait_reason");
        require(
                stage.equals("wait")
                        ? reason instanceof String text && REASONS.contains(text)
                        : reason == null,
                "invalid record wait reason");
        final Object note = record.get("note");
        require(
                note == null
                        || note instanceof String text
                                && (text.equals("no-session") || text.matches("[a-z]+")),
                "invalid record note");
        final Object result = record.get("result");
        require(
                result == null && !closed
                        || result instanceof String text && RESULTS.contains(text),
                "invalid record result");
        final Object estimate = record.get("estimate_min");
        require(
                stage.equals("estimate") ? estimate != null : estimate == null,
                "invalid record estimate_min");
        if (estimate != null) {
            number(estimate, "estimate_min");
        }
        final Instant start = Instant.parse(string(record, "start"));
        if (closed) {
            require(STATES.contains(string(record, "status_after")), "invalid record status_after");
            require(!Instant.parse(string(record, "end")).isBefore(start), "invalid record end");
            number(record.get("wall_min"), "wall_min");
            number(record.get("vm_min"), "vm_min");
        } else {
            require(
                    record.get("end") == null
                            && record.get("status_after") == null
                            && record.get("wall_min") == null
                            && record.get("vm_min") == null,
                    "private stage is already closed");
        }
        for (final String key : StageTokens.FIELDS) {
            final Object value = record.get(key);
            if (value != null) {
                integer(value, key);
            }
        }
    }

    static Map<String, @Nullable Object> object(@Nullable Object value) {
        if (!(value instanceof Map<?, ?> map)) {
            throw new IllegalArgumentException("expected a JSON object");
        }
        final Map<String, @Nullable Object> result = new LinkedHashMap<>();
        for (final var entry : map.entrySet()) {
            if (!(entry.getKey() instanceof String key)) {
                throw new IllegalArgumentException("expected a JSON object key");
            }
            result.put(key, entry.getValue());
        }
        return result;
    }

    static String string(Map<String, @Nullable Object> record, String key) {
        final Object value = record.get(key);
        if (!(value instanceof String text)) {
            throw new IllegalArgumentException("missing or invalid " + key);
        }
        return text;
    }

    static double number(@Nullable Object value, String key) {
        if (!(value instanceof Number numeric)) {
            throw new IllegalArgumentException("missing or invalid " + key);
        }
        final double number = numeric.doubleValue();
        require(Double.isFinite(number) && number >= 0, "invalid " + key);
        return number;
    }

    static long integer(@Nullable Object value, String key) {
        final double number = number(value, key);
        require(
                number == Math.rint(number) && number <= 9007199254740991L,
                "invalid integer " + key);
        return (long) number;
    }

    static void require(boolean condition, String reason) {
        if (!condition) {
            throw new IllegalArgumentException(reason);
        }
    }

    private static void safe(Path path) throws IOException {
        if (Files.isSymbolicLink(path)) {
            throw new IOException("stage storage must not be a symbolic link");
        }
    }

    private static String decimal(double value) {
        return BigDecimal.valueOf(value).stripTrailingZeros().toPlainString();
    }

    private static String digest(String session) {
        try {
            return HexFormat.of()
                    .formatHex(
                            MessageDigest.getInstance("SHA-256")
                                    .digest(session.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }
}
