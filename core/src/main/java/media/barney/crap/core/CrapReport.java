package media.barney.crap.core;

import java.util.List;
import org.jspecify.annotations.Nullable;

record CrapReport(
        int schemaVersion,
        String tool,
        double threshold,
        String coverageKind,
        ReportSummary summary,
        List<MethodReport> methods
) {
    private static final int SCHEMA_VERSION = 1;
    private static final String TOOL = "crap-java";
    private static final String COVERAGE_KIND = "instruction";

    static CrapReport from(List<MethodMetrics> metrics, double threshold) {
        List<MethodReport> methods = metrics.stream()
                .map(metric -> MethodReport.from(metric, threshold))
                .toList();
        return new CrapReport(
                SCHEMA_VERSION,
                TOOL,
                threshold,
                COVERAGE_KIND,
                ReportSummary.from(methods),
                methods
        );
    }

    record ReportSummary(
            String status,
            int total,
            int passed,
            int failed,
            int skipped,
            @Nullable Double maxCrapScore
    ) {
        private static ReportSummary from(List<MethodReport> methods) {
            int passed = 0;
            int failed = 0;
            int skipped = 0;
            double max = 0.0;
            boolean hasScore = false;
            for (MethodReport method : methods) {
                if (method.status() == MethodStatus.PASSED) {
                    passed++;
                } else if (method.status() == MethodStatus.FAILED) {
                    failed++;
                } else {
                    skipped++;
                }
                if (method.crapScore() != null) {
                    max = Math.max(max, method.crapScore());
                    hasScore = true;
                }
            }
            return new ReportSummary(
                    failed > 0 ? "failed" : "passed",
                    methods.size(),
                    passed,
                    failed,
                    skipped,
                    hasScore ? max : null
            );
        }
    }

    record MethodReport(
            MethodStatus status,
            String methodName,
            String className,
            String sourcePath,
            int startLine,
            int endLine,
            int complexity,
            @Nullable Double coveragePercent,
            @Nullable Double crapScore
    ) {
        private static MethodReport from(MethodMetrics metric, double threshold) {
            return new MethodReport(
                    status(metric, threshold),
                    metric.methodName(),
                    metric.className(),
                    metric.sourcePath(),
                    metric.startLine(),
                    metric.endLine(),
                    metric.complexity(),
                    metric.coveragePercent(),
                    metric.crapScore()
            );
        }

        private static MethodStatus status(MethodMetrics metric, double threshold) {
            if (metric.crapScore() == null) {
                return MethodStatus.SKIPPED;
            }
            if (Double.compare(metric.crapScore(), threshold) > 0) {
                return MethodStatus.FAILED;
            }
            return MethodStatus.PASSED;
        }
    }
}
