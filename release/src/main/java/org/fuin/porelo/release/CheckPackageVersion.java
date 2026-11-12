package org.fuin.porelo.release;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 * Whether the {@code .deb} and the {@code .rpm} a build made carry the project's version, read from
 * the packages.
 *
 * <p>jdeb and the rpm plugin each take their version from their own configuration, and a plugin may
 * take it from a property that means something else: a test of the pom would not see that, a look
 * into the built package does. The version expected is the project's, mapped as the packaging
 * promises: {@code 0.4.1-SNAPSHOT} built as run 7 is {@code 0.4.1~snapshot.7}, so a snapshot sorts
 * below its release and each run above the last; a release is its own version. The rpm's release is
 * always {@code 1}, so the rpm and the deb say the same version.
 */
final class CheckPackageVersion {

    /** Reads a built package's own fields. */
    interface PackageMetadata {

        /** The real {@code dpkg-deb} and {@code rpm}. */
        PackageMetadata TOOLS =
                new PackageMetadata() {
                    @Override
                    public String debField(Path deb, String field) throws IOException {
                        return run("dpkg-deb", "-f", deb.toString(), field);
                    }

                    @Override
                    public String rpmFields(Path rpm) throws IOException {
                        return run(
                                "rpm",
                                "-qp",
                                "--queryformat",
                                "%{NAME} %{VERSION} %{RELEASE}",
                                rpm.toString());
                    }
                };

        /**
         * Reads one field of a deb.
         *
         * @param deb the package
         * @param field the field, such as {@code Version}
         * @return the field's value
         * @throws IOException when the package cannot be read
         */
        String debField(Path deb, String field) throws IOException;

        /**
         * Reads an rpm's name, version and release.
         *
         * @param rpm the package
         * @return the three, separated by spaces
         * @throws IOException when the package cannot be read
         */
        String rpmFields(Path rpm) throws IOException;
    }

    private final PrintStream out;

    private final PrintStream err;

    private final PackageMetadata metadata;

    /**
     * Reads the packages with the real tools.
     *
     * @param out where the result goes
     * @param err where a refusal goes
     */
    CheckPackageVersion(PrintStream out, PrintStream err) {
        this(out, err, PackageMetadata.TOOLS);
    }

    /**
     * Reads the packages with the metadata given.
     *
     * @param out where the result goes
     * @param err where a refusal goes
     * @param metadata what reads each package's fields
     */
    CheckPackageVersion(PrintStream out, PrintStream err, PackageMetadata metadata) {
        this.out = out;
        this.err = err;
        this.metadata = metadata;
    }

    /**
     * Checks the newest {@code .deb} and {@code .rpm} of a name in a directory.
     *
     * @param name The package's name.
     * @param project The project's version, as Maven has it.
     * @param run The build's run number.
     * @param directory Where the build wrote the packages.
     * @return 0 when both carry the version, {@link Stop#REFUSED} otherwise, {@link
     *     Stop#UNANSWERED} when a package is missing or cannot be read
     */
    int check(String name, String project, String run, Path directory) {
        final String expected = packageVersion(project, run);
        final Optional<Path> deb;
        final Optional<Path> rpm;
        try {
            deb = newest(directory, name + "_", ".deb");
            rpm = newest(directory, name + "-", ".rpm");
        } catch (IOException ex) {
            err.println("could not list " + directory + ": " + ex.getMessage());
            return Stop.UNANSWERED;
        }
        if (deb.isEmpty() || rpm.isEmpty()) {
            err.println(
                    "no "
                            + name
                            + " .deb and .rpm in "
                            + directory
                            + " - built by the dist profile?");
            return Stop.UNANSWERED;
        }
        final List<String> problems = new ArrayList<>();
        try {
            final String debName = metadata.debField(deb.get(), "Package").strip();
            final String debVersion = metadata.debField(deb.get(), "Version").strip();
            final String rpmFields = metadata.rpmFields(rpm.get()).strip();
            problems.addAll(problems(name, expected, debName, debVersion, rpmFields));
        } catch (IOException ex) {
            err.println("could not read the packages: " + ex.getMessage());
            return Stop.UNANSWERED;
        }
        if (!problems.isEmpty()) {
            problems.forEach(problem -> err.println("FAIL  " + problem));
            return Stop.REFUSED;
        }
        out.println(
                "OK    "
                        + deb.get().getFileName()
                        + " and "
                        + rpm.get().getFileName()
                        + " carry "
                        + expected);
        return 0;
    }

    /**
     * Returns every way the packages' own fields differ from what they must say.
     *
     * @param name The package's name.
     * @param expected The version both must carry.
     * @param debName The deb's {@code Package} field.
     * @param debVersion The deb's {@code Version} field.
     * @param rpmFields The rpm's name, version and release, separated by spaces.
     * @return Problems, empty when both packages are right.
     */
    static List<String> problems(
            String name, String expected, String debName, String debVersion, String rpmFields) {
        final List<String> problems = new ArrayList<>();
        if (!name.equals(debName)) {
            problems.add("the deb is called '" + debName + "', not '" + name + "'");
        }
        if (!expected.equals(debVersion)) {
            problems.add("the deb is version " + debVersion + ", not " + expected);
        }
        final String wanted = name + " " + expected + " 1";
        if (!wanted.equals(rpmFields)) {
            problems.add("the rpm is '" + rpmFields + "', not '" + wanted + "'");
        }
        return problems;
    }

    /**
     * Maps a project version to the version its packages carry.
     *
     * @param project The project's version, as Maven has it.
     * @param run The build's run number.
     * @return The package version.
     */
    static String packageVersion(String project, String run) {
        return project.endsWith("-SNAPSHOT")
                ? project.replaceFirst("-SNAPSHOT$", "~snapshot." + run)
                : project;
    }

    private static Optional<Path> newest(Path directory, String prefix, String suffix)
            throws IOException {
        try (Stream<Path> files = Files.list(directory)) {
            return files.filter(
                            file ->
                                    file.getFileName().toString().startsWith(prefix)
                                            && file.getFileName().toString().endsWith(suffix))
                    .max(Comparator.comparingLong(file -> file.toFile().lastModified()));
        }
    }

    private static String run(String... command) throws IOException {
        final ProcessBuilder builder = new ProcessBuilder(command);
        builder.environment().put("LC_ALL", "C");
        // To files rather than pipes: reading a pipe to its end would wait out a hang, not the
        // timeout. rpm warns on stderr about a database a query of a package file does not need.
        final Path output = Files.createTempFile("package-version", ".out");
        final Path errors = Files.createTempFile("package-version", ".err");
        try {
            final Process process =
                    builder.redirectOutput(output.toFile()).redirectError(errors.toFile()).start();
            if (!process.waitFor(30, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                throw new IOException(List.of(command) + " did not finish within 30 seconds");
            }
            final String text = Files.readString(output, StandardCharsets.UTF_8);
            if (process.exitValue() != 0) {
                throw new IOException(
                        List.of(command)
                                + ": "
                                + text
                                + Files.readString(errors, StandardCharsets.UTF_8));
            }
            return text;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IOException("interrupted", ex);
        } finally {
            Files.deleteIfExists(output);
            Files.deleteIfExists(errors);
        }
    }
}
