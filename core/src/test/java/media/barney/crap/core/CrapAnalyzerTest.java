package media.barney.crap.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

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
        assertEquals("instruction", metric.coverageKind());
        assertEquals(2.0625, Objects.requireNonNull(metric.crapScore()), 0.00001);
    }

    @Test
    void computesScoresFromBranchCoverageWhenBranchCoverageIsWorse() throws IOException {
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
                  <package name="demo">
                    <class name="demo/Sample" sourcefilename="Sample.java">
                      <method name="alpha" desc="(Z)I" line="3">
                        <counter type="INSTRUCTION" missed="1" covered="3"/>
                        <counter type="BRANCH" missed="1" covered="1"/>
                      </method>
                    </class>
                  </package>
                </report>
                """);

        MethodMetrics metric = CrapAnalyzer.analyze(tempDir, List.of(source), jacoco).get(0);

        assertEquals(50.0, Objects.requireNonNull(metric.coveragePercent()), 0.001);
        assertEquals("branch", metric.coverageKind());
        assertEquals(2.5, Objects.requireNonNull(metric.crapScore()), 0.00001);
    }

    @Test
    void computesScoresFromInstructionCoverageWhenInstructionCoverageTies() throws IOException {
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
                  <package name="demo">
                    <class name="demo/Sample" sourcefilename="Sample.java">
                      <method name="alpha" desc="(Z)I" line="3">
                        <counter type="INSTRUCTION" missed="1" covered="1"/>
                        <counter type="BRANCH" missed="1" covered="1"/>
                      </method>
                    </class>
                  </package>
                </report>
                """);

        MethodMetrics metric = CrapAnalyzer.analyze(tempDir, List.of(source), jacoco).get(0);

        assertEquals(50.0, Objects.requireNonNull(metric.coveragePercent()), 0.001);
        assertEquals("instruction", metric.coverageKind());
        assertEquals(2.5, Objects.requireNonNull(metric.crapScore()), 0.00001);
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
    void attributesCoverageToNestedAndSecondaryTypes() throws IOException {
        Path sourceRoot = tempDir.resolve("src/main/java/demo");
        Files.createDirectories(sourceRoot);
        Path source = sourceRoot.resolve("Outer.java");
        Files.writeString(source, """
                package demo;

                class Outer {
                    int outer() {
                        return 1;
                    }

                    static class Inner {
                        int inner() {
                            return 2;
                        }
                    }
                }

                class Secondary {
                    int beta() {
                        return 3;
                    }
                }
                """);

        Path jacoco = tempDir.resolve("jacoco.xml");
        Files.writeString(jacoco, """
                <report>
                  <package name="demo">
                    <class name="demo/Outer" sourcefilename="Outer.java">
                      <method name="outer" desc="()I" line="4">
                        <counter type="INSTRUCTION" missed="0" covered="1"/>
                      </method>
                    </class>
                    <class name="demo/Outer$Inner" sourcefilename="Outer.java">
                      <method name="inner" desc="()I" line="9">
                        <counter type="INSTRUCTION" missed="0" covered="1"/>
                      </method>
                    </class>
                    <class name="demo/Secondary" sourcefilename="Outer.java">
                      <method name="beta" desc="()I" line="15">
                        <counter type="INSTRUCTION" missed="0" covered="1"/>
                      </method>
                    </class>
                  </package>
                </report>
                """);

        Map<String, MethodMetrics> metricsByMethod = CrapAnalyzer.analyze(tempDir, List.of(source), jacoco).stream()
                .collect(java.util.stream.Collectors.toMap(MethodMetrics::methodName, Function.identity()));

        assertEquals("demo.Outer", Objects.requireNonNull(metricsByMethod.get("outer")).className());
        assertEquals(100.0, Objects.requireNonNull(Objects.requireNonNull(metricsByMethod.get("outer")).coveragePercent()), 0.001);
        assertEquals("instruction", Objects.requireNonNull(metricsByMethod.get("outer")).coverageKind());
        assertEquals("demo.Outer$Inner", Objects.requireNonNull(metricsByMethod.get("inner")).className());
        assertEquals(100.0, Objects.requireNonNull(Objects.requireNonNull(metricsByMethod.get("inner")).coveragePercent()), 0.001);
        assertEquals("instruction", Objects.requireNonNull(metricsByMethod.get("inner")).coverageKind());
        assertEquals("demo.Secondary", Objects.requireNonNull(metricsByMethod.get("beta")).className());
        assertEquals(100.0, Objects.requireNonNull(Objects.requireNonNull(metricsByMethod.get("beta")).coveragePercent()), 0.001);
        assertEquals("instruction", Objects.requireNonNull(metricsByMethod.get("beta")).coverageKind());
    }

    @Test
    void lookupCoveragePrefersExactLineBeforeNearestMatch() {
        Map<String, CoverageData> coverageMap = Map.of(
                "demo.Sample#alpha:10", new CoverageData(1, 3),
                "demo.Sample#alpha:12", new CoverageData(0, 8)
        );

        EffectiveCoverage coverage = CrapAnalyzer.lookupCoverage(coverageMap, "demo.Sample", "alpha", 10);

        assertEquals(75.0, Objects.requireNonNull(coverage).percent(), 0.001);
        assertEquals("instruction", coverage.kind());
    }

    @Test
    void lookupCoverageFallsBackToNearestLineWithinMethod() {
        Map<String, CoverageData> coverageMap = Map.of(
                "demo.Sample#alpha:10", new CoverageData(1, 3),
                "demo.Sample#alpha:15", new CoverageData(0, 8)
        );

        EffectiveCoverage coverage = CrapAnalyzer.lookupCoverage(coverageMap, "demo.Sample", "alpha", 13);

        assertEquals(100.0, Objects.requireNonNull(coverage).percent(), 0.001);
        assertEquals("instruction", coverage.kind());
    }

    @Test
    void lookupCoverageReturnsBranchKindWhenBranchCoverageIsWorse() {
        Map<String, CoverageData> coverageMap = Map.of(
                "demo.Sample#alpha:10", new CoverageData(1, 9, 1, 1)
        );

        EffectiveCoverage coverage = CrapAnalyzer.lookupCoverage(coverageMap, "demo.Sample", "alpha", 10);

        assertEquals(50.0, Objects.requireNonNull(coverage).percent(), 0.001);
        assertEquals("branch", coverage.kind());
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
        EffectiveCoverage coverage = CrapAnalyzer.lookupCoverage(Map.of(), "demo.Sample", "alpha", 10);

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

