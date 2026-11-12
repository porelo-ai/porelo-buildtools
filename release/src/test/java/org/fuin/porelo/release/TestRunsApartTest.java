package org.fuin.porelo.release;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/**
 * The unit tests run alone in {@code test} and the integration tests apart at {@code verify}, as
 * the parent pom configures Surefire for every module.
 */
@Tag("documents")
class TestRunsApartTest {

    private static Document pom() throws Exception {
        final DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        return factory.newDocumentBuilder().parse(Path.of("../pom.xml").toFile());
    }

    private static String text(Document pom, String path) throws Exception {
        return (String)
                XPathFactory.newInstance().newXPath().evaluate(path, pom, XPathConstants.STRING);
    }

    private static final String SUREFIRE =
            "/project/build/pluginManagement/plugins/plugin[artifactId='maven-surefire-plugin']";

    // Prevents `./mvnw test` from starting a real process, socket or server again, which made the
    // unit run wait on them.
    @Test
    void theTestPhaseLeavesTheIntegrationTestsOut() throws Exception {
        final Document pom = pom();
        assertThat(text(pom, SUREFIRE + "/configuration/excludedGroups"))
                .isEqualTo("${porelo.excludedGroups}");
        assertThat(text(pom, "/project/properties/porelo.excludedGroups")).isEqualTo("integration");
    }

    // Prevents the integration tests from being left out of the Build altogether.
    @Test
    void verifyRunsTheIntegrationTestsApart() throws Exception {
        final Document pom = pom();
        final String execution = SUREFIRE + "/executions/execution[id='integration']";
        assertThat(text(pom, execution + "/phase")).isEqualTo("integration-test");
        assertThat(text(pom, execution + "/goals/goal")).isEqualTo("test");
        assertThat(text(pom, execution + "/configuration/groups")).isEqualTo("integration");
        assertThat(text(pom, execution + "/configuration/excludedGroups")).isEqualTo("none");
        assertThat(text(pom, "/project/properties/porelo.skipIntegration")).isEqualTo("false");
    }

    // Prevents the native parity check from selecting a class the inherited exclusion leaves out,
    // so `verify -Pnative` runs no test and stays green.
    @Test
    void theNativeParityRunTakesItsIntegrationTest() throws Exception {
        final DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        final Document stories =
                factory.newDocumentBuilder().parse(Path.of("../stories/pom.xml").toFile());
        final String execution =
                "/project/profiles/profile/build/plugins/plugin[artifactId='maven-surefire-plugin']"
                        + "/executions/execution[id='native-parity']/configuration";
        assertThat(text(stories, execution + "/includes/include"))
                .isEqualTo("**/NativeStoriesTest.java");
        assertThat(text(stories, execution + "/groups")).isEqualTo("integration");
        assertThat(text(stories, execution + "/excludedGroups")).isEqualTo("none");
        assertThat(
                        Files.readString(
                                Path.of(
                                        "../stories/src/test/java/org/fuin/porelo/stories/NativeStoriesTest.java")))
                .contains("@Tag(\"integration\")");
    }

    // Prevents the documents run from leaving out a document test that also starts a process.
    @Test
    void theDocumentsRunExcludesNothing() throws Exception {
        final NodeList profiles =
                (NodeList)
                        XPathFactory.newInstance()
                                .newXPath()
                                .evaluate(
                                        "/project/profiles/profile[id='documents']/properties",
                                        pom(),
                                        XPathConstants.NODESET);
        assertThat(profiles.getLength()).isOne();
        final Element properties = (Element) profiles.item(0);
        assertThat(properties.getElementsByTagName("groups").item(0).getTextContent())
                .isEqualTo("documents");
        assertThat(
                        properties
                                .getElementsByTagName("porelo.excludedGroups")
                                .item(0)
                                .getTextContent())
                .isEqualTo("none");
    }

    // Prevents a test that starts a real tool from hiding among the unit tests: each of these
    // reaches for a process or a server, so each carries the tag.
    @Test
    void theTestsThatStartAProcessOrAServerAreTagged() throws IOException {
        for (final String test :
                new String[] {
                    "../release/src/test/java/org/fuin/porelo/release/WebTest.java",
                    "../release/src/test/java/org/fuin/porelo/release/StageTest.java",
                    "../machines/src/test/java/org/fuin/porelo/machines/PinnedRetryTest.java"
                }) {
            assertThat(Files.readString(Path.of(test))).as(test).contains("@Tag(\"integration\")");
        }
    }
}
