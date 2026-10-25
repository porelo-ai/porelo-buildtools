package org.fuin.porelo.machines;

/** The capacity and lifecycle a caller needs, independent of where it is supplied. */
public enum Kind {
    /** One acceptance account or a leased machine. */
    AMD64("amd64"),
    /** Several acceptance accounts or a native-image build. */
    AMD64_BUILD("amd64-build"),
    /** A machine from which a bootable snapshot can be built. */
    AMD64_SNAPSHOT("amd64-snapshot");

    private final String externalName;

    Kind(String externalName) {
        this.externalName = externalName;
    }

    /**
     * Reads the name used by the command line.
     *
     * @param name The external name.
     * @return The requested kind.
     * @throws IllegalArgumentException If the name is unknown.
     */
    public static Kind of(String name) {
        for (final Kind kind : values()) {
            if (kind.externalName.equals(name)) {
                return kind;
            }
        }
        throw new IllegalArgumentException(
                "Unknown machine kind '" + name + "'. Known: amd64, amd64-build, amd64-snapshot.");
    }

    /** Returns the external name. @return The command-line name. */
    @Override
    public String toString() {
        return externalName;
    }
}
