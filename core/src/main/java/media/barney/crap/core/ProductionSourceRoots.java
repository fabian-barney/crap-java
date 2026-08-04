package media.barney.crap.core;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;

final class ProductionSourceRoots {

    private static final Path PRODUCTION_SOURCE_ROOT = Path.of("src", "main", "java");
    private static final Set<String> SKIPPED_DIRECTORY_NAMES = Set.of(".git", ".gradle", "build", "out", "target");

    private ProductionSourceRoots() {
    }

    static boolean isProductionSourceRoot(Path directory) {
        return isRelativeSourceRoot(directory, PRODUCTION_SOURCE_ROOT);
    }

    static boolean isUnderProductionSourceRoot(Path path) {
        return isUnderSourceRoot(path, List.of());
    }

    static boolean isSourceRoot(Path directory, Path sourceRoot) {
        Path normalizedSourceRoot = sourceRoot.normalize();
        if (normalizedSourceRoot.isAbsolute()) {
            return directory.normalize().equals(normalizedSourceRoot);
        }
        return isRelativeSourceRoot(directory, normalizedSourceRoot);
    }

    static boolean isUnderSourceRoot(Path path, List<Path> sourceRoots) {
        Path normalized = path.normalize();
        for (Path sourceRoot : effectiveSourceRoots(sourceRoots)) {
            Path normalizedSourceRoot = sourceRoot.normalize();
            if (normalizedSourceRoot.isAbsolute()) {
                if (normalized.startsWith(normalizedSourceRoot)) {
                    return true;
                }
                continue;
            }
            if (isUnderRelativeSourceRoot(normalized, normalizedSourceRoot)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isUnderRelativeSourceRoot(Path normalized, Path sourceRoot) {
        int lastStart = normalized.getNameCount() - sourceRoot.getNameCount();
        for (int index = 0; index <= lastStart; index++) {
            if (matchesSourceRoot(normalized, sourceRoot, index)) {
                return true;
            }
        }
        return false;
    }

    static List<Path> effectiveSourceRoots(List<Path> sourceRoots) {
        return sourceRoots.isEmpty() ? List.of(PRODUCTION_SOURCE_ROOT) : List.copyOf(sourceRoots);
    }

    static boolean usesDefaultSourceRoots(List<Path> sourceRoots) {
        return sourceRoots.isEmpty();
    }

    static boolean matchesAnyCustomSourceRoot(Path directory, List<Path> sourceRoots) {
        if (usesDefaultSourceRoots(sourceRoots)) {
            return false;
        }
        for (Path sourceRoot : effectiveSourceRoots(sourceRoots)) {
            if (isSourceRoot(directory, sourceRoot)) {
                return true;
            }
        }
        return false;
    }

    static boolean isSkippableDirectory(Path directory) {
        Path fileName = directory.getFileName();
        return fileName != null && SKIPPED_DIRECTORY_NAMES.contains(fileName.toString());
    }

    private static boolean isRelativeSourceRoot(Path directory, Path sourceRoot) {
        Path normalized = directory.normalize();
        int startIndex = normalized.getNameCount() - sourceRoot.getNameCount();
        return startIndex >= 0 && matchesSourceRoot(normalized, sourceRoot, startIndex);
    }

    private static boolean matchesSourceRoot(Path path, Path sourceRoot, int startIndex) {
        for (int offset = 0; offset < sourceRoot.getNameCount(); offset++) {
            if (!sourceRoot.getName(offset).toString().equals(path.getName(startIndex + offset).toString())) {
                return false;
            }
        }
        return true;
    }
}

