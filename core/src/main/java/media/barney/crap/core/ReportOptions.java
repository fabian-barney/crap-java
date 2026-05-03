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
    static ReportOptions textWithOptionalJunit(@Nullable Path junitReportPath) {
        return new ReportOptions(ReportFormat.TEXT, false, false, null, junitReportPath);
    }
}
