package media.barney.crapjava.core;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

final class SourceFileFinder {

    private SourceFileFinder() {
    }

    static List<Path> findAllJavaFilesUnderSourceRoots(Path projectRoot) throws IOException {
        if (!Files.isDirectory(projectRoot)) {
            return List.of();
        }

        List<Path> javaFiles = new ArrayList<>();
        for (Path sourceRoot : productionSourceRoots(projectRoot)) {
            try (var stream = Files.walk(sourceRoot)) {
                stream.filter(Files::isRegularFile)
                        .filter(path -> path.toString().endsWith(".java"))
                        .forEach(javaFiles::add);
            }
        }
        javaFiles.sort(Comparator.naturalOrder());
        return javaFiles;
    }

    private static List<Path> productionSourceRoots(Path projectRoot) throws IOException {
        List<Path> sourceRoots = new ArrayList<>();
        Files.walkFileTree(projectRoot, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                if (!dir.equals(projectRoot) && ProductionSourceRoots.isSkippableDirectory(dir)) {
                    return FileVisitResult.SKIP_SUBTREE;
                }
                if (ProductionSourceRoots.isProductionSourceRoot(dir)) {
                    sourceRoots.add(dir.normalize());
                    return FileVisitResult.SKIP_SUBTREE;
                }
                return FileVisitResult.CONTINUE;
            }
        });
        return sourceRoots;
    }
}
