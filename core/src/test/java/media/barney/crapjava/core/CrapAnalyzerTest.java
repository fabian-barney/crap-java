package media.barney.crapjava.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class CrapAnalyzerTest {

    @TempDir
    Path tempDir;

    @Test
    void computesScoresForChangedFiles() throws IOException {
        Path sourceRoot = tempDir.resolve("src/main/java/demo");
        Files.createDirectories(sourceRoot);
        Path source = sourceRoot.resolve("Sample.java");
        Files.writeString(source, """
                package demo;
                class Sample {
                    int alpha(boolean a) {
                        if (a) {
                            return 1;
                        }
                        return 0;
                    }
                }
                """);

        Path jacoco = tempDir.resolve("jacoco.xml");
        Files.writeString(jacoco, """
                <report>
                  <package name=\"demo\">
                    <class name=\"demo/Sample\" sourcefilename=\"Sample.java\">
                      <method name=\"alpha\" desc=\"(Z)I\" line=\"3\">
                        <counter type=\"INSTRUCTION\" missed=\"1\" covered=\"3\"/>
                      </method>
                    </class>
                  </package>
                </report>
                """);

        List<MethodMetrics> result = CrapAnalyzer.analyze(
                tempDir,
                List.of(source),
                jacoco
        );

        assertEquals(1, result.size());
        MethodMetrics metric = result.get(0);
        assertEquals("alpha", metric.methodName());
        assertEquals("demo.Sample", metric.className());
        assertEquals(2, metric.complexity());
        assertEquals(75.0, Objects.requireNonNull(metric.coveragePercent()), 0.001);
        assertEquals(2.0625, Objects.requireNonNull(metric.crapScore()), 0.00001);
    }

    @Test
    void usesSimpleClassNameWhenSourceHasNoPackage() {
        String className = CrapAnalyzer.classNameFromSource(
                Path.of("src/main/java/Sample.java"),
                """
                class Sample {
                }
                """
        );

        assertEquals("Sample", className);
    }

    @Test
    void lookupCoveragePrefersExactLineBeforeNearestMatch() {
        Map<String, CoverageData> coverageMap = Map.of(
                "demo.Sample#alpha:10", new CoverageData(1, 3),
                "demo.Sample#alpha:12", new CoverageData(0, 8)
        );

        Double coverage = CrapAnalyzer.lookupCoverage(coverageMap, "demo.Sample", "alpha", 10);

        assertEquals(75.0, Objects.requireNonNull(coverage), 0.001);
    }

    @Test
    void lookupCoverageFallsBackToNearestLineWithinMethod() {
        Map<String, CoverageData> coverageMap = Map.of(
                "demo.Sample#alpha:10", new CoverageData(1, 3),
                "demo.Sample#alpha:15", new CoverageData(0, 8)
        );

        Double coverage = CrapAnalyzer.lookupCoverage(coverageMap, "demo.Sample", "alpha", 13);

        assertEquals(100.0, Objects.requireNonNull(coverage), 0.001);
    }

    @Test
    void nearestCoverageKeepsFirstEntryWhenDistancesTie() {
        Map<String, CoverageData> coverageMap = new LinkedHashMap<>();
        coverageMap.put("demo.Sample#alpha:10", new CoverageData(1, 3));
        coverageMap.put("demo.Sample#alpha:14", new CoverageData(0, 8));

        CoverageData nearest = Objects.requireNonNull(CrapAnalyzer.nearestCoverage(coverageMap, "demo.Sample", "alpha", 12));

        assertEquals(75.0, nearest.coveragePercent(), 0.001);
    }

    @Test
    void lookupCoverageReturnsNullWhenMethodHasNoCoverageEntries() {
        Double coverage = CrapAnalyzer.lookupCoverage(Map.of(), "demo.Sample", "alpha", 10);

        assertNull(coverage);
    }

    @Test
    void parseTrailingLineReturnsMaxValueForMalformedKeys() {
        assertEquals(Integer.MAX_VALUE, CrapAnalyzer.parseTrailingLine("demo.Sample#alpha"));
        assertEquals(Integer.MAX_VALUE, CrapAnalyzer.parseTrailingLine("demo.Sample#alpha:"));
        assertEquals(Integer.MAX_VALUE, CrapAnalyzer.parseTrailingLine("demo.Sample#alpha:oops"));
    }

    @Test
    void parseTrailingLineReturnsParsedLineNumberForValidKey() {
        assertEquals(10, CrapAnalyzer.parseTrailingLine("demo.Sample#alpha:10"));
    }

    @Test
    void parseTrailingLineAcceptsLeadingSeparator() {
        assertEquals(10, CrapAnalyzer.parseTrailingLine(":10"));
    }

    @Test
    void sortsMetricsByScoreDescendingWithNullsLast() throws IOException {
        Path sourceRoot = tempDir.resolve("src/main/java/demo");
        Files.createDirectories(sourceRoot);
        Path source = sourceRoot.resolve("Sample.java");
        Files.writeString(source, """
                package demo;

                class Sample {
                    int alpha(boolean a) {
                        if (a) {
                            return 1;
                        }
                        return 0;
                    }

                    int beta() {
                        return 1;
                    }

                    int gamma() {
                        return 2;
                    }
                }
                """);

        Path jacoco = tempDir.resolve("jacoco.xml");
        Files.writeString(jacoco, """
                <report>
                  <package name="demo">
                    <class name="demo/Sample" sourcefilename="Sample.java">
                      <method name="alpha" desc="(Z)I" line="4">
                        <counter type="INSTRUCTION" missed="4" covered="0"/>
                      </method>
                      <method name="beta" desc="()I" line="10">
                        <counter type="INSTRUCTION" missed="0" covered="4"/>
                      </method>
                    </class>
                  </package>
                </report>
                """);

        List<MethodMetrics> result = CrapAnalyzer.analyze(tempDir, List.of(source), jacoco);

        assertEquals(List.of("alpha", "beta", "gamma"),
                result.stream().map(MethodMetrics::methodName).toList());
    }
}
