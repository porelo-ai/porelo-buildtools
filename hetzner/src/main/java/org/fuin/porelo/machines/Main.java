package org.fuin.porelo.machines;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;

/**
 * The command line, for the parts of this a workflow calls directly.
 *
 * <p>Only the sweep is here. Renting a machine is not a command, because a command that creates a
 * server and returns leaves the deleting to whoever remembers - which is the failure this whole
 * module exists to make structural. Callers that need a machine hold a {@link Lease} for as long as
 * they need it.
 */
public final class Main {

    /**
     * The longest time limit {@code --for} takes, in minutes.
     *
     * <p>The longest a GitHub-hosted job may run. More can only be a slip of the keyboard, and it
     * would keep a forgotten server from the sweep for as long.
     */
    private static final int LONGEST_TIME_LIMIT = 360;

    private Main() {
        throw new UnsupportedOperationException("Utility class");
    }

    /** Where the Hetzner API token is read from. Named once: a half-done rename is silent. */
    static final String API_TOKEN = "HETZNER_API";

    /** Where the private key is read from, as material rather than a path. */
    static final String SSH_KEY = "HETZNER_SSH";

    /** Where the local pool's run key is read from, as material rather than a path. */
    static final String POOL_SSH = "PORELO_POOL_SSH";

    /**
     * Runs a sweep.
     *
     * @param args {@code sweep} with {@code --mine}, {@code --from <file>}, {@code --now}, {@code
     *     --including-in-use} or nothing, which lists what would go; or another command.
     */
    public static void main(String[] args) {
        try {
            System.exit(
                    run(
                            args,
                            COMPLAIN,
                            () ->
                                    Hetzner.with(
                                            token(System.getenv(API_TOKEN)),
                                            runId(
                                                    System.getenv("GITHUB_RUN_ID"),
                                                    System.getenv("PORELO_CI_LEG")))));
        } catch (IOException | IllegalStateException | IllegalArgumentException ex) {
            // The refusals this code makes on purpose - a missing token, an API that said no.
            // Anything else is a fault here and keeps its stack trace, because a one-line message
            // for a NullPointerException hides the only useful thing about it.
            COMPLAIN.accept(ex.getMessage());
            System.exit(1);
        }
    }

    static int run(String[] args, Consumer<String> complain, Supplier<Hetzner> open)
            throws IOException {
        if (args.length > 0 && "snapshot".equals(args[0])) {
            return snapshot(args, complain, open);
        }
        if (args.length > 0 && "acceptance".equals(args[0])) {
            return acceptance(args, complain, System.getenv(), open, AgentLeg::run);
        }
        if (args.length > 0 && "lease".equals(args[0])) {
            return lease(args, complain, open);
        }
        if (args.length > 0 && "jdk".equals(args[0])) {
            return jdk(args, complain, System.getenv(), PinnedJdk.overHttps());
        }
        if (args.length > 0 && "musl".equals(args[0])) {
            return musl(args, complain, Main::runInBash);
        }
        if (args.length > 0 && !"sweep".equals(args[0])) {
            // Named rather than answered with the usage text alone. A repository that resolves this
            // from a published snapshot can be handed a build older than the command it is asking
            // for, and a bare usage dump reads as a mistake in the workflow rather than as tooling
            // that has not caught up.
            complain.accept(
                    "unknown command '"
                            + args[0]
                            + "'. This build of the tooling knows"
                            + " sweep, snapshot, acceptance, lease, jdk and musl - if you expected another, it is"
                            + " older than the caller.");
        }
        if (args.length == 0 || !"sweep".equals(args[0])) {
            System.err.println(
                    """
                Usage: sweep [--mine | --from <file>] [--now] [--including-in-use]
                       snapshot --os <ubuntu|fedora> [--key <file>] [--repo <dir>] [--type <t>]
                                [--unless-current] [--for <minutes>]
                       acceptance (--package <p> | --candidate <dir>) (--script <f> | --cucumber <dir>)
                                [--os <o>] [--key <file>] [--type <t,t...>] [--keep] [--accounts <n>]
                                [--for <minutes>] [--pool <file>]
                       lease    --os <ubuntu|fedora> [--key <file>] [--write <file>] [--candidate <dir>]
                                [--type <t>] [--for <minutes>] - rents a machine, installs Porelo,
                                starts the daemon as an unprivileged user, and leaves it running.
                                What deletes it is 'sweep --mine'.
                       jdk      [--into <dir>] [--github] - installs the GraalVM the CI snapshots
                                pin, checked against its digest, into <dir> (default: a directory
                                under RUNNER_TEMP). --github makes it JAVA_HOME and puts it on the
                                PATH for every later step of the job.
                       musl     - installs the musl cross-toolchain and a musl-built zlib that the
                                hooks link against, both checked against their digests, under
                                MUSL_PREFIX (default ~/.local/opt).

                  --mine                 delete what this run created, whatever its expiry. What a
                                         job uses to clean up after itself - deleting by age
                                         catches another run's server when that run is slow
                  --now                  delete, rather than saying what would be deleted
                  --including-in-use     delete the servers still within their time as well;
                                         without it a sweep deletes only servers whose
                                         porelo-expires has passed, or that have none
                  (none)                 say what would be deleted, and delete nothing

                  --for <minutes>        how long the job may hold the machine (default 60, at
                                         most 360): it is labelled porelo-expires with its
                                         creation plus that plus 15 minutes, and no sweep of
                                         everyone's servers deletes it before then

                  --pool <file>          take a run account of the host's local VM pool, as
                                         porelo-host's up wrote it, instead of renting a server;
                                         takes the run key from PORELO_POOL_SSH or --key, and the
                                         account is made anew when given back

                  --candidate <dir>      install the packages built in this run instead of
                                         published ones, from a directory holding one build of
                                         each. What a repository uses before it has published
                                         anything. Several different packages go in together, so
                                         a set that only makes sense together is proved together;
                                         two builds of the same package are refused, because the
                                         package manager would take whichever it prefers.

                The API token is read from the environment, never from an argument: everything on
                a command line is readable by every process on the machine.""");
            return 2;
        }

        boolean mine = false;
        String from = null;
        boolean delete = false;
        boolean includingInUse = false;
        for (int at = 1; at < args.length; at++) {
            switch (args[at]) {
                case "--mine" -> mine = true;
                case "--from" -> from = value(args, ++at);
                case "--now" -> delete = true;
                case "--including-in-use" -> includingInUse = true;
                case "--older-than" -> {
                    // Refused whatever its value, so a caller still sweeping by age fails rather
                    // than going on under a rule it did not ask for; 0 deleted every server in use.
                    complain.accept(
                            "--older-than is gone: a sweep deletes a server once its "
                                    + Hetzner.EXPIRES_LABEL
                                    + " has passed, or when it has none, whatever its age."
                                    + " To delete the servers still within their time as well,"
                                    + " pass --including-in-use.");
                    return 2;
                }
                default -> {
                    complain.accept("unknown option: " + args[at]);
                    return 2;
                }
            }
        }

        try (Hetzner hetzner = open.get()) {
            if (mine || from != null) {
                // Always deletes, whatever else was passed: a job cleaning up after itself is not a
                // question, and age does not come into it. --from is the same act for a run whose
                // id this process could not derive, so it belongs on this side of the branch - it
                // was nested under --mine when first written, which made the usage text say
                // 'either' while the code meant 'both'.
                if (from != null) {
                    // The run id a lease wrote down, for the case its own could not be derived
                    // again. Reading the file rather than taking an id on the command line: the
                    // caller then has one thing to pass between two steps instead of five.
                    final java.util.Properties leased = new java.util.Properties();
                    try (var in = java.nio.file.Files.newInputStream(java.nio.file.Path.of(from))) {
                        leased.load(in);
                    }
                    final String run = leased.getProperty("run");
                    if (run == null || run.isBlank()) {
                        complain.accept(
                                from
                                        + " names no run, so there is nothing to match on."
                                        + " It should have been written by 'lease --write'.");
                        return 2;
                    }
                    System.out.println(
                            "deleted " + hetzner.deleteRun(run) + " server(s) of " + run);
                } else {
                    System.out.println(
                            "deleted " + hetzner.deleteMine() + " of this run's servers");
                }
                return 0;
            }
            final int swept = hetzner.sweep(includingInUse, !delete);
            System.out.println((delete ? "deleted " : "would delete ") + swept + " server(s)");
            if (swept > 0 && !delete) {
                // The same exit the script uses, so a scheduled run that finds something without
                // being allowed to remove it is visible rather than quietly green.
                System.out.println("\nnothing was deleted - pass --now");
                return 1;
            }
            return 0;
        }
    }

    /**
     * Builds a snapshot for one operating system.
     *
     * @param args The command line.
     * @param open Where to build it.
     * @return An exit code.
     * @throws IOException If it cannot be built.
     */
    private static int snapshot(String[] args, Consumer<String> complain, Supplier<Hetzner> open)
            throws IOException {
        String os = null;
        String key = null;
        String repo = null;
        String type = null;
        boolean unlessCurrent = false;
        Duration limit = Hetzner.DEFAULT_TIME_LIMIT;
        for (int at = 1; at < args.length; at++) {
            switch (args[at]) {
                case "--os" -> os = value(args, ++at);
                case "--key" -> key = value(args, ++at);
                case "--repo" -> repo = value(args, ++at);
                case "--type" -> type = value(args, ++at);
                case "--unless-current" -> unlessCurrent = true;
                case "--for" -> {
                    final Duration given = timeLimit(value(args, ++at), complain);
                    if (given == null) {
                        return 2;
                    }
                    limit = given;
                }
                default -> {
                    complain.accept("unknown option: " + args[at]);
                    return 2;
                }
            }
        }
        if (os == null) {
            complain.accept("snapshot needs --os");
            return 2;
        }
        final Credential credential =
                Credential.of(
                        System.getenv(SSH_KEY), key == null ? null : java.nio.file.Path.of(key));
        // The toolchain comes with this tooling, so a snapshot has it whether or not a checkout
        // proves it.
        final String musl = Snapshots.muslInstaller();
        final java.nio.file.Path archive =
                repo == null ? null : archiveOf(java.nio.file.Path.of(repo));
        try (Hetzner hetzner = open.get().forJobsOf(limit)) {
            // Asked before anything is rented: a snapshot already made from what this one would be
            // made from is the image a leg boots, and building it again only bills.
            final String inputs = Snapshots.inputs(os);
            if (unlessCurrent && Snapshots.current(hetzner, os, inputs)) {
                System.out.println(
                        "the newest "
                                + os
                                + " snapshot was made from these inputs ("
                                + inputs
                                + "), so none is built");
                return 0;
            }
            // A named type wins over the small-disk preference. The floor an image gets is the disk
            // it was built on, and a leg rents a 320 GB machine anyway - so when the wait matters
            // more than the floor, say so rather than waiting on two cores.
            Snapshots.build(
                    hetzner,
                    os,
                    type == null ? Snapshots.BUILD_TYPES : List.of(type),
                    credential,
                    archive,
                    musl);
        }
        return 0;
    }

    /** Runs an acceptance leg against a source of machines. */
    @FunctionalInterface
    interface Leg {

        /**
         * Runs the leg.
         *
         * @param machines Where the machine comes from.
         * @param options What to do.
         * @param credential The key to connect with.
         * @throws IOException If the leg fails.
         */
        void run(Machines machines, AgentLeg.Options options, Credential credential)
                throws IOException;
    }

    /**
     * Runs an agent's acceptance leg.
     *
     * @param args The command line.
     * @param complain Where a refusal goes.
     * @param env The environment: {@link #POOL_SSH}, {@link #SSH_KEY}, {@code XDG_STATE_HOME}.
     * @param open Where to rent the machine.
     * @param leg Runs the leg against the chosen machines.
     * @return An exit code.
     * @throws IOException If the leg fails.
     */
    static int acceptance(
            String[] args,
            Consumer<String> complain,
            Map<String, String> env,
            Supplier<Hetzner> open,
            Leg leg)
            throws IOException {
        String os = "ubuntu";
        String key = null;
        String pkg = null;
        String script = null;
        String candidate = null;
        String cucumber = null;
        String type = null;
        String artifactory = "https://fuinorg.jfrog.io/artifactory";
        boolean keep = false;
        int accounts = 1;
        String pool = null;
        Duration limit = Hetzner.DEFAULT_TIME_LIMIT;
        for (int at = 1; at < args.length; at++) {
            switch (args[at]) {
                case "--os" -> os = value(args, ++at);
                case "--pool" -> pool = value(args, ++at);
                case "--for" -> {
                    final Duration given = timeLimit(value(args, ++at), complain);
                    if (given == null) {
                        return 2;
                    }
                    limit = given;
                }
                case "--key" -> key = value(args, ++at);
                // Scenarios beside each other, one account each; 1 runs them in order, as always.
                case "--accounts" -> {
                    final String count = value(args, ++at);
                    if (!count.matches("[1-9]\\d?")) {
                        complain.accept("--accounts needs a number from 1 to 99, not " + count);
                        return 2;
                    }
                    accounts = Integer.parseInt(count);
                }
                case "--package" -> pkg = value(args, ++at);
                case "--script" -> script = value(args, ++at);
                case "--candidate" -> candidate = value(args, ++at);
                case "--cucumber" -> cucumber = value(args, ++at);
                case "--type" -> type = value(args, ++at);
                case "--artifactory" -> artifactory = value(args, ++at);
                case "--keep" -> keep = true;
                default -> {
                    complain.accept("unknown option: " + args[at]);
                    return 2;
                }
            }
        }
        if ((script == null && cucumber == null) || (pkg == null && candidate == null)) {
            // --package names what to install from the repository; --candidate installs files built
            // in this run instead, and then the name is not used for anything. Asking for both
            // would make a caller invent a name for packages it is holding in its hand. A script,
            // scenarios or both: an agent that replaced its script has nothing to pass.
            complain.accept(
                    "acceptance needs --script or --cucumber, and --package or --candidate");
            return 2;
        }
        if (accounts > 1 && cucumber == null) {
            complain.accept("--accounts runs scenarios beside each other, so it needs --cucumber");
            return 2;
        }
        if (accounts > 1 && pool != null) {
            // Further accounts are made on the machine with sudo, and a pool account's reset
            // removes only itself: they would outlive the run on a VM others rent next.
            complain.accept(
                    "--accounts makes accounts on the machine, which a pool account's reset leaves"
                            + " behind; run one scenario at a time with --pool");
            return 2;
        }
        // One core and 2 GB holds one task at a time and nothing more: several accounts rent what a
        // leg rents, which has room for four.
        final List<String> types =
                type != null
                        ? Spec.order(type)
                        : accounts > 1 ? Spec.DEFAULT_TYPES : List.of("cpx12");
        final AgentLeg.Options options =
                new AgentLeg.Options(
                        os,
                        types,
                        artifactory,
                        pkg == null ? "" : pkg,
                        script == null
                                ? null
                                : java.nio.file.Files.readString(java.nio.file.Path.of(script)),
                        candidate == null ? null : java.nio.file.Path.of(candidate),
                        keep,
                        cucumber == null ? null : java.nio.file.Path.of(cucumber),
                        accounts);
        if (pool != null) {
            // The host's own pool: nothing is rented and no provider is asked, so no token either.
            final Credential credential = poolCredential(env.get(POOL_SSH), key);
            final Pool read = Pool.read(java.nio.file.Path.of(pool));
            try (Machines local = LocalPool.onThisHost(read, credential, poolState(env), limit)) {
                leg.run(local, options, credential);
            }
            return 0;
        }
        try (Hetzner hetzner = open.get().forJobsOf(limit)) {
            leg.run(
                    hetzner,
                    options,
                    Credential.of(
                            env.get(SSH_KEY), key == null ? null : java.nio.file.Path.of(key)));
        }
        return 0;
    }

    private static Credential poolCredential(
            @Nullable String fromEnvironment, @Nullable String key) {
        if ((fromEnvironment == null || fromEnvironment.isBlank()) && key == null) {
            throw new IllegalArgumentException(
                    "No private key for the pool: set "
                            + POOL_SSH
                            + " in the environment or pass --key <file>.");
        }
        return Credential.of(fromEnvironment, key == null ? null : java.nio.file.Path.of(key));
    }

    /**
     * Returns where the pool's locks are kept, which every run of this account must share.
     *
     * @param env The environment, for {@code XDG_STATE_HOME} and {@code HOME}.
     * @return The directory.
     */
    static java.nio.file.Path poolState(Map<String, String> env) {
        final String state = env.get("XDG_STATE_HOME");
        final java.nio.file.Path base =
                state != null && !state.isBlank()
                        ? java.nio.file.Path.of(state)
                        : java.nio.file.Path.of(env.getOrDefault("HOME", "."), ".local", "state");
        return base.resolve("porelo-machines").resolve("pool");
    }

    /**
     * Installs the pinned GraalVM, for a build job that is not on one of the snapshots.
     *
     * @param args The command line.
     * @param complain Where a refusal goes.
     * @param env The environment: {@code RUNNER_TEMP}, {@code GITHUB_ENV}, {@code GITHUB_PATH}.
     * @param download Where the archive is fetched from.
     * @return An exit code.
     * @throws IOException If it cannot be installed.
     */
    static int jdk(
            String[] args,
            Consumer<String> complain,
            Map<String, String> env,
            PinnedJdk.Download download)
            throws IOException {
        String into = null;
        boolean github = false;
        for (int at = 1; at < args.length; at++) {
            switch (args[at]) {
                case "--into" -> into = value(args, ++at);
                case "--github" -> github = true;
                default -> {
                    complain.accept("unknown option: " + args[at]);
                    return 2;
                }
            }
        }
        final Snapshots.Contents contents = Snapshots.Contents.pinned();
        if (into == null) {
            final String temp = env.get("RUNNER_TEMP");
            if (temp == null || temp.isBlank()) {
                complain.accept(
                        "jdk needs --into <dir> outside a GitHub job, which has no RUNNER_TEMP");
                return 2;
            }
            into =
                    java.nio.file.Path.of(temp, "porelo-graalvm-" + contents.graalvmVersion())
                            .toString();
        }
        final String envFile = env.get("GITHUB_ENV");
        final String pathFile = env.get("GITHUB_PATH");
        if (github && (envFile == null || pathFile == null)) {
            // Refused before the download: a JDK installed and then not exported is a job that goes
            // on with whatever java the runner had, which is the thing this exists to end.
            complain.accept(
                    "--github needs GITHUB_ENV and GITHUB_PATH, which only a GitHub job sets");
            return 2;
        }
        final PinnedJdk.Installed installed =
                PinnedJdk.install(contents, java.nio.file.Path.of(into), download);
        if (github) {
            PinnedJdk.exportTo(
                    installed, java.nio.file.Path.of(envFile), java.nio.file.Path.of(pathFile));
        }
        System.out.println(
                "GraalVM "
                        + installed.version()
                        + " in "
                        + installed.home()
                        + ", checked against "
                        + contents.graalvmSha256()
                        + (github ? "; JAVA_HOME and PATH set for the rest of the job" : ""));
        return 0;
    }

    /**
     * Installs the musl toolchain the hooks link against, with the installer this build carries.
     *
     * <p>The one installer for every repository that builds the hooks, and the same one a snapshot
     * runs: a checkout that kept a copy of its own let the two drift. Where it installs is the
     * installer's own {@code MUSL_PREFIX}.
     *
     * @param args {@code musl}, and nothing else.
     * @param complain Where a refusal goes.
     * @param run Runs the installer's text with bash and returns its exit code.
     * @return The installer's exit code, or 2 for an option it does not take.
     * @throws IOException If the installer cannot be run.
     */
    static int musl(String[] args, Consumer<String> complain, Installer run) throws IOException {
        if (args.length > 1) {
            complain.accept(
                    "musl takes no option, not "
                            + args[1]
                            + "; where it installs is MUSL_PREFIX in the"
                            + " environment (default ~/.local/opt)");
            return 2;
        }
        return run.run(Snapshots.muslInstaller());
    }

    /** Runs an installer's text. */
    @FunctionalInterface
    interface Installer {

        /**
         * Runs it.
         *
         * @param script The installer.
         * @return Its exit code.
         * @throws IOException If it cannot be run.
         */
        int run(String script) throws IOException;
    }

    private static int runInBash(String script) throws IOException {
        // On standard input, so nothing is written to a file another account could swap before it
        // runs.
        final Process bash =
                new ProcessBuilder("bash", "-s")
                        .redirectOutput(ProcessBuilder.Redirect.INHERIT)
                        .redirectError(ProcessBuilder.Redirect.INHERIT)
                        .start();
        try (java.io.OutputStream in = bash.getOutputStream()) {
            in.write(script.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
        try {
            return bash.waitFor();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            bash.destroy();
            throw new IOException("interrupted while installing musl", ex);
        }
    }

    /**
     * Rents a machine and leaves it running, recording what it took.
     *
     * @param args {@code lease --os <os> [--key <file>] [--write <file>] [--type <t>] [--candidate
     *     <dir>] [--for <minutes>]}.
     * @param complain Where a refusal goes.
     * @param open How to reach the provider.
     * @return Exit code.
     * @throws IOException If a step fails.
     */
    private static int lease(String[] args, Consumer<String> complain, Supplier<Hetzner> open)
            throws IOException {
        String os = "ubuntu";
        String key = null;
        String write = null;
        String type = "cpx12";
        String artifactory = "https://fuinorg.jfrog.io/artifactory";
        String candidate = null;
        Duration limit = Hetzner.DEFAULT_TIME_LIMIT;
        for (int at = 1; at < args.length; at++) {
            switch (args[at]) {
                case "--os" -> os = value(args, ++at);
                case "--key" -> key = value(args, ++at);
                case "--for" -> {
                    final Duration given = timeLimit(value(args, ++at), complain);
                    if (given == null) {
                        return 2;
                    }
                    limit = given;
                }
                case "--write" -> write = value(args, ++at);
                case "--type" -> type = value(args, ++at);
                case "--artifactory" -> artifactory = value(args, ++at);
                case "--candidate" -> candidate = value(args, ++at);
                default -> {
                    complain.accept("unknown option: " + args[at]);
                    return 2;
                }
            }
        }
        final Rental.Options options =
                new Rental.Options(
                        os,
                        List.of(type),
                        artifactory,
                        write == null ? null : java.nio.file.Path.of(write),
                        candidate == null ? null : java.nio.file.Path.of(candidate));
        try (Hetzner hetzner = open.get().forJobsOf(limit)) {
            Rental.run(
                    hetzner,
                    options,
                    Credential.of(
                            System.getenv(SSH_KEY),
                            key == null ? null : java.nio.file.Path.of(key)));
        }
        return 0;
    }

    /**
     * Reads how long a job may hold its machine.
     *
     * <p>Minutes, as a workflow's {@code timeout-minutes} beside it says them, so the two can be
     * read against each other on one screen.
     *
     * @param minutes What {@code --for} was given.
     * @param complain Where a refusal goes.
     * @return The limit, or {@code null} when it was refused.
     */
    static @Nullable Duration timeLimit(String minutes, Consumer<String> complain) {
        if (!minutes.matches("[1-9]\\d{0,2}") || Integer.parseInt(minutes) > LONGEST_TIME_LIMIT) {
            complain.accept(
                    "--for needs a number of minutes from 1 to "
                            + LONGEST_TIME_LIMIT
                            + ", not '"
                            + minutes
                            + "'");
            return null;
        }
        return Duration.ofMinutes(Integer.parseInt(minutes));
    }

    /**
     * Returns an option's value, which is the next word.
     *
     * <p>Refused rather than read as {@code null}: an option given last with nothing after it would
     * otherwise replace its default with nothing and fail somewhere that does not name it.
     *
     * @param args The command line.
     * @param at Where the value should be.
     * @return The value.
     * @throws IllegalArgumentException If there is none.
     */
    static String value(String[] args, int at) {
        if (at >= args.length) {
            throw new IllegalArgumentException("option " + args[at - 1] + " needs a value");
        }
        return args[at];
    }

    /**
     * Returns a tar of what is checked out.
     *
     * <p>{@code git archive} of the working tree rather than a clone: it sends exactly what is
     * here, which is what somebody testing a change needs.
     *
     * @param root The repository.
     * @return A temporary tar, which the caller may leave for the system to clean up.
     * @throws IOException If it cannot be made.
     */
    private static java.nio.file.Path archiveOf(java.nio.file.Path root) throws IOException {
        final java.nio.file.Path archive =
                java.nio.file.Files.createTempFile("porelo-tree", ".tar");
        final Process tar =
                new ProcessBuilder("git", "archive", "--format=tar", "HEAD")
                        .directory(root.toFile())
                        .redirectOutput(archive.toFile())
                        .redirectError(ProcessBuilder.Redirect.INHERIT)
                        .start();
        try {
            if (tar.waitFor() != 0) {
                throw new IOException("could not archive the working tree at " + root);
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IOException("interrupted archiving the working tree", ex);
        }
        return archive;
    }

    /**
     * Where a complaint goes when this is run as a command.
     *
     * <p><strong>Supplied by the caller, not chosen here.</strong> The {@code ::error::} prefix is
     * a workflow command and GitHub turns any line carrying one into an annotation - including a
     * line a unit test caused while checking that a bad option is refused. Gating on {@code
     * GITHUB_ACTIONS} does not help, because the tests run inside CI too, where the variable is
     * set: it made the annotations survive and a test assert on ambient state. A test passes its
     * own sink and prints nothing.
     */
    private static final Consumer<String> COMPLAIN =
            message ->
                    System.err.println(
                            System.getenv("GITHUB_ACTIONS") == null
                                    ? "porelo: " + message
                                    : "::error::" + message);

    /**
     * Returns the API token, or refuses when there is none.
     *
     * <p>From the environment and never an argument: {@code /proc/<pid>/cmdline} is world readable,
     * and neither supported distribution mounts {@code /proc} with {@code hidepid}.
     *
     * <p><strong>Taken as a parameter rather than read here.</strong> A test that called the
     * version which read the environment itself passed on a developer's machine, where nothing sets
     * the token, and on CI - where the workflow does set it - authenticated against the real
     * project and swept the server the build was running on. A function that reads ambient state
     * cannot be tested for what it does when that state is absent.
     *
     * @param fromEnvironment What the environment holds, or {@code null}.
     * @return The token.
     */
    static String token(String fromEnvironment) {
        if (fromEnvironment == null || fromEnvironment.isBlank()) {
            throw new IllegalStateException(
                    "No API token: set " + API_TOKEN + " in the environment.");
        }
        return fromEnvironment;
    }

    /**
     * Returns what identifies this run.
     *
     * <p>The workflow run in CI; a timestamp locally, which is enough to tell two developers apart
     * and does not pretend to be more. Both legs of a matrix share the run, so the leg is what
     * makes a server's label unique - and a sweep deletes by that label.
     *
     * <p><strong>The leg is carried whether or not there is a workflow run.</strong> It used to be
     * folded in only in CI, so a run started by hand produced {@code local-<millis>}: unique, which
     * is what the timestamp was written for, but saying nothing about whose it was. Several agents
     * share one Hetzner account, and a machine nobody can attribute is one nobody dares delete and
     * one anybody may delete by mistake. Uniqueness answers "is this the same run"; attribution
     * answers "is this yours", and only the second makes a shared account safe.
     *
     * @param run The workflow run, or {@code null} outside CI.
     * @param leg Which leg of the matrix, or {@code null}.
     * @return The run id.
     */
    static String runId(String run, String leg) {
        final String named = leg == null || leg.isBlank() ? "" : "-" + leg;
        if (run != null && !run.isBlank()) {
            return run + named;
        }
        return "local-" + System.currentTimeMillis() + named;
    }
}
