package crap4java;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;

final class CoverageRunner {

    private final CommandExecutor executor;

    CoverageRunner(CommandExecutor executor) {
        this.executor = executor;
    }

    void generateCoverage(Path projectRoot) throws Exception {
        deleteIfExists(projectRoot.resolve("target/site/jacoco"));
        deleteIfExists(projectRoot.resolve("target/jacoco.exec"));

        int exit = executor.run(List.of(
                "mvn", "-q",
                "org.jacoco:jacoco-maven-plugin:0.8.12:prepare-agent",
                "test",
                "org.jacoco:jacoco-maven-plugin:0.8.12:report"
        ), projectRoot);
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
