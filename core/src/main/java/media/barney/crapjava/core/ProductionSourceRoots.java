package media.barney.crapjava.core;

import java.nio.file.Path;
import java.util.Set;

final class ProductionSourceRoots {

    private static final Path PRODUCTION_SOURCE_ROOT = Path.of("src", "main", "java");
    private static final Set<String> SKIPPED_DIRECTORY_NAMES = Set.of(".git", ".gradle", "build", "out", "target");

    private ProductionSourceRoots() {
    }

    static boolean isProductionSourceRoot(Path directory) {
        Path normalized = directory.normalize();
        int startIndex = normalized.getNameCount() - PRODUCTION_SOURCE_ROOT.getNameCount();
        return startIndex >= 0 && matchesProductionSourceRoot(normalized, startIndex);
    }

    static boolean isUnderProductionSourceRoot(Path path) {
        Path normalized = path.normalize();
        for (int index = 0; index <= normalized.getNameCount() - PRODUCTION_SOURCE_ROOT.getNameCount(); index++) {
            if (matchesProductionSourceRoot(normalized, index)) {
                return true;
            }
        }
        return false;
    }

    static boolean isSkippableDirectory(Path directory) {
        Path fileName = directory.getFileName();
        return fileName != null && SKIPPED_DIRECTORY_NAMES.contains(fileName.toString());
    }

    private static boolean matchesProductionSourceRoot(Path path, int startIndex) {
        for (int offset = 0; offset < PRODUCTION_SOURCE_ROOT.getNameCount(); offset++) {
            if (!PRODUCTION_SOURCE_ROOT.getName(offset).toString().equals(path.getName(startIndex + offset).toString())) {
                return false;
            }
        }
        return true;
    }
}
