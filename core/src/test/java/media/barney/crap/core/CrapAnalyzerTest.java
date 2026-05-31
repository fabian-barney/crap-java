package media.barney.crap.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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
    void excludesClassesAnnotatedWithGeneratedSimpleNameFromAnyPackage() throws IOException {
        Path sourceRoot = tempDir.resolve("src/main/java/demo");
        Files.createDirectories(sourceRoot);
        Path source = sourceRoot.resolve("Sample.java");
        Files.writeString(source, """
                package demo;

                @javax.annotation.processing.Generated("tool")
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
        Files.writeString(jacoco, "<report/>");
        SourceExclusionAudit.Builder audit = SourceExclusionAudit.builder();
        List<MethodMetrics> result = CrapAnalyzer.analyze(
                tempDir,
                List.of(source),
                jacoco,
                SourceExclusionMatcher.create(tempDir, SourceExclusionOptions.defaults()),
                audit
        );

        assertEquals(List.of(), result);
        assertEquals(1, audit.build().excludedClassCount());
    }

    @Test
    void generatedAnnotationExclusionDoesNotApplyOuterAnnotationsToNestedClasses() throws IOException {
        Path sourceRoot = tempDir.resolve("src/main/java/demo");
        Files.createDirectories(sourceRoot);
        Path source = sourceRoot.resolve("Outer.java");
        Files.writeString(source, """
                package demo;

                @interface Generated {
                }

                @Generated
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
                """);

        Path jacoco = tempDir.resolve("jacoco.xml");
        Files.writeString(jacoco, """
                <report>
                  <package name="demo">
                    <class name="demo/Outer" sourcefilename="Outer.java">
                      <method name="outer" desc="()I" line="8">
                        <counter type="INSTRUCTION" missed="0" covered="1"/>
                      </method>
                    </class>
                    <class name="demo/Outer$Inner" sourcefilename="Outer.java">
                      <method name="inner" desc="()I" line="13">
                        <counter type="INSTRUCTION" missed="0" covered="1"/>
                      </method>
                    </class>
                  </package>
                </report>
                """);
        SourceExclusionAudit.Builder audit = SourceExclusionAudit.builder();

        List<MethodMetrics> result = CrapAnalyzer.analyze(
                tempDir,
                List.of(source),
                jacoco,
                SourceExclusionMatcher.create(tempDir, SourceExclusionOptions.defaults()),
                audit
        );

        assertEquals(List.of("inner"), result.stream().map(MethodMetrics::methodName).toList());
        assertEquals("demo.Outer$Inner", result.get(0).className());
        assertEquals(1, audit.build().excludedClassCount());
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
    void ignoresPackageDeclarationsInsideBlockCommentsWhenAttributingCoverage() throws IOException {
        Path sourceRoot = tempDir.resolve("src/main/java/real/example");
        Files.createDirectories(sourceRoot);
        Path source = sourceRoot.resolve("Sample.java");
        Files.writeString(source, """
                /*
                 * Example package declaration:
                package fake.example;
                 */
                package real.example;

                class Sample {
                    int alpha() {
                        return 1;
                    }
                }
                """);

        Path jacoco = tempDir.resolve("jacoco.xml");
        Files.writeString(jacoco, """
                <report>
                  <package name="real/example">
                    <class name="real/example/Sample" sourcefilename="Sample.java">
                      <method name="alpha" desc="()I" line="8">
                        <counter type="INSTRUCTION" missed="0" covered="1"/>
                      </method>
                    </class>
                  </package>
                </report>
                """);

        MethodMetrics metric = CrapAnalyzer.analyze(tempDir, List.of(source), jacoco).get(0);

        assertEquals("real.example.Sample", metric.className());
        assertEquals(100.0, Objects.requireNonNull(metric.coveragePercent()), 0.001);
    }

    @Test
    void attributesCoverageToDefaultPackageSources() throws IOException {
        Path sourceRoot = tempDir.resolve("src/main/java");
        Files.createDirectories(sourceRoot);
        Path source = sourceRoot.resolve("Sample.java");
        Files.writeString(source, """
                class Sample {
                    int alpha() {
                        return 1;
                    }
                }
                """);

        Path jacoco = tempDir.resolve("jacoco.xml");
        Files.writeString(jacoco, """
                <report>
                  <package name="">
                    <class name="Sample" sourcefilename="Sample.java">
                      <method name="alpha" desc="()I" line="2">
                        <counter type="INSTRUCTION" missed="0" covered="1"/>
                      </method>
                    </class>
                  </package>
                </report>
                """);

        MethodMetrics metric = CrapAnalyzer.analyze(tempDir, List.of(source), jacoco).get(0);

        assertEquals("Sample", metric.className());
        assertEquals(100.0, Objects.requireNonNull(metric.coveragePercent()), 0.001);
    }

    @Test
    void sameLineOverloadCoverageCollisionsAreSkippedAsUnavailable() throws IOException {
        Path sourceRoot = tempDir.resolve("src/main/java/demo");
        Files.createDirectories(sourceRoot);
        Path source = sourceRoot.resolve("Sample.java");
        Files.writeString(source, """
                package demo;
                class Sample {
                    int sum(int a, int b) { return a + b; } int sum(double a, double b) { return (int) (a + b); }
                }
                """);

        Path jacoco = tempDir.resolve("jacoco.xml");
        Files.writeString(jacoco, """
                <report>
                  <package name="demo">
                    <class name="demo/Sample" sourcefilename="Sample.java">
                      <method name="sum" desc="(II)I" line="3">
                        <counter type="INSTRUCTION" missed="1" covered="3"/>
                      </method>
                      <method name="sum" desc="(DD)I" line="3">
                        <counter type="INSTRUCTION" missed="0" covered="8"/>
                      </method>
                    </class>
                  </package>
                </report>
                """);

        List<MethodMetrics> result = CrapAnalyzer.analyze(tempDir, List.of(source), jacoco);

        assertEquals(2, result.size());
        for (MethodMetrics metric : result) {
            assertEquals("sum", metric.methodName());
            assertEquals(3, metric.startLine());
            assertNull(metric.coveragePercent());
            assertEquals("N/A", metric.coverageKind());
            assertNull(metric.crapScore());
        }
    }

    @Test
    void overloadsOnDifferentLinesKeepUniqueCoverage() throws IOException {
        Path sourceRoot = tempDir.resolve("src/main/java/demo");
        Files.createDirectories(sourceRoot);
        Path source = sourceRoot.resolve("Sample.java");
        Files.writeString(source, """
                package demo;

                class Sample {
                    int sum(int a, int b) {
                        return a + b;
                    }

                    int sum(double a, double b) {
                        return (int) (a + b);
                    }
                }
                """);

        Path jacoco = tempDir.resolve("jacoco.xml");
        Files.writeString(jacoco, """
                <report>
                  <package name="demo">
                    <class name="demo/Sample" sourcefilename="Sample.java">
                      <method name="sum" desc="(II)I" line="4">
                        <counter type="INSTRUCTION" missed="1" covered="3"/>
                      </method>
                      <method name="sum" desc="(DD)I" line="8">
                        <counter type="INSTRUCTION" missed="1" covered="9"/>
                        <counter type="BRANCH" missed="1" covered="1"/>
                      </method>
                    </class>
                  </package>
                </report>
                """);

        Map<Integer, MethodMetrics> metricsByLine = CrapAnalyzer.analyze(tempDir, List.of(source), jacoco).stream()
                .collect(java.util.stream.Collectors.toMap(MethodMetrics::startLine, Function.identity()));

        MethodMetrics intOverload = Objects.requireNonNull(metricsByLine.get(4));
        assertEquals(75.0, Objects.requireNonNull(intOverload.coveragePercent()), 0.001);
        assertEquals("instruction", intOverload.coverageKind());
        MethodMetrics doubleOverload = Objects.requireNonNull(metricsByLine.get(8));
        assertEquals(50.0, Objects.requireNonNull(doubleOverload.coveragePercent()), 0.001);
        assertEquals("branch", doubleOverload.coverageKind());
    }

    @Test
    void lookupCoveragePrefersExactLineBeforeNearestMatch() {
        CoverageIndex coverageIndex = coverageIndex(
                entry("alpha", 10, new CoverageData(1, 3)),
                entry("alpha", 12, new CoverageData(0, 8))
        );

        EffectiveCoverage coverage = coverageIndex.lookupCoverage("demo.Sample", "alpha", 10);

        assertEquals(75.0, Objects.requireNonNull(coverage).percent(), 0.001);
        assertEquals("instruction", coverage.kind());
    }

    @Test
    void lookupCoverageFallsBackToNearestLineWithinMethod() {
        CoverageIndex coverageIndex = coverageIndex(
                entry("alpha", 10, new CoverageData(1, 3)),
                entry("alpha", 15, new CoverageData(0, 8))
        );

        EffectiveCoverage coverage = coverageIndex.lookupCoverage("demo.Sample", "alpha", 13);

        assertEquals(100.0, Objects.requireNonNull(coverage).percent(), 0.001);
        assertEquals("instruction", coverage.kind());
    }

    @Test
    void lookupCoverageReturnsBranchKindWhenBranchCoverageIsWorse() {
        CoverageIndex coverageIndex = coverageIndex(entry("alpha", 10, new CoverageData(1, 9, 1, 1)));

        EffectiveCoverage coverage = coverageIndex.lookupCoverage("demo.Sample", "alpha", 10);

        assertEquals(50.0, Objects.requireNonNull(coverage).percent(), 0.001);
        assertEquals("branch", coverage.kind());
    }

    @Test
    void nearestCoverageKeepsFirstEntryWhenDistancesTie() {
        CoverageIndex coverageIndex = coverageIndex(
                entry("alpha", 10, new CoverageData(1, 3)),
                entry("alpha", 14, new CoverageData(0, 8))
        );

        EffectiveCoverage nearest = Objects.requireNonNull(coverageIndex.nearestCoverage("demo.Sample", "alpha", 12));

        assertEquals(75.0, nearest.percent(), 0.001);
    }

    @Test
    void lookupCoverageReturnsNullWhenMethodHasNoCoverageEntries() {
        EffectiveCoverage coverage = CoverageIndex.empty().lookupCoverage("demo.Sample", "alpha", 10);

        assertNull(coverage);
    }

    @Test
    void parseTrailingLineReturnsMaxValueForMalformedKeys() {
        assertEquals(Integer.MAX_VALUE, CoverageIndex.parseTrailingLine("demo.Sample#alpha"));
        assertEquals(Integer.MAX_VALUE, CoverageIndex.parseTrailingLine("demo.Sample#alpha:"));
        assertEquals(Integer.MAX_VALUE, CoverageIndex.parseTrailingLine("demo.Sample#alpha:oops"));
    }

    @Test
    void parseTrailingLineReturnsParsedLineNumberForValidKey() {
        assertEquals(10, CoverageIndex.parseTrailingLine("demo.Sample#alpha:10"));
    }

    @Test
    void parseTrailingLineAcceptsLeadingSeparator() {
        assertEquals(10, CoverageIndex.parseTrailingLine(":10"));
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

    @Test
    void sortsEqualScoresBySourcePathThenLineAndMethodName() throws IOException {
        Path sourceRoot = tempDir.resolve("src/main/java/demo");
        Files.createDirectories(sourceRoot);
        Path firstSource = sourceRoot.resolve("Alpha.java");
        Files.writeString(firstSource, """
                package demo;

                class Alpha {
                    int zeta() {
                        return 1;
                    }

                    int omega() {
                        return 2;
                    }
                }
                """);
        Path secondSource = sourceRoot.resolve("Beta.java");
        Files.writeString(secondSource, """
                package demo;

                class Beta {
                    int beta() {
                        return 1;
                    }
                }
                """);

        Path jacoco = tempDir.resolve("jacoco.xml");
        Files.writeString(jacoco, """
                <report>
                  <package name="demo">
                    <class name="demo/Alpha" sourcefilename="Alpha.java">
                      <method name="zeta" desc="()I" line="4">
                        <counter type="INSTRUCTION" missed="0" covered="1"/>
                      </method>
                      <method name="omega" desc="()I" line="8">
                        <counter type="INSTRUCTION" missed="0" covered="1"/>
                      </method>
                    </class>
                    <class name="demo/Beta" sourcefilename="Beta.java">
                      <method name="beta" desc="()I" line="4">
                        <counter type="INSTRUCTION" missed="0" covered="1"/>
                      </method>
                    </class>
                  </package>
                </report>
                """);

        List<MethodMetrics> result = CrapAnalyzer.analyze(tempDir, List.of(secondSource, firstSource), jacoco);

        assertEquals(List.of("zeta", "omega", "beta"),
                result.stream().map(MethodMetrics::methodName).toList());
    }

    private static CoverageEntry entry(String methodName, int line, CoverageData coverage) {
        return new CoverageEntry(methodName, line, coverage);
    }

    private static CoverageIndex coverageIndex(CoverageEntry... entries) {
        CoverageIndex.Builder builder = CoverageIndex.builder();
        for (CoverageEntry entry : entries) {
            builder.add("demo.Sample", entry.methodName(), entry.line(), entry.coverage());
        }
        return builder.build();
    }

    private record CoverageEntry(String methodName, int line, CoverageData coverage) {
    }
}

