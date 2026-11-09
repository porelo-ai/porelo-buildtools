package org.fuin.porelo.release;

import java.io.IOException;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
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
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.fuin.porelo.json.Json;
import org.fuin.porelo.json.JsonException;
import org.fuin.porelo.stories.Workflow;
import org.jspecify.annotations.Nullable;

/** Records public issue stages and keeps their session sources private. */
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
                    + " [--status-after STATE] [--wait operator|build|agent|vm] | stage median|p80"
                    + " <size>";

    private final Path root;
    private @Nullable Workflow workflow;
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

    private Workflow workflow() throws IOException {
        if (workflow == null) {
            final Path file = root.resolve("issues/workflow.yml");
            safe(file);
            try {
                workflow = Workflow.read(file);
            } catch (IOException ex) {
                throw new IllegalArgumentException(
                        "could not read " + file + ": " + ex.getMessage(), ex);
            }
        }
        return workflow;
    }

    /** Runs a stage operation, returning 2 with a reason on any refusal or unreadable input. */
    int run(List<String> arguments) {
        try {
            if (arguments.size() == 2 && Set.of("median", "p80").contains(arguments.getFirst())) {
                out.println(
                        decimal(statistic(arguments.get(1), arguments.getFirst().equals("p80"))));
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
                    !action.equals("begin") || options.value("--result") == null,
                    "result is supplied on end");
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
                    begin(issue, stage, actor, session, options, state);
                } else {
                    end(issue, stage, actor, session, options, state);
                }
            }
            return 0;
        } catch (IOException | UncheckedIOException ex) {
            err.println("stage: could not access stage storage or session file");
            return 2;
        } catch (InvalidPathException ex) {
            err.println("stage: invalid session path");
            return 2;
        } catch (JsonException | DateTimeException | NumberFormatException ex) {
            err.println("stage: invalid JSON, timestamp or number");
            return 2;
        } catch (IllegalArgumentException | ArithmeticException | OverlappingFileLockException ex) {
            err.println("stage: " + ex.getMessage());
            return 2;
        }
    }

    private void validateOptions(Main.Options options, String stage) throws IOException {
        for (final String value : options.values().values()) {
            require(!value.startsWith("-"), "an option value must not start with a hyphen");
        }
        for (final String key : List.of("--model", "--effort")) {
            final String value = options.value(key);
            require(
                    value == null || value.matches("[A-Za-z0-9._-]+"),
                    key + " must be one identifier");
        }
        final String result = options.value("--result");
        require(result == null || RESULTS.contains(result), "unknown result");
        final String status = options.value("--status-after");
        require(
                status == null || workflow().states().contains(status),
                "unknown status-after in " + root.resolve("issues/workflow.yml"));
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
                beginning && actor.equals("subagent") && Files.notExists(path)
                        || Files.isRegularFile(path) && Files.isReadable(path),
                "session is not a readable regular file");
        return path.toString();
    }

    private void begin(
            String issue,
            String stage,
            String actor,
            @Nullable String session,
            Main.Options options,
            Path state)
            throws IOException {
        require(!Files.exists(state), "a stage is already open for this actor/session");
        final Issue metadata = issue(issue);
        require(
                metadata.size() != null || Set.of("analyze", "estimate").contains(stage),
                "this stage needs an issue size");
        final Path output = stats(issue);
        final List<Map<String, @Nullable Object>> previous = records(output);
        if (actor.equals("agent")) {
            requireNoOpenOwnStage();
        }
        int ordinal = 1;
        for (final Map<String, @Nullable Object> record : previous) {
            if (stage.equals(record.get("stage"))) {
                ordinal = Math.max(ordinal, Math.toIntExact(integer(record.get("n"), "n")) + 1);
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
        record.put("size", metadata.size());
        record.put("status_before", metadata.status());
        record.put("status_after", null);
        record.put("start", clock.instant().toString());
        record.put("end", null);
        record.put("wall_min", null);
        record.put("vm_min", null);
        final String size = metadata.size();
        record.put("estimate_min", stage.equals("estimate") && size != null ? median(size) : null);
        record.put("result", options.value("--result"));
        record.put("wait_reason", reason);
        record.put("note", session == null ? "no-session" : null);
        StageTokens.empty(record);
        validate(record, false, workflow());
        final Map<String, @Nullable Object> saved = new LinkedHashMap<>();
        // Only the reference and session accounting stay private; the record lives in stats.
        saved.put("issue", issue);
        saved.put("stage", stage);
        saved.put("n", ordinal);
        saved.put("session", session);
        saved.put(
                "codex_begin",
                session == null ? null : StageTokens.baseline(root.resolve(session)));
        Files.writeString(
                state, Json.write(saved), StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        try {
            Files.createDirectories(output.getParent());
            Files.writeString(
                    output,
                    Json.write(record) + "\n",
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND);
        } catch (IOException ex) {
            Files.delete(state);
            throw ex;
        }
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
        require(
                issue.equals(saved.get("issue")) && stage.equals(saved.get("stage")),
                "end does not match the open stage");
        final Path output = stats(issue);
        final String contents = Files.readString(output);
        final List<Map<String, @Nullable Object>> earlier =
                records(contents, output.getFileName().toString(), false, workflow());
        int index = -1;
        for (int i = 0; i < earlier.size(); i++) {
            final Map<String, @Nullable Object> candidate = earlier.get(i);
            if (stage.equals(candidate.get("stage"))
                    && integer(saved.get("n"), "n") == integer(candidate.get("n"), "n")) {
                require(index == -1, "stage ordinal belongs to multiple records");
                index = i;
            }
        }
        require(index >= 0, "open stage is missing from stats");
        final Map<String, @Nullable Object> record = earlier.get(index);
        require(actor.equals(record.get("actor")), "end does not match the open actor");
        if (record.get("end") != null) {
            Files.delete(state);
            out.println("stage already closed");
            return;
        }
        validate(record, false, workflow());
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
            StageTokens.read(
                    root.resolve(path),
                    actor,
                    start,
                    end,
                    saved.get("codex_begin"),
                    Set.of("abandoned", "refused").contains(string(record, "result")),
                    record);
        }
        validate(record, true, workflow());
        record.put("n", integer(record.get("n"), "n"));
        int offset = 0;
        for (int i = 0; i < index; i++) {
            offset = contents.indexOf('\n', offset) + 1;
        }
        final int newline = contents.indexOf('\n', offset);
        replace(
                output,
                contents.substring(0, offset) + Json.write(record) + contents.substring(newline));
        Files.delete(state);
        out.println("stage closed");
    }

    private double median(String size) throws IOException {
        return statistic(size, false);
    }

    private double statistic(String size, boolean p80) throws IOException {
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
                boolean measured = false;
                boolean closed = true;
                for (final Map<String, @Nullable Object> record : records) {
                    if (record.get("end") == null) {
                        closed = false;
                        continue;
                    }
                    if ("agent".equals(record.get("actor"))
                            && MEASURED.contains(string(record, "stage"))
                            && size.equals(record.get("size"))) {
                        total += number(record.get("wall_min"), "wall_min");
                        measured = true;
                    }
                }
                require(Double.isFinite(total), "stage minutes overflow");
                if (closed
                        && measured
                        && workflow().closed(string(records.getLast(), "status_after"))
                        && !workflow().rejected(string(records.getLast(), "status_after"))) {
                    samples.add(total);
                }
            }
        }
        samples.sort(Double::compare);
        if (p80) {
            return samples.get((int) Math.ceil(samples.size() * 0.8) - 1);
        }
        final int middle = samples.size() / 2;
        return samples.size() % 2 == 1
                ? samples.get(middle)
                : samples.get(middle - 1) / 2 + samples.get(middle) / 2;
    }

    private record Issue(@Nullable String size, String status) {}

    private Issue issue(String issue) throws IOException {
        final Path directory = root.resolve("issues");
        safe(directory);
        final List<Path> matches;
        try (var files = Files.walk(directory)) {
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
        final String status = status(text, workflow());
        final String size =
                !workflow().measured(status)
                                && !workflow().closed(status)
                                && !Pattern.compile("(?m)^\\*\\*Size:").matcher(text).find()
                        ? null
                        : field(text, "Size", "XS|S|M|L|XL");
        return new Issue(size, status);
    }

    static String status(String text, Workflow workflow) {
        final String allowed =
                workflow.states().stream()
                        .sorted(java.util.Comparator.comparingInt(String::length).reversed())
                        .map(Pattern::quote)
                        .collect(java.util.stream.Collectors.joining("|"));
        return field(text, "Status", "(?:" + allowed + ")(?=\\s*(?:[.;](?:\\s|$)|$))");
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

    private List<Map<String, @Nullable Object>> records(Path file) throws IOException {
        safe(file);
        if (!Files.exists(file)) {
            return List.of();
        }
        return records(Files.readString(file), file.getFileName().toString(), false, workflow());
    }

    static List<Map<String, @Nullable Object>> records(
            String contents, String filename, boolean closed, Workflow workflow) {
        require(filename.matches("[A-Z]{1,3}\\d{2,3}\\.jsonl"), "invalid stats filename");
        require(!contents.isEmpty(), "empty stats file");
        require(contents.endsWith("\n"), "stats file has an incomplete final line");
        final List<Map<String, @Nullable Object>> records = new ArrayList<>();
        for (final String line : contents.substring(0, contents.length() - 1).split("\n", -1)) {
            final Map<String, @Nullable Object> record = object(Json.parse(line));
            require(
                    !closed || record.get("end") != null,
                    "stats file contains a stage without end");
            validate(record, record.get("end") != null, workflow);
            require(
                    filename.equals(string(record, "issue") + ".jsonl"),
                    "record issue disagrees with filename");
            records.add(record);
        }
        return records;
    }

    private void requireNoOpenOwnStage() throws IOException {
        final Path directory = root.resolve("stats");
        safe(directory);
        if (!Files.exists(directory)) {
            return;
        }
        try (var paths = Files.list(directory)) {
            for (final Path file :
                    paths.filter(p -> p.getFileName().toString().endsWith(".jsonl")).toList()) {
                for (final Map<String, @Nullable Object> record : records(file)) {
                    require(
                            !"agent".equals(record.get("actor")) || record.get("end") != null,
                            "an own stage is already open in stats");
                }
            }
        }
    }

    private static void replace(Path file, String contents) throws IOException {
        final Path temporary = Files.createTempFile(file.getParent(), "stage-", ".tmp");
        try {
            Files.writeString(temporary, contents);
            Files.move(
                    temporary,
                    file,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private Path gitDirectory() throws IOException {
        safe(root.resolve(".git"));
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

    private static void validate(
            Map<String, @Nullable Object> record, boolean closed, Workflow workflow) {
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
        final Object size = record.get("size");
        require(
                size == null && Set.of("analyze", "estimate").contains(stage)
                        || size instanceof String text && SIZES.contains(text),
                "invalid record size");
        require(
                workflow.states().contains(string(record, "status_before")),
                "invalid record status_before in issues/workflow.yml");
        require(integer(record.get("n"), "n") > 0, "invalid stage ordinal");
        for (final String key : List.of("model", "effort")) {
            final Object value = record.get(key);
            require(
                    value == null
                            || value instanceof String text
                                    && text.matches("[A-Za-z0-9._][A-Za-z0-9._-]*"),
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
                                && (Set.of("no-session", "no-usage").contains(text)
                                        || text.matches("[a-z]+")),
                "invalid record note");
        final Object result = record.get("result");
        require(
                result == null && !closed
                        || result instanceof String text && RESULTS.contains(text),
                "invalid record result");
        final Object estimate = record.get("estimate_min");
        require(
                stage.equals("estimate") && size != null ? estimate != null : estimate == null,
                "invalid record estimate_min");
        if (estimate != null) {
            number(estimate, "estimate_min");
        }
        final Instant start = Instant.parse(string(record, "start"));
        if (closed) {
            require(
                    workflow.states().contains(string(record, "status_after")),
                    "invalid record status_after in issues/workflow.yml");
            require(!Instant.parse(string(record, "end")).isBefore(start), "invalid record end");
            number(record.get("wall_min"), "wall_min");
            number(record.get("vm_min"), "vm_min");
        } else {
            require(
                    record.get("end") == null
                            && record.get("status_after") == null
                            && record.get("wall_min") == null
                            && record.get("vm_min") == null,
                    "open stage is already closed");
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

    private void safe(Path path) throws IOException {
        // A linked worktree legitimately names Git storage outside its own repository root.
        for (@Nullable Path part = path.toAbsolutePath().normalize();
                part != null && part.startsWith(root);
                part = part.getParent()) {
            if (Files.isSymbolicLink(part)) {
                throw new IOException("stage storage must not be a symbolic link");
            }
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
