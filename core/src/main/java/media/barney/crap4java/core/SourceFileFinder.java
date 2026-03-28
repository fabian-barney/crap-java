package media.barney.crap4java.core;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;

final class SourceFileFinder {

    private SourceFileFinder() {
    }

    static List<Path> findAllJavaFilesUnderSourceRoots(Path projectRoot) throws IOException {
        if (!Files.exists(projectRoot)) {
            return List.of();
        }

        try (var stream = Files.walk(projectRoot)) {
            return stream
                    .filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> isUnderSourceTree(projectRoot, path))
                    .sorted(Comparator.naturalOrder())
                    .toList();
        }
    }

    private static boolean isUnderSourceTree(Path projectRoot, Path file) {
        Path normalized = projectRoot.normalize().relativize(file.normalize());
        for (Path segment : normalized) {
            if ("src".equals(segment.toString())) {
                return true;
            }
        }
        return false;
    }
}
