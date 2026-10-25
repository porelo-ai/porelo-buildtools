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
 * @param providerOverride Explicit types retained for compatibility; empty selects by kind.
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
     * @param providerOverride Compatibility types, or empty to select by kind.
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

    /**
     * Requests a machine using the previous type-based API.
     *
     * @param name The name.
     * @param os The operating system.
     * @param serverTypes Explicit types to try, in order.
     * @param user The connection account.
     * @param credential The key.
     * @param keep Whether to keep the machine.
     * @deprecated Use {@link #Spec(String, String, Kind, String, Credential, boolean)}.
     */
    @Deprecated(forRemoval = true)
    public Spec(
            String name,
            String os,
            List<String> serverTypes,
            String user,
            Credential credential,
            boolean keep) {
        this(name, os, kindOf(serverTypes), user, credential, keep, 1, serverTypes);
    }

    /** Maps a previous type order onto the capacity it requested. */
    static Kind kindOf(List<String> types) {
        return CentralSetup.kindOf(types);
    }

    /**
     * The previous build-leg type order.
     *
     * @deprecated Request {@link Kind#AMD64_BUILD} instead.
     */
    @Deprecated(forRemoval = true)
    public static final List<String> DEFAULT_TYPES = CentralSetup.DEFAULT_TYPES;

    /**
     * Returns the previous explicit type order, or the kind's defaults for a new request.
     *
     * @return The types to try.
     * @deprecated Use {@link #kind()} and let the setup choose its capacity.
     */
    @Deprecated(forRemoval = true)
    public List<String> serverTypes() {
        return providerOverride.isEmpty()
                ? CentralSetup.typesFor(kind, accounts)
                : providerOverride;
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

    /**
     * Requests the previous default build capacity.
     *
     * @param name The name.
     * @param os The operating system.
     * @param user The connection account.
     * @param credential The key.
     * @return The request.
     * @deprecated Use {@link #of(String, String, Kind, String, Credential)}.
     */
    @Deprecated(forRemoval = true)
    public static Spec of(String name, String os, String user, Credential credential) {
        return new Spec(name, os, DEFAULT_TYPES, user, credential, false);
    }

    /**
     * Retains an explicit type order for a previous caller.
     *
     * @param types Types to try, in order.
     * @return The mapped request with its explicit override.
     * @deprecated Request a {@link Kind} with {@link #of(String, String, Kind, String,
     *     Credential)}.
     */
    @Deprecated(forRemoval = true)
    public Spec tryingInOrder(List<String> types) {
        return new Spec(name, os, kindOf(types), user, credential, keep, accounts, types);
    }

    /**
     * Reads the previous comma-separated type option.
     *
     * @param types The type order, or blank for the previous default.
     * @return The types in order.
     * @deprecated Read a kind with {@link Kind#of(String)} instead.
     */
    @Deprecated(forRemoval = true)
    public static List<String> order(String types) {
        if (types == null || types.isBlank()) {
            return DEFAULT_TYPES;
        }
        final List<String> wanted = new ArrayList<>();
        for (final String each : types.split(",")) {
            if (!each.isBlank()) {
                wanted.add(each.strip());
            }
        }
        return wanted.isEmpty() ? DEFAULT_TYPES : List.copyOf(wanted);
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
