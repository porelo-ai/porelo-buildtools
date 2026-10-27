package org.fuin.porelo.release;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CheckPushesTest {

    private static final Instant NOW = Instant.parse("2026-10-27T12:00:00Z");

    private static final String ACTIVITY =
            "https://api.github.com/repos/porelo-ai/porelo-x/activity"
                    + "?ref=refs%2Fheads%2Fmain&per_page=100";

    private static final String TOKEN = "ghs_secret0123456789";

    private final ByteArrayOutputStream out = new ByteArrayOutputStream();

    private final ByteArrayOutputStream err = new ByteArrayOutputStream();

    private final List<String> checked = new ArrayList<>();

    private final Map<String, Integer> answers = new HashMap<>();

    // Prevents a commit pushed a day after it was made from going unchecked: the push's own range
    // is
    // read, whatever its commits' dates.
    @Test
    void refusesAnOldCommitInADaysPush() {
        answers.put(range('a', 'b'), Stop.REFUSED);
        final FakeWeb web =
                new FakeWeb().serve(ACTIVITY, activity(push('a', 'b', "2026-10-27T11:00:00Z")));

        assertThat(run(web, env(), 26)).as("the day's push holds a refused commit").isEqualTo(1);
        assertThat(checked).as("its range is read").containsExactly(range('a', 'b'));
        assertThat(text(out)).as("the push is named").contains(sha('a').substring(0, 7));
    }

    // Prevents a quiet day from failing the scheduled run.
    @Test
    void aCleanDayPasses() {
        final FakeWeb web = new FakeWeb().serve(ACTIVITY, "[]");

        assertThat(run(web, env(), 26)).as("no push").isZero();
        assertThat(checked).as("nothing to read").isEmpty();
        assertThat(text(out)).as("it says so").contains("no push to main");
    }

    // Prevents an older push, already read by an earlier day, from being read again and again.
    @Test
    void aPushOlderThanTheWindowIsNotRead() {
        final FakeWeb web =
                new FakeWeb()
                        .serve(
                                ACTIVITY,
                                activity(
                                        push('c', 'd', "2026-10-27T10:00:00Z"),
                                        push('a', 'c', "2026-10-26T09:00:00Z")));

        assertThat(run(web, env(), 26)).as("the day's push is clean").isZero();
        assertThat(checked).as("only the push inside 26 hours").containsExactly(range('c', 'd'));
    }

    // Prevents a page the command cannot follow from hiding pushes inside the window.
    @Test
    void aFullPageStillInsideTheWindowIsUnanswered() {
        final String[] pushes = new String[100];
        for (int i = 0; i < pushes.length; i++) {
            pushes[i] = push('a', 'b', "2026-10-27T11:00:00Z");
        }
        final FakeWeb web = new FakeWeb().serve(ACTIVITY, activity(pushes));

        assertThat(run(web, env(), 26)).as("a page it cannot follow").isEqualTo(2);
        assertThat(checked).as("nothing is read half").isEmpty();
        assertThat(text(err)).as("it says why").contains("100");
    }

    // Prevents an unreachable API, a missing token or another forge from passing as a clean day.
    @Test
    void whatCannotBeAskedIsUnanswered() {
        assertThat(run(new FakeWeb().fail(ACTIVITY, "HTTP 403"), env(), 26))
                .as("the API refused")
                .isEqualTo(2);
        assertThat(text(err)).as("the address is named").contains("/activity");

        final Map<String, String> noToken = new HashMap<>(env());
        noToken.remove("GITHUB_TOKEN");
        assertThat(run(FakeWeb.untouchable(), noToken, 26)).as("no token").isEqualTo(2);

        final Map<String, String> forge = new HashMap<>(env());
        forge.put("GITHUB_API_URL", "https://10.73.0.1:3000/api/v1");
        assertThat(run(FakeWeb.untouchable(), forge, 26)).as("another forge's API").isEqualTo(2);
        assertThat(checked).as("nothing was read").isEmpty();
    }

    // Prevents a commit the checkout lacks from passing unread.
    @Test
    void aRangeTheCheckoutCannotReadIsUnanswered() {
        answers.put(range('e', 'f'), Stop.UNANSWERED);
        final FakeWeb web =
                new FakeWeb().serve(ACTIVITY, activity(push('e', 'f', "2026-10-27T11:30:00Z")));

        assertThat(run(web, env(), 26)).as("git could not read it").isEqualTo(2);
    }

    // Prevents the token from reaching a log.
    @Test
    void theTokenAppearsInNoOutput() {
        final FakeWeb web =
                new FakeWeb().serve(ACTIVITY, activity(push('a', 'b', "2026-10-27T11:00:00Z")));

        run(web, env(), 26);
        run(new FakeWeb().fail(ACTIVITY, "HTTP 500"), env(), 26);

        assertThat(web.headers).as("the token goes to GitHub").isNotEmpty();
        assertThat(web.headers.getFirst().get("Authorization")).isEqualTo("Bearer " + TOKEN);
        assertThat(text(out) + text(err)).as("and nowhere else").doesNotContain(TOKEN);
    }

    // Prevents the push that made main from going unread: every commit up to its head is new.
    @Test
    void thePushThatMadeMainIsReadWhole() {
        final FakeWeb web =
                new FakeWeb()
                        .serve(
                                ACTIVITY,
                                activity(
                                        activity(
                                                sha('0'),
                                                sha('a'),
                                                "refs/heads/main",
                                                "2026-10-27T11:00:00Z",
                                                "branch_creation")));

        assertThat(run(web, env(), 26)).as("main's first push is clean").isZero();
        assertThat(checked).as("its head and all before it").containsExactly(sha('a'));
    }

    // Prevents another branch's activity from being read as main's.
    @Test
    void anotherBranchIsNotRead() {
        final FakeWeb web =
                new FakeWeb()
                        .serve(
                                ACTIVITY,
                                activity(
                                        activity(
                                                sha('a'),
                                                sha('b'),
                                                "refs/heads/topic",
                                                "2026-10-27T11:00:00Z",
                                                "push")));

        assertThat(run(web, env(), 26)).as("no push to main").isZero();
        assertThat(checked).as("the topic branch is not read").isEmpty();
    }

    // Prevents a later clean push from hiding an earlier refused one, and keeps the pushes in
    // order.
    @Test
    void everyPushIsReadOldestFirstAndTheWorstCounts() {
        answers.put(range('a', 'b'), Stop.REFUSED);
        final FakeWeb web =
                new FakeWeb()
                        .serve(
                                ACTIVITY,
                                activity(
                                        push('b', 'c', "2026-10-27T11:00:00Z"),
                                        push('a', 'b', "2026-10-27T09:00:00Z")));

        assertThat(run(web, env(), 26)).as("the earlier push is refused").isEqualTo(1);
        assertThat(checked)
                .as("both, oldest first")
                .containsExactly(range('a', 'b'), range('b', 'c'));
    }

    // Prevents a token the client cannot send, or an error that repeats it, from reaching a log.
    @Test
    void aTokenInAnErrorIsNeverShown() {
        assertThat(run(new FakeWeb().fail(ACTIVITY, "rejected " + TOKEN), env(), 26))
                .as("the API refused")
                .isEqualTo(2);
        final Map<String, String> broken = new HashMap<>(env());
        broken.put("GITHUB_TOKEN", TOKEN + "\n");
        assertThat(run(FakeWeb.untouchable(), broken, 26))
                .as("a token with a newline")
                .isEqualTo(2);

        assertThat(text(out) + text(err)).as("neither shows it").doesNotContain(TOKEN);
    }

    private int run(Web web, Map<String, String> env, int hours) {
        return new CheckPushes(
                        new PrintStream(out, true, StandardCharsets.UTF_8),
                        new PrintStream(err, true, StandardCharsets.UTF_8),
                        web,
                        env,
                        Clock.fixed(NOW, ZoneOffset.UTC),
                        range -> {
                            checked.add(range);
                            return answers.getOrDefault(range, 0);
                        })
                .check(Duration.ofHours(hours));
    }

    private static Map<String, String> env() {
        return Map.of(
                "GITHUB_API_URL",
                "https://api.github.com",
                "GITHUB_REPOSITORY",
                "porelo-ai/porelo-x",
                "GITHUB_TOKEN",
                TOKEN);
    }

    private static String activity(String... pushes) {
        return "[" + String.join(",", pushes) + "]";
    }

    private static String push(char before, char after, String when) {
        return activity(sha(before), sha(after), "refs/heads/main", when, "push");
    }

    private static String activity(
            String before, String after, String ref, String when, String type) {
        return "{\"id\":1,\"before\":\""
                + before
                + "\",\"after\":\""
                + after
                + "\",\"ref\":\""
                + ref
                + "\",\"timestamp\":\""
                + when
                + "\",\"activity_type\":\""
                + type
                + "\"}";
    }

    private static String sha(char c) {
        return String.valueOf(c).repeat(40);
    }

    private static String range(char before, char after) {
        return sha(before) + ".." + sha(after);
    }

    private static String text(ByteArrayOutputStream stream) {
        return stream.toString(StandardCharsets.UTF_8);
    }
}
