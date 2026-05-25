package media.barney.crap.core;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;

record ReportOptions(
        ReportFormat format,
        boolean failuresOnly,
        boolean omitRedundancy,
        @Nullable Path outputPath,
        @Nullable Path junitReportPath,
        boolean includeExclusionAudit
) {
    ReportOptions(ReportFormat format,
                  boolean failuresOnly,
                  boolean omitRedundancy,
                  @Nullable Path outputPath,
                  @Nullable Path junitReportPath) {
        this(format, failuresOnly, omitRedundancy, outputPath, junitReportPath, true);
    }

    ReportOptions {
        outputPath = normalize(outputPath);
        junitReportPath = normalize(junitReportPath);
        validateReportPath("output", outputPath);
        validateReportPath("junitReport", junitReportPath);
        if (outputPath != null && junitReportPath != null && sameReportTarget(outputPath, junitReportPath)) {
            throw new IllegalArgumentException("output and junitReport must not point to the same file");
        }
    }

    static ReportOptions textWithOptionalJunit(@Nullable Path junitReportPath) {
        return new ReportOptions(ReportFormat.TEXT, false, false, null, junitReportPath, true);
    }

    private static @Nullable Path normalize(@Nullable Path path) {
        if (path == null) {
            return null;
        }
        return path.toAbsolutePath().normalize();
    }

    private static void validateReportPath(String name, @Nullable Path path) {
        if (path == null) {
            return;
        }
        if (path.getFileName() == null) {
            throw new IllegalArgumentException(name + " must not point to a filesystem root");
        }
        if (Files.isDirectory(path)) {
            throw new IllegalArgumentException(name + " must not point to a directory");
        }
    }

    private static boolean sameReportTarget(Path first, Path second) {
        if (first.equals(second)) {
            return true;
        }
        try {
            return sameExistingFile(first, second)
                    || sameRealPath(first, second)
                    || sameParentAndFileName(first, second);
        } catch (IOException exception) {
            return false;
        }
    }

    private static boolean sameExistingFile(Path first, Path second) throws IOException {
        return Files.exists(first) && Files.exists(second) && Files.isSameFile(first, second);
    }

    private static boolean sameParentAndFileName(Path first, Path second) throws IOException {
        Path firstParent = first.getParent();
        Path secondParent = second.getParent();
        return sameParent(firstParent, secondParent) && sameFileName(first, second, firstParent);
    }

    private static boolean sameParent(@Nullable Path firstParent, @Nullable Path secondParent) throws IOException {
        return (firstParent == null || secondParent == null)
                ? Objects.equals(firstParent, secondParent)
                : sameNonNullParent(firstParent, secondParent);
    }

    private static boolean sameNonNullParent(Path firstParent, Path secondParent) throws IOException {
        return firstParent.equals(secondParent)
                || sameAliasedParent(firstParent, secondParent);
    }

    private static boolean sameAliasedParent(Path firstParent, Path secondParent) throws IOException {
        return sameExistingFile(firstParent, secondParent)
                || sameRealPath(firstParent, secondParent)
                || sameCaseInsensitivePath(firstParent, secondParent);
    }

    private static boolean sameRealPath(Path first, Path second) {
        Path firstRealPath = realPathForComparison(first);
        Path secondRealPath = realPathForComparison(second);
        return firstRealPath != null && firstRealPath.equals(secondRealPath);
    }

    private static @Nullable Path realPathForComparison(Path path) {
        return realPathForComparison(path, 0);
    }

    private static @Nullable Path realPathForComparison(Path path, int symlinkDepth) {
        if (symlinkDepth > 8) {
            return null;
        }
        Path normalized = path.toAbsolutePath().normalize();
        try {
            if (Files.isSymbolicLink(normalized)) {
                return symbolicLinkTargetForComparison(normalized, symlinkDepth);
            }
            if (Files.exists(normalized)) {
                return normalized.toRealPath();
            }
            Path existing = nearestExistingPath(normalized);
            if (existing != null) {
                return existing.toRealPath().resolve(existing.relativize(normalized)).normalize();
            }
        } catch (IOException | SecurityException exception) {
            return null;
        }
        return null;
    }

    private static @Nullable Path symbolicLinkTargetForComparison(Path link, int symlinkDepth) throws IOException {
        Path target = Files.readSymbolicLink(link);
        Path resolved = link.resolveSibling(target);
        return realPathForComparison(resolved, symlinkDepth + 1);
    }

    private static @Nullable Path nearestExistingPath(Path path) {
        return ancestors(path).filter(Files::exists).findFirst().orElse(null);
    }

    private static boolean sameFileName(Path first, Path second, @Nullable Path parent) {
        Path firstFileName = first.getFileName();
        Path secondFileName = second.getFileName();
        if (firstFileName == null || secondFileName == null) {
            return false;
        }
        String firstName = firstFileName.toString();
        String secondName = secondFileName.toString();
        return firstName.equals(secondName)
                || (firstName.equalsIgnoreCase(secondName) && isCaseInsensitive(parent));
    }

    private static boolean isCaseInsensitive(@Nullable Path path) {
        Path directory = nearestExistingDirectory(path);
        return directory == null ? isLikelyCaseInsensitiveOs() : directoryIsCaseInsensitive(directory);
    }

    private static boolean directoryIsCaseInsensitive(Path directory) {
        try {
            Path probe = Files.createTempFile(directory, ".crap-java-case-", ".tmp");
            try {
                return caseVariantExists(probe);
            } finally {
                Files.deleteIfExists(probe);
            }
        } catch (IOException | SecurityException exception) {
            return isLikelyCaseInsensitiveOs();
        }
    }

    private static @Nullable Path nearestExistingDirectory(@Nullable Path path) {
        Path start = path == null ? Path.of(".").toAbsolutePath().normalize() : path.toAbsolutePath().normalize();
        return ancestors(start).filter(Files::isDirectory).findFirst().orElse(null);
    }

    private static Stream<Path> ancestors(Path path) {
        return Stream.iterate(path, Objects::nonNull, Path::getParent);
    }

    private static boolean caseVariantExists(Path probe) {
        Path fileName = probe.getFileName();
        if (fileName == null) {
            return false;
        }
        String name = fileName.toString();
        Path variant = probe.resolveSibling(name.toUpperCase(Locale.ROOT));
        Path variantFileName = variant.getFileName();
        return variantFileName != null
                && !name.equals(variantFileName.toString())
                && Files.exists(variant);
    }

    static boolean isLikelyCaseInsensitiveOs() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        return os.startsWith("windows");
    }

    private static boolean sameCaseInsensitivePath(Path first, Path second) {
        return first.toString().equalsIgnoreCase(second.toString()) && isCaseInsensitive(first);
    }
}
