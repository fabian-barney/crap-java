package media.barney.crap.core;

import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.jspecify.annotations.Nullable;

public final class Main {

    public static final double DEFAULT_THRESHOLD = Thresholds.DEFAULT;

    private Main() {
    }

    public static void main(String[] args) throws Exception {
        System.exit(run(args, Path.of(".").toAbsolutePath().normalize(), System.out, System.err));
    }

    public static int runWithExistingCoverage(String[] args,
                                              Path projectRoot,
                                              PrintStream out,
                                              PrintStream err) throws Exception {
        return run(args, projectRoot, out, err, new CoverageRunner((command, directory) -> 0), CoverageMode.USE_EXISTING);
    }

    public static int runWithExistingCoverage(List<ResolvedCoverageModule> modules,
                                              PrintStream out,
                                              PrintStream err) throws Exception {
        return runResolvedModules(
                modules,
                commonRoot(modules),
                out,
                err,
                ReportOptions.textWithOptionalJunit(null),
                DEFAULT_THRESHOLD
        );
    }

    public static int runWithExistingCoverage(List<ResolvedCoverageModule> modules,
                                              Path reportRoot,
                                              PrintStream out,
                                              PrintStream err,
                                              Path junitReportPath) throws Exception {
        return runWithExistingCoverage(modules, reportRoot, out, err, junitReportPath, DEFAULT_THRESHOLD);
    }

    public static int runWithExistingCoverage(List<ResolvedCoverageModule> modules,
                                              Path reportRoot,
                                              PrintStream out,
                                              PrintStream err,
                                              Path junitReportPath,
                                              double threshold) throws Exception {
        return runWithExistingCoverage(
                modules,
                reportRoot,
                out,
                err,
                ReportFormat.TEXT.name(),
                false,
                false,
                null,
                junitReportPath,
                threshold
        );
    }

    public static int runWithExistingCoverage(List<ResolvedCoverageModule> modules,
                                              Path reportRoot,
                                              PrintStream out,
                                              PrintStream err,
                                              String reportFormat,
                                              boolean failuresOnly,
                                              boolean omitRedundancy,
                                              @Nullable Path outputPath,
                                              @Nullable Path junitReportPath,
                                              double threshold) throws Exception {
        return runResolvedModules(
                modules,
                reportRoot.toAbsolutePath().normalize(),
                out,
                err,
                new ReportOptions(
                        ReportFormat.parse(reportFormat),
                        failuresOnly,
                        omitRedundancy,
                        normalize(outputPath),
                        normalize(junitReportPath)
                ),
                threshold
        );
    }

    public static int run(String[] args, Path projectRoot, PrintStream out, PrintStream err) throws Exception {
        return run(args, projectRoot, out, err, new CoverageRunner(new ProcessCommandExecutor()), CoverageMode.GENERATE);
    }

    static int run(String[] args,
                   Path projectRoot,
                   PrintStream out,
                   PrintStream err,
                   CoverageRunner coverageRunner) throws Exception {
        return run(args, projectRoot, out, err, coverageRunner, CoverageMode.GENERATE);
    }

    static int run(String[] args,
                   Path projectRoot,
                   PrintStream out,
                   PrintStream err,
                   CoverageRunner coverageRunner,
                   CoverageMode coverageMode) throws Exception {
        return new CliApplication(projectRoot, out, err, coverageRunner, coverageMode).execute(args);
    }

    private static int runResolvedModules(List<ResolvedCoverageModule> modules,
                                          Path reportRoot,
                                          PrintStream out,
                                          PrintStream err,
                                          ReportOptions reportOptions,
                                          double threshold) throws Exception {
        Thresholds.validate(threshold);
        writeThresholdWarning(err, threshold);
        List<MethodMetrics> metrics = new ArrayList<>();
        for (ResolvedCoverageModule module : modules) {
            if (module.sourceFiles().isEmpty()) {
                continue;
            }
            if (!Files.exists(module.coverageReport())) {
                err.println("Warning: JaCoCo XML not found at " + module.coverageReport() + ". Coverage will be N/A.");
            }
            metrics.addAll(CrapAnalyzer.analyze(reportRoot, module.sourceFiles(), module.coverageReport()));
        }

        CrapReport report = CrapReport.from(metrics, threshold);
        ReportPublisher.publish(report, reportOptions, out);

        double max = Main.maxCrap(metrics);
        if (CliApplication.thresholdExceeded(max, threshold)) {
            err.printf(Locale.ROOT, "CRAP threshold exceeded: %.1f > %.1f%n", max, threshold);
            return 2;
        }
        return 0;
    }

    static String usage() {
        return """
                Usage:
                  crap-java                                Analyze all Java files under any nested src/main/java tree
                  crap-java --changed                      Analyze changed Java files under any nested src/main/java tree
                  crap-java --build-tool gradle           Force Gradle for all resolved modules
                  crap-java --build-tool maven --changed  Force Maven for changed files
                  crap-java --format json                 Write report as toon, json, text, junit, or none (default: toon)
                  crap-java --agent                       Apply AI-agent defaults: toon, failures only, omit redundancy
                  crap-java --failures-only[=true|false]  Only include failing methods in the primary report
                  crap-java --omit-redundancy[=true|false]  Omit redundant method fields from the primary report
                  crap-java --output report.toon          Write the selected report format to a file
                  crap-java --junit-report report.xml     Also write a JUnit XML report for CI
                  crap-java --threshold 6                 Override the CRAP threshold (default: 8.0)
                  crap-java <path...>                     Analyze files, or for directory args analyze nested src/main/java trees under each path
                  crap-java --help                        Print this help message
                """;
    }

    static void writeThresholdWarning(PrintStream err, double threshold) {
        String warning = Thresholds.warning(threshold);
        if (!warning.isEmpty()) {
            err.println(warning);
        }
    }

    private static @Nullable Path normalize(@Nullable Path path) {
        return path == null ? null : path.toAbsolutePath().normalize();
    }

    private static Path commonRoot(List<ResolvedCoverageModule> modules) {
        Path common = null;
        for (ResolvedCoverageModule module : modules) {
            Path root = module.moduleRoot();
            if (common == null) {
                common = root;
            } else {
                common = commonRoot(common, root);
            }
        }
        return common == null ? Path.of(".").toAbsolutePath().normalize() : common;
    }

    private static Path commonRoot(Path left, Path right) {
        Path absoluteLeft = left.toAbsolutePath().normalize();
        Path absoluteRight = right.toAbsolutePath().normalize();
        Path common = absoluteLeft.getRoot();
        int max = Math.min(absoluteLeft.getNameCount(), absoluteRight.getNameCount());
        for (int index = 0; index < max && absoluteLeft.getName(index).equals(absoluteRight.getName(index)); index++) {
            common = common == null ? absoluteLeft.getName(index) : common.resolve(absoluteLeft.getName(index));
        }
        return common == null ? absoluteLeft : common;
    }

    static double maxCrap(List<MethodMetrics> metrics) {
        double max = 0.0;
        for (MethodMetrics metric : metrics) {
            if (metric.crapScore() != null) {
                max = Math.max(max, metric.crapScore());
            }
        }
        return max;
    }

    public record ResolvedCoverageModule(Path moduleRoot, Path coverageReport, List<Path> sourceFiles) {

        public ResolvedCoverageModule {
            moduleRoot = moduleRoot.toAbsolutePath().normalize();
            coverageReport = coverageReport.toAbsolutePath().normalize();
            sourceFiles = sourceFiles.stream()
                    .map(path -> path.toAbsolutePath().normalize())
                    .sorted()
                    .toList();
        }
    }
}

