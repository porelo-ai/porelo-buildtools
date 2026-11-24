package org.fuin.porelo.release;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The {@code pinned-mirror} command over an Artifactory held in memory: every pinned file in the
 * mirror by its digest, and nothing in it that does not match.
 */
class PinnedMirrorTest {

    private static final String TOOL = "https://example.invalid/releases/tool_v1_linux_amd64";

    private static final String OTHER = "https://example.invalid/other-1.0.tar.gz";

    @TempDir Path dir;

    private final FakeArtifactory artifactory = new FakeArtifactory();

    private final FakeWeb web = new FakeWeb();

    private final ByteArrayOutputStream out = new ByteArrayOutputStream();

    private final ByteArrayOutputStream err = new ByteArrayOutputStream();

    /** Folders it holds, the uploads it was sent, and failures it answers. */
    private static final class FakeArtifactory implements Artifactory {

        final Map<String, List<Child>> folders = new HashMap<>();

        final List<String> uploaded = new ArrayList<>();

        final Map<String, String> bodies = new HashMap<>();

        @Override
        public Optional<List<Child>> children(String repository, String folder) {
            return Optional.ofNullable(folders.get(repository + "/" + folder));
        }

        @Override
        public void upload(String target, Path file, String properties) throws IOException {
            uploaded.add(target + " [" + properties + "]");
            bodies.put(target, Files.readString(file));
        }

        @Override
        public byte[] call(String method, String path) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Instant created(String repository, String path) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Map<String, List<String>> properties(String repository, String path) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void put(String repository, String path, byte[] body) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void delete(String repository, String path) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<String> debianPackages(String repository, String distribution) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<String> rpmPrimary(String repository, String folder) {
            throw new UnsupportedOperationException();
        }
    }

    private int run(Web reader, PinnedMirror.Pin... pins) {
        return PinnedMirror.of(
                        artifactory,
                        reader,
                        new PrintStream(out, true, StandardCharsets.UTF_8),
                        new PrintStream(err, true, StandardCharsets.UTF_8),
                        time -> {})
                .run(List.of(pins));
    }

    private static PinnedMirror.Pin pin(String url, String content) {
        return new PinnedMirror.Pin("the tool", URI.create(url), sha256(content));
    }

    // Prevents a mirror that lacks a pinned file, so that a download whose own source fails has
    // nowhere to go.
    @Test
    void uploadsAPinnedFileTheMirrorLacksUnderItsDigestAndName() {
        web.serve(TOOL, "pinned");

        final int code = run(web, pin(TOOL, "pinned"));

        final String target = "porelo-files/pinned/" + sha256("pinned") + "/tool_v1_linux_amd64";
        assertThat(code).as(err.toString(StandardCharsets.UTF_8)).isZero();
        assertThat(web.asked)
                .as("fetched from its pinned address")
                .containsExactly(URI.create(TOOL));
        assertThat(artifactory.uploaded)
                .as("one upload, no properties")
                .containsExactly(target + " []");
        assertThat(artifactory.bodies).as("the checked bytes").containsEntry(target, "pinned");
    }

    // Prevents a fetch and an upload on every Build for files the mirror already holds.
    @Test
    void leavesAFileTheMirrorHoldsAlone() {
        artifactory.folders.put(
                "porelo-files/pinned/" + sha256("pinned"),
                List.of(new Artifactory.Child("tool_v1_linux_amd64", false)));

        final int code = run(FakeWeb.untouchable(), pin(TOOL, "pinned"));

        assertThat(code).as(err.toString(StandardCharsets.UTF_8)).isZero();
        assertThat(artifactory.uploaded).as("nothing uploaded").isEmpty();
    }

    // Prevents bytes that are not the pinned file from reaching the mirror, from which every
    // Build would then be refused.
    @Test
    void uploadsNothingThatDoesNotMatchItsDigestAndGoesOnWithTheRest() {
        web.serve(TOOL, "planted");
        web.serve(OTHER, "other");

        final int code = run(web, pin(TOOL, "pinned"), pin(OTHER, "other"));

        assertThat(code).as("refused").isEqualTo(Stop.REFUSED);
        assertThat(err.toString(StandardCharsets.UTF_8))
                .as("says which")
                .contains(TOOL)
                .contains("not the pinned " + sha256("pinned"));
        assertThat(artifactory.uploaded)
                .as("only the one that matched")
                .containsExactly("porelo-files/pinned/" + sha256("other") + "/other-1.0.tar.gz []");
    }

    // Prevents a pinned address that is gone from reading as a mirror that is filled.
    @Test
    void refusesWhenThePinnedAddressHasNoFile() {
        final int code = run(web, pin(TOOL, "pinned"));

        assertThat(code).isEqualTo(Stop.REFUSED);
        assertThat(err.toString(StandardCharsets.UTF_8)).contains(TOOL).contains("404");
        assertThat(artifactory.uploaded).isEmpty();
    }

    // Prevents a source that did not answer from reading as a mirror that is filled.
    @Test
    void cannotAnswerWhenThePinnedAddressDoesNotAnswer() {
        web.fail(TOOL, "HTTP 503; given up after 8 of at most 8 attempts");

        final int code = run(web, pin(TOOL, "pinned"));

        assertThat(code).isEqualTo(Stop.UNANSWERED);
        assertThat(err.toString(StandardCharsets.UTF_8)).contains(TOOL).contains("HTTP 503");
        assertThat(artifactory.uploaded).isEmpty();
    }

    // Prevents a pin the mirror never gets because its file was not read.
    @Tag("documents")
    @Test
    void readsEveryPinThisRepositoryHolds() throws Stop, IOException {
        final List<PinnedMirror.Pin> pins = PinnedMirror.pins(Path.of(".."));

        final Pom machines = Pom.read(Path.of("..", "machines", "pom.xml"));
        assertThat(pins)
                .extracting(PinnedMirror.Pin::what)
                .as("each pin by name")
                .containsExactly("GraalVM for Linux", "GraalVM for Windows", "shfmt");
        assertThat(pins.get(0).source())
                .isEqualTo(URI.create(machines.required("machines.graalvm.url")));
        assertThat(pins.get(0).sha256()).isEqualTo(machines.required("machines.graalvm.sha256"));
        assertThat(pins.get(1).source())
                .isEqualTo(URI.create(machines.required("machines.graalvm.windows.url")));
        assertThat(pins.get(1).sha256())
                .isEqualTo(machines.required("machines.graalvm.windows.sha256"));
        final String action =
                Files.readString(Path.of("..", ".github", "actions", "pinned-jdk", "action.yml"));
        assertThat(action)
                .as("shfmt's digest as the action pins it")
                .contains(pins.get(2).sha256());
        assertThat(pins.get(2).source().toString())
                .as("the version filled in")
                .startsWith("https://github.com/mvdan/sh/releases/download/v")
                .doesNotContain("$");
    }

    // Prevents a pin left out because a file no longer names it the way it is read.
    @Test
    void refusesACheckoutThatDoesNotNameAPinAsExpected() throws IOException {
        Files.createDirectories(dir.resolve("machines"));
        Files.writeString(
                dir.resolve("machines/pom.xml"),
                "<project><artifactId>porelo-machines</artifactId><properties>"
                        + "<machines.graalvm.url>https://github.com/a.tar.gz</machines.graalvm.url>"
                        + "</properties></project>");

        assertThatThrownBy(() -> PinnedMirror.pins(dir))
                .isInstanceOf(Stop.class)
                .hasMessageContaining("machines.graalvm.sha256");
    }

    // Prevents the command from fetching anything when it could not upload it.
    @Tag("documents")
    @Test
    void needsArtifactorysAddressAndTokenBeforeFetchingAnything() {
        final int code =
                Main.run(
                        new String[] {"pinned-mirror", ".."},
                        new PrintStream(out, true, StandardCharsets.UTF_8),
                        new PrintStream(err, true, StandardCharsets.UTF_8),
                        FakeWeb.untouchable(),
                        Map.of(),
                        (tree, manifest, lockfile, work) -> lockfile);

        assertThat(code).isEqualTo(Stop.UNANSWERED);
        assertThat(err.toString(StandardCharsets.UTF_8)).contains("JF_URL");
    }

    private static String sha256(String text) {
        try {
            return HexFormat.of()
                    .formatHex(
                            MessageDigest.getInstance("SHA-256")
                                    .digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
