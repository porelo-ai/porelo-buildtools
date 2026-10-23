package org.fuin.porelo.release;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/** Dates a page from source commits, never from the render clock or file modification times. */
final class PlanStand {
    private PlanStand() {}

    static String read(Path plan, Collection<URI> sources, Web web) throws IOException {
        final var inputs = new LinkedHashSet<URI>();
        for (final URI source : sources) {
            inputs.add(source.getScheme().equals("file") ? source : issuesDirectory(source));
        }
        inputs.add(plan.toAbsolutePath().toUri());
        final List<String> unavailable = new ArrayList<>();
        Optional<Instant> latest = Optional.empty();
        for (final URI input : inputs.stream().sorted().toList()) {
            final Optional<Instant> time =
                    input.getScheme().equals("file") ? local(Path.of(input)) : forge(input, web);
            if (time.isEmpty()) {
                unavailable.add(
                        input.getScheme().equals("file")
                                ? Path.of(input).getFileName().toString()
                                : input.getPath());
            } else if (latest.isEmpty() || time.get().isAfter(latest.get())) {
                latest = time;
            }
        }
        final String stamp = latest.map(Instant::toString).orElse("commit time unavailable");
        return unavailable.isEmpty()
                ? stamp
                : stamp
                        + "; no commit time for "
                        + String.join(", ", unavailable)
                        + " (excluded; no file-time fallback)";
    }

    static Optional<Instant> local(Path file) throws IOException {
        file = file.toRealPath();
        final Path output = Files.createTempFile("plan-commit-", ".out");
        final Path errors = Files.createTempFile("plan-commit-", ".err");
        try {
            final ProcessBuilder command =
                    new ProcessBuilder(
                            "git",
                            "--no-optional-locks",
                            "--literal-pathspecs",
                            "-C",
                            java.util.Objects.requireNonNull(file.toAbsolutePath().getParent())
                                    .toString(),
                            "log",
                            "-1",
                            "--format=%cI",
                            "--",
                            file.getFileName().toString());
            command.environment().put("GIT_CONFIG_GLOBAL", "/dev/null");
            command.environment().put("LC_ALL", "C");
            command.environment().put("GIT_CONFIG_NOSYSTEM", "1");
            final Process process =
                    command.redirectOutput(output.toFile()).redirectError(errors.toFile()).start();
            try {
                if (!process.waitFor(10, TimeUnit.SECONDS)) {
                    process.destroyForcibly();
                    throw new IOException("git log timed out for " + file);
                }
            } catch (InterruptedException ex) {
                process.destroyForcibly();
                Thread.currentThread().interrupt();
                throw new IOException("git log interrupted for " + file, ex);
            }
            if (process.exitValue() != 0) {
                // Exported trees and untracked files have no commit to date the page with.
                if (process.exitValue() == 128
                        && Files.readString(errors).contains("not a git repository")) {
                    return Optional.empty();
                }
                throw new IOException(
                        "git log for " + file + ": " + Files.readString(errors).strip());
            }
            final String text = Files.readString(output).strip();
            return text.isEmpty() ? Optional.empty() : Optional.of(parse(text, file.toString()));
        } finally {
            Files.deleteIfExists(output);
            Files.deleteIfExists(errors);
        }
    }

    private static URI issuesDirectory(URI source) throws IOException {
        if (!"https".equals(source.getScheme())
                || !"raw.githubusercontent.com".equals(source.getHost())) {
            throw new IOException("Unsupported commit source: " + source);
        }
        final String[] path = source.getPath().split("/", 5);
        if (path.length != 5
                || !path[1].equals("porelo-ai")
                || !path[3].equals("main")
                || !(path[4].equals("issues") || path[4].startsWith("issues/"))) {
            throw new IOException("Expected a main source: " + source);
        }
        return URI.create(
                "https://raw.githubusercontent.com/porelo-ai/" + path[2] + "/main/issues");
    }

    private static Optional<Instant> forge(URI source, Web web) throws IOException {
        final String repository = source.getPath().split("/")[2];
        final URI commits =
                URI.create(
                        "https://api.github.com/repos/porelo-ai/"
                                + repository
                                + "/commits?sha=main&path=issues"
                                + "&per_page=1");
        final var body = web.text(commits);
        if (body.isEmpty()) return Optional.empty();
        final var data =
                new ObjectMapper()
                        .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                        .readTree(body.get());
        if (data == null || !data.isArray())
            throw new IOException(commits + ": expected commits array");
        if (data.isEmpty()) return Optional.empty();
        final var date = data.path(0).path("commit").path("committer").path("date");
        if (!date.isTextual()) throw new IOException(commits + ": missing commit.committer.date");
        return Optional.of(parse(date.textValue(), commits.toString()));
    }

    private static Instant parse(String text, String source) throws IOException {
        try {
            return Instant.parse(text);
        } catch (DateTimeParseException ex) {
            throw new IOException(source + ": invalid commit time: " + text, ex);
        }
    }
}
