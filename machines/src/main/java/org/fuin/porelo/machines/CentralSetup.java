package org.fuin.porelo.machines;

import java.util.List;

/** What the central setup's provider offers, and the names its previous callers used. */
final class CentralSetup {

    /** Previous credentials remain usable while callers move to neutral names. */
    static final String OLD_API_TOKEN = "HETZNER_API";

    /** Previous connection keys follow the same fallback as the API credential. */
    static final String OLD_SSH_KEY = "HETZNER_SSH";

    /** Build-leg capacity, ordered by preferred performance and then adequate fallback. */
    static final List<String> DEFAULT_TYPES = List.of("cpx42", "cx43", "cpx32", "cx33");

    /** A snapshot's disk sets the floor for every machine that restores it. */
    static final List<String> BUILD_TYPES = List.of("cx23", "cpx22", "cx33");

    /** One task at a time fits without paying for build capacity. */
    private static final List<String> SINGLE_TYPES = List.of("cpx12");

    static Kind kindOf(List<String> types) {
        if (types == null || types.isEmpty()) {
            throw new IllegalArgumentException("Name at least one server type to try.");
        }
        if (types.equals(SINGLE_TYPES)) {
            return Kind.AMD64;
        }
        return types.equals(BUILD_TYPES) ? Kind.AMD64_SNAPSHOT : Kind.AMD64_BUILD;
    }

    static List<String> typesFor(Kind kind, int accounts) {
        return switch (kind) {
            case AMD64 -> accounts > 1 ? DEFAULT_TYPES : SINGLE_TYPES;
            case AMD64_BUILD -> DEFAULT_TYPES;
            case AMD64_SNAPSHOT -> BUILD_TYPES;
        };
    }

    private CentralSetup() {
        throw new UnsupportedOperationException("Utility class");
    }
}
