package org.fuin.porelo.release;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CheckWorkflowsTest {

    @TempDir Path directory;

    private final ByteArrayOutputStream out = new ByteArrayOutputStream();

    private final ByteArrayOutputStream err = new ByteArrayOutputStream();

    private int check(Path where) {
        return new CheckWorkflows(
                        new PrintStream(out, true, StandardCharsets.UTF_8),
                        new PrintStream(err, true, StandardCharsets.UTF_8))
                .check(where);
    }

    // Prevents a workflow with a trigger and a job from being refused, however 'on' is spelled.
    @Test
    void passesAWorkflowWithATriggerAndAJob() {
        assertThat(CheckWorkflows.fault("on: push\njobs:\n  a:\n    runs-on: x\n")).isNull();
        assertThat(CheckWorkflows.fault("\"on\":\n  push:\njobs:\n  a:\n    runs-on: x\n"))
                .isNull();
        assertThat(CheckWorkflows.fault("true: [push]\njobs:\n  a:\n    runs-on: x\n"))
                .as("the key YAML 1.1 reads 'on' as")
                .isNull();
    }

    // Prevents a file GitHub cannot read from passing.
    @Test
    void refusesAFileThatDoesNotLoad() {
        assertThat(CheckWorkflows.fault("on: [push\njobs: {}\n"))
                .startsWith("does not load as YAML");
    }

    // Prevents a broken or second document after a good one from passing unread.
    @Test
    void refusesAFileOfMoreThanOneDocument() {
        final String good = "on: push\njobs:\n  a:\n    runs-on: x\n";
        assertThat(CheckWorkflows.fault(good + "---\non: [broken\n"))
                .startsWith("does not load as YAML");
        assertThat(CheckWorkflows.fault(good + "---\n" + good))
                .isEqualTo("holds 2 documents, where GitHub reads one");
    }

    // Prevents a workflow that never starts from passing.
    @Test
    void refusesAWorkflowWithoutATrigger() {
        assertThat(CheckWorkflows.fault("jobs:\n  a:\n    runs-on: x\n"))
                .isEqualTo("names no trigger");
        assertThat(CheckWorkflows.fault("on:\njobs:\n  a:\n    runs-on: x\n"))
                .isEqualTo("names no trigger");
    }

    // Prevents a workflow that runs nothing from passing.
    @Test
    void refusesAWorkflowWithoutAJob() {
        assertThat(CheckWorkflows.fault("on: push\n")).isEqualTo("names no job");
        assertThat(CheckWorkflows.fault("on: push\njobs: {}\n")).isEqualTo("names no job");
    }

    // Prevents one bad file among good ones from passing, and names it.
    @Test
    void refusesADirectoryHoldingOneBadFile() throws Exception {
        Files.writeString(directory.resolve("good.yml"), "on: push\njobs:\n  a:\n    runs-on: x\n");
        Files.writeString(directory.resolve("bad.yaml"), "on: push\n");
        Files.writeString(directory.resolve("notes.txt"), "not a workflow");

        assertThat(check(directory)).isEqualTo(Stop.REFUSED);
        assertThat(err.toString(StandardCharsets.UTF_8))
                .contains(directory.resolve("bad.yaml") + ": names no job")
                .contains("1 workflow(s) GitHub would not run")
                .doesNotContain("good.yml");
    }

    // Prevents this repository's own workflows from drifting into one GitHub would not run.
    @Tag("documents")
    @Test
    void passesThisRepositorysWorkflows() {
        assertThat(check(Path.of("../.github/workflows")))
                .as(err.toString(StandardCharsets.UTF_8))
                .isZero();
        assertThat(out.toString(StandardCharsets.UTF_8)).startsWith("OK    ");
    }
}
