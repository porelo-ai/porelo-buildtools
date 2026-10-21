package org.fuin.porelo.release;

import java.io.IOException;
import java.io.PrintStream;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import org.cyclonedx.model.Bom;
import org.jspecify.annotations.Nullable;

/**
 * Adds the CLI an agent's image build fetches to the package's bill.
 *
 * <p>The package does not contain that CLI, so nothing reading the Maven graph knows about it - and
 * a bill that omits the largest thing an image installs is worse than none. It is read from the
 * filtered definition, the file the agent answers {@code describe} with, so the version and digest
 * recorded are the ones an image build uses.
 */
public final class AddFetchedCli {

    /** The bill now names the CLI, or the definition pins none. */
    public static final int DONE = 0;

    /** The bill could not be changed; the build must not go on with the one it has. */
    public static final int FAILED = 1;

    private final PrintStream out;

    private final PrintStream err;

    /**
     * A command that reports on two streams.
     *
     * @param out where the result goes
     * @param err where a failure is explained
     */
    public AddFetchedCli(PrintStream out, PrintStream err) {
        this.out = Objects.requireNonNull(out, "out");
        this.err = Objects.requireNonNull(err, "err");
    }

    /**
     * Records the pinned CLI in the bill, in place.
     *
     * @param bill the package's bill, rewritten with the CLI added
     * @param definition the filtered agent definition
     * @param name the component name - what {@code compare-bills --expect-moved} must be given
     * @return {@link #DONE} or {@link #FAILED}
     */
    public int add(Path bill, Path definition, String name) {
        return add(bill, definition, name, null);
    }

    /**
     * Records one of the pinned artifacts in the bill, in place: the one whose digest is given, as
     * a named pin's {@code sha256} property holds it, so a CLI downloaded as several artifacts is
     * recorded one call per artifact.
     *
     * @param bill the package's bill, rewritten with the artifact added
     * @param definition the filtered agent definition
     * @param name the component name - what {@code compare-bills --expect-moved} must be given
     * @param sha256 the digest of the artifact to record, or null when the definition pins one
     * @return {@link #DONE} or {@link #FAILED}
     */
    public int add(Path bill, Path definition, String name, @Nullable String sha256) {
        final Pinned agent;
        try (Reader reader = Files.newBufferedReader(definition)) {
            agent = Pinned.read(reader, definition.toString());
        } catch (IOException | Pinned.Unreadable ex) {
            return failed(
                    "cannot read the agent definition " + definition + ": " + ex.getMessage());
        }
        if (agent.version() == null || agent.artifacts().isEmpty()) {
            out.println(
                    "add-fetched-cli: no pinned artifact in " + definition + ", nothing to add");
            return DONE;
        }
        final Pinned.Artifact artifact;
        if (sha256 != null) {
            final List<Pinned.Artifact> named = agent.withDigest(sha256);
            if (named.size() != 1) {
                return failed(
                        definition
                                + " pins "
                                + named.size()
                                + " artifacts with the digest "
                                + sha256
                                + "; exactly one must carry it");
            }
            artifact = named.getFirst();
        } else if (agent.artifacts().size() > 1) {
            return failed(
                    definition
                            + " pins "
                            + agent.artifacts().size()
                            + " artifacts, and which one is the CLI is not something to guess:"
                            + " name it with --pin");
        } else {
            artifact = agent.artifacts().getFirst();
        }
        final boolean replaced;
        try {
            final Bom bom = Bill.parse(Files.readAllBytes(bill));
            replaced =
                    Recorded.into(
                            bom,
                            Recorded.component(
                                    name,
                                    agent.version(),
                                    artifact.url(),
                                    artifact.sha256(),
                                    Recorded.FETCHED,
                                    artifact.license()));
            Bill.write(bom, bill);
        } catch (IOException | IllegalArgumentException | IllegalStateException ex) {
            return failed("cannot add the CLI to " + bill + ": " + ex.getMessage());
        }
        out.println(
                "add-fetched-cli: recorded "
                        + name
                        + " "
                        + agent.version()
                        + " as fetched, not shipped"
                        + (replaced ? ", in place of the one recorded before" : ""));
        return DONE;
    }

    private int failed(String reason) {
        err.println("add-fetched-cli: " + reason);
        return FAILED;
    }
}
