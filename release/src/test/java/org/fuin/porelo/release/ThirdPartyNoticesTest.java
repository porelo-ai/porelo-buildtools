package org.fuin.porelo.release;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Tests for {@link ThirdPartyNotices}, on jars made here and on a real one of the build's own. */
class ThirdPartyNoticesTest {

    private static final Pattern NONE_OWN = Pattern.compile("(?!)");

    @TempDir Path dir;

    /** Every jar made here, as a bill names it: MIT, its SHA-256. */
    private final Map<String, ThirdPartyNotices.Component> bill = new HashMap<>();

    @Test
    void writesEachJarsLicenseAndNoticeInFullUnderItsCoordinates() throws Exception {
        final Path jar =
                jar(
                        "alpha-1.0.jar",
                        "org.example",
                        "alpha",
                        "1.0",
                        Map.of(
                                "META-INF/LICENSE.txt", "Alpha license text\n",
                                "META-INF/NOTICE.txt", "Alpha notice text\n"));

        final String text = ThirdPartyNotices.notices(List.of(jar), bill, jdk(), NONE_OWN);

        assertThat(text)
                .as("a jar's own texts stand in full under its coordinates")
                .contains("org.example:alpha:1.0")
                .contains("--- META-INF/LICENSE.txt ---\nAlpha license text\n")
                .contains("--- META-INF/NOTICE.txt ---\nAlpha notice text\n");
    }

    @Test
    void readsTextsKeptUnderALicensesDirectoryAnywhereInTheJar() throws Exception {
        // plexus-utils keeps the licenses of the code it embeds there, one without the word in
        // its name.
        final Path jar =
                jar(
                        "plexus-4.jar",
                        "org.example",
                        "plexus",
                        "4",
                        Map.of(
                                "META-INF/LICENSE", "Own\n",
                                "licenses/thoughtworks.TXT", "Thoughtworks text\n"));

        final String text = ThirdPartyNotices.notices(List.of(jar), bill, jdk(), NONE_OWN);

        assertThat(text)
                .as("an embedded code's license kept outside META-INF ships too")
                .contains("--- licenses/thoughtworks.TXT ---\nThoughtworks text\n");
    }

    @Test
    void aJarWithoutTextGetsTheStandardTextOfEachLicenseItsBillNames() throws Exception {
        final Path jar =
                jar(
                        "logback-core-1.5.jar",
                        "ch.qos.logback",
                        "logback-core",
                        "1.5",
                        Map.of("X", "x\n"));

        final String text =
                ThirdPartyNotices.notices(
                        List.of(jar),
                        licensed("ch.qos.logback:logback-core:1.5", "EPL-2.0", "LGPL-2.1-only"),
                        jdk(),
                        NONE_OWN);

        assertThat(text)
                .as("both licenses of a dual license stand, in full, as the POM names them")
                .contains("License: EPL-2.0, LGPL-2.1-only")
                .contains(ThirdPartyNotices.standardText("EPL-2.0"))
                .contains(ThirdPartyNotices.standardText("LGPL-2.1-only"));
    }

    @Test
    void addsTheStandardTextsTheBillNamesBesideTheJarsOwn() throws Exception {
        // A dual-licensed jar that carries one text, or only a NOTICE, still ships every license.
        final Path jar =
                jar(
                        "dual-1.jar",
                        "org.example",
                        "dual",
                        "1",
                        Map.of("META-INF/NOTICE", "Notice\n"));

        final String text =
                ThirdPartyNotices.notices(
                        List.of(jar),
                        licensed("org.example:dual:1", "EPL-2.0", "LGPL-2.1-only"),
                        jdk(),
                        NONE_OWN);

        assertThat(text)
                .as("the bill's licenses stand in full beside the jar's own notice")
                .contains("--- META-INF/NOTICE ---\nNotice\n")
                .contains(ThirdPartyNotices.standardText("EPL-2.0"))
                .contains(ThirdPartyNotices.standardText("LGPL-2.1-only"));
    }

    @Test
    void refusesAJarWithATextOfItsOwnWhoseLicenseHasNoPinnedText() throws Exception {
        final Path jar =
                jar("w-1.jar", "org.example", "w", "1", Map.of("META-INF/LICENSE", "Own\n"));

        assertThatThrownBy(
                        () ->
                                ThirdPartyNotices.notices(
                                        List.of(jar),
                                        licensed("org.example:w:1", "WTFPL"),
                                        jdk(),
                                        NONE_OWN))
                .as("a text of its own does not tell which license the jar is under")
                .isInstanceOf(Stop.class)
                .hasMessageContaining("WTFPL");
    }

    @Test
    void readsOnlyFilesNamedForALicenseNotEveryNameWithTheWord() throws Exception {
        final Path jar =
                jar(
                        "n-1.jar",
                        "org.example",
                        "n",
                        "1",
                        Map.of(
                                "META-INF/LICENSE-notice.md", "Notice md\n",
                                "META-INF/FastDoubleParser-LICENSE", "Embedded\n",
                                "org/example/LicenseValidator.properties", "validator\n",
                                "web/NoticeBoard.html", "board\n",
                                "org/example/Notice.java", "source\n",
                                "licenses/logo.png", "png\n"));

        final String text = ThirdPartyNotices.notices(List.of(jar), bill, jdk(), NONE_OWN);

        assertThat(text)
                .as("license files are read")
                .contains("Notice md\n")
                .contains("Embedded\n");
        assertThat(text)
                .as("a class, page or image that only has the word in its name is no license")
                .doesNotContain("validator")
                .doesNotContain("board")
                .doesNotContain("source")
                .doesNotContain("png\n");
    }

    @Test
    void aFatJarIsNotTakenForTheOneDependencyItEmbeds() throws Exception {
        // Its one pom.properties is a dependency's, claiming the project's own coordinates.
        final Path jar =
                jar("fat.jar", "org.fuin.porelo", "mine", "0.1", Map.of("META-INF/LICENSE", "F\n"));
        bill.put("org.example:fat:1", bill.remove("org.fuin.porelo:mine:0.1"));

        final String text =
                ThirdPartyNotices.notices(
                        List.of(jar), bill, jdk(), Pattern.compile("org\\.fuin\\.porelo:.*"));

        assertThat(text)
                .as("the bill's entry with the jar's hash names it, and --own cannot skip it")
                .contains("org.example:fat:1");
    }

    @Test
    void aVersionThatOnlyBeginsLikeTheFilesIsNotTheJars() throws Exception {
        final Path jar =
                jar(
                        "foo-1.0.1.jar",
                        "org.example",
                        "foo",
                        "1.0",
                        Map.of("META-INF/LICENSE", "F\n"));
        bill.put("org.example:foo:1.0.1", bill.remove("org.example:foo:1.0"));

        final String text = ThirdPartyNotices.notices(List.of(jar), bill, jdk(), NONE_OWN);

        assertThat(text)
                .as("foo-1.0 is not foo-1.0.1; the bill's hash tells")
                .contains("org.example:foo:1.0.1");
    }

    @Test
    void readsPluralLicenseAndNoticeFiles() throws Exception {
        final Path jar =
                jar(
                        "pl-1.jar",
                        "org.example",
                        "pl",
                        "1",
                        Map.of("LICENSES.txt", "Plural licenses\n", "NOTICES", "Plural notices\n"));

        final String text = ThirdPartyNotices.notices(List.of(jar), bill, jdk(), NONE_OWN);

        assertThat(text)
                .as("a file of several licenses is a license file too")
                .contains("Plural licenses\n")
                .contains("Plural notices\n");
    }

    @Test
    void aSnapshotsFileIsNotTakenForTheReleaseItsPomPropertiesClaim() throws Exception {
        final Path snapshot =
                jar(
                        "foo-1.0-SNAPSHOT.jar",
                        "org.example",
                        "foo",
                        "1.0",
                        Map.of("META-INF/LICENSE", "F\n"));
        final Path empty =
                jar("foo-1.0-.jar", "org.example", "foo", "1.0", Map.of("META-INF/LICENSE", "E\n"));
        bill.put("org.example:foo:1.0", component(null, "MIT"));

        for (Path jar : List.of(snapshot, empty)) {
            assertThatThrownBy(() -> ThirdPartyNotices.notices(List.of(jar), bill, jdk(), NONE_OWN))
                    .as("SNAPSHOT and an empty tail are no classifier: " + jar.getFileName())
                    .isInstanceOf(Stop.class)
                    .hasMessageContaining("says no coordinates of its own");
        }
    }

    @Test
    void jarsOfOneArtifactWithTwoClassifiersAreTwoSections() throws Exception {
        // netty ships one native jar per platform, all with one pom.properties.
        final String gav = "io.netty:netty-transport-native-epoll:4.1";
        final Path x86 =
                jar(
                        "netty-transport-native-epoll-4.1-linux-x86_64.jar",
                        "io.netty",
                        "netty-transport-native-epoll",
                        "4.1",
                        Map.of("META-INF/LICENSE.txt", "Netty\n", "x86", "x\n"));
        bill.put(gav + ":linux-x86_64", bill.remove(gav));
        final Path arm =
                jar(
                        "netty-transport-native-epoll-4.1-linux-aarch_64.jar",
                        "io.netty",
                        "netty-transport-native-epoll",
                        "4.1",
                        Map.of("META-INF/LICENSE.txt", "Netty\n", "arm", "a\n"));
        bill.put(gav + ":linux-aarch_64", bill.remove(gav));

        final String text = ThirdPartyNotices.notices(List.of(x86, arm), bill, jdk(), NONE_OWN);

        assertThat(text)
                .as("each platform's jar is its own section, held to its own hash")
                .contains(gav + ":linux-x86_64")
                .contains(gav + ":linux-aarch_64");
    }

    @Test
    void refusesOneJarFileTwiceOnTheClasspathByItsPomProperties() throws Exception {
        final Path one =
                jar("alpha-1.jar", "org.example", "alpha", "1", Map.of("META-INF/LICENSE", "A\n"));
        final Path two =
                Files.copy(
                        one, Files.createDirectories(dir.resolve("other")).resolve("alpha-1.jar"));

        assertThatThrownBy(
                        () -> ThirdPartyNotices.notices(List.of(one, two), bill, jdk(), NONE_OWN))
                .as("the same jar twice would stand as one section")
                .isInstanceOf(Stop.class)
                .hasMessageContaining(one.toString())
                .hasMessageContaining(two.toString());
    }

    @Test
    void readsAComponentsClassifierFromItsPurl() throws Exception {
        final Path bill =
                Files.writeString(
                        dir.resolve("bom.json"),
                        """
                        {"bomFormat": "CycloneDX", "components": [
                          {"group": "io.netty", "name": "epoll", "version": "4.1",
                           "purl": "pkg:maven/io.netty/epoll@4.1?classifier=linux-x86_64&type=jar",
                           "licenses": [{"license": {"id": "Apache-2.0"}}],
                           "hashes": [{"alg": "SHA-256", "content": "aa"}]},
                          {"group": "io.netty", "name": "epoll", "version": "4.1",
                           "purl": "pkg:maven/io.netty/epoll@4.1?classifier=linux-aarch_64&type=jar",
                           "licenses": [{"license": {"id": "Apache-2.0"}}],
                           "hashes": [{"alg": "SHA-256", "content": "bb"}]}
                        ]}
                        """);

        assertThat(ThirdPartyNotices.licenses(bill).keySet())
                .as("two classifiers of one artifact are two components, not one named twice")
                .containsExactlyInAnyOrder(
                        "io.netty:epoll:4.1:linux-x86_64", "io.netty:epoll:4.1:linux-aarch_64");
    }

    @Test
    void refusesOneJarTwiceOnTheClasspath() throws Exception {
        final Path one =
                jar("alpha-1.jar", "org.example", "alpha", "1", Map.of("META-INF/LICENSE", "A\n"));
        final Path two = Files.copy(one, dir.resolve("a-copy.jar"));

        assertThatThrownBy(
                        () -> ThirdPartyNotices.notices(List.of(one, two), bill, jdk(), NONE_OWN))
                .as("one section would silently stand for two entries")
                .isInstanceOf(Stop.class)
                .hasMessageContaining(one.toString())
                .hasMessageContaining(two.toString());
    }

    @Test
    void refusesABillThatNamesOneComponentTwiceDifferently() throws Exception {
        final Path bill =
                Files.writeString(
                        dir.resolve("bom.json"),
                        """
                        {"bomFormat": "CycloneDX", "components": [
                          {"group": "g", "name": "a", "version": "1",
                           "licenses": [{"license": {"id": "MIT"}}]},
                          {"group": "g", "name": "a", "version": "1",
                           "licenses": [{"license": {"id": "EPL-2.0"}}]}
                        ]}
                        """);

        assertThatThrownBy(() -> ThirdPartyNotices.licenses(bill))
                .as("which entry speaks for the jar would be chance")
                .isInstanceOf(Stop.class)
                .hasMessageContaining("g:a:1");
    }

    @Test
    void refusesALicenseTheBillNamesByUrlAlone() throws Exception {
        final Path jar = jar("u-1.jar", "org.example", "u", "1", Map.of("META-INF/LICENSE", "U\n"));
        bill.put(
                "org.example:u:1",
                new ThirdPartyNotices.Component(
                        List.of("MIT"),
                        List.of(),
                        List.of("{url=https://example.org/license}"),
                        bill.get("org.example:u:1").sha256()));

        assertThatThrownBy(() -> ThirdPartyNotices.notices(List.of(jar), bill, jdk(), NONE_OWN))
                .as("a license told by nothing but a link cannot be written in full")
                .isInstanceOf(Stop.class)
                .hasMessageContaining("example.org/license");
    }

    @Test
    void readsALicenseNamedByUrlAloneAsUnread() throws Exception {
        final Path bill =
                Files.writeString(
                        dir.resolve("bom.json"),
                        """
                        {"bomFormat": "CycloneDX", "components": [
                          {"group": "g", "name": "a", "version": "1",
                           "licenses": [{"license": {"url": "https://example.org/l"}}]}
                        ]}
                        """);

        assertThat(ThirdPartyNotices.licenses(bill).get("g:a:1").unread())
                .as("an entry with only a url is kept as one the bill does not tell")
                .hasSize(1);
    }

    @Test
    void readsExpressionOperatorsInAnyCaseAndKeepsExceptionsApart() throws Exception {
        final Path bill =
                Files.writeString(
                        dir.resolve("bom.json"),
                        """
                        {"bomFormat": "CycloneDX", "components": [
                          {"group": "g", "name": "a", "version": "1",
                           "licenses": [{"expression": "MIT or GPL-2.0-only with Classpath-exception-2.0"}]}
                        ]}
                        """);

        assertThat(ThirdPartyNotices.licenses(bill).get("g:a:1"))
                .as("lower-case operators are operators; the word after WITH is an exception")
                .isEqualTo(
                        new ThirdPartyNotices.Component(
                                List.of("MIT", "GPL-2.0-only"),
                                List.of("Classpath-exception-2.0"),
                                List.of(),
                                null));
    }

    @Test
    void refusesALicenseNamedAsAnException() throws Exception {
        final Path jar = jar("m-1.jar", "org.example", "m", "1", Map.of("META-INF/LICENSE", "M\n"));
        bill.put(
                "org.example:m:1",
                new ThirdPartyNotices.Component(
                        List.of("MIT"),
                        List.of("Apache-2.0"),
                        List.of(),
                        bill.get("org.example:m:1").sha256()));

        assertThatThrownBy(() -> ThirdPartyNotices.notices(List.of(jar), bill, jdk(), NONE_OWN))
                .as("MIT WITH Apache-2.0 names no exception the set holds")
                .isInstanceOf(Stop.class)
                .hasMessageContaining("Apache-2.0");
    }

    @Test
    void refusesAJarWithoutTextWhoseBillNamesNoLicense() throws Exception {
        final Path jar = jar("bare-2.0.jar", "org.example", "bare", "2.0", Map.of("README", "x\n"));
        licensed("org.example:bare:2.0");

        assertThatThrownBy(() -> ThirdPartyNotices.notices(List.of(jar), bill, jdk(), NONE_OWN))
                .as("a compiled-in jar of no known license would ship without its notice")
                .isInstanceOf(Stop.class)
                .hasMessageContaining("org.example:bare:2.0")
                .hasMessageContaining("no license for it");
    }

    @Test
    void refusesAJarTheBillDoesNotNameEvenWithATextOfItsOwn() throws Exception {
        final Path jar =
                jar(
                        "bare-2.0.jar",
                        "org.example",
                        "bare",
                        "2.0",
                        Map.of("META-INF/LICENSE", "x\n"));
        bill.clear();

        assertThatThrownBy(() -> ThirdPartyNotices.notices(List.of(jar), bill, jdk(), NONE_OWN))
                .as("a jar the bill does not know has no license to tell")
                .isInstanceOf(Stop.class)
                .hasMessageContaining("org.example:bare:2.0")
                .hasMessageContaining("no entry in the bill");
    }

    @Test
    void tellsAJarWithoutPomPropertiesByItsHashInTheBill() throws Exception {
        // Spring Boot's jars are built by Gradle and carry no pom.properties.
        final Path jar =
                jar(
                        "spring-boot-4.jar",
                        "org.springframework.boot",
                        "spring-boot",
                        "4",
                        Map.of("META-INF/LICENSE.txt", "Boot\n"),
                        false);

        final String text = ThirdPartyNotices.notices(List.of(jar), bill, jdk(), NONE_OWN);

        assertThat(text)
                .as("the bill's entry with the jar's hash names it")
                .contains("org.springframework.boot:spring-boot:4");
    }

    @Test
    void refusesAJarWithoutPomPropertiesNoBillEntryCarriesTheHashOf() throws Exception {
        final Path jar =
                jar("x.jar", "org.example", "x", "1", Map.of("META-INF/LICENSE.txt", "X\n"), false);
        bill.put("org.example:x:1", component("00".repeat(32)));

        assertThatThrownBy(() -> ThirdPartyNotices.notices(List.of(jar), bill, jdk(), NONE_OWN))
                .as("a jar nothing names cannot be told")
                .isInstanceOf(Stop.class)
                .hasMessageContaining(jar.toString())
                .hasMessageContaining("no bill entry carries its SHA-256");
    }

    @Test
    void refusesAJarWhoseHashDiffersFromTheBills() throws Exception {
        final Path jar =
                jar("alpha-1.jar", "org.example", "alpha", "1", Map.of("META-INF/LICENSE", "A\n"));
        bill.put(
                "org.example:alpha:1",
                new ThirdPartyNotices.Component(List.of("MIT"), "00".repeat(32)));

        assertThatThrownBy(() -> ThirdPartyNotices.notices(List.of(jar), bill, jdk(), NONE_OWN))
                .as("a bill of another build cannot speak for this jar")
                .isInstanceOf(Stop.class)
                .hasMessageContaining("org.example:alpha:1")
                .hasMessageContaining("SHA-256 differs");
    }

    @Test
    void aLicenseWithAnExceptionGetsBothTexts() throws Exception {
        final Path jar = jar("gpl-1.jar", "org.example", "gpl", "1", Map.of("X", "x\n"));

        final String text =
                ThirdPartyNotices.notices(
                        List.of(jar),
                        Map.of(
                                "org.example:gpl:1",
                                new ThirdPartyNotices.Component(
                                        List.of("GPL-2.0-only"),
                                        List.of("Classpath-exception-2.0"),
                                        List.of(),
                                        null)),
                        jdk(),
                        NONE_OWN);

        assertThat(text)
                .as("the exception is part of the license, and ships with it")
                .contains(ThirdPartyNotices.standardText("GPL-2.0-only"))
                .contains(ThirdPartyNotices.standardText("Classpath-exception-2.0"));
    }

    @Test
    void aJarUnderTheGplGetsTheGnuText() throws Exception {
        // Porelo's own GPL libraries reach the agents' executables; refused, they are left unnamed.
        final Path jar = jar("api-1.0.jar", "org.example", "api", "1.0", Map.of("README", "x\n"));

        final String text =
                ThirdPartyNotices.notices(
                        List.of(jar),
                        licensed("org.example:api:1.0", "GPL-3.0-or-later"),
                        jdk(),
                        NONE_OWN);

        assertThat(text)
                .as("the GNU General Public License in full")
                .contains("License: GPL-3.0-or-later")
                .contains(ThirdPartyNotices.standardText("GPL-3.0"));
    }

    @Test
    void aJarUnderTheLgplGetsTheLesserTextAndTheGplItBuildsOn() throws Exception {
        // porelo-stories is LGPL-3.0-or-later; a program that links it ships both texts.
        final Path jar =
                jar(
                        "porelo-stories-1.jar",
                        "org.fuin.porelo",
                        "porelo-stories",
                        "1",
                        Map.of("README", "x\n"));

        final String text =
                ThirdPartyNotices.notices(
                        List.of(jar),
                        licensed("org.fuin.porelo:porelo-stories:1", "LGPL-3.0-or-later"),
                        jdk(),
                        NONE_OWN);

        assertThat(text)
                .as(
                        "the GNU Lesser General Public License with the GPL its permissions are added to")
                .contains("License: LGPL-3.0-or-later")
                .contains(ThirdPartyNotices.standardText("LGPL-3.0"))
                .contains(ThirdPartyNotices.standardText("GPL-3.0"));
        assertThat(text.split("GNU LESSER GENERAL PUBLIC LICENSE", -1))
                .as("the lesser text stands once")
                .hasSize(2);
    }

    @Test
    void refusesAGplComponentInTheClosureOfTheLgplStoriesLibrary() throws Exception {
        // porelo-stories is LGPL so that others may link it; a GPL jar below it would break that.
        final Path bill =
                Files.writeString(
                        dir.resolve("bom.json"),
                        """
                        {"bomFormat": "CycloneDX", "components": [
                          {"bom-ref": "s", "group": "org.fuin.porelo", "name": "porelo-stories", "version": "1",
                           "licenses": [{"license": {"id": "LGPL-3.0-or-later"}}]},
                          {"bom-ref": "m", "group": "g", "name": "middle", "version": "1",
                           "licenses": [{"license": {"id": "MIT"}}]},
                          {"bom-ref": "x", "group": "g", "name": "gpl", "version": "2",
                           "licenses": [{"expression": "GPL-3.0-or-later"}]}
                        ], "dependencies": [
                          {"ref": "s", "dependsOn": ["m"]},
                          {"ref": "m", "dependsOn": ["x"]}
                        ]}
                        """);

        assertThatThrownBy(() -> ThirdPartyNotices.licenses(bill))
                .as("a GPL jar porelo-stories reaches through another")
                .isInstanceOf(Stop.class)
                .hasMessageContaining("g:gpl:2")
                .hasMessageContaining("porelo-stories");
    }

    @Test
    void takesAGplComponentOutsideTheClosureOfTheStoriesLibrary() throws Exception {
        final Path bill =
                Files.writeString(
                        dir.resolve("bom.json"),
                        """
                        {"bomFormat": "CycloneDX", "components": [
                          {"bom-ref": "a", "group": "g", "name": "app", "version": "1",
                           "licenses": [{"license": {"id": "MIT"}}]},
                          {"bom-ref": "s", "group": "org.fuin.porelo", "name": "porelo-stories", "version": "1",
                           "licenses": [{"license": {"id": "LGPL-3.0-or-later"}}]},
                          {"bom-ref": "x", "group": "g", "name": "gpl", "version": "2",
                           "licenses": [{"license": {"id": "GPL-3.0-or-later"}}]}
                        ], "dependencies": [
                          {"ref": "a", "dependsOn": ["s", "x"]},
                          {"ref": "s", "dependsOn": []}
                        ]}
                        """);

        assertThat(ThirdPartyNotices.licenses(bill))
                .as("a GPL jar beside porelo-stories, not below it, is the executable's own affair")
                .containsKey("g:gpl:2");
    }

    @Test
    void refusesAJarWithoutTextWhoseLicenseHasNoPinnedText() throws Exception {
        final Path jar = jar("bare-2.0.jar", "org.example", "bare", "2.0", Map.of("README", "x\n"));

        assertThatThrownBy(
                        () ->
                                ThirdPartyNotices.notices(
                                        List.of(jar),
                                        licensed("org.example:bare:2.0", "WTFPL"),
                                        jdk(),
                                        NONE_OWN))
                .as("a license with no pinned text cannot be written in full")
                .isInstanceOf(Stop.class)
                .hasMessageContaining("WTFPL");
    }

    @Test
    void readsTheLicensesFromACycloneDxBillWithIdsNamesAndExpressions() throws Exception {
        final Path bill =
                Files.writeString(
                        dir.resolve("bom.json"),
                        """
                        {"bomFormat": "CycloneDX", "components": [
                          {"group": "g", "name": "a", "version": "1",
                           "licenses": [{"license": {"id": "EPL-2.0"}},
                                        {"license": {"id": "LGPL-2.1-only"}}]},
                          {"group": "g", "name": "b", "version": "2",
                           "licenses": [{"expression": "(MIT OR Apache-2.0) AND GPL-2.0-only WITH Classpath-exception-2.0"}],
                           "hashes": [{"alg": "SHA-1", "content": "aa"}, {"alg": "SHA-256", "content": "ABCD"}]},
                          {"group": "g", "name": "c", "version": "3",
                           "licenses": [{"license": {"name": "Custom"}}],
                           "components": [{"group": "g", "name": "d", "version": "4"}]}
                        ]}
                        """);

        assertThat(ThirdPartyNotices.licenses(bill))
                .as("every way a bill names a license, nested components included")
                .isEqualTo(
                        Map.of(
                                "g:a:1", component(null, "EPL-2.0", "LGPL-2.1-only"),
                                "g:b:2",
                                        new ThirdPartyNotices.Component(
                                                List.of("MIT", "Apache-2.0", "GPL-2.0-only"),
                                                List.of("Classpath-exception-2.0"),
                                                List.of(),
                                                "abcd"),
                                "g:c:3", component(null, "Custom"),
                                "g:d:4", component(null)));
    }

    @Test
    void refusesAFileThatIsNoCycloneDxBill() throws Exception {
        final Path bill = Files.writeString(dir.resolve("bom.json"), "{\"components\": []}");

        assertThatThrownBy(() -> ThirdPartyNotices.licenses(bill))
                .as("another JSON file would name no license and refuse every jar for nothing")
                .isInstanceOf(Stop.class)
                .hasMessageContaining("no CycloneDX bill");
    }

    @Test
    void thePinnedStandardTextsAreExactlyThoseOfTheLicenseList() throws Exception {
        // Taken from SPDX license-list-data at the tag, by git, which checks every object.
        final Map<String, String> expected = new TreeMap<>();
        expected.put(
                "Apache-2.0", "074e6e32c86a4c0ef8b3ed25b721ca23aca83df277cd88106ef7177c354615ff");
        expected.put(
                "BSD-2-Clause", "f32fb3b417a194167cfad068223fc975ba96c5960513a10f66a3c28720aec1df");
        expected.put(
                "BSD-3-Clause", "5a93d5831e1297ab10fe643e1a631e83be392896da14ee2951285a79012df69d");
        expected.put(
                "Classpath-exception-2.0",
                "f36ecd7d28dc3c49854d3908a3824696972aed34f5ffe426174f35fb03c5f194");
        expected.put("EPL-1.0", "f3d8dc494be2e7ea9cd31ac2fb902e35dce7ff8181b9b30d05861b47c339cb25");
        expected.put("EPL-2.0", "61616a4066701bc7888ad910de1633dbc55eb4d4d30d930feef55afb9492e33c");
        expected.put(
                "GPL-2.0-only", "aaf135472f81c5b4a0dca9367e5bb5e9750032b5bebe5442b36e4c0a47430df3");
        // gnu.org's gpl-3.0.txt, the same bytes as this repository's own LICENSE.
        expected.put("GPL-3.0", "3972dc9744f6499f0f9b2dbf76696f2ae7ad8af9b23dde66d6af86c9dfb36986");
        expected.put(
                "LGPL-2.1-only",
                "5749785c8bdefafcb5d798270ed0a967036fe2ca63dcedade1627565dfef81d2");
        // gnu.org's lgpl-3.0.txt, the same bytes as porelo-stories' own LICENSE.
        expected.put(
                "LGPL-3.0", "e3a994d82e644b03a792a930f574002658412f62407f5fee083f2555c5f23118");
        expected.put("MIT", "b05785f9f18e6716bab63424b11454513b9943a222595b70411009202fc592b5");
        final Map<String, String> actual = new TreeMap<>();
        for (List<String> files : ThirdPartyNotices.STANDARD.values()) {
            for (String file : files) {
                actual.put(file, sha256(file));
            }
        }
        for (String exception : ThirdPartyNotices.EXCEPTIONS) {
            actual.put(exception, sha256(exception));
        }

        assertThat(actual)
                .as("a changed or swapped text would ship as the standard one")
                .isEqualTo(expected);
        assertThat(ThirdPartyNotices.standardIds())
                .as("each pinned license id")
                .containsExactly(
                        "Apache-2.0",
                        "BSD-2-Clause",
                        "BSD-3-Clause",
                        "EPL-1.0",
                        "EPL-2.0",
                        "GPL-2.0-only",
                        "GPL-2.0-with-classpath-exception",
                        "GPL-3.0-or-later",
                        "LGPL-2.1-only",
                        "LGPL-3.0-or-later",
                        "MIT");
    }

    @Test
    void leavesOutTheProjectsOwnModules() throws Exception {
        final Path own = jar("mine-0.1.jar", "org.fuin.porelo", "mine", "0.1", Map.of("X", "x\n"));

        final String text =
                ThirdPartyNotices.notices(
                        List.of(own), bill, jdk(), Pattern.compile("org\\.fuin\\.porelo:.*"));

        assertThat(text).as("the project's own module is no third party").doesNotContain("mine");
    }

    @Test
    void refusesAClassesDirectory() throws Exception {
        final Path classes = Files.createDirectories(dir.resolve("classes"));

        assertThatThrownBy(() -> ThirdPartyNotices.notices(List.of(classes), bill, jdk(), NONE_OWN))
                .as("a directory has no coordinates to leave out by, nor a jar's texts")
                .isInstanceOf(Stop.class)
                .hasMessageContaining(classes.toString());
    }

    @Test
    void writesASameTextOnceAndNamesItElsewhere() throws Exception {
        final String apache = "Same Apache text\n";
        final Path a =
                jar("alpha-1.jar", "org.example", "alpha", "1", Map.of("META-INF/LICENSE", apache));
        final Path b =
                jar("beta-1.jar", "org.example", "beta", "1", Map.of("META-INF/LICENSE", apache));

        final String text = ThirdPartyNotices.notices(List.of(b, a), bill, jdk(), NONE_OWN);

        assertThat(text.split("Same Apache text", -1)).as("the text stands once").hasSize(2);
        assertThat(text)
                .as("the second jar names where it stands")
                .contains("same text as org.example:alpha:1 META-INF/LICENSE");
    }

    @Test
    void writesTheRuntimesTextsFromTheJdk() throws Exception {
        final String text = ThirdPartyNotices.notices(List.of(), bill, jdk(), NONE_OWN);

        assertThat(text)
                .as("the runtime compiled into every native executable is one section")
                .contains("GraalVM runtime 25.9.9")
                .contains("--- LICENSE_NATIVEIMAGE.txt ---\nGPL with Classpath Exception\n")
                .contains("--- legal/java.base/aes.md ---\nAES text\n");
    }

    @Test
    void refusesAJdkWithoutLegalTexts() throws Exception {
        final Path empty = Files.createDirectories(dir.resolve("nojdk"));

        assertThatThrownBy(() -> ThirdPartyNotices.notices(List.of(), bill, empty, NONE_OWN))
                .as("without the runtime's texts the file would be incomplete")
                .isInstanceOf(Stop.class)
                .hasMessageContaining("has no legal directory");
    }

    @Test
    void theSameInputGivesTheSameBytesWhateverTheClasspathsOrder() throws Exception {
        final Path a =
                jar("alpha-1.jar", "org.example", "alpha", "1", Map.of("META-INF/LICENSE", "A\n"));
        final Path b =
                jar(
                        "beta-1.jar",
                        "org.example",
                        "beta",
                        "1",
                        Map.of("META-INF/NOTICE", "BN\n", "META-INF/LICENSE", "BL\n"));
        final Path jdk = jdk();

        assertThat(ThirdPartyNotices.notices(List.of(b, a), bill, jdk, NONE_OWN))
                .as("a package rebuilt from the same input must not change")
                .isEqualTo(ThirdPartyNotices.notices(List.of(a, b), bill, jdk, NONE_OWN));
    }

    @Test
    void carriesTheLicensesJacksonCoreEmbeds() throws Exception {
        // A real jar of the build's own: jackson-core embeds FastDoubleParser under its own
        // license, which the jar's LICENSE does not hold.
        final Path jackson = codeSource(com.fasterxml.jackson.core.JsonFactory.class);
        bill.put(
                "com.fasterxml.jackson.core:jackson-core:"
                        + com.fasterxml.jackson.core.json.PackageVersion.VERSION,
                component(null, "Apache-2.0"));

        final String text = ThirdPartyNotices.notices(List.of(jackson), bill, jdk(), NONE_OWN);

        assertThat(text)
                .as("an embedded library's license ships with the jar's")
                .contains("com.fasterxml.jackson.core:jackson-core:")
                .contains("--- META-INF/FastDoubleParser-LICENSE ---");
    }

    @Test
    void theCommandWritesTheFileAndRefusesWithExitOne() throws Exception {
        final Path good =
                jar("alpha-1.jar", "org.example", "alpha", "1", Map.of("META-INF/LICENSE", "A\n"));
        final Path bare = jar("bare-1.jar", "org.example", "bare", "1", Map.of("X", "x\n"));
        final Path classpath = Files.writeString(dir.resolve("cp.txt"), good + ":" + bare + "\n");
        final Path bill =
                Files.writeString(
                        dir.resolve("bom.json"),
                        "{\"bomFormat\": \"CycloneDX\", \"components\": [{\"group\":"
                                + " \"org.example\", \"name\": \"alpha\", \"version\": \"1\","
                                + " \"licenses\": [{\"license\": {\"id\": \"MIT\"}}]}]}");
        final Path out = dir.resolve("THIRD-PARTY-NOTICES");
        final ByteArrayOutputStream err = new ByteArrayOutputStream();

        final int refused =
                new ThirdPartyNotices(nowhere(), new PrintStream(err, true, UTF_8))
                        .write(classpath, bill, jdk(), out, NONE_OWN);

        assertThat(refused).as("a jar the bill does not name is refused").isEqualTo(Stop.REFUSED);
        assertThat(out).as("a refused run writes no file").doesNotExist();

        Files.writeString(classpath, good + "\n");
        final int written =
                new ThirdPartyNotices(nowhere(), nowhere())
                        .write(classpath, bill, jdk(), out, NONE_OWN);

        assertThat(written).as("a classpath whose jars all carry texts passes").isZero();
        assertThat(
                        java.nio.file.attribute.PosixFilePermissions.toString(
                                Files.getPosixFilePermissions(out)))
                .as("a notice every user of the package may read")
                .isEqualTo("rw-r--r--");
        assertThat(Files.readString(out)).contains("org.example:alpha:1");
    }

    @Test
    void theStandardTextsNameTheTagTheyAreTakenFrom() throws Exception {
        try (var in = ThirdPartyNotices.class.getResourceAsStream("licenses/SOURCE.txt")) {
            assertThat(in).as("the source of the texts ships with them").isNotNull();
            assertThat(new String(in.readAllBytes(), UTF_8))
                    .as("the tag the code names is the one the texts come from")
                    .contains("tag v" + ThirdPartyNotices.LICENSE_LIST + ",");
        }
    }

    private Map<String, ThirdPartyNotices.Component> licensed(String key, String... licenses) {
        bill.put(key, component(bill.get(key) == null ? null : bill.get(key).sha256(), licenses));
        return bill;
    }

    private static ThirdPartyNotices.Component component(
            @Nullable String sha256, String... licenses) {
        return new ThirdPartyNotices.Component(List.of(licenses), sha256);
    }

    private Path jdk() throws IOException {
        final Path jdk = dir.resolve("jdk");
        if (Files.isDirectory(jdk)) {
            return jdk;
        }
        Files.createDirectories(jdk.resolve("legal/java.base"));
        Files.writeString(jdk.resolve("release"), "GRAALVM_VERSION=\"25.9.9\"\n");
        Files.writeString(jdk.resolve("LICENSE_NATIVEIMAGE.txt"), "GPL with Classpath Exception\n");
        Files.writeString(jdk.resolve("legal/java.base/aes.md"), "AES text\n");
        return jdk;
    }

    private Path jar(
            String name, String group, String artifact, String version, Map<String, String> files)
            throws Exception {
        return jar(name, group, artifact, version, files, true);
    }

    private Path jar(
            String name,
            String group,
            String artifact,
            String version,
            Map<String, String> files,
            boolean properties)
            throws Exception {
        final Path jar = dir.resolve(name);
        final Map<String, String> all = new LinkedHashMap<>(files);
        if (properties) {
            all.put(
                    "META-INF/maven/" + group + "/" + artifact + "/pom.properties",
                    "groupId="
                            + group
                            + "\nartifactId="
                            + artifact
                            + "\nversion="
                            + version
                            + "\n");
        }
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(jar))) {
            for (Map.Entry<String, String> file : all.entrySet()) {
                zip.putNextEntry(new ZipEntry(file.getKey()));
                zip.write(file.getValue().getBytes(UTF_8));
                zip.closeEntry();
            }
        }
        bill.put(
                group + ":" + artifact + ":" + version,
                new ThirdPartyNotices.Component(
                        List.of("MIT"),
                        HexFormat.of()
                                .formatHex(
                                        MessageDigest.getInstance("SHA-256")
                                                .digest(Files.readAllBytes(jar)))));
        return jar;
    }

    private static String sha256(String id) throws Exception {
        try (var in = ThirdPartyNotices.class.getResourceAsStream("licenses/" + id + ".txt")) {
            assertThat(in).as("the text of " + id + " is in the build").isNotNull();
            return HexFormat.of()
                    .formatHex(MessageDigest.getInstance("SHA-256").digest(in.readAllBytes()));
        }
    }

    private static Path codeSource(Class<?> type) throws URISyntaxException {
        return Path.of(type.getProtectionDomain().getCodeSource().getLocation().toURI());
    }

    private static PrintStream nowhere() {
        return new PrintStream(OutputStream.nullOutputStream(), true, UTF_8);
    }
}
