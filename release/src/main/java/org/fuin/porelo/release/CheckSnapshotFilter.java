package org.fuin.porelo.release;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

/**
 * Refuses a repository's snapshot settings that would ask the central source about a name the
 * repository did not list, or let a task branch's snapshot become the newest on a host.
 *
 * <p>Maven's remote repository filter asks everything of a repository with no file, so the central
 * repository stands only in a profile that activates on the switch and on the file together. A
 * repository whose {@code settings.xml} reads no snapshots has nothing to hold, but no pom may name
 * that repository to read from.
 */
final class CheckSnapshotFilter {

    private static final String CENTRAL = "central.sonatype.snapshots";

    private static final String CENTRAL_URL =
            "https://central.sonatype.com/repository/maven-snapshots";

    private static final String FILTER = "groupId-" + CENTRAL + ".txt";

    private static final String SWITCH = "aether.remoteRepositoryFilter.groupId";

    private static final String BASEDIR =
            "-D" + SWITCH + ".basedir=${session.rootDirectory}/.mvn/rrf";

    private static final String FORGE_DEPLOY = "deploy -P\"$PORELO_DEPLOY_PROFILE\"";

    private final PrintStream out;

    private final PrintStream err;

    /**
     * Constructor.
     *
     * @param out where the result goes
     * @param err where a refusal goes
     */
    CheckSnapshotFilter(PrintStream out, PrintStream err) {
        this.out = out;
        this.err = err;
    }

    /**
     * Checks one repository.
     *
     * @param root its root
     * @return 0 when it holds or reads no snapshots, {@link Stop#REFUSED} when it does not hold or
     *     a file there cannot be read
     */
    int check(Path root) {
        final List<String> faults = new ArrayList<>();
        try {
            final Path settings = root.resolve("settings.xml");
            if (Files.isRegularFile(settings) && settings(settings, faults)) {
                filter(root, faults);
            }
            poms(root, faults);
            deploys(root.resolve(".github/workflows"), faults);
        } catch (IOException e) {
            faults.add(e.getMessage());
        }
        if (!faults.isEmpty()) {
            faults.forEach(err::println);
            return Stop.REFUSED;
        }
        out.println("OK    snapshot filter in " + root);
        return 0;
    }

    private static boolean settings(Path file, List<String> faults) throws IOException {
        final NodeList profiles = parse(file).getElementsByTagName("profile");
        boolean reads = false;
        for (int i = 0; i < profiles.getLength(); i++) {
            final Element profile = (Element) profiles.item(i);
            final String id = first(profile, "id");
            final boolean central =
                    holds(profile.getElementsByTagName("repository"))
                            || holds(profile.getElementsByTagName("pluginRepository"));
            if (central) {
                reads = true;
                if (!first(profile, "exists").endsWith("/.mvn/rrf/" + FILTER)
                        || !first(profile, "name").equals(SWITCH)
                        || !first(profile, "value").equals("true")) {
                    faults.add(
                            "settings.xml: profile "
                                    + id
                                    + " reads "
                                    + CENTRAL
                                    + " without activating on the switch and on the file "
                                    + FILTER);
                }
            } else if (profile.getElementsByTagName("repository").getLength() > 0) {
                reads = true;
            }
        }
        return reads;
    }

    private static void poms(Path root, List<String> faults) throws IOException {
        final List<Path> poms;
        try (Stream<Path> walked = Files.walk(root)) {
            poms =
                    walked.filter(path -> path.endsWith("pom.xml"))
                            .filter(path -> !built(root.relativize(path)))
                            .sorted()
                            .toList();
        }
        for (final Path pom : poms) {
            final Document document = parse(pom);
            if (reads(document.getElementsByTagName("repository"))
                    || reads(document.getElementsByTagName("pluginRepository"))) {
                faults.add(
                        root.relativize(pom)
                                + ": it reads Sonatype's snapshots, which only the guarded profile"
                                + " of settings.xml may");
            }
        }
    }

    private static boolean built(Path relative) {
        for (final Path part : relative) {
            if (part.toString().equals("target") || part.toString().startsWith(".")) {
                return true;
            }
        }
        return false;
    }

    private static boolean reads(NodeList repositories) {
        for (int i = 0; i < repositories.getLength(); i++) {
            final Element repository = (Element) repositories.item(i);
            if (CENTRAL.equals(first(repository, "id"))
                    || first(repository, "url").startsWith(CENTRAL_URL)) {
                return true;
            }
        }
        return false;
    }

    private static boolean holds(NodeList repositories) {
        for (int i = 0; i < repositories.getLength(); i++) {
            if (CENTRAL.equals(first((Element) repositories.item(i), "id"))) {
                return true;
            }
        }
        return false;
    }

    private static String first(Element parent, String tag) {
        final NodeList found = parent.getElementsByTagName(tag);
        return found.getLength() == 0 ? "" : found.item(0).getTextContent().strip();
    }

    private static void filter(Path root, List<String> faults) throws IOException {
        final Path config = root.resolve(".mvn/maven.config");
        final List<String> lines =
                Files.isRegularFile(config) ? Files.readAllLines(config) : List.of();
        if (!lines.contains("-D" + SWITCH + "=true") || !lines.contains(BASEDIR)) {
            faults.add(".mvn/maven.config: it must hold -D" + SWITCH + "=true and " + BASEDIR);
        }
        if (!Files.isRegularFile(root.resolve(".mvn/rrf").resolve(FILTER))) {
            faults.add(".mvn/rrf/" + FILTER + ": it is missing, so every name would be asked");
        }
    }

    private static void deploys(Path workflows, List<String> faults) throws IOException {
        if (!Files.isDirectory(workflows)) {
            return;
        }
        final ObjectMapper yaml = new ObjectMapper(new YAMLFactory());
        final List<Path> files;
        try (Stream<Path> listed = Files.list(workflows)) {
            files = listed.filter(path -> path.toString().endsWith(".yml")).sorted().toList();
        }
        for (final Path file : files) {
            for (final JsonNode job : yaml.readTree(file.toFile()).path("jobs")) {
                final String held = job.path("if").asText("");
                for (final JsonNode step : job.path("steps")) {
                    if (step.path("run").asText("").contains(FORGE_DEPLOY)
                            && !mainAndTags(held)
                            && !mainAndTags(step.path("if").asText(""))) {
                        faults.add(
                                ".github/workflows/"
                                        + file.getFileName()
                                        + ": a deploy runs on more than main and tags");
                    }
                }
            }
        }
    }

    private static boolean mainAndTags(String condition) {
        return condition.contains("refs/heads/main") && condition.contains("refs/tags/");
    }

    private static Document parse(Path file) throws IOException {
        try {
            final DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            return factory.newDocumentBuilder().parse(file.toFile());
        } catch (ParserConfigurationException | SAXException e) {
            throw new IOException(file + " does not read: " + e.getMessage(), e);
        }
    }
}
