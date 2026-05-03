package media.barney.crap.gradle;

import media.barney.crap.core.Main;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFile;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public abstract class CrapJavaCheckTask extends DefaultTask {

    private final Provider<RegularFile> defaultJunitReport;
    private final Provider<RegularFile> junitReportState;

    public CrapJavaCheckTask() {
        defaultJunitReport = getProject().getProviders()
                .provider(this::defaultJunitReportRelativePath)
                .flatMap(path -> getProject().getLayout().getBuildDirectory().file(path));
        junitReportState = getProject().getLayout().getBuildDirectory()
                .file("tmp/crap-java/" + getName() + "/junit-report.path");
        getThreshold().convention(Main.DEFAULT_THRESHOLD);
        getAgent().convention(false);
        getFormat().convention(getAgent().map(agent -> agent ? "toon" : "none"));
        getFailuresOnly().convention(getAgent());
        getOmitRedundancy().convention(getAgent());
        getJunit().convention(true);
        getJunitReport().convention(defaultJunitReport);
    }

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

    @Input
    public abstract Property<Double> getThreshold();

    @Input
    public abstract Property<String> getFormat();

    @Input
    public abstract Property<Boolean> getAgent();

    @Input
    public abstract Property<Boolean> getFailuresOnly();

    @Input
    public abstract Property<Boolean> getOmitRedundancy();

    @OutputFile
    @Optional
    public abstract RegularFileProperty getOutput();

    @Input
    public abstract Property<Boolean> getJunit();

    @Internal
    public abstract RegularFileProperty getJunitReport();

    @OutputFile
    @Optional
    public Provider<RegularFile> getJunitReportOutput() {
        return getJunit().flatMap(enabled -> enabled
                ? getJunitReport()
                : getProject().getProviders().provider(() -> (RegularFile) null));
    }

    @TaskAction
    void runCheck() throws Exception {
        deleteDisabledJunitReport();
        List<Path> sourceFiles = getAnalysisSources().getFiles().stream()
                .map(file -> file.toPath().toAbsolutePath().normalize())
                .sorted()
                .toList();
        Path analysisRoot = getAnalysisRoot().get().getAsFile().toPath().toAbsolutePath().normalize();
        Path configuredOutputPath = outputPath();
        Path configuredJunitReportPath = junitReportPath();
        if (sourceFiles.isEmpty()) {
            try (var out = GradleLoggingPrintStreams.standardOut(getLogger());
                 var err = GradleLoggingPrintStreams.standardErr(getLogger())) {
                Main.runWithExistingCoverage(
                        List.of(),
                        analysisRoot,
                        out,
                        err,
                        getFormat().get(),
                        getFailuresOnly().get(),
                        getOmitRedundancy().get(),
                        configuredOutputPath,
                        configuredJunitReportPath,
                        getThreshold().get()
                );
                rememberJunitReportPath(configuredJunitReportPath);
            }
            return;
        }
        List<Main.ResolvedCoverageModule> modules = resolvedModules(sourceFiles);
        try (var out = GradleLoggingPrintStreams.standardOut(getLogger());
             var err = GradleLoggingPrintStreams.standardErr(getLogger())) {
            int exit = Main.runWithExistingCoverage(
                    modules,
                    analysisRoot,
                    out,
                    err,
                    getFormat().get(),
                    getFailuresOnly().get(),
                    getOmitRedundancy().get(),
                    configuredOutputPath,
                    configuredJunitReportPath,
                    getThreshold().get()
            );
            rememberJunitReportPath(configuredJunitReportPath);
            if (exit != 0) {
                throw new GradleException("crap-java-check failed with exit " + exit);
            }
        }
    }

    private Path outputPath() {
        if (!getOutput().isPresent()) {
            return null;
        }
        return getOutput().get().getAsFile().toPath().toAbsolutePath().normalize();
    }

    private Path junitReportPath() {
        if (!getJunit().get()) {
            return null;
        }
        return getJunitReport().get().getAsFile().toPath().toAbsolutePath().normalize();
    }

    private void deleteDisabledJunitReport() throws Exception {
        if (getJunit().get()) {
            return;
        }
        for (Path path : disabledJunitReportPaths()) {
            Files.deleteIfExists(path);
        }
        Files.deleteIfExists(junitReportStatePath());
    }

    private Set<Path> disabledJunitReportPaths() throws Exception {
        Set<Path> paths = new LinkedHashSet<>();
        if (getJunitReport().isPresent()) {
            paths.add(getJunitReport().get().getAsFile().toPath().toAbsolutePath().normalize());
        }
        paths.add(defaultJunitReportPath());
        Path rememberedPath = rememberedJunitReportPath();
        if (rememberedPath != null) {
            paths.add(rememberedPath);
        }
        return paths;
    }

    private Path defaultJunitReportPath() {
        return defaultJunitReport.get()
                .getAsFile()
                .toPath()
                .toAbsolutePath()
                .normalize();
    }

    private String defaultJunitReportRelativePath() {
        if ("crap-java-check".equals(getName())) {
            return "reports/crap-java/TEST-crap-java.xml";
        }
        return "reports/crap-java/" + getName() + "/TEST-crap-java.xml";
    }

    private void rememberJunitReportPath(Path path) throws Exception {
        if (path == null) {
            return;
        }
        Path statePath = junitReportStatePath();
        Files.createDirectories(statePath.getParent());
        Files.writeString(statePath, path.toString());
    }

    private Path rememberedJunitReportPath() throws Exception {
        Path statePath = junitReportStatePath();
        if (!Files.isRegularFile(statePath)) {
            return null;
        }
        String value = Files.readString(statePath).trim();
        if (value.isBlank()) {
            return null;
        }
        return Path.of(value).toAbsolutePath().normalize();
    }

    private Path junitReportStatePath() {
        return junitReportState.get()
                .getAsFile()
                .toPath()
                .toAbsolutePath()
                .normalize();
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

    static boolean matchesModulePath(String relativeSourcePath, String modulePath) {
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

