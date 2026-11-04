package org.fuin.porelo.release;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathFactory;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;

/** Guards snapshot resolution before Maven can resolve the parent. */
@Tag("documents")
class RegistrySettingsTest {

    // Prevents a private forge build from failing to authenticate while resolving its parent.
    @Test
    void theForgeServerUsesThePublisherTokenFromTheEnvironment() throws Exception {
        final Document settings = settings();
        assertValue(settings, "count(/settings/servers/server[id='porelo-forge'])", "1");
        final String server = "/settings/servers/server[id='porelo-forge']/";
        assertValue(settings, server + "username", "porelo-publisher");
        assertValue(settings, server + "password", "${env.PORELO_PUBLISHER_TOKEN}");
    }

    // Prevents every name being asked of Sonatype, or a build on a forge missing what the host
    // built.
    @Test
    void sonatypeNeedsTheFilterAndTheForgeOnlyAddsItsRegistry() throws Exception {
        final Document settings = settings();
        assertValue(settings, "count(/settings/activeProfiles)", "0");
        assertValue(settings, "count(/settings/profiles/profile)", "2");
        final String central = "/settings/profiles/profile[id='central']/";
        assertValue(
                settings,
                central + "activation/property/name",
                "aether.remoteRepositoryFilter.groupId");
        assertValue(settings, central + "activation/property/value", "true");
        assertValue(
                settings,
                central + "activation/file/exists",
                "${maven.multiModuleProjectDirectory}/.mvn/rrf/"
                        + "groupId-central.sonatype.snapshots.txt");
        assertRepositories(
                settings,
                "central",
                "central.sonatype.snapshots",
                "https://central.sonatype.com/repository/maven-snapshots/");
        assertValue(
                settings,
                "/settings/profiles/profile[id='forge']/activation/property/name",
                "env.MAVEN_SNAPSHOTS_URL");
        assertRepositories(settings, "forge", "porelo-forge", "${env.MAVEN_SNAPSHOTS_URL}");
    }

    private static void assertRepositories(
            Document settings, String id, String repositoryId, String url) throws Exception {
        final String profile = "/settings/profiles/profile[id='" + id + "']/";
        assertValue(settings, "count(/settings/profiles/profile[id='" + id + "'])", "1");
        assertValue(settings, "count(" + profile + "activation/activeByDefault)", "0");
        for (final String path :
                new String[] {"repositories/repository", "pluginRepositories/pluginRepository"}) {
            final String repository = profile + path;
            assertValue(settings, "count(" + repository + ")", "1");
            assertValue(settings, repository + "/id", repositoryId);
            assertValue(settings, repository + "/url", url);
            assertValue(settings, repository + "/releases/enabled", "false");
            assertValue(settings, repository + "/snapshots/enabled", "true");
            assertValue(settings, repository + "/snapshots/updatePolicy", "always");
        }
    }

    private static void assertValue(Document document, String expression, String expected)
            throws Exception {
        final XPath xpath = XPathFactory.newInstance().newXPath();
        assertThat(xpath.evaluate(expression, document))
                .as("settings.xml: %s", expression)
                .isEqualTo(expected);
    }

    private static Document settings() throws Exception {
        final DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        return factory.newDocumentBuilder().parse(Path.of("../settings.xml").toFile());
    }
}
