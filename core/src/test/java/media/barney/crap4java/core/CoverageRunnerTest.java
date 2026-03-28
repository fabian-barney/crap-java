package media.barney.crap4java.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CoverageRunnerTest {

    @TempDir
    Path tempDir;

    @Test
    void deletesStaleCoverageAndRunsMavenCoverageCommand() throws Exception {
        Path moduleRoot = tempDir.resolve("module-a");
        Files.createDirectories(moduleRoot);
        Path jacocoDir = moduleRoot.resolve("target/site/jacoco");
        Files.createDirectories(jacocoDir);
        Files.writeString(jacocoDir.resolve("old.xml"), "stale");
        Path exec = moduleRoot.resolve("target/jacoco.exec");
        Files.createDirectories(exec.getParent());
        Files.writeString(exec, "stale");

        RecordingExecutor executor = new RecordingExecutor(0);
        CoverageRunner runner = new CoverageRunner(executor);

        runner.generateCoverage(new ProjectModule(moduleRoot, tempDir, BuildTool.MAVEN));

        assertFalse(Files.exists(jacocoDir));
        assertFalse(Files.exists(exec));
        assertEquals(List.of(
                mavenCommand(), "-q",
                "-pl", "module-a", "-am",
                "org.jacoco:jacoco-maven-plugin:0.8.13:prepare-agent",
                "test",
                "org.jacoco:jacoco-maven-plugin:0.8.13:report"
        ), executor.commands.get(0));
        assertEquals(tempDir, executor.directories.get(0));
    }

    @Test
    void failsWhenCoverageCommandFails() {
        RecordingExecutor executor = new RecordingExecutor(2);
        CoverageRunner runner = new CoverageRunner(executor);

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> runner.generateCoverage(new ProjectModule(tempDir, tempDir, BuildTool.GRADLE)));

        assertEquals("Coverage command failed with exit 2", ex.getMessage());
    }

    private static final class RecordingExecutor implements CommandExecutor {
        private final int exitCode;
        private final List<List<String>> commands = new ArrayList<>();
        private final List<Path> directories = new ArrayList<>();

        private RecordingExecutor(int exitCode) {
            this.exitCode = exitCode;
        }

        @Override
        public int run(List<String> command, Path directory) {
            commands.add(command);
            directories.add(directory);
            return exitCode;
        }
    }

    private static String mavenCommand() {
        if (System.getProperty("os.name").toLowerCase(Locale.ROOT).startsWith("windows")) {
            return "mvn.cmd";
        }
        return "mvn";
    }
}
