package media.barney.crap.core;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.jspecify.annotations.Nullable;

final class CrapAnalyzer {

    private CrapAnalyzer() {
    }

    static List<MethodMetrics> analyze(Path projectRoot, List<Path> changedFiles, Path jacocoXml) throws IOException {
        return analyze(
                projectRoot,
                changedFiles,
                jacocoXml,
                SourceExclusionMatcher.create(projectRoot, SourceExclusionOptions.defaults()),
                SourceExclusionAudit.builder()
        );
    }

    static List<MethodMetrics> analyze(Path projectRoot,
                                       List<Path> changedFiles,
                                       Path jacocoXml,
                                       SourceExclusionMatcher exclusions,
                                       SourceExclusionAudit.Builder audit) throws IOException {
        Map<String, CoverageData> coverageMap = JacocoCoverageParser.parse(jacocoXml);
        List<MethodMetrics> metrics = new ArrayList<>();
        Set<String> excludedClasses = new LinkedHashSet<>();
        Path normalizedProjectRoot = projectRoot.toAbsolutePath().normalize();

        for (Path file : changedFiles) {
            analyzeFile(
                    file,
                    normalizedProjectRoot,
                    coverageMap,
                    exclusions,
                    audit,
                    excludedClasses,
                    metrics
            );
        }

        metrics.sort(Comparator.comparing(
                        MethodMetrics::crapScore,
                        Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(MethodMetrics::sourcePath)
                .thenComparingInt(MethodMetrics::startLine)
                .thenComparing(MethodMetrics::methodName));
        return metrics;
    }

    private static void analyzeFile(Path file,
                                    Path projectRoot,
                                    Map<String, CoverageData> coverageMap,
                                    SourceExclusionMatcher exclusions,
                                    SourceExclusionAudit.Builder audit,
                                    Set<String> excludedClasses,
                                    List<MethodMetrics> metrics) throws IOException {
        if (!Files.exists(file)) {
            return;
        }
        Path normalizedFile = file.toAbsolutePath().normalize();
        String source = Files.readString(file);
        Path fileName = file.getFileName();
        String sourceName = fileName == null ? normalizedFile.toString() : fileName.toString();
        for (MethodDescriptor method : JavaMethodParser.parse(sourceName, source)) {
            addMetricIfIncluded(method, projectRoot, normalizedFile, coverageMap, exclusions, audit, excludedClasses, metrics);
        }
    }

    private static void addMetricIfIncluded(MethodDescriptor method,
                                            Path projectRoot,
                                            Path file,
                                            Map<String, CoverageData> coverageMap,
                                            SourceExclusionMatcher exclusions,
                                            SourceExclusionAudit.Builder audit,
                                            Set<String> excludedClasses,
                                            List<MethodMetrics> metrics) {
        Optional<String> classExclusion = exclusions.classExclusionReason(method.className(), method.classAnnotations());
        if (classExclusion.isPresent()) {
            recordExcludedClass(method, classExclusion.get(), audit, excludedClasses);
            return;
        }
        metrics.add(methodMetric(method, projectRoot, file, coverageMap));
    }

    private static void recordExcludedClass(MethodDescriptor method,
                                            String reason,
                                            SourceExclusionAudit.Builder audit,
                                            Set<String> excludedClasses) {
        if (excludedClasses.add(method.className())) {
            audit.recordExcludedClass(reason);
        }
    }

    private static MethodMetrics methodMetric(MethodDescriptor method,
                                              Path projectRoot,
                                              Path file,
                                              Map<String, CoverageData> coverageMap) {
        EffectiveCoverage coverage = lookupCoverage(coverageMap, method.className(), method.name(), method.startLine());
        Double coveragePercent = coverage == null ? null : coverage.percent();
        String coverageKind = coverage == null ? CoverageData.UNAVAILABLE_KIND : coverage.kind();
        Double crap = CrapScore.calculate(method.complexity(), coveragePercent);
        return new MethodMetrics(
                method.name(),
                method.className(),
                sourcePath(projectRoot, file),
                method.startLine(),
                method.endLine(),
                method.complexity(),
                coveragePercent,
                coverageKind,
                crap
        );
    }

    private static String sourcePath(Path projectRoot, Path file) {
        Path path = file.startsWith(projectRoot) ? projectRoot.relativize(file) : file;
        return path.normalize().toString().replace('\\', '/');
    }

    static @Nullable EffectiveCoverage lookupCoverage(Map<String, CoverageData> coverageMap,
                                                      String className,
                                                      String methodName,
                                                      int line) {
        EffectiveCoverage exactCoverage = exactCoverage(coverageMap, className, methodName, line);
        if (exactCoverage != null) {
            return exactCoverage;
        }

        CoverageData nearest = nearestCoverage(coverageMap, className, methodName, line);
        if (nearest == null) {
            return null;
        }
        return nearest.effectiveCoverage();
    }

    static @Nullable EffectiveCoverage exactCoverage(Map<String, CoverageData> coverageMap,
                                                     String className,
                                                     String methodName,
                                                     int line) {
        String exactKey = className + "#" + methodName + ":" + line;
        CoverageData exact = coverageMap.get(exactKey);
        if (exact == null) {
            return null;
        }
        return exact.effectiveCoverage();
    }

    static @Nullable CoverageData nearestCoverage(Map<String, CoverageData> coverageMap,
                                                  String className,
                                                  String methodName,
                                                  int line) {
        String prefix = className + "#" + methodName + ":";
        CoverageData nearest = null;
        int nearestDistance = Integer.MAX_VALUE;
        for (Map.Entry<String, CoverageData> entry : coverageMap.entrySet()) {
            String key = entry.getKey();
            if (!key.startsWith(prefix)) {
                continue;
            }
            int jacocoLine = parseTrailingLine(key);
            int distance = Math.abs(jacocoLine - line);
            if (distance < nearestDistance) {
                nearestDistance = distance;
                nearest = entry.getValue();
            }
        }
        return nearest;
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
}

