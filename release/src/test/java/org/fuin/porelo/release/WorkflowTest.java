package org.fuin.porelo.release;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Guards issue-source validation against using a release's built-in state list. */
class WorkflowTest {
    @TempDir Path root;

    private IssueLinks index(String workflow, String state) throws Exception {
        final Path issues = Files.createDirectories(root.resolve("issues"));
        Files.writeString(issues.resolve("workflow.yml"), workflow);
        Files.writeString(
                issues.resolve("README.md"),
                "## "
                        + state
                        + "\n| # | Size | Blocked by | What it covers |\n| <a id=\"ex12\"></a>EX12 | S | - | Read the work |\n");
        final IssueLinks links =
                new IssueLinks(
                        (uri, headers) -> {
                            throw new AssertionError("unexpected network " + uri);
                        },
                        IssueLinks.Sources.LOCAL_FIRST);
        links.tree("porelo-example", root);
        return links;
    }

    private String workflow() throws Exception {
        return Files.readString(Path.of("../stories/src/test/resources/workflow.yml"));
    }

    @Test
    void addedStateGetsItsSection() throws Exception {
        final String workflow =
                workflow()
                        .replace("  - {name: Reworking, closed: false, measured: true}\n", "")
                        .replace(
                                "  - {name: Finished,",
                                "  - {name: Reworking, closed: false}\n  - {name: Finished,");
        final IssueLinks links = index(workflow, "Reworking");
        assertThat(links.issueIndex(root, "porelo-example").get("EX12").state())
                .isEqualTo("Reworking");
    }

    @Test
    void refusesAStateTheFileLacks() throws Exception {
        final IssueLinks links =
                index(
                        workflow()
                                .replace("name: Idea", "name: Proposed")
                                .replace("[Idea, Accepted]", "[Proposed, Accepted]"),
                        "Idea");
        assertThatThrownBy(() -> links.issueIndex(root, "porelo-example"))
                .isInstanceOf(Stop.class)
                .hasMessageContaining("workflow.yml")
                .hasMessageContaining("Idea");
    }

    @Test
    void missingFileIsRefused() throws Exception {
        final IssueLinks links = index(workflow(), "Idea");
        Files.delete(root.resolve("issues/workflow.yml"));
        assertThatThrownBy(() -> links.issueIndex(root, "porelo-example"))
                .hasMessageContaining(root.resolve("issues/workflow.yml").toString());
    }

    // Prevents the CLI's generated issue headings from disagreeing with the configured board order.
    @Test
    void generatedIssueHeadingsFollowTheSettledBoardOrder() throws Exception {
        final String yaml = workflow();
        final IssueLinks links = index(yaml, "Idea");
        final var workflow =
                org.fuin.porelo.stories.Workflow.read(root.resolve("issues/workflow.yml"));
        final StringBuilder page = new StringBuilder();
        int number = 10;
        for (String state : workflow.states().reversed()) {
            page.append("## ")
                    .append(state)
                    .append("\n| # | Size | Blocked by | What it covers | Title |\n")
                    .append("| EX")
                    .append(number++)
                    .append(" | S | - | Sample | Sample |\n");
        }
        Files.writeString(root.resolve("issues/README.md"), page.toString());
        final var before = links.issueIndex(root, "porelo-example");
        final String generated = links.storySource(root).index("porelo-example");
        assertThat(
                        generated
                                .lines()
                                .filter(line -> line.startsWith("## "))
                                .map(line -> line.substring(3))
                                .toList())
                .containsExactlyElementsOf(workflow.states());
        final var json = new com.fasterxml.jackson.databind.ObjectMapper();
        // Generated rows keep their metadata; their source line and first-cell link change.
        final com.fasterxml.jackson.databind.JsonNode after =
                json.valueToTree(
                        org.fuin.porelo.stories.IssueIndexes.read(
                                generated, "porelo-example", workflow));
        assertThat((Object) after).isEqualTo(json.valueToTree(before));
    }
}
