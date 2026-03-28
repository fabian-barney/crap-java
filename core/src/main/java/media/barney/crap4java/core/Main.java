package media.barney.crap4java.core;

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
        return run(args, projectRoot, out, err, new CoverageRunner((command, directory) -> 0));
    }

    public static int run(String[] args, Path projectRoot, PrintStream out, PrintStream err) throws Exception {
        return run(args, projectRoot, out, err, new CoverageRunner(new ProcessCommandExecutor()));
    }

    static int run(String[] args,
                   Path projectRoot,
                   PrintStream out,
                   PrintStream err,
                   CoverageRunner coverageRunner) throws Exception {
        return new CliApplication(projectRoot, out, err, coverageRunner).execute(args);
    }

    static String usage() {
        return """
                Usage:
                  crap4java                                Analyze all Java files under src/
                  crap4java --changed                      Analyze changed Java files under src/
                  crap4java --build-tool gradle           Force Gradle for all resolved modules
                  crap4java --build-tool maven --changed  Force Maven for changed files
                  crap4java <path...>                     Analyze files, or for directory args analyze <dir>/**/src/**/*.java
                  crap4java --help                        Print this help message
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
