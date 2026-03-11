package crap4java;

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

final class CliApplication {

    private final Path projectRoot;
    private final PrintStream out;
    private final PrintStream err;
    private final CoverageRunner coverageRunner;

    CliApplication(Path projectRoot, PrintStream out, PrintStream err, CoverageRunner coverageRunner) {
        this.projectRoot = projectRoot;
        this.out = out;
        this.err = err;
        this.coverageRunner = coverageRunner;
    }

    int execute(String[] args) throws Exception {
        ParseOutcome parse = parseArguments(args);
        if (parse.exitCode >= 0) {
            return parse.exitCode;
        }
        CliArguments parsed = parse.arguments;
        List<Path> filesToAnalyze = filesForMode(parsed);
        if (filesToAnalyze.isEmpty()) {
            out.println("No Java files to analyze.");
            return 0;
        }

        List<MethodMetrics> metrics = analyzeByModule(filesToAnalyze);
        metrics.sort(Comparator.comparing(MethodMetrics::crapScore,
                Comparator.nullsLast(Comparator.reverseOrder())));
        out.print(ReportFormatter.format(metrics));

        double max = Main.maxCrap(metrics);
        if (thresholdExceeded(max)) {
            err.printf("CRAP threshold exceeded: %.1f > 8.0%n", max);
            return 2;
        }
        return 0;
    }

    private List<MethodMetrics> analyzeByModule(List<Path> filesToAnalyze) throws Exception {
        List<MethodMetrics> metrics = new ArrayList<>();
        for (Map.Entry<Path, List<Path>> entry : groupByModuleRoot(filesToAnalyze).entrySet()) {
            Path moduleRoot = entry.getKey();
            Path jacocoXml = moduleRoot.resolve("target/site/jacoco/jacoco.xml");
            coverageRunner.generateCoverage(moduleRoot);
            if (!Files.exists(jacocoXml)) {
                err.println("Warning: JaCoCo XML not found at " + jacocoXml + ". Coverage will be N/A.");
            }
            metrics.addAll(CrapAnalyzer.analyze(moduleRoot, entry.getValue(), jacocoXml));
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
            case ALL_SRC -> SourceFileFinder.findAllJavaFilesUnderSrc(projectRoot);
            case CHANGED_SRC -> ChangedFileDetector.changedJavaFilesUnderSrc(projectRoot);
            case EXPLICIT_FILES -> explicitFiles(parsed.fileArgs());
            case HELP -> List.of();
        };
    }

    private List<Path> explicitFiles(List<String> args) throws Exception {
        Set<Path> files = new LinkedHashSet<>();
        for (String arg : args) {
            Path path = projectRoot.resolve(arg).normalize();
            if (Files.isDirectory(path)) {
                files.addAll(SourceFileFinder.findAllJavaFilesUnderSrc(path));
            } else {
                files.add(path);
            }
        }
        List<Path> sorted = new ArrayList<>(files);
        sorted.sort(Comparator.naturalOrder());
        return sorted;
    }

    static Path moduleRootFor(Path workspaceRoot, Path file) {
        Path normalizedWorkspaceRoot = workspaceRoot.normalize();
        Path current = Files.isDirectory(file) ? file.normalize() : file.normalize().getParent();
        while (current != null && current.startsWith(normalizedWorkspaceRoot)) {
            if (Files.exists(current.resolve("pom.xml"))) {
                return current;
            }
            current = current.getParent();
        }
        return normalizedWorkspaceRoot;
    }

    private Map<Path, List<Path>> groupByModuleRoot(List<Path> filesToAnalyze) {
        Map<Path, List<Path>> grouped = new LinkedHashMap<>();
        for (Path file : filesToAnalyze) {
            Path moduleRoot = moduleRootFor(projectRoot, file);
            grouped.computeIfAbsent(moduleRoot, ignored -> new ArrayList<>()).add(file);
        }
        return grouped;
    }

    private static final class ParseOutcome {
        private final CliArguments arguments;
        private final int exitCode;

        private ParseOutcome(CliArguments arguments, int exitCode) {
            this.arguments = arguments;
            this.exitCode = exitCode;
        }

        private static ParseOutcome ok(CliArguments arguments) {
            return new ParseOutcome(arguments, -1);
        }

        private static ParseOutcome exit(int code) {
            return new ParseOutcome(null, code);
        }
    }
}
