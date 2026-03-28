package media.barney.crap4java.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SourceFileFinderTest {

    @TempDir
    Path tempDir;

    @Test
    void findsAllJavaFilesUnderSrcOnly() throws Exception {
        Path rootSrc = tempDir.resolve("src/main/java/demo");
        Files.createDirectories(rootSrc);
        Path inRootSrc = rootSrc.resolve("Sample.java");
        Files.writeString(inRootSrc, "class Sample {}\n");

        Path nestedModuleSrc = tempDir.resolve("module-a/src/test/java/demo");
        Files.createDirectories(nestedModuleSrc);
        Path inNestedSrc = nestedModuleSrc.resolve("NestedSample.java");
        Files.writeString(inNestedSrc, "class NestedSample {}\n");

        Path outOfSrc = tempDir.resolve("other/Elsewhere.java");
        Files.createDirectories(outOfSrc.getParent());
        Files.writeString(outOfSrc, "class Elsewhere {}\n");

        List<Path> files = SourceFileFinder.findAllJavaFilesUnderSourceRoots(tempDir);

        assertEquals(List.of(inNestedSrc, inRootSrc), files);
    }
}
