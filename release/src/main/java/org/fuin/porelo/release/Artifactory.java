package org.fuin.porelo.release;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;
import org.jspecify.annotations.Nullable;

/**
 * What the snapshot pruning and the uploads of a Publish ask of Artifactory: listings, a file's
 * facts, put, upload, delete, indexes, and a call to its REST interface.
 */
public interface Artifactory {

    /** Artifactory did not answer, or answered a 5xx: worth asking again. */
    final class Unanswered extends IOException {

        @java.io.Serial private static final long serialVersionUID = 1L;

        Unanswered(String message) {
            super(message);
        }

        Unanswered(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /**
     * One entry of a folder.
     *
     * @param name its name, without a slash
     * @param folder whether it is a folder
     */
    record Child(String name, boolean folder) {}

    /**
     * Lists a folder.
     *
     * @param repository the repository's key
     * @param folder the folder's path in it, without a leading slash; empty for its root
     * @return its entries, or empty when the folder does not exist
     * @throws IOException when it could not be read
     */
    Optional<List<Child>> children(String repository, String folder) throws IOException;

    /**
     * When a file was stored.
     *
     * @param repository the repository's key
     * @param path the file's path
     * @return Artifactory's {@code created}
     * @throws IOException when it could not be read, or the file is not there
     */
    Instant created(String repository, String path) throws IOException;

    /**
     * A file's properties.
     *
     * @param repository the repository's key
     * @param path the file's path
     * @return every property and its values; empty when it has none
     * @throws IOException when it could not be read, or the file is not there
     */
    Map<String, List<String>> properties(String repository, String path) throws IOException;

    /**
     * Stores a file.
     *
     * @param repository the repository's key
     * @param path where
     * @param body what
     * @throws IOException when it was not stored
     */
    void put(String repository, String path, byte[] body) throws IOException;

    /**
     * Uploads a file, its SHA-1 and SHA-256 with it, so Artifactory refuses bytes that do not match
     * them.
     *
     * @param target the repository's key and the path in it, joined by {@code /}
     * @param file the file, streamed
     * @param properties {@code key=value} joined by {@code ;}, set with the upload; empty for none
     * @throws Unanswered when Artifactory did not answer or answered a 5xx
     * @throws IOException when it was not stored
     */
    void upload(String target, java.nio.file.Path file, String properties) throws IOException;

    /**
     * A call to the REST interface.
     *
     * @param method the HTTP method
     * @param path the path under {@code /artifactory}, starting with {@code /}, a query included
     * @return the answer's body
     * @throws Unanswered when Artifactory did not answer or answered a 5xx
     * @throws IOException when it answered anything else but a 2xx
     */
    byte[] call(String method, String path) throws IOException;

    /**
     * Deletes a file.
     *
     * @param repository the repository's key
     * @param path which
     * @throws IOException when it was not deleted
     */
    void delete(String repository, String path) throws IOException;

    /**
     * A Debian distribution's {@code Packages} index for {@code main}, {@code amd64}.
     *
     * @param repository the repository's key
     * @param distribution its name, {@code snapshots} or {@code releases}
     * @return the index's text, or empty when the distribution has none
     * @throws IOException when it could not be read
     */
    Optional<String> debianPackages(String repository, String distribution) throws IOException;

    /**
     * The primary metadata of an RPM folder.
     *
     * @param repository the repository's key
     * @param folder the folder holding {@code repodata/}
     * @return its XML, unpacked, or empty when the folder has none
     * @throws IOException when it could not be read
     */
    Optional<String> rpmPrimary(String repository, String folder) throws IOException;

    /**
     * Asks Artifactory to recalculate a Debian repository's indexes.
     *
     * @param repository the repository's key
     * @throws IOException when it refused
     */
    void recalculateDebian(String repository) throws IOException;

    /**
     * Asks Artifactory to recalculate an RPM folder's metadata.
     *
     * @param repository the repository's key
     * @param folder the folder holding {@code repodata/}
     * @throws IOException when it refused
     */
    void recalculateRpm(String repository, String folder) throws IOException;

    /**
     * Artifactory over its REST interface.
     *
     * <p>The token travels only as the {@code Authorization} header of calls to {@code base}; the
     * JDK drops it on a redirect to another host, which is how Artifactory offloads a large file.
     *
     * @param base the platform's address, without {@code /artifactory}
     * @param token the access token
     * @return the client
     */
    static Artifactory overHttp(URI base, String token) {
        return new Http(base, token);
    }

    /** {@link #overHttp}'s implementation. */
    final class Http implements Artifactory {

        private static final Pattern PRIMARY =
                Pattern.compile(
                        "<data type=\"primary\">.*?<location href=\"([^\"]+)\"", Pattern.DOTALL);

        private final String root;

        private final String token;

        private final HttpClient client =
                HttpClient.newBuilder()
                        .followRedirects(HttpClient.Redirect.NORMAL)
                        .connectTimeout(Duration.ofSeconds(60))
                        .build();

        private final ObjectMapper json = new ObjectMapper();

        private Http(URI base, String token) {
            final String text = base.toString();
            this.root =
                    (text.endsWith("/") ? text.substring(0, text.length() - 1) : text)
                            + "/artifactory";
            this.token = token;
        }

        @Override
        public Optional<List<Child>> children(String repository, String folder) throws IOException {
            final Optional<byte[]> body = send("GET", storage(repository, folder), null);
            if (body.isEmpty()) {
                return Optional.empty();
            }
            final JsonNode children = json.readTree(body.get()).path("children");
            if (!children.isArray()) {
                throw new IOException(
                        "a listing of " + repository + "/" + folder + " has no children");
            }
            final List<Child> result = new ArrayList<>();
            for (JsonNode child : children) {
                final String uri = child.path("uri").asText("");
                final String name = uri.startsWith("/") ? uri.substring(1) : uri;
                if (name.isEmpty() || name.contains("/") || !child.path("folder").isBoolean()) {
                    throw new IOException(
                            "a listing of "
                                    + repository
                                    + "/"
                                    + folder
                                    + " has an entry it cannot read: "
                                    + child);
                }
                result.add(new Child(name, child.path("folder").asBoolean()));
            }
            return Optional.of(result);
        }

        @Override
        public Instant created(String repository, String path) throws IOException {
            final JsonNode info = json.readTree(required("GET", storage(repository, path)));
            final String created = info.path("created").asText("");
            try {
                return java.time.OffsetDateTime.parse(created).toInstant();
            } catch (java.time.format.DateTimeParseException ex) {
                throw new IOException(
                        repository
                                + "/"
                                + path
                                + " has no readable created time: '"
                                + created
                                + "'",
                        ex);
            }
        }

        @Override
        public Map<String, List<String>> properties(String repository, String path)
                throws IOException {
            final URI address = URI.create(storage(repository, path) + "?properties");
            final Optional<byte[]> body = send("GET", address, null);
            if (body.isEmpty()) {
                // Artifactory answers 404 both for a missing file and for one with no properties.
                created(repository, path);
                return Map.of();
            }
            final Map<String, List<String>> result = new LinkedHashMap<>();
            final JsonNode answer = json.readTree(body.get()).path("properties");
            if (!answer.isObject()) {
                throw new IOException(repository + "/" + path + " answered properties without any");
            }
            final var fields = answer.properties();
            for (Map.Entry<String, JsonNode> field : fields) {
                final List<String> values = new ArrayList<>();
                field.getValue().forEach(value -> values.add(value.asText()));
                result.put(field.getKey(), List.copyOf(values));
            }
            return result;
        }

        @Override
        public void put(String repository, String path, byte[] body) throws IOException {
            required("PUT", URI.create(root + "/" + repository + "/" + encode(path)), body);
        }

        @Override
        public void upload(String target, java.nio.file.Path file, String properties)
                throws IOException {
            final String slash = target.substring(0, target.indexOf('/') + 1);
            final URI address =
                    URI.create(
                            root
                                    + "/"
                                    + slash
                                    + encode(target.substring(slash.length()))
                                    + (properties.isEmpty() ? "" : ";" + properties));
            final long size = java.nio.file.Files.size(file);
            final HttpRequest.Builder request =
                    HttpRequest.newBuilder(address)
                            .header("X-Checksum-Sha1", digest(file, "SHA-1"))
                            .header("X-Checksum-Sha256", digest(file, "SHA-256"))
                            .PUT(HttpRequest.BodyPublishers.ofFile(file));
            try {
                sent("PUT", address, request)
                        .orElseThrow(() -> new IOException("PUT " + address + ": HTTP 404"));
            } catch (Unanswered ex) {
                // A file gone or changed while it was sent: asking again sends something else.
                if (!java.nio.file.Files.isRegularFile(
                                file, java.nio.file.LinkOption.NOFOLLOW_LINKS)
                        || java.nio.file.Files.size(file) != size) {
                    throw new IOException(
                            "PUT " + address + ": " + file + " changed while it was sent", ex);
                }
                throw ex;
            }
        }

        @Override
        public byte[] call(String method, String path) throws IOException {
            final URI address = URI.create(root + path);
            // A path that leaves /artifactory would take the token with it.
            final String under = URI.create(root).getRawPath() + "/";
            if (!address.normalize().getRawPath().startsWith(under)) {
                throw new IOException(method + " " + path + ": not under " + under);
            }
            return required(method, address);
        }

        private static String digest(java.nio.file.Path file, String algorithm) throws IOException {
            final java.security.MessageDigest digest;
            try {
                digest = java.security.MessageDigest.getInstance(algorithm);
            } catch (java.security.NoSuchAlgorithmException ex) {
                throw new IllegalStateException("every Java has " + algorithm, ex);
            }
            try (java.io.InputStream in = java.nio.file.Files.newInputStream(file)) {
                final byte[] buffer = new byte[64 * 1024];
                for (int read = in.read(buffer); read >= 0; read = in.read(buffer)) {
                    digest.update(buffer, 0, read);
                }
            }
            return java.util.HexFormat.of().formatHex(digest.digest());
        }

        @Override
        public void delete(String repository, String path) throws IOException {
            required("DELETE", URI.create(root + "/" + repository + "/" + encode(path)));
        }

        @Override
        public Optional<String> debianPackages(String repository, String distribution)
                throws IOException {
            return send(
                            "GET",
                            URI.create(
                                    root
                                            + "/"
                                            + repository
                                            + "/dists/"
                                            + encode(distribution)
                                            + "/main/binary-amd64/Packages"),
                            null)
                    .map(body -> new String(body, StandardCharsets.UTF_8));
        }

        @Override
        public Optional<String> rpmPrimary(String repository, String folder) throws IOException {
            final String base = root + "/" + repository + "/" + encode(folder) + "/";
            final Optional<byte[]> repomd =
                    send("GET", URI.create(base + "repodata/repomd.xml"), null);
            if (repomd.isEmpty()) {
                return Optional.empty();
            }
            final Matcher primary =
                    PRIMARY.matcher(new String(repomd.get(), StandardCharsets.UTF_8));
            if (!primary.find()) {
                throw new IOException(
                        repository
                                + "/"
                                + folder
                                + "/repodata/repomd.xml names no primary metadata");
            }
            final String href = primary.group(1);
            final byte[] body = required("GET", URI.create(base + encode(href)));
            if (!href.endsWith(".gz")) {
                return Optional.of(new String(body, StandardCharsets.UTF_8));
            }
            try (GZIPInputStream unpacked = new GZIPInputStream(new ByteArrayInputStream(body))) {
                return Optional.of(new String(unpacked.readAllBytes(), StandardCharsets.UTF_8));
            }
        }

        @Override
        public void recalculateDebian(String repository) throws IOException {
            required(
                    "POST",
                    URI.create(root + "/api/deb/reindex/" + repository + "?async=0"),
                    new byte[0]);
        }

        @Override
        public void recalculateRpm(String repository, String folder) throws IOException {
            required(
                    "POST",
                    URI.create(
                            root
                                    + "/api/yum/"
                                    + repository
                                    + "?path="
                                    + URLEncoder.encode(folder, StandardCharsets.UTF_8)
                                    + "&async=0"),
                    new byte[0]);
        }

        private URI storage(String repository, String path) {
            return URI.create(
                    root
                            + "/api/storage/"
                            + repository
                            + (path.isEmpty() ? "" : "/" + encode(path)));
        }

        /**
         * Each segment encoded, so a {@code ~} or a space in a file name reaches the file it names.
         */
        private static String encode(String path) {
            final StringBuilder result = new StringBuilder();
            for (String segment : path.split("/", -1)) {
                if (!result.isEmpty()) {
                    result.append('/');
                }
                result.append(
                        URLEncoder.encode(segment, StandardCharsets.UTF_8)
                                .replace("+", "%20")
                                .replace("%7E", "~"));
            }
            return result.toString();
        }

        private byte[] required(String method, URI address) throws IOException {
            return required(method, address, null);
        }

        private byte[] required(String method, URI address, byte @Nullable [] body)
                throws IOException {
            return send(method, address, body)
                    .orElseThrow(() -> new IOException(method + " " + address + ": HTTP 404"));
        }

        private Optional<byte[]> send(String method, URI address, byte @Nullable [] body)
                throws IOException {
            return sent(
                    method,
                    address,
                    HttpRequest.newBuilder(address)
                            .method(
                                    method,
                                    body == null
                                            ? HttpRequest.BodyPublishers.noBody()
                                            : HttpRequest.BodyPublishers.ofByteArray(body)));
        }

        private Optional<byte[]> sent(String method, URI address, HttpRequest.Builder request)
                throws IOException {
            // An upload of a few hundred megabytes takes longer than a call.
            request.timeout(Duration.ofMinutes(10)).header("Authorization", "Bearer " + token);
            final HttpResponse<byte[]> response;
            try {
                response = client.send(request.build(), HttpResponse.BodyHandlers.ofByteArray());
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new IOException("interrupted: " + method + " " + address, ex);
            } catch (IOException ex) {
                final String why = method + " " + address + ": " + Web.reason(ex);
                throw Web.mayAnswerNextTime(ex)
                        ? new Unanswered(why, ex)
                        : new IOException(why, ex);
            }
            final int status = response.statusCode();
            // Empty, which every call that must change something turns into a failure.
            if (status == 404) {
                return Optional.empty();
            }
            if (status / 100 == 5) {
                throw new Unanswered(method + " " + address + ": HTTP " + status);
            }
            if (status / 100 != 2) {
                throw new IOException(method + " " + address + ": HTTP " + status);
            }
            return Optional.of(response.body());
        }
    }
}
