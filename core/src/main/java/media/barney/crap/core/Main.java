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
        return runResolvedModules(modules, out, err);
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
                                          PrintStream out,
                                          PrintStream err) throws Exception {
        List<MethodMetrics> metrics = new ArrayList<>();
        for (ResolvedCoverageModule module : modules) {
            if (module.sourceFiles().isEmpty()) {
                continue;
            }
            if (!Files.exists(module.coverageReport())) {
                err.println("Warning: JaCoCo XML not found at " + module.coverageReport() + ". Coverage will be N/A.");
            }
            metrics.addAll(CrapAnalyzer.analyze(module.moduleRoot(), module.sourceFiles(), module.coverageReport()));
        }
        if (metrics.isEmpty()) {
            out.println("No Java files to analyze.");
            return 0;
        }

        out.print(ReportFormatter.format(metrics));

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
                  crap-java <path...>                     Analyze files, or for directory args analyze nested src/main/java trees under each path
                  crap-java --help                        Print this help message
                """;
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

