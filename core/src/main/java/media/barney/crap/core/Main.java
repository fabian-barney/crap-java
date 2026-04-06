package media.barney.crap.core;

import java.io.PrintStream;
import java.nio.file.Path;
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
}

