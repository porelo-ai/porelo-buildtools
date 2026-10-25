package org.fuin.porelo.machines;

import java.io.IOException;
import java.time.Duration;

/**
 * Somewhere a test machine comes from.
 *
 * <p>A setup rents a cloud server or supplies an account on a local virtual machine. Callers
 * request a kind and hold the resulting lease for as long as they need it.
 *
 * <p>The half that is genuinely the same either way - waiting for ssh, running commands, putting
 * the agent under test on the machine - is on {@link Lease} and written once.
 */
public interface Machines extends AutoCloseable {

    /** How long a job holds a machine unless it says otherwise. */
    Duration DEFAULT_TIME_LIMIT = Duration.ofMinutes(60);

    /** The label that keeps an active machine out of the expiry sweep. */
    String EXPIRES_LABEL = "porelo-expires";

    /**
     * Opens the central source of machines; local pools are configured with {@link LocalPool}.
     *
     * @param token The API token, supplied through the environment.
     * @param runId Identifies this run for cleanup.
     * @return The configured source.
     */
    static Machines setup(String token, String runId) {
        return Api.machines(token, runId);
    }

    /**
     * Uses this source for jobs with the given time limit.
     *
     * @param limit A positive time limit.
     * @return The source for that limit; closing either closes the shared connection.
     */
    default Machines forJobsOf(Duration limit) {
        if (limit.isNegative() || limit.isZero()) {
            throw new IllegalArgumentException("a time limit must be positive, not " + limit);
        }
        return this;
    }

    /**
     * Deletes what this run created.
     *
     * @return The number deleted.
     * @throws IOException If cleanup is unsupported or fails.
     */
    default int deleteMine() throws IOException {
        return deleteRun(runId());
    }

    /**
     * Deletes what the named run created.
     *
     * @param run The run to clean up.
     * @return The number deleted.
     * @throws IOException If cleanup is unsupported or fails.
     */
    default int deleteRun(String run) throws IOException {
        throw new IOException(
                "This machine source cannot delete a run; close its leases to return them.");
    }

    /**
     * Sweeps machines past their expiry.
     *
     * @param includingInUse Whether to include machines still in use.
     * @param dryRun Whether to only list them.
     * @return The number found or deleted.
     * @throws IOException If sweeping is unsupported or fails.
     */
    default int sweep(boolean includingInUse, boolean dryRun) throws IOException {
        throw new IOException(
                "This machine source cannot sweep machines; close its leases to return them.");
    }

    /**
     * Stops a machine before its snapshot is taken.
     *
     * @param id The machine.
     * @throws IOException If snapshots are unsupported or stopping fails.
     */
    default void shutdown(long id) throws IOException {
        throw new IOException("This machine source cannot stop machines for amd64-snapshot.");
    }

    /**
     * Takes a snapshot of a stopped machine.
     *
     * @param id The machine.
     * @param description The snapshot's name.
     * @param os The operating system.
     * @param inputs The digest of its build inputs.
     * @return The snapshot id.
     * @throws IOException If snapshots are unsupported or creation fails.
     */
    default long snapshot(long id, String description, String os, String inputs)
            throws IOException {
        throw new IOException("This machine source cannot create an amd64-snapshot image.");
    }

    /**
     * Checks whether the newest snapshot was built from these inputs.
     *
     * @param os The operating system.
     * @param inputs The digest of the build inputs.
     * @return Whether it is current.
     * @throws IOException If snapshots are unsupported or the check fails.
     */
    default boolean snapshotCurrent(String os, String inputs) throws IOException {
        throw new IOException("This machine source cannot inspect amd64-snapshot images.");
    }

    /**
     * Gets a machine, ready to be connected to.
     *
     * @param spec What is wanted.
     * @return The machine, which must be closed.
     * @throws IOException If there is none to be had.
     */
    Lease acquire(Spec spec) throws IOException;

    /**
     * Gets a machine built from a named stock image rather than from one of ours.
     *
     * <p>A cloud concept: somewhere that creates machines can be told what to create them from.
     * Somewhere that already has them cannot, and says so by ignoring the name.
     *
     * @param spec What is wanted.
     * @param image A stock image name, such as {@code ubuntu-26.04}.
     * @return The machine, which must be closed.
     * @throws IOException If there is none to be had.
     */
    Lease acquireFromStock(Spec spec, String image) throws IOException;

    /**
     * Returns what identifies this run, for naming and labelling what it creates.
     *
     * @return The run id.
     */
    String runId();

    /** Closes the shared connection; each lease must be returned separately. */
    @Override
    void close();
}
