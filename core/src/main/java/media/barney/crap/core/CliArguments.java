package media.barney.crap.core;

import java.util.List;
import org.jspecify.annotations.Nullable;

record CliArguments(
        CliMode mode,
        BuildToolSelection buildToolSelection,
        ReportFormat reportFormat,
        @Nullable String outputPath,
        @Nullable String junitReportPath,
        List<String> fileArgs
) {
}

