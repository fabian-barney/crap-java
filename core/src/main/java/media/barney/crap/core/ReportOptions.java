package media.barney.crap.core;

import java.nio.file.Path;
import org.jspecify.annotations.Nullable;

record ReportOptions(
        ReportFormat format,
        boolean failuresOnly,
        boolean omitRedundancy,
        @Nullable Path outputPath,
        @Nullable Path junitReportPath
) {
    ReportOptions {
        outputPath = normalize(outputPath);
        junitReportPath = normalize(junitReportPath);
        if (outputPath != null && outputPath.equals(junitReportPath)) {
            throw new IllegalArgumentException("output and junitReport must not point to the same file");
        }
    }

    static ReportOptions textWithOptionalJunit(@Nullable Path junitReportPath) {
        return new ReportOptions(ReportFormat.TEXT, false, false, null, junitReportPath);
    }

    private static @Nullable Path normalize(@Nullable Path path) {
        if (path == null) {
            return null;
        }
        return path.toAbsolutePath().normalize();
    }
}
