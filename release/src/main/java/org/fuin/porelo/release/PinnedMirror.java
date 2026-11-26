package org.fuin.porelo.release;

import java.io.IOException;
import java.io.PrintStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Fills the organization's Artifactory with every file this repository pins, so a pinned download
 * whose own source fails is fetched from there: {@code porelo-files/pinned/<sha256>/<file name>}.
 *
 * <p>A file already there is left alone. Any other is fetched from its pinned address, checked
 * against its digest and uploaded by {@code artifactory put}, which sends its SHA-1 and SHA-256 so
 * Artifactory refuses bytes that do not match them. One pin that fails does not keep the others
 * out; the command still ends with the worst of their answers.
 */
final class PinnedMirror {

    static final String USAGE = "pinned-mirror [REPOSITORY ROOT, default .]";

    /** The Artifactory repository the mirror is. */
    static final String REPOSITORY = "porelo-files";

    private static final Pattern SHA256 = Pattern.compile("[0-9a-f]{64}");

    /** A file name the mirror holds: what {@code pinned.sh} and porelo-machines ask it for. */
    private static final Pattern NAME = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._+-]*");

    /**
     * One pinned file.
     *
     * @param what what it is, for what is said
     * @param source the address it is pinned at
     * @param sha256 its pinned digest
     */
    record Pin(String what, URI source, String sha256) {

        /**
         * The file's name: the last segment of its pinned address.
         *
         * @return the name
         */
        String name() {
            final String path = String.valueOf(source.getPath());
            return path.substring(path.lastIndexOf('/') + 1);
        }

        /**
         * Where the mirror keeps it, the repository first.
         *
         * @return {@code porelo-files/pinned/<sha256>/<name>}
         */
        String target() {
            return REPOSITORY + "/" + folder() + "/" + name();
        }

        private String folder() {
            return "pinned/" + sha256;
        }
    }

    private final Artifactory artifactory;

    private final ArtifactoryCommand command;

    private final Web web;

    private final PrintStream out;

    private final PrintStream err;

    private PinnedMirror(
            Artifactory artifactory,
            Web web,
            PrintStream out,
            PrintStream err,
            ArtifactoryCommand.Pause pause) {
        this.artifactory = artifactory;
        this.command = new ArtifactoryCommand(artifactory, out, err, pause);
        this.web = web;
        this.out = out;
        this.err = err;
    }

    /**
     * The mirror over an Artifactory and a web.
     *
     * @param artifactory where the files go
     * @param web where they are fetched from, asked again by the pinned rule
     * @param out where what was done goes
     * @param err where what failed goes
     * @param pause how a wait between attempts at Artifactory is spent
     * @return the mirror
     */
    static PinnedMirror of(
            Artifactory artifactory,
            Web web,
            PrintStream out,
            PrintStream err,
            ArtifactoryCommand.Pause pause) {
        return new PinnedMirror(artifactory, web, out, err, pause);
    }

    /**
     * Every file a porelo-buildtools checkout fetches by the pinned rule: the GraalVM for Linux and
     * Windows from {@code machines/pom.xml}, shfmt from the {@code pinned-jdk} action, and the musl
     * toolchain and zlib from the musl installer.
     *
     * @param root the checkout
     * @return the pins, in that order
     * @throws Stop refused when a file cannot be read or does not name a pin as expected
     */
    static List<Pin> pins(Path root) throws Stop {
        final Pom machines = Pom.read(root.resolve("machines").resolve("pom.xml"));
        final List<Pin> pins = new ArrayList<>();
        pins.add(
                pin(
                        "GraalVM for Linux",
                        machines.required("machines.graalvm.url"),
                        machines.required("machines.graalvm.sha256"),
                        machines.file()));
        pins.add(
                pin(
                        "GraalVM for Windows",
                        machines.required("machines.graalvm.windows.url"),
                        machines.required("machines.graalvm.windows.sha256"),
                        machines.file()));
        final Path action = root.resolve(Path.of(".github", "actions", "pinned-jdk", "action.yml"));
        final String steps = read(action);
        final String shfmt =
                found(steps, "VERSION=([0-9][0-9A-Za-z.-]*)\\n", action, "shfmt's VERSION");
        final Matcher fetch =
                Pattern.compile(
                                "pinned_fetch \"(https://github\\.com/mvdan/sh/[^\"]+)\" \\\\\\n\\s*([0-9a-f]{64})")
                        .matcher(steps);
        if (!fetch.find()) {
            throw Stop.refused(action + " names no pinned_fetch of shfmt with its sha256");
        }
        pins.add(
                pin(
                        "shfmt",
                        fetch.group(1).replace("${VERSION}", shfmt).replace("$VERSION", shfmt),
                        fetch.group(2),
                        action));
        final Path installer =
                root.resolve(
                        Path.of(
                                "machines",
                                "src",
                                "main",
                                "resources",
                                "org",
                                "fuin",
                                "porelo",
                                "machines",
                                "install-musl.sh"));
        final String musl = read(installer);
        final String zlib =
                found(musl, "(?m)^ZLIB_VERSION=([0-9][0-9.]*)$", installer, "ZLIB_VERSION");
        pins.add(
                pin(
                        "the musl toolchain",
                        found(musl, "(?m)^MUSL_URL=(\\S+)$", installer, "MUSL_URL"),
                        found(musl, "(?m)^MUSL_SHA256=(\\S+)$", installer, "MUSL_SHA256"),
                        installer));
        pins.add(
                pin(
                        "zlib for musl",
                        found(musl, "(?m)^ZLIB_URL=(\\S+)$", installer, "ZLIB_URL")
                                .replace("$ZLIB_VERSION", zlib),
                        found(musl, "(?m)^ZLIB_SHA256=(\\S+)$", installer, "ZLIB_SHA256"),
                        installer));
        return List.copyOf(pins);
    }

    private static String read(Path file) throws Stop {
        try {
            return Files.readString(file);
        } catch (IOException ex) {
            throw Stop.refused("cannot read " + file + ": " + ex.getMessage(), ex);
        }
    }

    private static String found(String text, String regex, Path file, String what) throws Stop {
        final Matcher matcher = Pattern.compile(regex).matcher(text);
        if (!matcher.find()) {
            throw Stop.refused(file + " names no " + what + " as expected");
        }
        return matcher.group(1);
    }

    private static Pin pin(String what, String url, String sha256, Path file) throws Stop {
        final URI source;
        try {
            source = URI.create(url);
        } catch (IllegalArgumentException ex) {
            throw Stop.refused(file + " pins " + what + " at " + url + ", which is no address", ex);
        }
        final Pin pin = new Pin(what, source, sha256);
        if (!"https".equals(source.getScheme())
                || !SHA256.matcher(sha256).matches()
                || !NAME.matcher(pin.name()).matches()) {
            throw Stop.refused(
                    file
                            + " pins "
                            + what
                            + " at "
                            + url
                            + " with "
                            + sha256
                            + ": not an https address ending in a file name, with a sha256");
        }
        return pin;
    }

    /**
     * Puts each pinned file into the mirror that is not there yet.
     *
     * @param pins the files
     * @return 0 when every one is there now; {@link Stop#REFUSED} when one did not match its digest
     *     or was refused; {@link Stop#UNANSWERED} when a source or Artifactory did not answer
     */
    int run(List<Pin> pins) {
        boolean refused = false;
        boolean unanswered = false;
        for (final Pin pin : pins) {
            final int code = filled(pin);
            refused |= code == Stop.REFUSED;
            unanswered |= code != 0 && code != Stop.REFUSED;
        }
        if (refused) {
            return Stop.REFUSED;
        }
        return unanswered ? Stop.UNANSWERED : 0;
    }

    private int filled(Pin pin) {
        try {
            final Optional<List<Artifactory.Child>> held =
                    command.retried(
                            "GET " + REPOSITORY + "/" + pin.folder(),
                            () -> artifactory.children(REPOSITORY, pin.folder()));
            if (held.isPresent() && held.get().contains(new Artifactory.Child(pin.name(), false))) {
                out.println("  there " + pin.target());
                return 0;
            }
        } catch (IOException ex) {
            err.println("::error::" + pin.what() + ": " + Web.reason(ex));
            return Stop.UNANSWERED;
        }
        final Optional<byte[]> body;
        try {
            body = web.read(pin.source());
        } catch (IOException ex) {
            err.println("::error::" + pin.what() + ": " + Web.reason(ex));
            return Stop.UNANSWERED;
        }
        if (body.isEmpty()) {
            err.println("::error::" + pin.what() + ": " + pin.source() + " answered HTTP 404");
            return Stop.REFUSED;
        }
        final String got = sha256(body.get());
        if (!got.equals(pin.sha256())) {
            err.println(
                    "::error::"
                            + pin.what()
                            + ": "
                            + pin.source()
                            + " has the digest "
                            + got
                            + ", not the pinned "
                            + pin.sha256()
                            + "; nothing was uploaded");
            return Stop.REFUSED;
        }
        return uploaded(pin, body.get());
    }

    private int uploaded(Pin pin, byte[] body) {
        Path folder = null;
        try {
            folder = Files.createTempDirectory("pinned-mirror-");
            final Path file = folder.resolve(pin.name());
            Files.write(file, body);
            return command.run(List.of("put", file.toString(), pin.target()));
        } catch (IOException ex) {
            err.println(
                    "::error::"
                            + pin.what()
                            + ": cannot keep it for the upload: "
                            + Web.reason(ex));
            return Stop.REFUSED;
        } finally {
            if (folder != null) {
                try {
                    Files.deleteIfExists(folder.resolve(pin.name()));
                    Files.deleteIfExists(folder);
                } catch (IOException ex) {
                    err.println("::warning::" + folder + " is left behind: " + Web.reason(ex));
                }
            }
        }
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("every Java has SHA-256", ex);
        }
    }
}
