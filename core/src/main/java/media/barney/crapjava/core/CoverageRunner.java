package media.barney.crapjava.core;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;

final class CoverageRunner {

    private final CommandExecutor executor;

    CoverageRunner(CommandExecutor executor) {
        this.executor = executor;
    }

    void generateCoverage(ProjectModule module) throws Exception {
        Path moduleRootRealPath = module.moduleRoot().toRealPath();
        for (Path staleCoveragePath : module.staleCoveragePaths()) {
            deleteIfExists(module.moduleRoot(), moduleRootRealPath, staleCoveragePath);
        }

        int exit = executor.run(module.coverageCommand(), module.executionRoot());
        if (exit != 0) {
            throw new IllegalStateException("Coverage command failed with exit " + exit);
        }
    }

    private void deleteIfExists(Path moduleRoot, Path moduleRootRealPath, Path path) throws IOException {
        Path normalizedPath = path.normalize();
        if (!normalizedPath.startsWith(moduleRoot.normalize())) {
            throw new IllegalStateException("Refusing to delete stale coverage outside module root: " + normalizedPath);
        }
        if (!Files.exists(normalizedPath, LinkOption.NOFOLLOW_LINKS)) {
            return;
        }
        deleteRecursively(moduleRootRealPath, normalizedPath);
    }

    private void deleteRecursively(Path moduleRootRealPath, Path path) throws IOException {
        BasicFileAttributes attributes = Files.readAttributes(path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        if (isLinkLike(attributes)) {
            Files.deleteIfExists(path);
            return;
        }

        Path realPath = path.toRealPath();
        if (!realPath.startsWith(moduleRootRealPath)) {
            throw new IllegalStateException("Refusing to delete stale coverage outside module root: " + path);
        }

        if (attributes.isDirectory()) {
            try (DirectoryStream<Path> entries = Files.newDirectoryStream(path)) {
                for (Path entry : entries) {
                    deleteRecursively(moduleRootRealPath, entry);
                }
            }
        }

        Files.deleteIfExists(path);
    }

    private boolean isLinkLike(BasicFileAttributes attributes) {
        return attributes.isSymbolicLink() || attributes.isOther();
    }
}
