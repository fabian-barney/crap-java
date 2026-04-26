package media.barney.crap.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JacocoCoverageParserTest {

    @TempDir
    Path tempDir;

    @Test
    void parsesCoverageByClassAndMethod() throws IOException {
        Path xml = tempDir.resolve("jacoco.xml");
        Files.writeString(xml, """
                <report name=\"demo\">
                  <package name=\"demo\">
                    <class name=\"demo/Sample\" sourcefilename=\"Sample.java\">
                      <method name=\"alpha\" desc=\"()V\" line=\"10\">
                        <counter type=\"INSTRUCTION\" missed=\"1\" covered=\"9\"/>
                      </method>
                      <method name=\"beta\" desc=\"()V\" line=\"20\">
                        <counter type=\"INSTRUCTION\" missed=\"0\" covered=\"0\"/>
                      </method>
                    </class>
                  </package>
                </report>
                """);

        Map<String, CoverageData> result = JacocoCoverageParser.parse(xml);

        assertEquals(90.0, Objects.requireNonNull(result.get("demo.Sample#alpha:10")).coveragePercent(), 0.001);
        assertEquals("instruction", Objects.requireNonNull(result.get("demo.Sample#alpha:10")).coverageKind());
        assertEquals(0.0, Objects.requireNonNull(result.get("demo.Sample#beta:20")).coveragePercent(), 0.001);
        assertEquals("instruction", Objects.requireNonNull(result.get("demo.Sample#beta:20")).coverageKind());
    }

    @Test
    void usesBranchCoverageWhenBranchCoverageIsWorse() throws IOException {
        Path xml = tempDir.resolve("jacoco-branch-worse.xml");
        Files.writeString(xml, """
                <report name="demo">
                  <package name="demo">
                    <class name="demo/Sample" sourcefilename="Sample.java">
                      <method name="alpha" desc="()V" line="10">
                        <counter type="INSTRUCTION" missed="1" covered="9"/>
                        <counter type="BRANCH" missed="1" covered="1"/>
                      </method>
                    </class>
                  </package>
                </report>
                """);

        CoverageData result = Objects.requireNonNull(JacocoCoverageParser.parse(xml).get("demo.Sample#alpha:10"));

        assertEquals(50.0, result.coveragePercent(), 0.001);
        assertEquals("branch", result.coverageKind());
    }

    @Test
    void usesInstructionCoverageWhenInstructionCoverageIsWorse() throws IOException {
        Path xml = tempDir.resolve("jacoco-instruction-worse.xml");
        Files.writeString(xml, """
                <report name="demo">
                  <package name="demo">
                    <class name="demo/Sample" sourcefilename="Sample.java">
                      <method name="alpha" desc="()V" line="10">
                        <counter type="INSTRUCTION" missed="1" covered="3"/>
                        <counter type="BRANCH" missed="0" covered="2"/>
                      </method>
                    </class>
                  </package>
                </report>
                """);

        CoverageData result = Objects.requireNonNull(JacocoCoverageParser.parse(xml).get("demo.Sample#alpha:10"));

        assertEquals(75.0, result.coveragePercent(), 0.001);
        assertEquals("instruction", result.coverageKind());
    }

    @Test
    void usesInstructionCoverageWhenCoverageTies() throws IOException {
        Path xml = tempDir.resolve("jacoco-tie.xml");
        Files.writeString(xml, """
                <report name="demo">
                  <package name="demo">
                    <class name="demo/Sample" sourcefilename="Sample.java">
                      <method name="alpha" desc="()V" line="10">
                        <counter type="INSTRUCTION" missed="1" covered="1"/>
                        <counter type="BRANCH" missed="1" covered="1"/>
                      </method>
                    </class>
                  </package>
                </report>
                """);

        CoverageData result = Objects.requireNonNull(JacocoCoverageParser.parse(xml).get("demo.Sample#alpha:10"));

        assertEquals(50.0, result.coveragePercent(), 0.001);
        assertEquals("instruction", result.coverageKind());
    }

    @Test
    void zeroTotalCoverageUsesInstructionTieBreak() throws IOException {
        Path xml = tempDir.resolve("jacoco-zero-total.xml");
        Files.writeString(xml, """
                <report name="demo">
                  <package name="demo">
                    <class name="demo/Sample" sourcefilename="Sample.java">
                      <method name="alpha" desc="()V" line="10">
                        <counter type="INSTRUCTION" missed="0" covered="0"/>
                        <counter type="BRANCH" missed="0" covered="0"/>
                      </method>
                    </class>
                  </package>
                </report>
                """);

        CoverageData result = Objects.requireNonNull(JacocoCoverageParser.parse(xml).get("demo.Sample#alpha:10"));

        assertEquals(0.0, result.coveragePercent(), 0.001);
        assertEquals("instruction", result.coverageKind());
    }

    @Test
    void skipsMethodsWithoutInstructionCounter() throws IOException {
        Path xml = tempDir.resolve("jacoco-no-instruction.xml");
        Files.writeString(xml, """
                <report name="demo">
                  <package name="demo">
                    <class name="demo/Sample" sourcefilename="Sample.java">
                      <method name="alpha" desc="()V" line="10">
                        <counter type="BRANCH" missed="0" covered="2"/>
                      </method>
                    </class>
                  </package>
                </report>
                """);

        Map<String, CoverageData> result = JacocoCoverageParser.parse(xml);

        assertNull(result.get("demo.Sample#alpha:10"));
    }

    @Test
    void parsesXmlWithDoctypeWithoutRequiringLocalDtdFile() throws IOException {
        Path xml = tempDir.resolve("jacoco-with-doctype.xml");
        Files.writeString(xml, """
                <!DOCTYPE report PUBLIC "-//JACOCO//DTD Report 1.1//EN" "report.dtd">
                <report name="demo">
                  <package name="demo">
                    <class name="demo/Sample" sourcefilename="Sample.java">
                      <method name="alpha" desc="()V" line="10">
                        <counter type="INSTRUCTION" missed="1" covered="9"/>
                      </method>
                    </class>
                  </package>
                </report>
                """);

        Map<String, CoverageData> result = JacocoCoverageParser.parse(xml);

        assertEquals(90.0, Objects.requireNonNull(result.get("demo.Sample#alpha:10")).coveragePercent(), 0.001);
    }

    @Test
    void parsesInvalidLineNumbersAsZero() throws IOException {
        Path xml = tempDir.resolve("jacoco-invalid-line.xml");
        Files.writeString(xml, """
                <report name="demo">
                  <package name="demo">
                    <class name="demo/Sample" sourcefilename="Sample.java">
                      <method name="alpha" desc="()V" line="oops">
                        <counter type="INSTRUCTION" missed="1" covered="9"/>
                      </method>
                    </class>
                  </package>
                </report>
                """);

        Map<String, CoverageData> result = JacocoCoverageParser.parse(xml);

        assertEquals(90.0, Objects.requireNonNull(result.get("demo.Sample#alpha:0")).coveragePercent(), 0.001);
    }

    @Test
    void configuresSecureFactoryFeatures() throws Exception {
        var factory = JacocoCoverageParser.newSecureFactory();

        assertTrue(factory.getFeature(javax.xml.XMLConstants.FEATURE_SECURE_PROCESSING));
        assertFalse(factory.getFeature("http://apache.org/xml/features/disallow-doctype-decl"));
        assertFalse(factory.getFeature("http://xml.org/sax/features/external-general-entities"));
        assertFalse(factory.getFeature("http://xml.org/sax/features/external-parameter-entities"));
        assertFalse(factory.getFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd"));
        assertEquals("", factory.getAttribute(javax.xml.XMLConstants.ACCESS_EXTERNAL_DTD));
        assertEquals("", factory.getAttribute(javax.xml.XMLConstants.ACCESS_EXTERNAL_SCHEMA));
        assertFalse(factory.isXIncludeAware());
        assertFalse(factory.isExpandEntityReferences());
    }
}

