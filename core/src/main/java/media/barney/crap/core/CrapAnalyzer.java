package media.barney.crap.core;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

final class CrapAnalyzer {

    private static final Pattern PACKAGE_PATTERN = Pattern.compile("(?m)^\\s*package\\s+([a-zA-Z_][\\w.]*)\\s*;");

    private CrapAnalyzer() {
    }

    static List<MethodMetrics> analyze(Path projectRoot, List<Path> changedFiles, Path jacocoXml) throws IOException {
        Map<String, CoverageData> coverageMap = JacocoCoverageParser.parse(jacocoXml);
        List<MethodMetrics> metrics = new ArrayList<>();
        Path normalizedProjectRoot = projectRoot.toAbsolutePath().normalize();

        for (Path file : changedFiles) {
            if (!Files.exists(file)) {
                continue;
            }
            Path normalizedFile = file.toAbsolutePath().normalize();
            String source = Files.readString(file);
            String primaryClassName = classNameFromSource(file, source);
            List<MethodDescriptor> methods = JavaMethodParser.parse(primaryClassName, source);
            for (MethodDescriptor method : methods) {
                EffectiveCoverage coverage = lookupCoverage(coverageMap, method.className(), method.name(), method.startLine());
                Double coveragePercent = coverage == null ? null : coverage.percent();
                String coverageKind = coverage == null ? CoverageData.UNAVAILABLE_KIND : coverage.kind();
                Double crap = CrapScore.calculate(method.complexity(), coveragePercent);
                metrics.add(new MethodMetrics(
                        method.name(),
                        method.className(),
                        sourcePath(normalizedProjectRoot, normalizedFile),
                        method.startLine(),
                        method.endLine(),
                        method.complexity(),
                        coveragePercent,
                        coverageKind,
                        crap
                ));
            }
        }

        metrics.sort(Comparator.comparing(MethodMetrics::crapScore,
                Comparator.nullsLast(Comparator.reverseOrder())));
        return metrics;
    }

    private static String sourcePath(Path projectRoot, Path file) {
        Path path = file.startsWith(projectRoot) ? projectRoot.relativize(file) : file;
        return path.normalize().toString().replace('\\', '/');
    }

    static String classNameFromSource(Path file, String source) {
        String simpleName = file.getFileName().toString().replaceFirst("\\.java$", "");
        Matcher matcher = PACKAGE_PATTERN.matcher(source);
        if (!matcher.find()) {
            return simpleName;
        }
        return matcher.group(1) + "." + simpleName;
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

