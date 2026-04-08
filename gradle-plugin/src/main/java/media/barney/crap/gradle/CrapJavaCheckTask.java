package media.barney.crap.gradle;

import media.barney.crap.core.Main;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public abstract class CrapJavaCheckTask extends DefaultTask {

    @Internal
    public abstract DirectoryProperty getAnalysisRoot();

    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract ConfigurableFileCollection getAnalysisSources();

    @InputFiles
    @Optional
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract ConfigurableFileCollection getCoverageReports();

    @Input
    public abstract MapProperty<String, String> getModuleCoverageReports();

    @TaskAction
    void runCheck() throws Exception {
        List<Path> sourceFiles = getAnalysisSources().getFiles().stream()
                .map(file -> file.toPath().toAbsolutePath().normalize())
                .sorted()
                .toList();
        if (sourceFiles.isEmpty()) {
            getLogger().lifecycle("No Java files to analyze.");
            return;
        }
        List<Main.ResolvedCoverageModule> modules = resolvedModules(sourceFiles);
        try (var out = GradleLoggingPrintStreams.standardOut(getLogger());
             var err = GradleLoggingPrintStreams.standardErr(getLogger())) {
            int exit = Main.runWithExistingCoverage(modules, out, err);
            if (exit != 0) {
                throw new GradleException("crap-java-check failed with exit " + exit);
            }
        }
    }

    private List<Main.ResolvedCoverageModule> resolvedModules(List<Path> sourceFiles) {
        Path analysisRoot = getAnalysisRoot().get().getAsFile().toPath().toAbsolutePath().normalize();
        Map<String, String> configuredModules = new LinkedHashMap<>(getModuleCoverageReports().get());
        List<String> orderedModulePaths = configuredModules.keySet().stream()
                .sorted(Comparator.naturalOrder())
                .toList();
        List<String> matchingModulePaths = orderedModulePaths.stream()
                .sorted(Comparator.comparingInt(String::length).reversed().thenComparing(Comparator.naturalOrder()))
                .toList();

        Map<String, List<Path>> sourceFilesByModule = new LinkedHashMap<>();
        for (String modulePath : orderedModulePaths) {
            sourceFilesByModule.put(modulePath, new ArrayList<>());
        }
        for (Path sourceFile : sourceFiles) {
            String modulePath = matchingModulePath(analysisRoot, sourceFile, matchingModulePaths);
            sourceFilesByModule.get(modulePath).add(sourceFile);
        }

        List<Main.ResolvedCoverageModule> modules = new ArrayList<>();
        for (String modulePath : orderedModulePaths) {
            List<Path> moduleSources = sourceFilesByModule.get(modulePath);
            if (moduleSources.isEmpty()) {
                continue;
            }
            String coverageReport = configuredModules.get(modulePath);
            modules.add(new Main.ResolvedCoverageModule(
                    resolveModuleRoot(analysisRoot, modulePath),
                    resolveRelativePath(analysisRoot, coverageReport),
                    moduleSources
            ));
        }
        return modules;
    }

    private String matchingModulePath(Path analysisRoot, Path sourceFile, List<String> matchingModulePaths) {
        String relativeSourcePath = normalizeRelativePath(analysisRoot.relativize(sourceFile));
        return matchingModulePaths.stream()
                .filter(modulePath -> matchesModulePath(relativeSourcePath, modulePath))
                .findFirst()
                .orElseThrow(() -> new GradleException("No configured Gradle module matches " + relativeSourcePath));
    }

    private static boolean matchesModulePath(String relativeSourcePath, String modulePath) {
        if (".".equals(modulePath)) {
            return true;
        }
        return relativeSourcePath.equals(modulePath) || relativeSourcePath.startsWith(modulePath + "/");
    }

    private static Path resolveModuleRoot(Path analysisRoot, String modulePath) {
        if (".".equals(modulePath)) {
            return analysisRoot;
        }
        return analysisRoot.resolve(modulePath).normalize();
    }

    private static Path resolveRelativePath(Path analysisRoot, String relativePath) {
        if (".".equals(relativePath)) {
            return analysisRoot;
        }
        return analysisRoot.resolve(relativePath).normalize();
    }

    private static String normalizeRelativePath(Path path) {
        return path.normalize().toString().replace('\\', '/');
    }
}

