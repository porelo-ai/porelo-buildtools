package org.fuin.porelo.release;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.fuin.porelo.json.Json;
import org.jspecify.annotations.Nullable;

/** Complete public records, independent of the production validator's field list. */
final class StageFixtures {
    private StageFixtures() {}

    static Map<String, @Nullable Object> record(
            String issue,
            String stage,
            String actor,
            int minutes,
            String status,
            @Nullable String size) {
        final Map<String, @Nullable Object> record = new LinkedHashMap<>();
        record.put("issue", issue);
        record.put("stage", stage);
        record.put("n", 1);
        record.put("actor", actor);
        record.put("model", null);
        record.put("effort", null);
        record.put("size", size);
        record.put("status_before", "Implementing");
        record.put("status_after", status);
        record.put("start", "2026-01-01T10:00:00Z");
        record.put(
                "end", Instant.parse("2026-01-01T10:00:00Z").plusSeconds(minutes * 60L).toString());
        record.put("wall_min", minutes);
        record.put("vm_min", 0);
        record.put("estimate_min", "estimate".equals(stage) && size != null ? 53 : null);
        record.put("result", "done");
        record.put("wait_reason", "wait".equals(stage) ? "build" : null);
        record.put("note", "no-session");
        record.put("claude_input_tokens", null);
        record.put("claude_output_tokens", null);
        record.put("claude_cache_read_tokens", null);
        record.put("claude_cache_write_tokens", null);
        record.put("claude_messages", null);
        record.put("codex_input_tokens", null);
        record.put("codex_cached_tokens", null);
        record.put("codex_output_tokens", null);
        record.put("codex_reasoning_tokens", null);
        record.put("agy_input_tokens", null);
        record.put("agy_cache_read_tokens", null);
        record.put("agy_output_tokens", null);
        return record;
    }

    static String closed(String issue) {
        return Json.write(record(issue, "implement", "agent", 4, "Review", "M")) + "\n";
    }
}
