package org.fuin.porelo.stories;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Who works an issue, as its head says: read, written last, and absent when not said. */
class IssueHeadWorkerTest {

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

    // Prevents the planner's choice of who works an issue from being lost or moved on a rewrite.
    @Test
    void readsTheWorkerAndWritesItLast() throws IOException {
        final IssueHead head =
                IssueHead.read(
                        HEAD.formatted(",\n  \"kind\": \"Task\",\n  \"worker\": \"Manual\""),
                        workflow());

        assertThat(head).isNotNull();
        assertThat(head.worker()).isEqualTo("Manual");
        assertThat(head.json()).endsWith("  \"kind\": \"Task\",\n  \"worker\": \"Manual\"\n}");
    }

    // Prevents the library from putting a default the planner did not choose into a head.
    @Test
    void aHeadWithoutAWorkerHasNoneAndWritesNone() throws IOException {
        final IssueHead head = IssueHead.read(HEAD.formatted(""), workflow());

        assertThat(head).isNotNull();
        assertThat(head.worker()).isNull();
        assertThat(head.json()).doesNotContain("worker");
    }

    // Prevents a caller of the published library built against the old constructor from breaking.
    @Test
    void theConstructorWithoutAWorkerStays() {
        final IssueHead head =
                new IssueHead(
                        "EX12", "Twelve", "Idea", "-", "Medium", 1, List.of(), "Twelve.", null,
                        null, null, null, null);

        assertThat(head.worker()).isNull();
    }
}
