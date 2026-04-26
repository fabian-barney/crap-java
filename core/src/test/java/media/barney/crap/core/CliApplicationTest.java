package media.barney.crap.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CliApplicationTest {

    @TempDir
    Path tempDir;

    private static final CoverageRunner NOOP_COVERAGE =
            new CoverageRunner((command, directory) -> 0);

    @Test
    void parseErrorsReturnUsageAndExitOne() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();

        int exit = new CliApplication(tempDir, new PrintStream(out), new PrintStream(err), NOOP_COVERAGE, CoverageMode.GENERATE)
                .execute(new String[]{"--changed", "src/main/java/demo/Sample.java"});

        assertEquals(1, exit);
        assertTrue(utf8(out).contains("Usage:"));
        assertTrue(utf8(err).contains("--changed cannot be combined with file arguments"));
    }

    @Test
    void unknownFlagsReturnUsageAndExitOne() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();

        int exit = new CliApplication(tempDir, new PrintStream(out), new PrintStream(err), NOOP_COVERAGE, CoverageMode.GENERATE)
                .execute(new String[]{"--bogus"});

        assertEquals(1, exit);
        assertTrue(utf8(out).contains("Usage:"));
        assertTrue(utf8(err).contains("Unknown option: --bogus"));
    }

    @Test
    void returnsZeroWhenNoFilesAreFound() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        int exit = new CliApplication(tempDir, new PrintStream(out), new PrintStream(new ByteArrayOutputStream()), NOOP_COVERAGE, CoverageMode.GENERATE)
                .execute(new String[0]);

        assertEquals(0, exit);
        assertTrue(utf8(out).contains("status: passed"));
        assertTrue(utf8(out).contains("methods[0]"));
    }

    @Test
    void changedModeReturnsNoFilesWhenOnlyDeletedJavaFilesRemain() throws Exception {
        run(tempDir, "git", "init");
        run(tempDir, "git", "config", "user.email", "test@example.com");
        run(tempDir, "git", "config", "user.name", "test");
        Path src = tempDir.resolve("src/main/java/demo");
        Files.createDirectories(src);
        Path tracked = src.resolve("Tracked.java");
        Files.writeString(tracked, "class Tracked {}\n");
        run(tempDir, "git", "add", ".");
        run(tempDir, "git", "commit", "-m", "init");
        Files.delete(tracked);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();

        int exit = new CliApplication(tempDir, new PrintStream(out), new PrintStream(err), NOOP_COVERAGE, CoverageMode.GENERATE)
                .execute(new String[]{"--changed"});

        assertEquals(0, exit);
        assertTrue(utf8(out).contains("methods[0]"));
        assertEquals("", utf8(err));
    }

    @Test
    void doesNotWarnWhenJacocoXmlExists() throws Exception {
        Path sourceRoot = tempDir.resolve("src/main/java/demo");
        Files.createDirectories(sourceRoot);
        Files.writeString(tempDir.resolve("pom.xml"), "<project/>");
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
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        CoverageRunner coverageRunner = new CoverageRunner((command, directory) -> {
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
            return 0;
        });

        int exit = new CliApplication(tempDir, new PrintStream(out), new PrintStream(err), coverageRunner, CoverageMode.GENERATE)
                .execute(new String[]{"src/main/java/demo/Sample.java"});

        assertEquals(0, exit);
        assertTrue(utf8(out).contains("Sample"));
        assertFalse(utf8(err).contains("Warning: JaCoCo XML not found"));
    }

    @Test
    void explicitFileUsesOwningModuleForCoverageAndJacocoXml() throws Exception {
        Path moduleRoot = tempDir.resolve("tools/mutate4java");
        Path sourceRoot = moduleRoot.resolve("src/mutate4java");
        Files.createDirectories(sourceRoot);
        Files.writeString(moduleRoot.resolve("pom.xml"), "<project/>");
        Path source = sourceRoot.resolve("Sample.java");
        Files.writeString(source, """
                package mutate4java;

                class Sample {
                    int alpha() {
                        return 1;
                    }
                }
                """);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        List<Path> directories = new ArrayList<>();
        Path jacocoXml = moduleRoot.resolve("target/site/jacoco/jacoco.xml");
        CoverageRunner coverageRunner = new CoverageRunner((command, directory) -> {
            directories.add(directory);
            Files.createDirectories(jacocoXml.getParent());
            Files.writeString(jacocoXml, """
                    <report name="mutate4java">
                      <package name="mutate4java">
                        <class name="mutate4java/Sample" sourcefilename="Sample.java">
                          <method name="alpha" desc="()I" line="4">
                            <counter type="INSTRUCTION" missed="0" covered="1"/>
                          </method>
                        </class>
                      </package>
                    </report>
                    """);
            return 0;
        });

        int exit = new CliApplication(tempDir, new PrintStream(out), new PrintStream(err), coverageRunner, CoverageMode.GENERATE)
                .execute(new String[]{"tools/mutate4java/src/mutate4java/Sample.java"});

        assertEquals(0, exit);
        assertEquals(List.of(moduleRoot), directories);
        assertTrue(utf8(out).contains("mutate4java.Sample"));
        assertFalse(utf8(err).contains("Warning: JaCoCo XML not found"));
    }

    @Test
    void explicitFileUsesOwningGradleModuleAndExecutionRoot() throws Exception {
        Files.writeString(tempDir.resolve("settings.gradle"), "rootProject.name = 'workspace'");
        Path moduleRoot = tempDir.resolve("apps/demo");
        Path sourceRoot = moduleRoot.resolve("src/main/java/demo");
        Files.createDirectories(sourceRoot);
        Files.writeString(moduleRoot.resolve("build.gradle"), "plugins { id 'java' }");
        Path source = sourceRoot.resolve("Sample.java");
        Files.writeString(source, """
                package demo;

                class Sample {
                    int alpha() {
                        return 1;
                    }
                }
                """);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        List<List<String>> commands = new ArrayList<>();
        List<Path> directories = new ArrayList<>();
        Path jacocoXml = moduleRoot.resolve("build/reports/jacoco/test/jacocoTestReport.xml");
        CoverageRunner coverageRunner = new CoverageRunner((command, directory) -> {
            commands.add(command);
            directories.add(directory);
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
            return 0;
        });

        int exit = new CliApplication(tempDir, new PrintStream(out), new PrintStream(err), coverageRunner, CoverageMode.GENERATE)
                .execute(new String[]{"apps/demo/src/main/java/demo/Sample.java"});

        assertEquals(0, exit);
        assertEquals(List.of(tempDir), directories);
        assertEquals(List.of(List.of("gradle", "--no-daemon", "-q", ":apps:demo:test", ":apps:demo:jacocoTestReport")), commands);
        assertTrue(utf8(out).contains("demo.Sample"));
        assertFalse(utf8(err).contains("Warning: JaCoCo XML not found"));
    }

    @Test
    void thresholdExceededUsesStrictlyGreaterThanEight() {
        assertFalse(CliApplication.thresholdExceeded(8.0));
        assertTrue(CliApplication.thresholdExceeded(8.1));
    }

    @Test
    void moduleForFindsNearestAncestorWithPom() throws Exception {
        Path moduleRoot = tempDir.resolve("tools/mutate4java");
        Path source = moduleRoot.resolve("src/mutate4java/Sample.java");
        Files.createDirectories(source.getParent());
        Files.writeString(moduleRoot.resolve("pom.xml"), "<project/>");
        Files.writeString(source, "class Sample {}");

        ProjectModule module = CliApplication.moduleFor(tempDir, source, BuildToolSelection.AUTO);

        assertEquals(moduleRoot, module.moduleRoot());
        assertEquals(BuildTool.MAVEN, module.buildTool());
    }

    @Test
    void ambiguousBuildToolReturnsExitOneWithoutUsage() throws Exception {
        Path moduleRoot = tempDir.resolve("demo");
        Path sourceRoot = moduleRoot.resolve("src/main/java/demo");
        Files.createDirectories(sourceRoot);
        Files.writeString(moduleRoot.resolve("pom.xml"), "<project/>");
        Files.writeString(moduleRoot.resolve("build.gradle"), "plugins { id 'java' }");
        Files.writeString(sourceRoot.resolve("Sample.java"), "class Sample {}");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();

        int exit = new CliApplication(tempDir, new PrintStream(out), new PrintStream(err), NOOP_COVERAGE, CoverageMode.GENERATE)
                .execute(new String[]{"demo/src/main/java/demo/Sample.java"});

        assertEquals(1, exit);
        assertEquals("", utf8(out));
        assertTrue(utf8(err).contains("Ambiguous build tool for module"));
    }

    @Test
    void missingExplicitFileReturnsExitOneWithoutRunningAnalysis() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();

        int exit = new CliApplication(tempDir, new PrintStream(out), new PrintStream(err), NOOP_COVERAGE, CoverageMode.GENERATE)
                .execute(new String[]{"src/main/java/demo/Missing.java"});

        assertEquals(1, exit);
        assertEquals("", utf8(out));
        assertTrue(utf8(err).contains("Path does not exist: src/main/java/demo/Missing.java"));
    }

    private static String utf8(ByteArrayOutputStream output) {
        return output.toString(StandardCharsets.UTF_8);
    }

    private static void run(Path dir, String... command) throws Exception {
        Process process = new ProcessBuilder(command)
                .directory(dir.toFile())
                .redirectErrorStream(true)
                .start();
        if (process.waitFor() != 0) {
            throw new IllegalStateException(process.inputReader(StandardCharsets.UTF_8).readLine());
        }
    }
}

