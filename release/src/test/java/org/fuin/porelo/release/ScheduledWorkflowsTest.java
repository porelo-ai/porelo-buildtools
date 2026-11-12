package org.fuin.porelo.release;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * A scheduled workflow runs on every forge that holds the repository, the host's among them; what
 * there speaks to GitHub alone fails there every day.
 */
@Tag("documents")
class ScheduledWorkflowsTest {

    private static final Path WORKFLOWS = Path.of("../.github/workflows");

    private static final ObjectMapper YAML = new ObjectMapper(new YAMLFactory());

    /** What speaks to GitHub alone: its command line, and the push check that reads its API. */
    private static final Pattern GITHUB_ONLY = Pattern.compile("(^|[\\s;(|&])gh\\s|check-pushes");

    /** The condition that holds a job or a step to GitHub. */
    private static final String ON_GITHUB = "github.server_url == 'https://github.com'";

    // Prevents a daily red run on the host's forge: machines.yml's pull request by gh and
    // shared-rules' read of the pushes from GitHub's API, each run there by its schedule.
    @Test
    void whatSpeaksToGitHubInAScheduledWorkflowRunsOnGitHubAlone() throws IOException {
        final List<String> loose = new ArrayList<>();
        try (Stream<Path> files = Files.list(WORKFLOWS)) {
            for (Path file : files.filter(f -> f.toString().endsWith(".yml")).sorted().toList()) {
                final JsonNode workflow = YAML.readTree(file.toFile());
                // YAML reads the bare key "on" as true.
                final JsonNode on = workflow.has("on") ? workflow.get("on") : workflow.get("true");
                if (on == null || !on.has("schedule")) {
                    continue;
                }
                for (Map.Entry<String, JsonNode> job : workflow.path("jobs").properties()) {
                    final boolean jobHeld = job.getValue().path("if").asText().contains(ON_GITHUB);
                    for (JsonNode step : job.getValue().path("steps")) {
                        final boolean held =
                                jobHeld || step.path("if").asText().contains(ON_GITHUB);
                        if (!held && GITHUB_ONLY.matcher(step.path("run").asText()).find()) {
                            loose.add(
                                    file.getFileName()
                                            + ": "
                                            + job.getKey()
                                            + ": "
                                            + step.path("name").asText("(unnamed)"));
                        }
                    }
                }
            }
        }
        assertThat(loose).as("steps of a scheduled workflow that speak to GitHub alone").isEmpty();
    }
}
