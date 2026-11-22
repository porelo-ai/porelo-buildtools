package org.fuin.porelo.machines;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * What to rent, including the capacity needed by the accounts sharing it.
 *
 * @param name A name unique to this run.
 * @param os The operating system.
 * @param kind The capacity and lifecycle needed.
 * @param user Who to connect as.
 * @param credential The connection key.
 * @param keep Whether to leave it running.
 * @param accounts How many accounts share the machine.
 * @param providerOverride Explicit types to try in order, as {@code --type} names them; empty
 *     selects by kind.
 */
public record Spec(
        String name,
        String os,
        Kind kind,
        String user,
        Credential credential,
        boolean keep,
        int accounts,
        List<String> providerOverride) {

    /**
     * Validates capacity and fixes the provider override against later mutation.
     *
     * @param name The name.
     * @param os The operating system.
     * @param kind The capacity and lifecycle.
     * @param user The connection account.
     * @param credential The connection key.
     * @param keep Whether to keep the machine.
     * @param accounts How many accounts share it.
     * @param providerOverride Explicit types to try in order, or empty to select by kind.
     */
    public Spec {
        Objects.requireNonNull(kind, "A machine kind is required.");
        if (accounts < 1) {
            throw new IllegalArgumentException(
                    "A machine needs at least one account, not " + accounts);
        }
        providerOverride = List.copyOf(providerOverride);
    }

    /**
     * Requests one account by kind.
     *
     * @param name The name.
     * @param os The operating system.
     * @param kind The capacity and lifecycle.
     * @param user The connection account.
     * @param credential The key.
     * @param keep Whether to keep the machine.
     */
    public Spec(
            String name, String os, Kind kind, String user, Credential credential, boolean keep) {
        this(name, os, kind, user, credential, keep, 1, List.of());
    }

    /** Maps an explicit type order onto the capacity it requests. */
    static Kind kindOf(List<String> types) {
        return CentralSetup.kindOf(types);
    }

    /**
     * Requests a machine by kind without keeping it.
     *
     * @param name The name.
     * @param os The operating system.
     * @param kind The capacity and lifecycle.
     * @param user The connection account.
     * @param credential The key.
     * @return The request.
     */
    public static Spec of(String name, String os, Kind kind, String user, Credential credential) {
        return new Spec(name, os, kind, user, credential, false);
    }

    /** Reads the comma-separated {@code --type} option, blank for the build kind's types. */
    static List<String> order(String types) {
        if (types == null || types.isBlank()) {
            return CentralSetup.DEFAULT_TYPES;
        }
        final List<String> wanted = new ArrayList<>();
        for (final String each : types.split(",")) {
            if (!each.isBlank()) {
                wanted.add(each.strip());
            }
        }
        return wanted.isEmpty() ? CentralSetup.DEFAULT_TYPES : List.copyOf(wanted);
    }

    /**
     * Requests capacity for several accounts.
     *
     * @param count How many accounts share it.
     * @return The request with that account count.
     */
    public Spec forAccounts(int count) {
        return new Spec(name, os, kind, user, credential, keep, count, providerOverride);
    }

    /** Returns the request left running afterwards. @return The kept request. */
    public Spec kept() {
        return new Spec(name, os, kind, user, credential, true, accounts, providerOverride);
    }
}
