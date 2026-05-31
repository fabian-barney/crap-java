package media.barney.crap.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
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

        CoverageIndex result = JacocoCoverageParser.parse(xml);

        assertEquals(90.0, coverage(result, "demo.Sample", "alpha", 10).percent(), 0.001);
        assertEquals("instruction", coverage(result, "demo.Sample", "alpha", 10).kind());
        assertEquals(0.0, coverage(result, "demo.Sample", "beta", 20).percent(), 0.001);
        assertEquals("instruction", coverage(result, "demo.Sample", "beta", 20).kind());
    }

    @Test
    void recordsDuplicateSourceCoordinateAsAmbiguous() throws IOException {
        Path xml = tempDir.resolve("jacoco-duplicate-source-coordinate.xml");
        Files.writeString(xml, """
                <report name="demo">
                  <package name="demo">
                    <class name="demo/Sample" sourcefilename="Sample.java">
                      <method name="sum" desc="(II)I" line="10">
                        <counter type="INSTRUCTION" missed="1" covered="9"/>
                      </method>
                      <method name="sum" desc="(DD)I" line="10">
                        <counter type="INSTRUCTION" missed="9" covered="1"/>
                      </method>
                    </class>
                  </package>
                </report>
                """);

        CoverageIndex result = JacocoCoverageParser.parse(xml);

        assertEquals(2, result.entryCount("demo.Sample", "sum", 10));
        assertNull(result.lookupCoverage("demo.Sample", "sum", 10));
    }

    @Test
    void skipsSyntheticLambdaImplementationMethods() throws IOException {
        Path xml = tempDir.resolve("jacoco-lambda.xml");
        Files.writeString(xml, """
                <report name="demo">
                  <package name="demo">
                    <class name="demo/Sample" sourcefilename="Sample.java">
                      <method name="alpha" desc="()V" line="10">
                        <counter type="INSTRUCTION" missed="1" covered="9"/>
                      </method>
                      <method name="lambda$alpha$0" desc="()V" line="11">
                        <counter type="INSTRUCTION" missed="0" covered="10"/>
                      </method>
                    </class>
                  </package>
                </report>
                """);

        CoverageIndex result = JacocoCoverageParser.parse(xml);

        assertEquals(90.0, coverage(result, "demo.Sample", "alpha", 10).percent(), 0.001);
        assertNull(result.lookupCoverage("demo.Sample", "lambda$alpha$0", 11));
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

        EffectiveCoverage result = coverage(JacocoCoverageParser.parse(xml), "demo.Sample", "alpha", 10);

        assertEquals(50.0, result.percent(), 0.001);
        assertEquals("branch", result.kind());
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

        EffectiveCoverage result = coverage(JacocoCoverageParser.parse(xml), "demo.Sample", "alpha", 10);

        assertEquals(75.0, result.percent(), 0.001);
        assertEquals("instruction", result.kind());
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

        EffectiveCoverage result = coverage(JacocoCoverageParser.parse(xml), "demo.Sample", "alpha", 10);

        assertEquals(50.0, result.percent(), 0.001);
        assertEquals("instruction", result.kind());
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

        EffectiveCoverage result = coverage(JacocoCoverageParser.parse(xml), "demo.Sample", "alpha", 10);

        assertEquals(0.0, result.percent(), 0.001);
        assertEquals("instruction", result.kind());
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

        CoverageIndex result = JacocoCoverageParser.parse(xml);

        assertNull(result.lookupCoverage("demo.Sample", "alpha", 10));
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

        CoverageIndex result = JacocoCoverageParser.parse(xml);

        assertEquals(90.0, coverage(result, "demo.Sample", "alpha", 10).percent(), 0.001);
    }

    @Test
    void rejectsInvalidLineNumbers() throws IOException {
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

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> JacocoCoverageParser.parse(xml));

        String message = Objects.requireNonNull(Objects.requireNonNull(error.getCause()).getMessage());
        assertTrue(message.contains("Invalid JaCoCo integer attribute line=\"oops\""));
    }

    @Test
    void rejectsInvalidCounterValues() throws IOException {
        Path xml = tempDir.resolve("jacoco-invalid-counter.xml");
        Files.writeString(xml, """
                <report name="demo">
                  <package name="demo">
                    <class name="demo/Sample" sourcefilename="Sample.java">
                      <method name="alpha" desc="()V" line="10">
                        <counter type="INSTRUCTION" missed="abc" covered="9"/>
                      </method>
                    </class>
                  </package>
                </report>
                """);

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> JacocoCoverageParser.parse(xml));

        String message = Objects.requireNonNull(Objects.requireNonNull(error.getCause()).getMessage());
        assertTrue(message.contains("Invalid JaCoCo integer attribute missed=\"abc\""));
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

    private static EffectiveCoverage coverage(CoverageIndex index, String className, String methodName, int line) {
        return Objects.requireNonNull(index.lookupCoverage(className, methodName, line));
    }
}

