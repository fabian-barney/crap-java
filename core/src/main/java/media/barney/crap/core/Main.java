package media.barney.crap.core;

import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class Main {

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
        return runResolvedModules(modules, commonRoot(modules), out, err, ReportOptions.textWithOptionalJunit(null));
    }

    public static int runWithExistingCoverage(List<ResolvedCoverageModule> modules,
                                              Path reportRoot,
                                              PrintStream out,
                                              PrintStream err,
                                              Path junitReportPath) throws Exception {
        return runResolvedModules(
                modules,
                reportRoot.toAbsolutePath().normalize(),
                out,
                err,
                ReportOptions.textWithOptionalJunit(junitReportPath.toAbsolutePath().normalize())
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
                                          ReportOptions reportOptions) throws Exception {
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

        CrapReport report = CrapReport.from(metrics, ReportPublisher.THRESHOLD);
        ReportPublisher.publish(report, reportOptions, out);

        double max = Main.maxCrap(metrics);
        if (CliApplication.thresholdExceeded(max)) {
            err.printf("CRAP threshold exceeded: %.1f > 8.0%n", max);
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
                  crap-java --format json                 Write report as toon, json, text, or junit (default: toon)
                  crap-java --output report.toon          Write the selected report format to a file
                  crap-java --junit-report report.xml     Also write a JUnit XML report for CI
                  crap-java <path...>                     Analyze files, or for directory args analyze nested src/main/java trees under each path
                  crap-java --help                        Print this help message
                """;
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

