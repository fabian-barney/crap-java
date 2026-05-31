package media.barney.crap.core;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

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
        CoverageIndex coverageIndex = JacocoCoverageParser.parse(jacocoXml);
        List<MethodMetrics> metrics = new ArrayList<>();
        Set<String> excludedClasses = new LinkedHashSet<>();
        Path normalizedProjectRoot = projectRoot.toAbsolutePath().normalize();

        for (Path file : changedFiles) {
            analyzeFile(
                    file,
                    normalizedProjectRoot,
                    coverageIndex,
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
                                    CoverageIndex coverageIndex,
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
            addMetricIfIncluded(method, projectRoot, normalizedFile, coverageIndex, exclusions, audit, excludedClasses, metrics);
        }
    }

    private static void addMetricIfIncluded(MethodDescriptor method,
                                            Path projectRoot,
                                            Path file,
                                            CoverageIndex coverageIndex,
                                            SourceExclusionMatcher exclusions,
                                            SourceExclusionAudit.Builder audit,
                                            Set<String> excludedClasses,
                                            List<MethodMetrics> metrics) {
        Optional<String> classExclusion = exclusions.classExclusionReason(method.className(), method.classAnnotations());
        if (classExclusion.isPresent()) {
            recordExcludedClass(method, classExclusion.get(), audit, excludedClasses);
            return;
        }
        metrics.add(methodMetric(method, projectRoot, file, coverageIndex));
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
                                              CoverageIndex coverageIndex) {
        EffectiveCoverage coverage = coverageIndex.lookupCoverage(method.className(), method.name(), method.startLine());
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
}

