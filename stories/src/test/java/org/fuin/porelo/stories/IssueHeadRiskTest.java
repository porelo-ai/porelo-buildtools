package org.fuin.porelo.stories;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

/** How much an issue risks, as its head says: read, written last, and absent when not said. */
class IssueHeadRiskTest {

    private static final String HEAD =
            """
            # EX12 — Twelve

            ```json
            {
              "$schema": "issue-1.json",
              "id": "EX12",
              "title": "Twelve",
              "state": "Idea",
              "size": "-",
              "priority": "Medium",
              "rank": 1,
              "blockedBy": [],
              "covers": "Twelve."%s
            }
            ```
            """;

    private static Workflow workflow() throws IOException {
        return Workflow.read(Path.of("../issues/workflow.yml"));
    }

    // Prevents the planner's risk of an issue from being lost or moved on a rewrite.
    @Test
    void readsTheRiskAndWritesItLastAfterTheWorker() throws IOException {
        final IssueHead head =
                IssueHead.read(
                        HEAD.formatted(",\n  \"risk\": \"harm\",\n  \"worker\": \"Manual\""),
                        workflow());

        assertThat(head).isNotNull();
        assertThat(head.risk()).isEqualTo("harm");
        assertThat(head.json()).endsWith("  \"worker\": \"Manual\",\n  \"risk\": \"harm\"\n}");
    }

    // Prevents the library from putting a default the planner did not choose into a head.
    @Test
    void aHeadWithoutARiskHasNoneAndWritesNone() throws IOException {
        final IssueHead head = IssueHead.read(HEAD.formatted(""), workflow());

        assertThat(head).isNotNull();
        assertThat(head.risk()).isNull();
        assertThat(head.json()).doesNotContain("risk");
    }

    // Prevents a caller of the published library built against the old constructor from breaking.
    @Test
    void theConstructorsWithoutARiskStay() {
        final IssueHead before =
                new IssueHead(
                        "EX12", "Twelve", "Idea", "-", "Medium", 1, List.of(), "Twelve.", null,
                        null, null, null, null);
        final IssueHead withWorker =
                new IssueHead(
                        "EX12", "Twelve", "Idea", "-", "Medium", 1, List.of(), "Twelve.", null,
                        null, null, null, null, "Agent");

        assertThat(before.risk()).isNull();
        assertThat(withWorker.risk()).isNull();
        assertThat(withWorker.worker()).isEqualTo("Agent");
    }
}
