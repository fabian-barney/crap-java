package media.barney.crap.core;

import org.jspecify.annotations.Nullable;

record MethodMetrics(
        String methodName,
        String className,
        String sourcePath,
        int startLine,
        int endLine,
        int complexity,
        @Nullable Double coveragePercent,
        @Nullable Double crapScore
) {
}

