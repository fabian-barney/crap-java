package media.barney.crapjava.core;

import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;

final class CliApplication {

    private final Path projectRoot;
    private final PrintStream out;
    private final PrintStream err;
    private final CoverageRunner coverageRunner;
    private final CoverageMode coverageMode;

    CliApplication(Path projectRoot,
                   PrintStream out,
                   PrintStream err,
                   CoverageRunner coverageRunner,
                   CoverageMode coverageMode) {
        this.projectRoot = projectRoot;
        this.out = out;
        this.err = err;
        this.coverageRunner = coverageRunner;
        this.coverageMode = coverageMode;
    }

    int execute(String[] args) throws Exception {
        ParseOutcome parse = parseArguments(args);
        if (parse.exitCode >= 0) {
            return parse.exitCode;
        }
        CliArguments parsed = parse.arguments();
        try {
            List<Path> filesToAnalyze = filesForMode(parsed);
            if (filesToAnalyze.isEmpty()) {
                out.println("No Java files to analyze.");
                return 0;
            }

            List<MethodMetrics> metrics = analyzeByModule(filesToAnalyze, parsed.buildToolSelection());
            metrics.sort(Comparator.comparing(MethodMetrics::crapScore,
                    Comparator.nullsLast(Comparator.reverseOrder())));
            out.print(ReportFormatter.format(metrics));

            double max = Main.maxCrap(metrics);
            if (thresholdExceeded(max)) {
                err.printf("CRAP threshold exceeded: %.1f > 8.0%n", max);
                return 2;
            }
            return 0;
        } catch (IllegalArgumentException ex) {
            err.println(ex.getMessage());
            return 1;
        }
    }

    private List<MethodMetrics> analyzeByModule(List<Path> filesToAnalyze,
                                                BuildToolSelection buildToolSelection) throws Exception {
        List<MethodMetrics> metrics = new ArrayList<>();
        for (Map.Entry<ProjectModule, List<Path>> entry : groupByModule(filesToAnalyze, buildToolSelection).entrySet()) {
            ProjectModule module = entry.getKey();
            Path jacocoXml = module.jacocoXmlPath();
            if (coverageMode.shouldGenerateCoverage()) {
                coverageRunner.generateCoverage(module);
            }
            if (!Files.exists(jacocoXml)) {
                err.println("Warning: JaCoCo XML not found at " + jacocoXml + ". Coverage will be N/A.");
            }
            metrics.addAll(CrapAnalyzer.analyze(module.moduleRoot(), entry.getValue(), jacocoXml));
        }
        return metrics;
    }

    static boolean thresholdExceeded(double max) {
        return Double.compare(max, 8.0) > 0;
    }

    private ParseOutcome parseArguments(String[] args) {
        try {
            CliArguments parsed = CliArgumentsParser.parse(args);
            if (parsed.mode() == CliMode.HELP) {
                out.println(Main.usage());
                return ParseOutcome.exit(0);
            }
            return ParseOutcome.ok(parsed);
        } catch (IllegalArgumentException ex) {
            err.println(ex.getMessage());
            out.println(Main.usage());
            return ParseOutcome.exit(1);
        }
    }

    private List<Path> filesForMode(CliArguments parsed) throws Exception {
        return switch (parsed.mode()) {
            case ALL_SRC -> SourceFileFinder.findAllJavaFilesUnderSourceRoots(projectRoot);
            case CHANGED_SRC -> ChangedFileDetector.changedJavaFilesUnderSourceRoots(projectRoot);
            case EXPLICIT_FILES -> explicitFiles(parsed.fileArgs());
            case HELP -> List.of();
        };
    }

    private List<Path> explicitFiles(List<String> args) throws Exception {
        Set<Path> files = new LinkedHashSet<>();
        for (String arg : args) {
            Path path = projectRoot.resolve(arg).normalize();
            if (Files.isDirectory(path)) {
                files.addAll(SourceFileFinder.findAllJavaFilesUnderSourceRoots(path));
            } else {
                files.add(path);
            }
        }
        List<Path> sorted = new ArrayList<>(files);
        sorted.sort(Comparator.naturalOrder());
        return sorted;
    }

    static ProjectModule moduleFor(Path workspaceRoot, Path file, BuildToolSelection buildToolSelection) {
        return ProjectModuleResolver.resolve(workspaceRoot, file, buildToolSelection);
    }

    private Map<ProjectModule, List<Path>> groupByModule(List<Path> filesToAnalyze, BuildToolSelection buildToolSelection) {
        Map<ProjectModule, List<Path>> grouped = new LinkedHashMap<>();
        for (Path file : filesToAnalyze) {
            ProjectModule module = moduleFor(projectRoot, file, buildToolSelection);
            grouped.computeIfAbsent(module, ignored -> new ArrayList<>()).add(file);
        }
        return grouped;
    }

    private static final class ParseOutcome {
        private final @Nullable CliArguments arguments;
        private final int exitCode;

        private ParseOutcome(@Nullable CliArguments arguments, int exitCode) {
            this.arguments = arguments;
            this.exitCode = exitCode;
        }

        private static ParseOutcome ok(CliArguments arguments) {
            return new ParseOutcome(arguments, -1);
        }

        private static ParseOutcome exit(int code) {
            return new ParseOutcome(null, code);
        }

        private CliArguments arguments() {
            if (arguments == null) {
                throw new IllegalStateException("No parsed arguments are available");
            }
            return arguments;
        }
    }
}
