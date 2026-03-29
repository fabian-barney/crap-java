package media.barney.crapjava.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChangedFileDetectorTest {

    @TempDir
    Path tempDir;

    @Test
    void findsModifiedAndUntrackedJavaFiles() throws Exception {
        run(tempDir, "git", "init");
        run(tempDir, "git", "config", "user.email", "test@example.com");
        run(tempDir, "git", "config", "user.name", "test");

        Path src = tempDir.resolve("src/main/java/demo");
        Files.createDirectories(src);
        Path tracked = src.resolve("Tracked.java");
        Files.writeString(tracked, "class Tracked {}\n");

        run(tempDir, "git", "add", ".");
        run(tempDir, "git", "commit", "-m", "init");

        Files.writeString(tracked, "class Tracked { int x = 1; }\n");
        Path untracked = src.resolve("NewFile.java");
        Files.writeString(untracked, "class NewFile {}\n");
        Files.writeString(tempDir.resolve("README.md"), "ignore me\n");

        List<Path> changed = ChangedFileDetector.changedJavaFiles(tempDir);

        assertEquals(List.of(
                tempDir.resolve("src/main/java/demo/NewFile.java"),
                tempDir.resolve("src/main/java/demo/Tracked.java")
        ), changed);
    }

    @Test
    void includesGitErrorOutputWhenStatusFails() {
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> ChangedFileDetector.changedJavaFiles(tempDir));

        assertTrue(Objects.requireNonNull(error.getMessage()).contains("not a git repository"));
    }

    @Test
    void filtersChangedFilesToSourceTreesOnly() throws Exception {
        run(tempDir, "git", "init");
        run(tempDir, "git", "config", "user.email", "test@example.com");
        run(tempDir, "git", "config", "user.name", "test");

        Path mainSrc = tempDir.resolve("src/main/java/demo");
        Path moduleTestSrc = tempDir.resolve("module-a/src/test/java/demo");
        Path nestedMainSrc = tempDir.resolve("module-b/src/main/java/demo");
        Path nonSourceTree = tempDir.resolve("test/crap-java");
        Files.createDirectories(mainSrc);
        Files.createDirectories(moduleTestSrc);
        Files.createDirectories(nestedMainSrc);
        Files.createDirectories(nonSourceTree);

        Path tracked = mainSrc.resolve("Tracked.java");
        Files.writeString(tracked, "class Tracked {}\n");
        run(tempDir, "git", "add", ".");
        run(tempDir, "git", "commit", "-m", "init");

        Files.writeString(tracked, "class Tracked { int x = 1; }\n");
        Path nested = moduleTestSrc.resolve("NestedChanged.java");
        Files.writeString(nested, "class NestedChanged {}\n");
        Path nestedMain = nestedMainSrc.resolve("NestedMainChanged.java");
        Files.writeString(nestedMain, "class NestedMainChanged {}\n");
        Files.writeString(nonSourceTree.resolve("ChangedFileDetectorTest.java"), "class ChangedFileDetectorTest {}\n");

        List<Path> changed = ChangedFileDetector.changedJavaFilesUnderSourceRoots(tempDir);

        assertEquals(List.of(
                tempDir.resolve("module-b/src/main/java/demo/NestedMainChanged.java"),
                tempDir.resolve("src/main/java/demo/Tracked.java")
        ), changed);
    }

    @Test
    void candidateLineRequiresAtLeastFourCharacters() {
        assertEquals(false, ChangedFileDetector.isCandidateLine(null));
        assertEquals(false, ChangedFileDetector.isCandidateLine(""));
        assertEquals(false, ChangedFileDetector.isCandidateLine("abc"));
        assertEquals(true, ChangedFileDetector.isCandidateLine("abcd"));
    }

    @Test
    void renameTargetUsesReplacementSideEvenAtStartOfString() {
        assertEquals("src/New.java", ChangedFileDetector.renameTarget("src/Old.java -> src/New.java"));
        assertEquals("New.java", ChangedFileDetector.renameTarget(" -> New.java"));
        assertEquals("Plain.java", ChangedFileDetector.renameTarget("Plain.java"));
    }

    private static void run(Path dir, String... command) throws IOException, InterruptedException {
        Process process = new ProcessBuilder(command)
                .directory(dir.toFile())
                .redirectErrorStream(true)
                .start();
        if (process.waitFor() != 0) {
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            throw new IllegalStateException(output);
        }
    }
}
