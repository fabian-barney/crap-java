package media.barney.crap.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MainTest {

    @TempDir
    Path tempDir;

    private static final CoverageRunner NOOP_COVERAGE =
            new CoverageRunner((command, directory) -> 0);

    @Test
    void helpWritesUsageToStdout() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();

        int exit = Main.run(new String[]{"--help"}, tempDir, new PrintStream(out), new PrintStream(err), NOOP_COVERAGE);

        assertEquals(0, exit);
        assertTrue(utf8(out).contains("Usage:"));
        assertTrue(utf8(out).contains("--build-tool"));
        assertTrue(utf8(out).contains("--format"));
        assertTrue(utf8(out).contains("--agent"));
        assertTrue(utf8(out).contains("--failures-only"));
        assertTrue(utf8(out).contains("--omit-redundancy"));
        assertTrue(utf8(out).contains("--threshold"));
    }

    @Test
    void mainProcessExitsZeroForHelp() throws Exception {
        Process process = new ProcessBuilder(
                "java",
                "-cp",
                System.getProperty("java.class.path"),
                "media.barney.crap.core.Main",
                "--help"
        ).directory(tempDir.toFile()).start();

        assertEquals(0, process.waitFor());
    }

    @Test
    void mainProcessExitsNonZeroForUnknownOption() throws Exception {
        Process process = new ProcessBuilder(
                "java",
                "-cp",
                System.getProperty("java.class.path"),
                "media.barney.crap.core.Main",
                "--changed",
                "src/main/java/demo/Sample.java"
        ).directory(tempDir.toFile()).start();

        assertEquals(1, process.waitFor());
    }

    @Test
    void explicitFileArgsAreAnalyzed() throws Exception {
        Path sourceRoot = tempDir.resolve("src/main/java/demo");
        Files.createDirectories(sourceRoot);
        Files.writeString(tempDir.resolve("pom.xml"), "<project/>");
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

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();

        int exit = Main.run(
                new String[]{"src/main/java/demo/Sample.java"},
                tempDir,
                new PrintStream(out),
                new PrintStream(err),
                NOOP_COVERAGE
        );

        assertEquals(0, exit);
        assertTrue(utf8(out).contains("status: passed"));
        assertTrue(utf8(out).contains("covKind"));
        assertTrue(utf8(out).contains("Sample"));
        assertTrue(utf8(out).contains("alpha"));
    }

    @Test
    void directoryArgAnalyzesJavaFilesUnderThatDirectorySrc() throws Exception {
        Path moduleRoot = tempDir.resolve("module-a");
        Path sourceRoot = moduleRoot.resolve("src/main/java/demo");
        Files.createDirectories(sourceRoot);
        Files.writeString(moduleRoot.resolve("build.gradle"), "plugins { id 'java' }");
        Files.writeString(sourceRoot.resolve("Sample.java"), """
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

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();

        int exit = Main.run(new String[]{"module-a"}, tempDir, new PrintStream(out), new PrintStream(err), NOOP_COVERAGE);

        assertEquals(0, exit);
        assertTrue(utf8(out).contains("Sample"));
        assertTrue(utf8(out).contains("alpha"));
    }

    @Test
    void runWithExistingCoverageKeepsTheExistingJacocoXml() throws Exception {
        Files.writeString(tempDir.resolve("settings.gradle"), "rootProject.name = 'workspace'");
        Files.writeString(tempDir.resolve("build.gradle"), "plugins { id 'java' }");
        Path sourceRoot = tempDir.resolve("src/main/java/demo");
        Files.createDirectories(sourceRoot);
        Path source = sourceRoot.resolve("Sample.java");
        Files.writeString(source, """
                package demo;

                class Sample {
                    int alpha() {
                        return 1;
                    }
                }
                """);
        Path jacocoXml = tempDir.resolve("build/reports/jacoco/test/jacocoTestReport.xml");
        Files.createDirectories(jacocoXml.getParent());
        Files.writeString(jacocoXml, """
                <report name="demo">
                  <package name="demo">
                    <class name="demo/Sample" sourcefilename="Sample.java">
                      <method name="alpha" desc="()I" line="4">
                        <counter type="INSTRUCTION" missed="0" covered="1"/>
                      </method>
                    </class>
                  </package>
                </report>
                """);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();

        int exit = Main.runWithExistingCoverage(
                new String[]{"--format", "text", "src/main/java/demo/Sample.java"},
                tempDir,
                new PrintStream(out),
                new PrintStream(err)
        );

        assertEquals(0, exit);
        assertTrue(Files.exists(jacocoXml));
        assertTrue(utf8(out).contains("100.0%"));
        assertEquals("", utf8(err));
    }

    @Test
    void writesPrimaryOutputAndAdditionalJunitReportFiles() throws Exception {
        Files.writeString(tempDir.resolve("pom.xml"), "<project/>");
        Path sourceRoot = tempDir.resolve("src/main/java/demo");
        Files.createDirectories(sourceRoot);
        Path source = sourceRoot.resolve("Sample.java");
        Files.writeString(source, """
                package demo;

                class Sample {
                    int alpha() {
                        return 1;
                    }
                }
                """);
        Path jacocoXml = tempDir.resolve("target/site/jacoco/jacoco.xml");
        Files.createDirectories(jacocoXml.getParent());
        Files.writeString(jacocoXml, """
                <report name="demo">
                  <package name="demo">
                    <class name="demo/Sample" sourcefilename="Sample.java">
                      <method name="alpha" desc="()I" line="4">
                        <counter type="INSTRUCTION" missed="0" covered="1"/>
                      </method>
                    </class>
                  </package>
                </report>
                """);
        Path jsonReport = tempDir.resolve("target/crap-java/report.json");
        Path junitReport = tempDir.resolve("target/crap-java/TEST-crap-java.xml");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();

        int exit = Main.runWithExistingCoverage(
                new String[]{
                        "--format", "json",
                        "--output", "target/crap-java/report.json",
                        "--junit-report", "target/crap-java/TEST-crap-java.xml",
                        "src/main/java/demo/Sample.java"
                },
                tempDir,
                new PrintStream(out),
                new PrintStream(err)
        );

        assertEquals(0, exit);
        assertEquals("", utf8(out));
        assertEquals("", utf8(err));
        assertTrue(Files.readString(jsonReport).contains("\"covKind\": \"instruction\""));
        assertTrue(Files.readString(junitReport).contains("<testsuites tests=\"1\" failures=\"0\" errors=\"0\" skipped=\"0\" time=\"0\">"));
    }

    @Test
    void agentModeFiltersPrimaryOutputButKeepsJunitSidecarComplete() throws Exception {
        writeMixedCoverageSample();
        Path jsonReport = tempDir.resolve("target/crap-java/agent.json");
        Path junitReport = tempDir.resolve("target/crap-java/TEST-crap-java.xml");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();

        int exit = Main.runWithExistingCoverage(
                new String[]{
                        "--agent",
                        "--format", "json",
                        "--output", "target/crap-java/agent.json",
                        "--junit-report", "target/crap-java/TEST-crap-java.xml",
                        "src/main/java/demo/Sample.java"
                },
                tempDir,
                new PrintStream(out),
                new PrintStream(err)
        );

        String primary = Files.readString(jsonReport);
        String junit = Files.readString(junitReport);
        assertEquals(2, exit);
        assertEquals("", utf8(out));
        assertTrue(primary.contains("\"status\": \"failed\""));
        assertTrue(primary.contains("\"threshold\": 8.0"));
        assertTrue(primary.contains("\"method\": \"danger\""));
        assertFalse(primary.contains("\"method\": \"safe\""));
        assertFalse(primary.contains("\"method\": \"unknown\""));
        assertTrue(junit.contains("<testsuites tests=\"3\" failures=\"1\" errors=\"0\" skipped=\"1\" time=\"0\">"));
        assertTrue(junit.contains("FAILED danger"));
        assertTrue(junit.contains("PASSED safe"));
        assertTrue(junit.contains("SKIPPED unknown"));
    }

    @Test
    void failuresOnlyFiltersPrimaryOutputButKeepsJunitSidecarComplete() throws Exception {
        writeMixedCoverageSample();
        Path jsonReport = tempDir.resolve("target/crap-java/failures.json");
        Path junitReport = tempDir.resolve("target/crap-java/TEST-crap-java.xml");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();

        int exit = Main.runWithExistingCoverage(
                new String[]{
                        "--failures-only",
                        "--format", "json",
                        "--output", "target/crap-java/failures.json",
                        "--junit-report", "target/crap-java/TEST-crap-java.xml",
                        "src/main/java/demo/Sample.java"
                },
                tempDir,
                new PrintStream(out),
                new PrintStream(err)
        );

        String primary = Files.readString(jsonReport);
        String junit = Files.readString(junitReport);
        assertEquals(2, exit);
        assertEquals("", utf8(out));
        assertTrue(primary.contains("\"status\": \"failed\""));
        assertTrue(primary.contains("\"threshold\": 8.0"));
        assertTrue(primary.contains("\"method\": \"danger\""));
        assertFalse(primary.contains("\"method\": \"safe\""));
        assertFalse(primary.contains("\"method\": \"unknown\""));
        assertTrue(junit.contains("<testsuites tests=\"3\" failures=\"1\" errors=\"0\" skipped=\"1\" time=\"0\">"));
        assertTrue(junit.contains("FAILED danger"));
        assertTrue(junit.contains("PASSED safe"));
        assertTrue(junit.contains("SKIPPED unknown"));
    }

    @Test
    void omitRedundancyOmitsPrimaryStatusFieldsButKeepsJunitSidecarComplete() throws Exception {
        writeMixedCoverageSample();
        Path jsonReport = tempDir.resolve("target/crap-java/compact.json");
        Path junitReport = tempDir.resolve("target/crap-java/TEST-crap-java.xml");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();

        int exit = Main.runWithExistingCoverage(
                new String[]{
                        "--omit-redundancy",
                        "--format", "json",
                        "--output", "target/crap-java/compact.json",
                        "--junit-report", "target/crap-java/TEST-crap-java.xml",
                        "src/main/java/demo/Sample.java"
                },
                tempDir,
                new PrintStream(out),
                new PrintStream(err)
        );

        String primary = Files.readString(jsonReport);
        String junit = Files.readString(junitReport);
        assertEquals(2, exit);
        assertEquals("", utf8(out));
        assertTrue(primary.contains("\"status\": \"failed\""));
        assertTrue(primary.contains("\"threshold\": 8.0"));
        assertFalse(primary.contains("      \"status\":"));
        assertTrue(primary.contains("\"method\": \"danger\""));
        assertTrue(primary.contains("\"method\": \"safe\""));
        assertTrue(primary.contains("\"method\": \"unknown\""));
        assertTrue(junit.contains("<property name=\"status\" value=\"failed\"/>"));
        assertTrue(junit.contains("<property name=\"status\" value=\"passed\"/>"));
        assertTrue(junit.contains("<property name=\"status\" value=\"skipped\"/>"));
    }

    @Test
    void runWithExistingCoverageAnalyzesPreResolvedModules() throws Exception {
        Path moduleRoot = tempDir.resolve("app");
        Path source = moduleRoot.resolve("src/main/java/demo/Sample.java");
        Files.createDirectories(source.getParent());
        Files.writeString(source, """
                package demo;

                class Sample {
                    int alpha() {
                        return 1;
                    }
                }
                """);
        Path jacocoXml = moduleRoot.resolve("build/reports/jacoco/test/jacocoTestReport.xml");
        Files.createDirectories(jacocoXml.getParent());
        Files.writeString(jacocoXml, """
                <report name="demo">
                  <package name="demo">
                    <class name="demo/Sample" sourcefilename="Sample.java">
                      <method name="alpha" desc="()I" line="4">
                        <counter type="INSTRUCTION" missed="0" covered="1"/>
                      </method>
                    </class>
                  </package>
                </report>
                """);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();

        int exit = Main.runWithExistingCoverage(
                List.of(new Main.ResolvedCoverageModule(moduleRoot, jacocoXml, List.of(source))),
                new PrintStream(out),
                new PrintStream(err)
        );

        assertEquals(0, exit);
        assertTrue(utf8(out).contains("100.0%"));
        assertEquals("", utf8(err));
    }

    @Test
    void runWithExistingCoverageUsesCommonRootForPreResolvedModules() throws Exception {
        Path appRoot = tempDir.resolve("app");
        Path libRoot = tempDir.resolve("lib");
        Path appSource = appRoot.resolve("src/main/java/demo/app/AppSample.java");
        Path libSource = libRoot.resolve("src/main/java/demo/lib/LibSample.java");
        Files.createDirectories(appSource.getParent());
        Files.createDirectories(libSource.getParent());
        Files.writeString(appSource, """
                package demo.app;

                class AppSample {
                    int alpha() {
                        return 1;
                    }
                }
                """);
        Files.writeString(libSource, """
                package demo.lib;

                class LibSample {
                    int beta() {
                        return 2;
                    }
                }
                """);
        Path appJacocoXml = appRoot.resolve("build/reports/jacoco/test/jacocoTestReport.xml");
        Path libJacocoXml = libRoot.resolve("build/reports/jacoco/test/jacocoTestReport.xml");
        writeCoverageXml(appJacocoXml, "demo/app/AppSample", "alpha", 4);
        writeCoverageXml(libJacocoXml, "demo/lib/LibSample", "beta", 4);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();

        int exit = Main.runWithExistingCoverage(
                List.of(
                        new Main.ResolvedCoverageModule(appRoot, appJacocoXml, List.of(appSource)),
                        new Main.ResolvedCoverageModule(libRoot, libJacocoXml, List.of(libSource))
                ),
                new PrintStream(out),
                new PrintStream(err)
        );

        assertEquals(0, exit);
        assertTrue(utf8(out).contains("demo.app.AppSample"));
        assertTrue(utf8(out).contains("demo.lib.LibSample"));
        assertEquals("", utf8(err));
    }

    @Test
    void maxCrapReturnsLargestNonNullScore() {
        List<MethodMetrics> metrics = List.of(
                new MethodMetrics("alpha", "demo.Sample", "src/main/java/demo/Sample.java", 1, 2, 1, null, "N/A", null),
                new MethodMetrics("beta", "demo.Sample", "src/main/java/demo/Sample.java", 3, 4, 1, 75.0, "instruction", 4.5),
                new MethodMetrics("gamma", "demo.Sample", "src/main/java/demo/Sample.java", 5, 6, 1, 85.0, "instruction", 7.0)
        );

        assertEquals(7.0, Main.maxCrap(metrics));
    }

    private static String utf8(ByteArrayOutputStream output) {
        return output.toString(StandardCharsets.UTF_8);
    }

    private void writeMixedCoverageSample() throws Exception {
        Files.writeString(tempDir.resolve("pom.xml"), "<project/>");
        Path sourceRoot = tempDir.resolve("src/main/java/demo");
        Files.createDirectories(sourceRoot);
        Path source = sourceRoot.resolve("Sample.java");
        Files.writeString(source, """
                package demo;

                class Sample {
                    int danger(boolean left, boolean right) {
                        if (left) {
                            return 1;
                        }
                        if (right) {
                            return 2;
                        }
                        return 0;
                    }

                    int safe() {
                        return 1;
                    }

                    int unknown() {
                        return 2;
                    }
                }
                """);
        Path jacocoXml = tempDir.resolve("target/site/jacoco/jacoco.xml");
        Files.createDirectories(jacocoXml.getParent());
        Files.writeString(jacocoXml, """
                <report name="demo">
                  <package name="demo">
                    <class name="demo/Sample" sourcefilename="Sample.java">
                      <method name="danger" desc="(ZZ)I" line="4">
                        <counter type="INSTRUCTION" missed="10" covered="0"/>
                      </method>
                      <method name="safe" desc="()I" line="14">
                        <counter type="INSTRUCTION" missed="0" covered="1"/>
                      </method>
                    </class>
                  </package>
                </report>
                """);
    }

    private static void writeCoverageXml(Path path, String className, String methodName, int line) throws Exception {
        Files.createDirectories(path.getParent());
        Files.writeString(path, """
                <report name="demo">
                  <package name="demo">
                    <class name="%s" sourcefilename="Sample.java">
                      <method name="%s" desc="()I" line="%d">
                        <counter type="INSTRUCTION" missed="0" covered="1"/>
                      </method>
                    </class>
                  </package>
                </report>
                """.formatted(className, methodName, line));
    }
}

