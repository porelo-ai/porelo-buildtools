package org.fuin.porelo.machines;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.fuin.porelo.json.Json;
import org.jspecify.annotations.Nullable;

/**
 * The Hetzner Cloud REST API, as much of it as this repository uses.
 *
 * <p><strong>Seven endpoints, not a client library.</strong> What is needed here is creating a
 * server, waiting for it, deleting it, listing what was left behind, and finding an image, a
 * location and a key. Everything else the API offers is somebody else's problem.
 *
 * <p><strong>Paging is not optional.</strong> The API returns 25 entries a page. A caller that
 * reads the first page and stops finds fewer leaked servers than exist and reports success - and a
 * server nobody deletes costs 81 EUR a month against 3 cents for the fifteen minutes it was meant
 * to live. That is why {@link #all} exists and why nothing here calls {@link #get} for a list.
 */
final class Api implements AutoCloseable {

    static Machines machines(String token, String runId) {
        return Hetzner.open(token, runId);
    }

    private static final String BASE = "https://api.hetzner.cloud/v1";

    private final String base;

    /** Long enough for a slow create, short enough that a hung call is not mistaken for work. */
    private static final Duration TIMEOUT = Duration.ofSeconds(60);

    /** How many times to obey a rate limit before giving up on it. */
    private static final int RATE_LIMIT_ATTEMPTS = 5;

    /** How long a read or a delete is tried again through an outage of the API. */
    private static final Duration OUTAGE_PATIENCE = Duration.ofSeconds(60);

    /** The pause before a read or a delete is tried again through an outage. */
    private static final Duration OUTAGE_PAUSE = Duration.ofSeconds(5);

    private final HttpClient http;

    private final String token;

    private final Waiting waiting;

    Api(String token) {
        this(token, BASE);
    }

    /**
     * Points at another root, for a test that stands one up.
     *
     * @param token The API token.
     * @param base The API root, without a trailing slash.
     */
    Api(String token, String base) {
        this(token, base, Waiting.SYSTEM);
    }

    /**
     * Points at another root and waits out a rate limit as told, for a test of the waiting.
     *
     * @param token The API token.
     * @param base The API root, without a trailing slash.
     * @param waiting The pause a rate limit is waited out with.
     */
    Api(String token, String base, Waiting waiting) {
        this.waiting = waiting;
        this.base = base;
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException(
                    "No API token. Set the token in the environment; it is never an argument.");
        }
        this.token = token;
        this.http = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();
    }

    /**
     * Returns one page, or one object.
     *
     * @param path Path below the API root, starting with a slash.
     * @return The parsed body.
     * @throws IOException If the call fails or the API refuses it.
     */
    Map<String, Object> get(String path) throws IOException {
        return send("GET", path, null);
    }

    /**
     * Returns every entry of a collection, following the API's paging to the end.
     *
     * @param path Path below the API root, with any query but {@code page}.
     * @param field Name of the array in the body, such as {@code servers}.
     * @return Every entry, in the order the API returned them.
     * @throws IOException If any page fails.
     */
    List<Map<String, Object>> all(String path, String field) throws IOException {
        final List<Map<String, Object>> found = new ArrayList<>();
        final String separator = path.contains("?") ? "&" : "?";
        long page = 1;
        while (page > 0) {
            final Map<String, Object> body =
                    get(path + separator + "page=" + page + "&per_page=50");
            found.addAll(Values.objects(body, field));
            page = Values.nextPage(body);
        }
        return found;
    }

    /**
     * Creates something.
     *
     * @param path Path below the API root.
     * @param body What to send.
     * @return The parsed answer.
     * @throws IOException If the call fails or the API refuses it.
     */
    Map<String, Object> post(String path, Map<String, Object> body) throws IOException {
        return send("POST", path, Json.write(body));
    }

    /**
     * Deletes something.
     *
     * @param path Path below the API root.
     * @return The parsed answer, which carries an action.
     * @throws IOException If the call fails or the API refuses it.
     */
    Map<String, Object> delete(String path) throws IOException {
        return send("DELETE", path, null);
    }

    private Map<String, Object> send(String method, String path, @Nullable String body)
            throws IOException {
        // A read or a delete may be sent again; a create whose answer was lost may have made a
        // machine, so it is sent once.
        final boolean again = "GET".equals(method) || "DELETE".equals(method);
        final java.time.Instant giveUp = waiting.now().plus(OUTAGE_PATIENCE);
        int outageTries = 0;
        int rateLimited = 0;
        while (true) {
            final HttpRequest.BodyPublisher content =
                    body == null
                            ? HttpRequest.BodyPublishers.noBody()
                            : HttpRequest.BodyPublishers.ofString(body);
            final HttpRequest request =
                    HttpRequest.newBuilder(URI.create(base + path))
                            .header("Authorization", "Bearer " + token)
                            .header("Content-Type", "application/json")
                            .timeout(TIMEOUT)
                            .method(method, content)
                            .build();
            final HttpResponse<String> response;
            try {
                response = http.send(request, HttpResponse.BodyHandlers.ofString());
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new IOException("interrupted calling " + method + " " + path, ex);
            } catch (IOException ex) {
                outageTries++;
                if (again && waitedOut(giveUp)) {
                    continue;
                }
                throw new IOException(
                        method
                                + " "
                                + path
                                + " failed, "
                                + outageTries
                                + " tries in "
                                + OUTAGE_PATIENCE.toSeconds()
                                + " s: "
                                + ex.getClass().getSimpleName()
                                + ": "
                                + ex.getMessage(),
                        ex);
            }
            if (again && OUTAGE.contains(response.statusCode())) {
                outageTries++;
                if (waitedOut(giveUp)) {
                    continue;
                }
            }
            if (response.statusCode() == 429 && ++rateLimited < RATE_LIMIT_ATTEMPTS) {
                // The API says when it will answer again. Guessing shorter only spends the
                // remaining budget faster.
                pause(Values.retryAfter(response.headers().firstValue("Retry-After").orElse(null)));
                continue;
            }
            return answered(method, path, response);
        }
    }

    private static Map<String, Object> answered(
            String method, String path, HttpResponse<String> response) throws IOException {
        final String body = response.body();
        if (response.statusCode() >= 400 && (body == null || body.isBlank())) {
            throw new ApiException(
                    "",
                    method + " " + path + " answered " + response.statusCode() + " with no body");
        }
        if (response.statusCode() == 204 || body == null || body.isBlank()) {
            return Map.of();
        }
        final Map<String, Object> parsed;
        try {
            parsed = Values.object(Json.parse(body));
        } catch (RuntimeException ex) {
            throw new IOException(
                    method
                            + " "
                            + path
                            + " answered "
                            + response.statusCode()
                            + " with something that is not JSON: "
                            + Values.brief(body),
                    ex);
        }
        if (response.statusCode() >= 400) {
            throw new ApiException(
                    Values.errorCode(parsed),
                    method
                            + " "
                            + path
                            + " answered "
                            + response.statusCode()
                            + ": "
                            + Values.errorMessage(parsed));
        }
        return parsed;
    }

    /** What an API answers while it is down for a moment, rather than refusing the call. */
    private static final java.util.Set<Integer> OUTAGE = java.util.Set.of(502, 503, 504);

    /** Pauses before another try, unless that would outlast the patience. */
    private boolean waitedOut(java.time.Instant giveUp) throws IOException {
        if (waiting.now().plus(OUTAGE_PAUSE).isAfter(giveUp)) {
            return false;
        }
        pause(OUTAGE_PAUSE);
        return true;
    }

    private void pause(Duration duration) throws IOException {
        try {
            waiting.sleep(duration);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IOException("interrupted while waiting out a rate limit or an outage", ex);
        }
    }

    @Override
    public void close() {
        http.close();
    }

    /** What the API said when it refused, with the code it refused by. */
    static final class ApiException extends IOException {

        private static final long serialVersionUID = 1L;

        private final String code;

        ApiException(String code, String message) {
            super(message);
            this.code = code;
        }

        /**
         * Returns the API's own name for the refusal, such as {@code resource_limit_exceeded}.
         *
         * @return The code, or an empty string when it gave none.
         */
        String code() {
            return code;
        }
    }
}
