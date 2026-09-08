package media.barney.crap.core;

import java.util.List;
import org.jspecify.annotations.Nullable;

record CrapReport(
        String status,
        double threshold,
        List<MethodReport> methods,
        SourceExclusionAudit exclusions,
        double elapsedSeconds
) {
    CrapReport(String status, double threshold, List<MethodReport> methods, SourceExclusionAudit exclusions) {
        this(status, threshold, methods, exclusions, 0.0);
    }

    CrapReport(String status, double threshold, List<MethodReport> methods) {
        this(status, threshold, methods, SourceExclusionAudit.empty());
    }

    static CrapReport from(List<MethodMetrics> metrics, double threshold) {
        return from(metrics, threshold, SourceExclusionAudit.empty());
    }

    static CrapReport from(List<MethodMetrics> metrics, double threshold, SourceExclusionAudit exclusions) {
        double validatedThreshold = Thresholds.validate(threshold);
        List<MethodReport> methods = metrics.stream()
                .map(metric -> MethodReport.from(metric, validatedThreshold))
                .toList();
        return new CrapReport(status(methods), validatedThreshold, methods, exclusions);
    }

    CrapReport withElapsedNanos(long elapsedNanos) {
        double seconds = Math.max(0.0, elapsedNanos / 1_000_000_000.0);
        return new CrapReport(status, threshold, methods, exclusions, seconds);
    }

    private static String status(List<MethodReport> methods) {
        boolean failed = methods.stream()
                .anyMatch(method -> method.status() == MethodStatus.FAILED);
        return failed ? MethodStatus.FAILED.value() : MethodStatus.PASSED.value();
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
            String coverageKind,
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
                    metric.coverageKind(),
                    metric.crapScore()
            );
        }

        private static MethodStatus status(MethodMetrics metric, double threshold) {
            Double crapScore = metric.crapScore();
            if (crapScore == null) {
                return MethodStatus.SKIPPED;
            }
            if (Double.compare(crapScore, threshold) > 0) {
                return MethodStatus.FAILED;
            }
            return MethodStatus.PASSED;
        }
    }
}
