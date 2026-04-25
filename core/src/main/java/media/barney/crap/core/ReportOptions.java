package media.barney.crap.core;

import java.nio.file.Path;
import org.jspecify.annotations.Nullable;

record ReportOptions(
        ReportFormat format,
        @Nullable Path outputPath,
        @Nullable Path junitReportPath
) {
    static ReportOptions textWithOptionalJunit(@Nullable Path junitReportPath) {
        return new ReportOptions(ReportFormat.TEXT, null, junitReportPath);
    }
}
