package media.barney.crap4java.core;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

final class CoverageRunner {

    private final CommandExecutor executor;

    CoverageRunner(CommandExecutor executor) {
        this.executor = executor;
    }

    void generateCoverage(ProjectModule module) throws Exception {
        for (Path staleCoveragePath : module.staleCoveragePaths()) {
            deleteIfExists(staleCoveragePath);
        }

        int exit = executor.run(module.coverageCommand(), module.executionRoot());
        if (exit != 0) {
            throw new IllegalStateException("Coverage command failed with exit " + exit);
        }
    }

    private void deleteIfExists(Path path) throws IOException {
        if (!Files.exists(path)) {
            return;
        }
        if (Files.isDirectory(path)) {
            try (var walk = Files.walk(path)) {
                walk.sorted(Comparator.reverseOrder())
                        .forEach(p -> {
                            try {
                                Files.deleteIfExists(p);
                            } catch (IOException ex) {
                                throw new IllegalStateException("Failed deleting stale coverage: " + p, ex);
                            }
                        });
            }
            return;
        }
        Files.deleteIfExists(path);
    }
}
