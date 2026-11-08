package org.fuin.porelo.machines;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import org.fuin.porelo.pinned.PinnedRetry;
import org.jspecify.annotations.Nullable;

/**
 * Installs the GraalVM the CI snapshots pin, on a machine that is not one of them - a GitHub
 * runner.
 *
 * <p><strong>Why a build job needs it.</strong> A job that compiles what is published used to
 * install its JDK by a moving name ({@code '25'}), the newest release of that line, and nothing
 * checked what it downloaded. The snapshots a leg boots from already pin one GraalVM by address and
 * digest, moved under the release tooling's age rule. Installing that same one here makes the
 * build's JDK and the leg's the same by construction: one pin, one mover, one digest, for every
 * repository.
 *
 * <p><strong>Checked before anything is unpacked.</strong> The archive is hashed as it is written
 * and compared with the pinned digest; a mismatch leaves nothing installed. Nothing is installed
 * over an existing directory either, so a second run cannot mix two JDKs.
 *
 * <p><strong>Tried again when the server could not answer.</strong> A release download answers a
 * 504 now and then, and one such answer must not turn a whole Build red. Only that is tried again,
 * a 5xx or a connection that failed or broke off, by {@link PinnedRetry}'s pauses: each attempt
 * starts over with an empty file and a new digest. A 4xx and a wrong digest will not get better by
 * waiting and fail at once.
 *
 * <p><strong>Kept in a cache by its digest.</strong> Given a cache directory, the archive that
 * matched is kept there as {@code <sha256>.tar.gz}, where a CI cache keyed by the same digest
 * brings it back. A cached archive is hashed again before it is used; one that does not match is
 * deleted and fetched anew, never unpacked.
 *
 * <p><strong>With the job's token, to github.com alone.</strong> On GitHub, a download from {@code
 * https://github.com} goes with the job's token, so GitHub counts it against the job's own limit
 * rather than the shared runner's address. The token goes to the address asked for, and only when
 * that is on {@code https://github.com}. Redirects are followed here, not by the client, so it
 * never goes along to the signed address a release download is sent on to, and never anywhere on a
 * host's forge, whose token it would be. A 401 or 403 fails at once.
 */
final class PinnedJdk {

    /** How long the download may take; the archive is a few hundred megabytes. */
    private static final Duration DOWNLOAD = Duration.ofMinutes(10);

    private static final int BUFFER = 64 * 1024;

    /** The one origin the job's token is sent to. */
    static final URI GITHUB = URI.create("https://github.com");

    /** Where the token is named, set by the action only on github.com. */
    static final String TOKEN_VARIABLE = "PORELO_PINNED_GITHUB_TOKEN";

    /** How many redirects a download follows. */
    private static final int REDIRECTS = 10;

    /**
     * A token and the one origin it is sent to.
     *
     * @param origin scheme, host and port the token goes to, and nothing else
     * @param token the token, never told
     */
    record Credential(URI origin, String token) {

        @Override
        public String toString() {
            return "Credential[origin=" + origin + ", token=<hidden>]";
        }

        boolean isFor(URI address) {
            return origin.getScheme().equals(address.getScheme())
                    && origin.getHost().equalsIgnoreCase(String.valueOf(address.getHost()))
                    && port(origin) == port(address);
        }

        private static int port(URI address) {
            if (address.getPort() >= 0) {
                return address.getPort();
            }
            return "https".equals(address.getScheme()) ? 443 : 80;
        }
    }

    /**
     * The job's token for github.com, when the job runs on github.com and was given one.
     *
     * @param env the job's environment
     * @return the credential, or {@code null} on a host's forge, without a token, or with one not
     *     of a token's form
     */
    static @Nullable Credential githubToken(Map<String, String> env) {
        final String token = env.get(TOKEN_VARIABLE);
        if (!GITHUB.toString().equals(env.get("GITHUB_SERVER_URL"))
                || token == null
                || !token.matches("[A-Za-z0-9_]{20,255}")) {
            return null;
        }
        return new Credential(GITHUB, token);
    }

    /** Where an archive is fetched from. */
    interface Download {

        /**
         * Writes what an address serves into a stream.
         *
         * @param address where the archive is
         * @param into where its bytes go
         * @throws Unavailable if the server could not answer, and asking again may help
         * @throws IOException if it cannot be fetched completely, and asking again will not help
         */
        void to(URI address, OutputStream into) throws IOException;
    }

    /** A download that may answer next time: a 5xx, or a connection that failed or broke off. */
    static final class Unavailable extends IOException {

        private static final long serialVersionUID = 1L;

        Unavailable(String message) {
            super(message);
        }

        Unavailable(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /** How a wait between attempts is spent. */
    interface Pause {

        /**
         * Waits.
         *
         * @param time how long
         * @throws InterruptedException if interrupted
         */
        void of(Duration time) throws InterruptedException;

        /**
         * The time now, by which the rule's deadline is kept: a clock that only counts up, which no
         * step of the system's clock moves, unless a test keeps its own.
         *
         * @return a point on that clock; only the time between two of them counts
         */
        default Instant now() {
            return Instant.EPOCH.plusNanos(System.nanoTime());
        }
    }

    /**
     * What was installed.
     *
     * @param home the JDK's home directory
     * @param version the pinned version
     */
    record Installed(Path home, String version) {}

    private PinnedJdk() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Downloads, checks and unpacks the pinned GraalVM.
     *
     * @param contents what the pom pins
     * @param home where the JDK goes; it must not exist yet, or be empty
     * @param download where the archive is fetched from
     * @return what was installed
     * @throws IOException if it cannot be fetched, does not match its digest, or does not unpack to
     *     a JDK
     */
    static Installed install(Snapshots.Contents contents, Path home, Download download)
            throws IOException {
        return install(contents, home, download, null, Thread::sleep, System.err::println);
    }

    /**
     * Downloads, checks and unpacks the pinned GraalVM, waiting as told between attempts.
     *
     * @param contents what the pom pins
     * @param home where the JDK goes; it must not exist yet, or be empty
     * @param download where the archive is fetched from
     * @param cache where the checked archive is kept by its digest, or null to keep none
     * @param pause how a wait is spent
     * @param say where an attempt that failed is told
     * @return what was installed
     * @throws IOException if it cannot be fetched, does not match its digest, or does not unpack to
     *     a JDK
     */
    static Installed install(
            Snapshots.Contents contents,
            Path home,
            Download download,
            @Nullable Path cache,
            Pause pause,
            Consumer<String> say)
            throws IOException {
        if (Files.isDirectory(home)) {
            try (var entries = Files.list(home)) {
                if (entries.findAny().isPresent()) {
                    throw new IOException(home + " is not empty; nothing was installed over it");
                }
            }
        }
        Files.createDirectories(home);
        final String pinned = contents.graalvmSha256();
        final Path cached = cache == null ? null : cache.resolve(pinned + ".tar.gz");
        if (cached != null && Files.isRegularFile(cached, LinkOption.NOFOLLOW_LINKS)) {
            if (digestOf(cached).equals(pinned)) {
                say.accept(
                        contents.graalvmUrl() + " taken from the cache, checked against " + pinned);
                unpack(cached, home);
                return checked(contents, home);
            }
            say.accept(cached + " did not match the pinned " + pinned + "; fetching it again");
            Files.delete(cached);
        }
        // Beside the cached file, so that a checked archive moves into it whole.
        final Path folder =
                cache != null ? Files.createDirectories(cache) : home.toAbsolutePath().getParent();
        final Path archive = Files.createTempFile(folder, "graalvm-", ".tar.gz");
        try {
            final String got =
                    fetchedDigest(URI.create(contents.graalvmUrl()), archive, download, pause, say);
            if (!got.equals(pinned)) {
                throw new IOException(
                        contents.graalvmUrl()
                                + " has the digest "
                                + got
                                + ", not the pinned "
                                + pinned
                                + "; nothing was installed");
            }
            if (cached != null) {
                Files.move(archive, cached, StandardCopyOption.ATOMIC_MOVE);
                unpack(cached, home);
            } else {
                unpack(archive, home);
            }
        } finally {
            Files.deleteIfExists(archive);
        }
        return checked(contents, home);
    }

    private static Installed checked(Snapshots.Contents contents, Path home) throws IOException {
        for (final String tool : List.of("java", "native-image")) {
            if (!Files.isExecutable(home.resolve("bin").resolve(tool))) {
                throw new IOException(
                        contents.graalvmUrl() + " unpacked to no bin/" + tool + " in " + home);
            }
        }
        return new Installed(home, contents.graalvmVersion());
    }

    private static String fetchedDigest(
            URI address, Path archive, Download download, Pause pause, Consumer<String> say)
            throws IOException {
        final int attempts = PinnedRetry.attempts();
        final Instant start = pause.now();
        for (int attempt = 1; ; attempt++) {
            final MessageDigest sha256 = sha256();
            try (OutputStream out =
                            Files.newOutputStream(archive, StandardOpenOption.TRUNCATE_EXISTING);
                    OutputStream hashed = new java.security.DigestOutputStream(out, sha256)) {
                download.to(address, hashed);
                return HexFormat.of().formatHex(sha256.digest());
            } catch (Unavailable ex) {
                final Duration elapsed = Duration.between(start, pause.now());
                final Optional<Duration> after = PinnedRetry.pauseAfter(attempt, elapsed);
                if (after.isEmpty()) {
                    throw new Unavailable(
                            ex.getMessage()
                                    + "; given up after "
                                    + attempt
                                    + " of at most "
                                    + attempts
                                    + " attempts in "
                                    + Math.max(0, elapsed.toSeconds())
                                    + " s",
                            ex);
                }
                final Duration next = after.get();
                say.accept(
                        ex.getMessage()
                                + " on attempt "
                                + attempt
                                + " of "
                                + attempts
                                + "; trying again in "
                                + next.toSeconds()
                                + " s");
                try {
                    pause.of(next);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new IOException("interrupted while waiting for " + address, interrupted);
                }
            }
        }
    }

    /**
     * Makes the installed JDK the one every later step of a GitHub job uses.
     *
     * <p>Through the files GitHub reads between steps, never by printing a command a log would
     * carry.
     *
     * @param installed what was installed
     * @param env the file {@code GITHUB_ENV} names
     * @param path the file {@code GITHUB_PATH} names
     * @throws IOException if either cannot be written
     */
    static void exportTo(Installed installed, Path env, Path path) throws IOException {
        final String home = installed.home().toAbsolutePath().toString();
        Files.writeString(
                env,
                "JAVA_HOME=" + home + "\nGRAALVM_HOME=" + home + "\n",
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.APPEND);
        Files.writeString(
                path,
                installed.home().toAbsolutePath().resolve("bin") + "\n",
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.APPEND);
    }

    /**
     * Fetches over HTTPS, following the redirect GitHub answers a release download with.
     *
     * @param credential the token for its one origin, or {@code null}
     * @return the download
     */
    static Download overHttps(@Nullable Credential credential) {
        final HttpClient http = client();
        return (address, into) -> {
            if (!"https".equals(address.getScheme())) {
                throw new IOException(
                        "refusing to fetch a JDK over " + address.getScheme() + ": " + address);
            }
            fetch(http, address, credential, into);
        };
    }

    /**
     * The client a download goes through: it follows no redirect itself, so {@link #fetch} decides
     * where the token goes.
     *
     * @return the client
     */
    static HttpClient client() {
        return HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NEVER)
                .connectTimeout(Duration.ofSeconds(30))
                .build();
    }

    /**
     * Where a redirect leads, refused when it is no address, not http(s), or leaves https.
     *
     * @param at the address that answered the redirect
     * @param location its {@code Location}
     * @param name how the download is named in a failure
     * @return the next address
     * @throws IOException when it is refused
     */
    static URI redirected(URI at, String location, String name) throws IOException {
        final URI next;
        try {
            next = at.resolve(location.strip());
        } catch (IllegalArgumentException ex) {
            throw new IOException(name + " redirected to an address that is none", ex);
        }
        if (next.getHost() == null
                || !("https".equalsIgnoreCase(next.getScheme())
                        || "http".equalsIgnoreCase(next.getScheme()))
                || ("https".equalsIgnoreCase(at.getScheme())
                        && !"https".equalsIgnoreCase(next.getScheme()))) {
            throw new IOException(name + " redirected to " + next.getScheme() + ", not https");
        }
        return next;
    }

    /**
     * Fetches an address once and tells what asking again may help with from what it will not.
     *
     * @param http the client
     * @param address where the archive is
     * @param into where its bytes go
     * @throws Unavailable if the server answered a 5xx, or the connection failed or broke off
     * @throws IOException if the server answered anything else but 200, or writing failed
     */
    static void fetch(HttpClient http, URI address, OutputStream into) throws IOException {
        fetch(http, address, null, into);
    }

    /**
     * Fetches an address once, following its redirects, with a token for its one origin.
     *
     * @param http the client, which follows no redirect itself
     * @param address where the archive is
     * @param credential the token and the one origin it goes to, or {@code null}
     * @param into where its bytes go
     * @throws Unavailable if the server answered a 5xx, or the connection failed or broke off
     * @throws IOException if the server answered anything else but 200, or writing failed
     */
    static void fetch(
            HttpClient http, URI address, @Nullable Credential credential, OutputStream into)
            throws IOException {
        URI at = address;
        String name = address.toString();
        for (int hop = 0; ; hop++) {
            // The token goes with the address asked for, and never along a redirect, wherever it
            // leads.
            final HttpResponse<InputStream> response =
                    send(http, at, hop == 0 ? credential : null, name);
            final int status = response.statusCode();
            final Optional<String> location = response.headers().firstValue("Location");
            if (isRedirect(status) && location.isPresent()) {
                response.body().close();
                if (hop >= REDIRECTS) {
                    throw new IOException(
                            address + " redirected more than " + REDIRECTS + " times");
                }
                final URI next = redirected(at, location.get(), name);
                at = next;
                // Where a release download is sent on carries its signature in the query: named by
                // its host alone.
                name =
                        address
                                + " (redirected to "
                                + next.getScheme()
                                + "://"
                                + next.getRawAuthority()
                                + ")";
                continue;
            }
            answered(name, response, into);
            return;
        }
    }

    private static boolean isRedirect(int status) {
        return status == 301 || status == 302 || status == 303 || status == 307 || status == 308;
    }

    private static HttpResponse<InputStream> send(
            HttpClient http, URI address, @Nullable Credential credential, String name)
            throws IOException {
        final HttpRequest.Builder request;
        try {
            request = HttpRequest.newBuilder(address).timeout(DOWNLOAD).GET();
        } catch (IllegalArgumentException ex) {
            throw new IOException(name + " is no address to fetch", ex);
        }
        if (credential != null && credential.isFor(address)) {
            request.header("Authorization", "Bearer " + credential.token());
        }
        try {
            return http.send(request.build(), HttpResponse.BodyHandlers.ofInputStream());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IOException("interrupted while fetching " + name, ex);
        } catch (IOException ex) {
            throw new Unavailable(
                    name + " could not be reached: " + ex.getClass().getSimpleName(), ex);
        }
    }

    private static void answered(
            String address, HttpResponse<InputStream> response, OutputStream into)
            throws IOException {
        try (InputStream body = response.body()) {
            final int status = response.statusCode();
            if (status >= 500 && status < 600) {
                throw new Unavailable(address + " answered HTTP " + status);
            }
            if (status != 200) {
                throw new IOException(address + " answered HTTP " + status);
            }
            copy(address, body, into);
        }
    }

    // Not transferTo: a body that breaks off is worth another attempt, a disk that is full is not.
    private static void copy(String address, InputStream body, OutputStream into)
            throws IOException {
        final byte[] buffer = new byte[BUFFER];
        while (true) {
            final int read;
            try {
                read = body.read(buffer);
            } catch (IOException ex) {
                throw new Unavailable(address + " broke off: " + ex.getClass().getSimpleName(), ex);
            }
            if (read < 0) {
                return;
            }
            into.write(buffer, 0, read);
        }
    }

    // tar rather than a reader of our own: it is on every runner and every snapshot, and the recipe
    // the snapshots run unpacks the same archive with the same command.
    private static void unpack(Path archive, Path home) throws IOException {
        final Process tar =
                new ProcessBuilder(
                                "tar",
                                "-xzf",
                                archive.toString(),
                                "-C",
                                home.toString(),
                                "--strip-components=1")
                        .redirectErrorStream(true)
                        .start();
        final String said = new String(tar.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        try {
            if (tar.waitFor() != 0) {
                throw new IOException(
                        "could not unpack the JDK into " + home + ": " + said.strip());
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IOException("interrupted while unpacking the JDK", ex);
        }
    }

    private static String digestOf(Path file) throws IOException {
        final MessageDigest sha256 = sha256();
        try (InputStream in = Files.newInputStream(file)) {
            final byte[] buffer = new byte[BUFFER];
            for (int read = in.read(buffer); read >= 0; read = in.read(buffer)) {
                sha256.update(buffer, 0, read);
            }
        }
        return HexFormat.of().formatHex(sha256.digest());
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("every Java has SHA-256", ex);
        }
    }
}
