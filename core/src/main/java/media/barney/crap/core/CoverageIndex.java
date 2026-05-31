package media.barney.crap.core;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import org.jspecify.annotations.Nullable;

final class CoverageIndex {

    private final Map<String, CoverageBucket> coverageByKey;

    private CoverageIndex(Map<String, CoverageBucket> coverageByKey) {
        this.coverageByKey = Collections.unmodifiableMap(new LinkedHashMap<>(coverageByKey));
    }

    static CoverageIndex empty() {
        return new CoverageIndex(Map.of());
    }

    static Builder builder() {
        return new Builder();
    }

    @Nullable EffectiveCoverage lookupCoverage(String className, String methodName, int line) {
        CoverageBucket exact = coverageByKey.get(key(className, methodName, line));
        if (exact != null) {
            return exact.effectiveCoverage();
        }
        return nearestCoverage(className, methodName, line);
    }

    @Nullable EffectiveCoverage nearestCoverage(String className, String methodName, int line) {
        String prefix = String.format(Locale.ROOT, "%s#%s:", className, methodName);
        CoverageBucket nearest = null;
        int nearestDistance = Integer.MAX_VALUE;
        for (Map.Entry<String, CoverageBucket> entry : coverageByKey.entrySet()) {
            String entryKey = entry.getKey();
            if (!entryKey.startsWith(prefix)) {
                continue;
            }
            int jacocoLine = parseTrailingLine(entryKey);
            int distance = Math.abs(jacocoLine - line);
            if (distance < nearestDistance) {
                nearestDistance = distance;
                nearest = entry.getValue();
            }
        }
        if (nearest == null) {
            return null;
        }
        return nearest.effectiveCoverage();
    }

    int entryCount(String className, String methodName, int line) {
        CoverageBucket bucket = coverageByKey.get(key(className, methodName, line));
        return bucket == null ? 0 : bucket.count();
    }

    static int parseTrailingLine(String key) {
        int separator = key.lastIndexOf(':');
        if (separator < 0) {
            return Integer.MAX_VALUE;
        }
        String lineText = key.substring(separator + 1);
        if (lineText.isEmpty()) {
            return Integer.MAX_VALUE;
        }
        try {
            return Integer.parseInt(lineText);
        } catch (NumberFormatException ex) {
            return Integer.MAX_VALUE;
        }
    }

    private static String key(String className, String methodName, int line) {
        return String.format(Locale.ROOT, "%s#%s:%d", className, methodName, line);
    }

    static final class Builder {
        private final Map<String, CoverageBucket> coverageByKey = new LinkedHashMap<>();

        void add(String className, String methodName, int line, CoverageData data) {
            coverageByKey.compute(key(className, methodName, line),
                    (ignored, existing) -> existing == null ? new CoverageBucket(data, 1) : existing.addDuplicate());
        }

        CoverageIndex build() {
            return new CoverageIndex(coverageByKey);
        }
    }

    private record CoverageBucket(CoverageData data, int count) {

        private CoverageBucket addDuplicate() {
            return new CoverageBucket(data, count + 1);
        }

        private @Nullable EffectiveCoverage effectiveCoverage() {
            if (count > 1) {
                return null;
            }
            return data.effectiveCoverage();
        }
    }
}
