package media.barney.crap4java.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
        assertTrue(out.toString().contains("Usage:"));
        assertTrue(out.toString().contains("--build-tool"));
    }

    @Test
    void mainProcessExitsZeroForHelp() throws Exception {
        Process process = new ProcessBuilder(
                "java",
                "-cp",
                System.getProperty("java.class.path"),
                "media.barney.crap4java.core.Main",
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
                "media.barney.crap4java.core.Main",
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
        assertTrue(out.toString().contains("Sample"));
        assertTrue(out.toString().contains("alpha"));
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
        assertTrue(out.toString().contains("Sample"));
        assertTrue(out.toString().contains("alpha"));
    }

    @Test
    void maxCrapReturnsLargestNonNullScore() {
        List<MethodMetrics> metrics = List.of(
                new MethodMetrics("alpha", "demo.Sample", 1, null, null),
                new MethodMetrics("beta", "demo.Sample", 1, 75.0, 4.5),
                new MethodMetrics("gamma", "demo.Sample", 1, 85.0, 7.0)
        );

        assertEquals(7.0, Main.maxCrap(metrics));
    }
}
