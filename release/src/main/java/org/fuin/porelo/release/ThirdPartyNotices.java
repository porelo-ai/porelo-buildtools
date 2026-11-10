package org.fuin.porelo.release;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.io.StringReader;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.fuin.porelo.json.Json;
import org.fuin.porelo.json.JsonException;
import org.jspecify.annotations.Nullable;

/**
 * Writes a package's third-party notices from the jars compiled into its native executable and the
 * runtime the executable carries.
 *
 * <p>Each jar is one section headed by its Maven coordinates. It holds the standard text of each
 * license and exception its CycloneDX bill names - the effective POM's, parents' included - and, in
 * full, every file the jar carries named license, notice or copying, and every file under a {@code
 * license} or {@code licenses} directory: those are where a jar names what it embeds, such as
 * jackson-core's FastDoubleParser or plexus-utils' Extreme! Lab code. A jar the bill has no entry
 * for, names no license for, or names one the pinned standard texts do not hold, is refused. The
 * runtime is one more section, from the JDK's own license files and its whole {@code legal/}
 * directory: which modules an image reaches no build output says, and naming every one is never
 * less than the image needs. A text that stood once is named, not repeated. The same input gives
 * the same bytes.
 */
final class ThirdPartyNotices {

    /** The SPDX license list release the standard texts are taken from. */
    static final String LICENSE_LIST = "3.29.0";

    /**
     * The licenses whose standard texts are pinned, by SPDX id, each to the texts it consists of.
     */
    static final Map<String, List<String>> STANDARD =
            Map.ofEntries(
                    Map.entry("Apache-2.0", List.of("Apache-2.0")),
                    Map.entry("BSD-2-Clause", List.of("BSD-2-Clause")),
                    Map.entry("BSD-3-Clause", List.of("BSD-3-Clause")),
                    Map.entry("EPL-1.0", List.of("EPL-1.0")),
                    Map.entry("EPL-2.0", List.of("EPL-2.0")),
                    Map.entry("GPL-2.0-only", List.of("GPL-2.0-only")),
                    Map.entry(
                            "GPL-2.0-with-classpath-exception",
                            List.of("GPL-2.0-only", "Classpath-exception-2.0")),
                    Map.entry("GPL-3.0-or-later", List.of("GPL-3.0")),
                    Map.entry("LGPL-2.1-only", List.of("LGPL-2.1-only")),
                    // LGPL-3.0 adds permissions to the GPL-3.0, whose text ships with it.
                    Map.entry("LGPL-3.0-or-later", List.of("LGPL-3.0", "GPL-3.0")),
                    Map.entry("MIT", List.of("MIT")));

    /** The license exceptions whose standard texts are pinned, by SPDX id. */
    static final Set<String> EXCEPTIONS = Set.of("Classpath-exception-2.0");

    /** The LGPL library no GPL-3.0 component may sit below, by {@code groupId:artifactId}. */
    private static final String STORIES = "org.fuin.porelo:porelo-stories";

    /** A text larger than this is no license file, and is refused rather than read. */
    private static final int MOST_BYTES = 1 << 20;

    /** The word as a whole name part: {@code LICENSE.txt}, {@code FastDoubleParser-LICENSE}. */
    private static final Pattern NAME =
            Pattern.compile("(?i)(.*[-_.])?(licen[cs]es?|notices?|copying)([-_.].*)?");

    /**
     * A Maven classifier after the version in a jar's file name, such as {@code linux-x86_64}: it
     * begins with a letter, so a version's own tail ({@code -1}) is none, and {@code SNAPSHOT} is a
     * version's too.
     */
    private static final Pattern CLASSIFIER =
            Pattern.compile("(?!(?i)SNAPSHOT$)[A-Za-z][A-Za-z0-9_.-]*(?<!-)");

    private static final Pattern DIRECTORY = Pattern.compile("(?i)(.*/)?licen[cs]es?/[^/]+");

    private static final Pattern NO_TEXT =
            Pattern.compile("(?i).*\\.(class|java|properties|png|jpe?g|gif|svg|ico)");

    private static final String RULE = "=".repeat(78);

    /**
     * What a bill says of one component.
     *
     * @param licenses the SPDX ids, or names where the bill has no id
     * @param exceptions the SPDX ids named after {@code WITH}
     * @param unread what the bill names of a license that tells none, such as a URL alone
     * @param sha256 the jar's SHA-256 in lower-case hex, or {@code null} when the bill carries none
     */
    record Component(
            List<String> licenses,
            List<String> exceptions,
            List<String> unread,
            @Nullable String sha256) {

        /**
         * A component named by license ids alone.
         *
         * @param licenses the SPDX ids
         * @param sha256 the jar's SHA-256, or {@code null}
         */
        Component(List<String> licenses, @Nullable String sha256) {
            this(licenses, List.of(), List.of(), sha256);
        }
    }

    private record Made(String text, int written, int own) {}

    private record Text(String origin, String body) {}

    private record Section(String heading, @Nullable String license, List<Text> texts) {}

    private final PrintStream out;

    private final PrintStream err;

    /**
     * Constructor.
     *
     * @param out where the result goes
     * @param err where a refusal goes
     */
    ThirdPartyNotices(PrintStream out, PrintStream err) {
        this.out = out;
        this.err = err;
    }

    /**
     * Writes the notices of a classpath and a JDK into a file.
     *
     * @param classpath a file naming the jars, separated by lines or the path separator, as {@code
     *     dependency:build-classpath} writes it
     * @param bill the build's CycloneDX bill in JSON
     * @param jdk the JDK the executable is built with
     * @param target the file to write
     * @param own the project's own modules, matched against {@code groupId:artifactId}
     * @return 0 when written, {@link Stop#REFUSED} when a jar's license cannot be told, {@link
     *     Stop#UNANSWERED} when an input cannot be read
     */
    int write(Path classpath, Path bill, Path jdk, Path target, Pattern own) {
        try {
            final List<Path> jars = classpath(classpath);
            final Made made = make(jars, licenses(bill), jdk, own);
            final String text = made.text();
            final Path temporary =
                    Files.createTempFile(parent(target), ".third-party-notices", ".tmp");
            try {
                Files.writeString(temporary, text, UTF_8);
                // A temporary file is the owner's alone; the notice is for everyone the package
                // reaches.
                Files.setPosixFilePermissions(
                        temporary,
                        java.nio.file.attribute.PosixFilePermissions.fromString("rw-r--r--"));
                Files.move(
                        temporary,
                        target,
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } finally {
                Files.deleteIfExists(temporary);
            }
            out.println(
                    "OK    "
                            + target
                            + " holds the notices of "
                            + made.written()
                            + " jar(s) and the runtime; "
                            + made.own()
                            + " of the project's own left out");
            return 0;
        } catch (Stop stop) {
            err.println("FAIL  " + stop.getMessage());
            return stop.code();
        } catch (IOException ex) {
            err.println("could not write " + target + ": " + ex.getMessage());
            return Stop.UNANSWERED;
        }
    }

    /**
     * Reads a classpath file.
     *
     * @param file the file
     * @return the entries, in the file's order
     * @throws Stop when it cannot be read or names nothing
     */
    static List<Path> classpath(Path file) throws Stop {
        final String text;
        try {
            text = Files.readString(file, UTF_8);
        } catch (IOException ex) {
            throw Stop.unanswered("could not read the classpath " + file + ": " + ex.getMessage());
        }
        final List<Path> entries = new ArrayList<>();
        for (String entry : text.split("[\\r\\n" + Pattern.quote(File.pathSeparator) + "]")) {
            if (!entry.isBlank()) {
                entries.add(Path.of(entry.strip()));
            }
        }
        if (entries.isEmpty()) {
            throw Stop.unanswered("the classpath " + file + " names no entry");
        }
        return entries;
    }

    /**
     * Reads the licenses a CycloneDX bill names for each component.
     *
     * @param bill the bill, in JSON
     * @return the components, by {@code groupId:artifactId:version}, with {@code :classifier} where
     *     the component's purl names one
     * @throws Stop when the bill cannot be read, or names one component twice, differently
     */
    static Map<String, Component> licenses(Path bill) throws Stop {
        final Object parsed;
        try {
            parsed = Json.parse(Files.readString(bill, UTF_8));
        } catch (IOException | JsonException ex) {
            throw Stop.unanswered("could not read the bill " + bill + ": " + ex.getMessage());
        }
        if (!(parsed instanceof Map<?, ?> document)
                || !"CycloneDX".equals(document.get("bomFormat"))) {
            throw Stop.unanswered(bill + " is no CycloneDX bill in JSON");
        }
        final Map<String, Component> licenses = new HashMap<>();
        components(document.get("components"), licenses);
        refuseGplBelowStories(document);
        return licenses;
    }

    /**
     * Refuses a GPL-3.0 component porelo-stories reaches in the bill's dependency graph: the
     * library is LGPL so that others may link it, and a GPL jar below it would take that away.
     */
    private static void refuseGplBelowStories(Map<?, ?> document) throws Stop {
        final Map<String, String> keys = new HashMap<>();
        final Map<String, List<String>> named = new HashMap<>();
        refs(document.get("components"), keys, named);
        final Map<String, List<String>> edges = new HashMap<>();
        if (document.get("dependencies") instanceof List<?> dependencies) {
            for (Object one : dependencies) {
                if (one instanceof Map<?, ?> dependency
                        && dependency.get("ref") instanceof String ref
                        && dependency.get("dependsOn") instanceof List<?> on) {
                    final List<String> to = edges.computeIfAbsent(ref, r -> new ArrayList<>());
                    on.stream()
                            .filter(String.class::isInstance)
                            .map(String.class::cast)
                            .forEach(to::add);
                }
            }
        }
        for (Map.Entry<String, String> root : keys.entrySet()) {
            if (!root.getValue().startsWith(STORIES + ":")) {
                continue;
            }
            final java.util.Deque<String> open =
                    new java.util.ArrayDeque<>(edges.getOrDefault(root.getKey(), List.of()));
            final Set<String> seen = new java.util.HashSet<>();
            while (!open.isEmpty()) {
                final String ref = open.pop();
                if (!seen.add(ref)) {
                    continue;
                }
                for (String license : named.getOrDefault(ref, List.of())) {
                    if (license.startsWith("GPL-3.0")) {
                        throw Stop.refused(
                                keys.getOrDefault(ref, ref)
                                        + " is under "
                                        + license
                                        + " and below "
                                        + root.getValue()
                                        + ", which is LGPL-3.0-or-later so that others may link it");
                    }
                }
                open.addAll(edges.getOrDefault(ref, List.of()));
            }
        }
    }

    private static void refs(
            @Nullable Object list, Map<String, String> keys, Map<String, List<String>> named) {
        if (!(list instanceof List<?> components)) {
            return;
        }
        for (Object one : components) {
            if (one instanceof Map<?, ?> component
                    && component.get("bom-ref") instanceof String ref) {
                if (component.get("group") instanceof String group
                        && component.get("name") instanceof String name
                        && component.get("version") instanceof String version) {
                    keys.put(ref, group + ":" + name + ":" + version);
                }
                named.put(ref, component(component.get("licenses"), null).licenses());
            }
            if (one instanceof Map<?, ?> component) {
                refs(component.get("components"), keys, named);
            }
        }
    }

    private static void components(@Nullable Object list, Map<String, Component> licenses)
            throws Stop {
        if (!(list instanceof List<?> components)) {
            return;
        }
        for (Object one : components) {
            if (one instanceof Map<?, ?> component
                    && component.get("group") instanceof String group
                    && component.get("name") instanceof String name
                    && component.get("version") instanceof String version) {
                final String classifier = classifier(component.get("purl"));
                final String key =
                        group
                                + ":"
                                + name
                                + ":"
                                + version
                                + (classifier == null ? "" : ":" + classifier);
                final Component read =
                        component(component.get("licenses"), sha256(component.get("hashes")));
                final Component before = licenses.putIfAbsent(key, read);
                if (before != null && !before.equals(read)) {
                    throw Stop.refused(
                            "the bill names "
                                    + key
                                    + " twice, with other licenses or another hash");
                }
            }
            if (one instanceof Map<?, ?> component) {
                components(component.get("components"), licenses);
            }
        }
    }

    private static @Nullable String classifier(@Nullable Object purl) {
        if (purl instanceof String text && text.indexOf('?') >= 0) {
            for (String qualifier : text.substring(text.indexOf('?') + 1).split("[&#]")) {
                if (qualifier.startsWith("classifier=")) {
                    return qualifier.substring("classifier=".length());
                }
            }
        }
        return null;
    }

    private static @Nullable String sha256(@Nullable Object list) {
        if (list instanceof List<?> hashes) {
            for (Object one : hashes) {
                if (one instanceof Map<?, ?> hash
                        && "SHA-256".equals(hash.get("alg"))
                        && hash.get("content") instanceof String content) {
                    return content.toLowerCase(java.util.Locale.ROOT);
                }
            }
        }
        return null;
    }

    private static Component component(@Nullable Object list, @Nullable String sha256) {
        final List<String> licenses = new ArrayList<>();
        final List<String> exceptions = new ArrayList<>();
        final List<String> unread = new ArrayList<>();
        if (list instanceof List<?> entries) {
            for (Object entry : entries) {
                if (!(entry instanceof Map<?, ?> choice)) {
                    unread.add(String.valueOf(entry));
                } else if (choice.get("expression") instanceof String expression) {
                    expression(expression, licenses, exceptions, unread);
                } else if (choice.get("license") instanceof Map<?, ?> license
                        && license.get("id") instanceof String id) {
                    licenses.add(id);
                } else if (choice.get("license") instanceof Map<?, ?> license
                        && license.get("name") instanceof String name) {
                    licenses.add(name);
                } else {
                    unread.add(String.valueOf(choice));
                }
            }
        }
        return new Component(
                List.copyOf(licenses), List.copyOf(exceptions), List.copyOf(unread), sha256);
    }

    private static void expression(
            String expression,
            List<String> licenses,
            List<String> exceptions,
            List<String> unread) {
        boolean exception = false;
        for (String word : expression.trim().split("[\\s()]+")) {
            if (word.isEmpty() || word.equalsIgnoreCase("AND") || word.equalsIgnoreCase("OR")) {
                continue;
            }
            if (word.equalsIgnoreCase("WITH")) {
                if (exception || licenses.isEmpty()) {
                    unread.add(expression);
                }
                exception = true;
            } else if (exception) {
                exceptions.add(word);
                exception = false;
            } else {
                licenses.add(word);
            }
        }
        if (exception) {
            unread.add(expression);
        }
    }

    /**
     * Makes the notices' text.
     *
     * @param classpath the entries compiled in
     * @param licenses the licenses the bill names, by {@code groupId:artifactId:version}, with
     *     {@code :classifier} where it names one
     * @param jdk the JDK the executable is built with
     * @param own the project's own modules, matched against {@code groupId:artifactId}
     * @return the text
     * @throws Stop when a jar's license cannot be told, or an input is unreadable
     */
    static String notices(
            List<Path> classpath, Map<String, Component> licenses, Path jdk, Pattern own)
            throws Stop {
        return make(classpath, licenses, jdk, own).text();
    }

    private static Made make(
            List<Path> classpath, Map<String, Component> licenses, Path jdk, Pattern own)
            throws Stop {
        final List<String> problems = new ArrayList<>();
        final TreeMap<String, Section> sections = new TreeMap<>();
        final Map<String, Path> from = new HashMap<>();
        int owned = 0;
        for (Path entry : classpath) {
            if (!Files.isRegularFile(entry, LinkOption.NOFOLLOW_LINKS)) {
                problems.add(entry + " is not a jar - built after package, its jars are");
                continue;
            }
            final Section section = jar(entry, licenses, own, problems);
            if (section == OWN) {
                owned++;
            } else if (section != null) {
                final Path before = from.putIfAbsent(section.heading(), entry);
                if (before != null) {
                    problems.add(
                            section.heading()
                                    + " stands on the classpath twice: "
                                    + before
                                    + " and "
                                    + entry);
                } else {
                    sections.put(section.heading(), section);
                }
            }
        }
        if (!problems.isEmpty()) {
            throw Stop.refused(String.join("\n      ", problems));
        }
        final List<Section> all = new ArrayList<>(sections.values());
        all.add(runtime(jdk));
        return new Made(render(all), sections.size(), owned);
    }

    /** The answer for a jar of the project's own, which has no section. */
    private static final Section OWN = new Section("", null, List.of());

    private static @Nullable Section jar(
            Path jar, Map<String, Component> licenses, Pattern own, List<String> problems)
            throws Stop {
        try (ZipFile zip = new ZipFile(jar.toFile(), UTF_8)) {
            final String hash = sha256(jar);
            final String heading = coordinates(zip, jar, hash, licenses);
            if (heading == null) {
                problems.add(
                        jar
                                + " says no coordinates of its own, and no bill entry carries its"
                                + " SHA-256");
                return null;
            }
            final String[] parts = heading.split(":");
            if (own.matcher(parts[0] + ":" + parts[1]).matches()) {
                return OWN;
            }
            final Component component = licenses.get(heading);
            if (component == null) {
                problems.add(heading + " (" + jar + ") has no entry in the bill");
                return null;
            }
            final String billed = component.sha256();
            if (billed != null && !billed.equals(hash)) {
                problems.add(
                        heading
                                + " ("
                                + jar
                                + ") is not the jar the bill names: its SHA-256 differs");
                return null;
            }
            final TreeMap<String, Text> texts = new TreeMap<>();
            final Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                final ZipEntry entry = entries.nextElement();
                final String name = entry.getName();
                if (!entry.isDirectory() && licenseFile(name)) {
                    texts.put(name, new Text(name, read(zip, entry, heading)));
                }
            }
            return section(heading, component, texts, problems);
        } catch (IOException ex) {
            throw Stop.unanswered("could not read " + jar + ": " + ex.getMessage());
        }
    }

    private static @Nullable Section section(
            String heading, Component component, TreeMap<String, Text> own, List<String> problems)
            throws Stop {
        final List<String> named = component.licenses();
        if (!component.unread().isEmpty()) {
            problems.add(
                    heading
                            + ": the bill names a license that tells no id or name: "
                            + String.join(", ", component.unread()));
            return null;
        }
        if (named.isEmpty()) {
            problems.add(heading + ": the bill names no license for it");
            return null;
        }
        final List<String> unknown =
                Stream.concat(
                                named.stream().filter(name -> !STANDARD.containsKey(name)),
                                component.exceptions().stream()
                                        .filter(name -> !EXCEPTIONS.contains(name)))
                        .toList();
        if (!unknown.isEmpty()) {
            problems.add(
                    heading
                            + ": the bill's "
                            + String.join(", ", unknown)
                            + " is none of the standard texts pinned: "
                            + String.join(", ", new TreeMap<>(STANDARD).keySet())
                            + ", with the exception "
                            + String.join(", ", new java.util.TreeSet<>(EXCEPTIONS)));
            return null;
        }
        final TreeMap<String, Text> standard = new TreeMap<>();
        final List<String> files = new ArrayList<>(component.exceptions());
        named.forEach(name -> files.addAll(STANDARD.getOrDefault(name, List.of())));
        for (String file : files) {
            standard.put(
                    file,
                    new Text(
                            "standard text of " + file + ", SPDX license list " + LICENSE_LIST,
                            standardText(file)));
        }
        final List<Text> texts = new ArrayList<>(standard.values());
        texts.addAll(own.values());
        final String license =
                String.join(", ", named)
                        + (component.exceptions().isEmpty()
                                ? ""
                                : " with " + String.join(", ", component.exceptions()))
                        + " - as the bill names it";
        return new Section(heading, license, texts);
    }

    /**
     * Reads one of the pinned standard texts.
     *
     * @param id its SPDX id
     * @return the text
     * @throws Stop when it is missing from this build
     */
    static String standardText(String id) throws Stop {
        try (InputStream in =
                ThirdPartyNotices.class.getResourceAsStream("licenses/" + id + ".txt")) {
            if (in == null) {
                throw Stop.unanswered("this build carries no standard text of " + id);
            }
            return decode(in.readAllBytes(), "the standard text of " + id);
        } catch (IOException ex) {
            throw Stop.unanswered("could not read the standard text of " + id, ex);
        }
    }

    private static String sha256(Path jar) throws IOException {
        try (InputStream in = Files.newInputStream(jar)) {
            final java.security.MessageDigest digest =
                    java.security.MessageDigest.getInstance("SHA-256");
            final byte[] buffer = new byte[1 << 16];
            for (int read = in.read(buffer); read >= 0; read = in.read(buffer)) {
                digest.update(buffer, 0, read);
            }
            return java.util.HexFormat.of().formatHex(digest.digest());
        } catch (java.security.NoSuchAlgorithmException ex) {
            throw new IllegalStateException("every Java has SHA-256", ex);
        }
    }

    private static Path parent(Path file) throws Stop {
        final Path parent = file.toAbsolutePath().normalize().getParent();
        if (parent == null) {
            throw Stop.unanswered(file + " has no directory");
        }
        return parent;
    }

    private static boolean licenseFile(String name) {
        final String base = name.substring(name.lastIndexOf('/') + 1);
        return !NO_TEXT.matcher(base).matches()
                && (NAME.matcher(base).matches() || DIRECTORY.matcher(name).matches());
    }

    /**
     * Tells a jar's {@code groupId:artifactId:version}: from the {@code pom.properties} its file is
     * named after, {@code <artifact>-<version>.jar} or {@code
     * <artifact>-<version>-<classifier>.jar}; otherwise - a fat jar, or one Gradle built without,
     * as Spring Boot's - from the bill entry carrying its SHA-256.
     */
    private static @Nullable String coordinates(
            ZipFile zip, Path jar, String hash, Map<String, Component> licenses)
            throws IOException, Stop {
        final List<String[]> found = new ArrayList<>();
        final Enumeration<? extends ZipEntry> entries = zip.entries();
        while (entries.hasMoreElements()) {
            final ZipEntry entry = entries.nextElement();
            if (entry.getName().startsWith("META-INF/maven/")
                    && entry.getName().endsWith("/pom.properties")) {
                final Properties properties = new Properties();
                properties.load(new StringReader(read(zip, entry, jar.toString())));
                final String group = properties.getProperty("groupId");
                final String artifact = properties.getProperty("artifactId");
                final String version = properties.getProperty("version");
                if (group != null && artifact != null && version != null) {
                    found.add(new String[] {group, artifact, version});
                }
            }
        }
        // A shaded jar carries the pom.properties of what it embeds too; its own is the one its
        // file is named after.
        final String file = jar.getFileName().toString();
        final List<String> named = new ArrayList<>();
        for (String[] one : found) {
            final String stem = one[1] + "-" + one[2];
            if (file.equals(stem + ".jar")) {
                named.add(String.join(":", one));
            } else if (file.startsWith(stem + "-")
                    && file.endsWith(".jar")
                    && CLASSIFIER
                            .matcher(file.substring(stem.length() + 1, file.length() - 4))
                            .matches()) {
                named.add(
                        String.join(":", one)
                                + ":"
                                + file.substring(stem.length() + 1, file.length() - 4));
            }
        }
        if (named.size() == 1) {
            return named.getFirst();
        }
        final List<String> billed =
                licenses.entrySet().stream()
                        .filter(entry -> hash.equals(entry.getValue().sha256()))
                        .map(Map.Entry::getKey)
                        .toList();
        return billed.size() == 1 ? billed.getFirst() : null;
    }

    private static String read(ZipFile zip, ZipEntry entry, String where) throws IOException, Stop {
        try (InputStream in = zip.getInputStream(entry)) {
            final byte[] bytes = in.readNBytes(MOST_BYTES + 1);
            if (bytes.length > MOST_BYTES) {
                throw Stop.refused(
                        where + " " + entry.getName() + " is larger than a license text can be");
            }
            return decode(bytes, where + " " + entry.getName());
        }
    }

    private static String readFile(Path text) throws Stop {
        try {
            if (Files.size(text) > MOST_BYTES) {
                throw Stop.refused(text + " is larger than a license text can be");
            }
            return decode(Files.readAllBytes(text), text.toString());
        } catch (IOException ex) {
            throw Stop.unanswered("could not read " + text + ": " + ex.getMessage());
        }
    }

    private static String decode(byte[] bytes, String where) throws Stop {
        try {
            final String text =
                    UTF_8.newDecoder()
                            .onMalformedInput(CodingErrorAction.REPORT)
                            .onUnmappableCharacter(CodingErrorAction.REPORT)
                            .decode(ByteBuffer.wrap(bytes))
                            .toString()
                            .replace("\r\n", "\n");
            return text.endsWith("\n") ? text : text + "\n";
        } catch (CharacterCodingException ex) {
            throw Stop.refused(where + " is not UTF-8 text");
        }
    }

    private static Section runtime(Path jdk) throws Stop {
        final Path legal = jdk.resolve("legal");
        if (!Files.isDirectory(legal, LinkOption.NOFOLLOW_LINKS)) {
            throw Stop.unanswered(jdk + " has no legal directory - is it a JDK?");
        }
        final TreeMap<String, Text> texts = new TreeMap<>();
        try (Stream<Path> top = Files.list(jdk);
                Stream<Path> below = Files.walk(legal)) {
            final List<Path> files =
                    Stream.concat(
                                    top.filter(
                                            path ->
                                                    NAME.matcher(path.getFileName().toString())
                                                            .matches()),
                                    below)
                            .filter(Files::isRegularFile)
                            .toList();
            for (Path file : files) {
                final String name = jdk.relativize(file).toString().replace('\\', '/');
                texts.put(name, new Text(name, readFile(file)));
            }
        } catch (IOException ex) {
            throw Stop.unanswered("could not read " + jdk + ": " + ex.getMessage());
        }
        if (texts.isEmpty()) {
            throw Stop.unanswered(jdk + " has no license texts in its legal directory");
        }
        return new Section(
                "GraalVM runtime " + version(jdk), null, new ArrayList<>(texts.values()));
    }

    private static String version(Path jdk) throws Stop {
        final Properties release = new Properties();
        try {
            release.load(new StringReader(Files.readString(jdk.resolve("release"), UTF_8)));
        } catch (IOException ex) {
            throw Stop.unanswered(
                    "could not read " + jdk.resolve("release") + ": " + ex.getMessage());
        }
        final String version = release.getProperty("GRAALVM_VERSION");
        if (version == null || !version.matches("\"[0-9][0-9A-Za-z.+-]*\"")) {
            throw Stop.unanswered(jdk + " names no GRAALVM_VERSION in its release file");
        }
        return version.substring(1, version.length() - 1);
    }

    private static String render(List<Section> sections) {
        final StringBuilder text = new StringBuilder();
        text.append("THIRD-PARTY NOTICES\n\n")
                .append("This program compiles in the components below. Each section gives a\n")
                .append("component's license texts in full; a text that stood already is named.\n");
        final Map<String, String> seen = new HashMap<>();
        for (Section section : sections) {
            text.append('\n').append(RULE).append('\n').append(section.heading()).append('\n');
            if (section.license() != null) {
                text.append("License: ").append(section.license()).append('\n');
            }
            text.append(RULE).append('\n');
            for (Text one : section.texts()) {
                final String where = section.heading() + " " + one.origin();
                final String before = seen.putIfAbsent(one.body(), where);
                text.append("\n--- ").append(one.origin()).append(" ---\n");
                if (before == null) {
                    text.append(one.body());
                } else {
                    text.append("(same text as ").append(before).append(")\n");
                }
            }
        }
        return text.toString();
    }

    /**
     * The ids whose standard texts are pinned.
     *
     * @return the ids, sorted
     */
    static Set<String> standardIds() {
        return new TreeMap<>(STANDARD).keySet();
    }
}
