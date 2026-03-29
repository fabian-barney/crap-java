package media.barney.crap4java.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    @Test
    void deletesDirectoryLinkWithoutTouchingLinkTarget() throws Exception {
        Path moduleRoot = tempDir.resolve("module-a");
        Files.createDirectories(moduleRoot.resolve("build"));
        Path externalJacoco = tempDir.resolve("external-jacoco");
        Files.createDirectories(externalJacoco.resolve("nested"));
        Path sentinel = externalJacoco.resolve("nested/keep.txt");
        Files.writeString(sentinel, "keep");
        Path link = moduleRoot.resolve("build/jacoco");
        createDirectoryLink(link, externalJacoco);

        RecordingExecutor executor = new RecordingExecutor(0);
        CoverageRunner runner = new CoverageRunner(executor);

        runner.generateCoverage(new ProjectModule(moduleRoot, moduleRoot, BuildTool.GRADLE));

        assertFalse(Files.exists(link, LinkOption.NOFOLLOW_LINKS));
        assertTrue(Files.exists(sentinel));
    }

    @Test
    void rejectsDeletionWhenResolvedPathEscapesModuleRoot() throws Exception {
        Path moduleRoot = tempDir.resolve("module-a");
        Files.createDirectories(moduleRoot);
        Path externalBuild = tempDir.resolve("external-build");
        Path externalJacoco = externalBuild.resolve("jacoco");
        Files.createDirectories(externalJacoco);
        Path sentinel = externalJacoco.resolve("keep.txt");
        Files.writeString(sentinel, "keep");
        createDirectoryLink(moduleRoot.resolve("build"), externalBuild);

        RecordingExecutor executor = new RecordingExecutor(0);
        CoverageRunner runner = new CoverageRunner(executor);

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> runner.generateCoverage(new ProjectModule(moduleRoot, moduleRoot, BuildTool.GRADLE)));

        assertEquals("Refusing to delete stale coverage outside module root: " + moduleRoot.resolve("build/jacoco"), ex.getMessage());
        assertTrue(Files.exists(sentinel));
        assertTrue(Files.exists(moduleRoot.resolve("build"), LinkOption.NOFOLLOW_LINKS));
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

    private static void createDirectoryLink(Path link, Path target) throws Exception {
        if (isWindows()) {
            Process process = new ProcessBuilder("cmd", "/c", "mklink", "/J", link.toString(), target.toString())
                    .redirectErrorStream(true)
                    .start();
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            assertEquals(0, process.waitFor(), output);
            return;
        }
        Files.createSymbolicLink(link, target);
    }

    private static boolean isWindows() {
        return System.getProperty("os.name").toLowerCase(Locale.ROOT).startsWith("windows");
    }
}
