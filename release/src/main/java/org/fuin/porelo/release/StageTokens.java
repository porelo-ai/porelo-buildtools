package org.fuin.porelo.release;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.fuin.porelo.json.Json;
import org.fuin.porelo.json.JsonException;
import org.jspecify.annotations.Nullable;

/** Reads measured usage without copying session content or paths into stage records. */
final class StageTokens {
    static final List<String> FIELDS =
            List.of(
                    "claude_input_tokens",
                    "claude_output_tokens",
                    "claude_cache_read_tokens",
                    "claude_cache_write_tokens",
                    "claude_messages",
                    "codex_input_tokens",
                    "codex_cached_tokens",
                    "codex_output_tokens",
                    "codex_reasoning_tokens",
                    "agy_input_tokens",
                    "agy_cache_read_tokens",
                    "agy_output_tokens");
    private static final List<String> CLAUDE =
            List.of(
                    "input_tokens",
                    "output_tokens",
                    "cache_read_input_tokens",
                    "cache_creation_input_tokens");
    private static final List<String> CODEX =
            List.of(
                    "input_tokens",
                    "cached_input_tokens",
                    "output_tokens",
                    "reasoning_output_tokens");
    private static final List<String> AGY = List.of("input", "cacheRead", "output");
    private static final List<String> AGY_TOKEN_FIELDS =
            List.of("input_tokens", "cache_read_input_tokens", "output_tokens");

    private StageTokens() {}

    static void empty(Map<String, @Nullable Object> record) {
        FIELDS.forEach(field -> record.put(field, null));
    }

    static void read(
            Path file,
            String actor,
            Instant start,
            Instant end,
            Map<String, @Nullable Object> record)
            throws IOException {
        final String content;
        try {
            content = Files.readString(file);
        } catch (IOException ex) {
            // Filesystem exceptions contain the private source path and must not escape into logs.
            throw new IOException("could not read the session file");
        }
        try {
            final List<Map<String, @Nullable Object>> entries = entries(content);
            final boolean claude =
                    entries.stream().anyMatch(entry -> "assistant".equals(entry.get("type")));
            final boolean codex = entries.stream().anyMatch(StageTokens::codex);
            final boolean agy = entries.size() == 1 && entries.getFirst().containsKey("usage");
            Stage.require(
                    (claude ? 1 : 0) + (codex ? 1 : 0) + (agy ? 1 : 0) == 1,
                    "unknown or mixed session format");
            Stage.require(!actor.equals("agent") || claude, "own stages need a Claude session log");
            if (claude) {
                Stage.require(actor.equals("agent"), "subagent session must be Codex or agy");
                claude(entries, start, end, record);
            } else if (codex) {
                codex(entries, record);
            } else {
                final Map<String, @Nullable Object> usage =
                        Stage.object(entries.getFirst().get("usage"));
                final boolean plain = AGY.stream().anyMatch(usage::containsKey);
                final boolean tokens = AGY_TOKEN_FIELDS.stream().anyMatch(usage::containsKey);
                Stage.require(plain != tokens, "agy usage fields are missing or mixed");
                copy(usage, plain ? AGY : AGY_TOKEN_FIELDS, FIELDS.subList(9, 12), record);
            }
        } catch (JsonException ex) {
            throw new IllegalArgumentException("session contains invalid JSON");
        }
    }

    private static List<Map<String, @Nullable Object>> entries(String content) {
        Stage.require(!content.isBlank(), "session file is empty");
        // agy's result may be pretty-printed; rollout and Claude logs have one object per line.
        try {
            return List.of(Stage.object(Json.parse(content)));
        } catch (JsonException ex) {
            final List<Map<String, @Nullable Object>> result = new ArrayList<>();
            for (final String line : content.lines().toList()) {
                Stage.require(!line.isBlank(), "session contains an empty JSON line");
                result.add(Stage.object(Json.parse(line)));
            }
            return result;
        }
    }

    private static void claude(
            List<Map<String, @Nullable Object>> entries,
            Instant start,
            Instant end,
            Map<String, @Nullable Object> record) {
        final long[] totals = new long[5];
        for (final Map<String, @Nullable Object> entry : entries) {
            if (!"assistant".equals(entry.get("type"))) {
                continue;
            }
            final Instant timestamp = Instant.parse(Stage.string(entry, "timestamp"));
            if (timestamp.isBefore(start) || !timestamp.isBefore(end)) {
                continue;
            }
            final Map<String, @Nullable Object> usage =
                    Stage.object(Stage.object(entry.get("message")).get("usage"));
            for (int i = 0; i < CLAUDE.size(); i++) {
                totals[i] = add(totals[i], Stage.integer(usage.get(CLAUDE.get(i)), CLAUDE.get(i)));
            }
            totals[4] = add(totals[4], 1);
        }
        for (int i = 0; i < totals.length; i++) {
            record.put(FIELDS.get(i), totals[i]);
        }
    }

    private static boolean codex(Map<String, @Nullable Object> entry) {
        return "event_msg".equals(entry.get("type"))
                && entry.get("payload") instanceof Map<?, ?> payload
                && "token_count".equals(payload.get("type"));
    }

    private static void codex(
            List<Map<String, @Nullable Object>> entries, Map<String, @Nullable Object> record) {
        Map<String, @Nullable Object> last = null;
        for (final Map<String, @Nullable Object> entry : entries) {
            if (!codex(entry)) {
                continue;
            }
            final Map<String, @Nullable Object> payload = Stage.object(entry.get("payload"));
            // Rate-limit-only events carry no usage; they do not replace the last measured total.
            if (payload.get("info") == null) {
                continue;
            }
            final Map<String, @Nullable Object> usage =
                    Stage.object(Stage.object(payload.get("info")).get("total_token_usage"));
            for (final String key : CODEX) {
                Stage.integer(usage.get(key), key);
            }
            if (last != null) {
                for (final String key : CODEX) {
                    Stage.require(
                            Stage.integer(usage.get(key), key) >= Stage.integer(last.get(key), key),
                            "Codex cumulative usage decreased");
                }
            }
            last = usage;
        }
        if (last == null) {
            throw new IllegalArgumentException("Codex session contains no measured token usage");
        }
        copy(last, CODEX, FIELDS.subList(5, 9), record);
    }

    private static void copy(
            Map<String, @Nullable Object> usage,
            List<String> source,
            List<String> target,
            Map<String, @Nullable Object> record) {
        for (int i = 0; i < source.size(); i++) {
            record.put(target.get(i), Stage.integer(usage.get(source.get(i)), source.get(i)));
        }
    }

    private static long add(long total, long value) {
        Stage.require(total <= 9007199254740991L - value, "session token total overflow");
        return total + value;
    }
}
