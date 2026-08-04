package media.barney.crap.gradle;

import media.barney.crap.core.Main;
import media.barney.crap.core.SourceExclusionOptions;
import org.jspecify.annotations.Nullable;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.Project;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.Directory;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFile;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.ListProperty;
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
import org.gradle.work.DisableCachingByDefault;

import java.io.File;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

@DisableCachingByDefault(because = "Runs analysis with local state and generated reports outside Gradle's cache model.")
public abstract class CrapJavaCheckTask extends DefaultTask {

    private static final String LINK_OWNERSHIP = "link";
    private static final String ENCODED_PATH_PREFIX = "path-base64\t";

    private final Provider<RegularFile> defaultJunitReport;
    private final Provider<RegularFile> executionMarker;
    private final Provider<RegularFile> junitReportState;
    private final Provider<RegularFile> outputState;
    private final Provider<RegularFile> stateLock;
    private final List<Provider<Directory>> internalExecutionMarkerRootProviders;
    private final List<Path> internalRememberedStateRootPaths;
    private final Provider<String> absentString;
    private final Provider<RegularFile> absentRegularFile;

    public CrapJavaCheckTask() {
        absentString = getProject().getProviders().provider(() -> (String) null);
        absentRegularFile = getProject().getProviders().provider(() -> (RegularFile) null);
        defaultJunitReport = getProject().getProviders()
                .provider(this::defaultJunitReportRelativePath)
                .flatMap(path -> getProject().getLayout().getBuildDirectory().file(path));
        executionMarker = getProject().getLayout().getBuildDirectory()
                .file("tmp/crap-java/" + getName() + "/execution.marker");
        junitReportState = localStateFileProvider("junit-report.path");
        outputState = localStateFileProvider("primary-output.path");
        stateLock = globalStateFileProvider("state.lock");
        internalExecutionMarkerRootProviders = getProject().getRootProject().getAllprojects().stream()
                .map(project -> project.getLayout().getBuildDirectory().dir("tmp/crap-java"))
                .toList();
        internalRememberedStateRootPaths = getProject().getRootProject().getAllprojects().stream()
                .flatMap(project -> {
                    Path stateRoot = projectCacheRoot(project).resolve("crap-java");
                    return Stream.of(stateRoot, stateRoot.resolve(projectStateName(project)));
                })
                .distinct()
                .toList();
        getThreshold().convention(Main.DEFAULT_THRESHOLD);
        getAgent().convention(false);
        getFormat().convention(getAgent().map(agent -> agent ? "toon" : "none"));
        getFailuresOnly().convention(getAgent());
        getOmitRedundancy().convention(getAgent());
        getJunit().convention(true);
        getJunitReport().convention(defaultJunitReport);
        getExcludes().convention(List.of());
        getExcludeClasses().convention(List.of());
        getExcludeAnnotations().convention(List.of());
        getUseDefaultExclusions().convention(true);
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

    @Input
    public abstract ListProperty<String> getExcludes();

    @Input
    public abstract ListProperty<String> getExcludeClasses();

    @Input
    public abstract ListProperty<String> getExcludeAnnotations();

    @Input
    public abstract Property<Boolean> getUseDefaultExclusions();

    @Input
    @Optional
    public Provider<String> getDisabledJunitReportPathInput() {
        return getJunit().flatMap(enabled -> enabled
                ? absentString
                : getJunitReport().map(file -> file.getAsFile().toPath().toAbsolutePath().normalize().toString()));
    }

    @OutputFile
    @Optional
    public Provider<RegularFile> getJunitReportOutput() {
        return getJunit().flatMap(enabled -> enabled
                ? getJunitReport()
                : absentRegularFile);
    }

    @OutputFile
    public Provider<RegularFile> getExecutionMarkerOutput() {
        return executionMarker;
    }

    @TaskAction
    void runCheck() throws Exception {
        List<Path> sourceFiles = getAnalysisSources().getFiles().stream()
                .map(file -> file.toPath().toAbsolutePath().normalize())
                .sorted()
                .toList();
        Path analysisRoot = getAnalysisRoot().get().getAsFile().toPath().toAbsolutePath().normalize();
        @Nullable Path configuredOutputPath = outputPath();
        @Nullable Path configuredJunitReportPath = junitReportPath();
        validateReportOptions(configuredOutputPath, configuredJunitReportPath);
        List<Main.ResolvedCoverageModule> modules = sourceFiles.isEmpty() ? List.of() : resolvedModules(sourceFiles);
        int exit = runWithReportStateLock(
                modules,
                analysisRoot,
                configuredOutputPath,
                configuredJunitReportPath
        );
        if (exit != 0) {
            throw new GradleException("crap-java-check failed with exit " + exit);
        }
        writeExecutionMarker();
    }

    private int runWithReportStateLock(
            List<Main.ResolvedCoverageModule> modules,
            Path analysisRoot,
            @Nullable Path configuredOutputPath,
            @Nullable Path configuredJunitReportPath
    ) throws Exception {
        Path lockPath = stateLockPath();
        Files.createDirectories(Objects.requireNonNull(lockPath.getParent()));
        try (FileChannel channel = FileChannel.open(lockPath, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
             FileLock ignored = channel.lock()) {
            return runAndRememberReports(
                    modules,
                    analysisRoot,
                    configuredOutputPath,
                    configuredJunitReportPath
            );
        }
    }

    private int runAndRememberReports(
            List<Main.ResolvedCoverageModule> modules,
            Path analysisRoot,
            @Nullable Path configuredOutputPath,
            @Nullable Path configuredJunitReportPath
    ) throws Exception {
        ReportSnapshot outputBefore = reportSnapshot(configuredOutputPath);
        ReportSnapshot junitBefore = reportSnapshot(configuredJunitReportPath);
        try (var out = GradleLoggingPrintStreams.standardOut(getLogger());
             var err = GradleLoggingPrintStreams.standardErr(getLogger())) {
            int exit;
            try {
                exit = Main.runWithExistingCoverage(
                        modules,
                        analysisRoot,
                        out,
                        err,
                        getFormat().get(),
                        getAgent().get(),
                        getFailuresOnly().get(),
                        getOmitRedundancy().get(),
                        configuredOutputPath,
                        configuredJunitReportPath,
                        getThreshold().get(),
                        new SourceExclusionOptions(
                                getExcludes().get(),
                                getExcludeClasses().get(),
                                getExcludeAnnotations().get(),
                                getUseDefaultExclusions().get()
                        )
                );
            } catch (Exception exception) {
                rememberChangedReportState(
                        configuredOutputPath,
                        configuredJunitReportPath,
                        outputBefore,
                        junitBefore
                );
                throw exception;
            }
            cleanupStaleReports(configuredOutputPath, configuredJunitReportPath);
            rememberReportState(configuredOutputPath, configuredJunitReportPath);
            return exit;
        }
    }

    private void validateReportOptions(@Nullable Path outputPath, @Nullable Path junitReportPath) throws IOException {
        validateReportFormat(getFormat().get());
        validateThreshold(getThreshold().get());
        validateReportPaths(outputPath, junitReportPath);
    }

    private void validateReportFormat(String format) {
        if (format == null) {
            throw new GradleException("Unknown report format: null");
        }
        switch (format.toLowerCase(Locale.ROOT)) {
            case "toon", "json", "text", "junit", "none" -> { return; }
            default -> throw new GradleException("Unknown report format: " + format);
        }
    }

    private void validateThreshold(double threshold) {
        if (!Double.isFinite(threshold) || Double.compare(threshold, 0.0) <= 0) {
            throw new GradleException("Threshold must be a finite number greater than 0");
        }
    }

    private void validateReportPaths(@Nullable Path outputPath, @Nullable Path junitReportPath) throws IOException {
        if (outputPath != null && junitReportPath != null && sameReportTarget(outputPath, junitReportPath)) {
            throw new GradleException("output and junitReport must not point to the same file");
        }
        validateReportPathDoesNotUseInternalFile("output", outputPath);
        validateReportPathDoesNotUseInternalFile("junitReport", junitReportPath);
    }

    private void validateReportPathDoesNotUseInternalFile(String propertyName, @Nullable Path reportPath) {
        if (reportPath == null) {
            return;
        }
        if (reportPath.getFileName() == null) {
            throw new GradleException(propertyName + " must not point to a filesystem root");
        }
        if (Files.isDirectory(reportPath)) {
            throw new GradleException(propertyName + " must not point to a directory");
        }
        if (isInternalTaskFile(reportPath)) {
            throw new GradleException(propertyName + " must not point to a crap-java internal task file: "
                    + reportPath);
        }
    }

    private boolean isInternalTaskFile(Path reportPath) {
        return isUnderAnyInternalRoot(reportPath) || sameFileAsExistingInternalFile(reportPath);
    }

    private boolean isUnderAnyInternalRoot(Path reportPath) {
        return internalTaskRoots().stream()
                .anyMatch(internalRoot -> isUnderInternalRoot(reportPath, internalRoot));
    }

    private List<Path> internalExecutionMarkerRoots() {
        return internalExecutionMarkerRootProviders.stream()
                .map(Provider::get)
                .map(directory -> directory.getAsFile().toPath().toAbsolutePath().normalize())
                .toList();
    }

    private List<Path> internalRememberedStateRoots() {
        return internalRememberedStateRootPaths;
    }

    private List<Path> internalTaskRoots() {
        return Stream.concat(internalExecutionMarkerRoots().stream(), internalRememberedStateRoots().stream())
                .distinct()
                .toList();
    }

    private boolean sameFileAsExistingInternalFile(Path reportPath) {
        if (!Files.exists(reportPath)) {
            return false;
        }
        return internalTaskRoots().stream()
                .anyMatch(internalRoot -> sameFileAsExistingInternalFile(reportPath, internalRoot));
    }

    private boolean sameFileAsExistingInternalFile(Path reportPath, Path internalRoot) {
        if (!Files.isDirectory(internalRoot)) {
            return false;
        }
        try (Stream<Path> candidates = Files.walk(internalRoot)) {
            return candidates
                    .filter(this::isInternalStateOrMarkerFile)
                    .filter(Files::isRegularFile)
                    .anyMatch(candidate -> sameFile(reportPath, candidate));
        } catch (IOException | SecurityException exception) {
            return false;
        }
    }

    private boolean isInternalStateOrMarkerFile(Path path) {
        return isInternalFileName(path, "execution.marker")
                || isInternalFileName(path, "primary-output.path")
                || isInternalFileName(path, "junit-report.path")
                || isInternalFileName(path, "state.lock");
    }

    private boolean isInternalFileName(Path path, String internalFileName) {
        Path fileName = path.getFileName();
        if (fileName == null) {
            return false;
        }
        String name = fileName.toString();
        return name.equals(internalFileName)
                || sameCaseInsensitiveFileName(name, internalFileName, path.getParent());
    }

    private boolean sameFile(Path first, Path second) {
        try {
            return !first.toAbsolutePath().normalize().equals(second.toAbsolutePath().normalize())
                    && Files.isSameFile(first, second);
        } catch (IOException | SecurityException exception) {
            return false;
        }
    }

    private boolean isUnderInternalRoot(Path reportPath, Path internalRoot) {
        return reportPath.startsWith(internalRoot) || realPathStartsWith(reportPath, internalRoot);
    }

    private boolean realPathStartsWith(Path reportPath, Path internalRoot) {
        @Nullable Path realReportPath = realPathForComparison(reportPath);
        @Nullable Path realInternalRoot = realPathForComparison(internalRoot);
        return realReportPath != null && realInternalRoot != null && realReportPath.startsWith(realInternalRoot);
    }

    private @Nullable Path realPathForComparison(Path path) {
        return realPathForComparison(path, 0);
    }

    private @Nullable Path realPathForComparison(Path path, int symlinkDepth) {
        if (symlinkDepth > 8) {
            return null;
        }
        Path normalized = path.toAbsolutePath().normalize();
        try {
            return realPathForNormalizedPath(normalized, symlinkDepth);
        } catch (IOException | SecurityException exception) {
            return null;
        }
    }

    private @Nullable Path realPathForNormalizedPath(Path normalized, int symlinkDepth) throws IOException {
        if (Files.isSymbolicLink(normalized)) {
            return symbolicLinkTargetForComparison(normalized, symlinkDepth);
        }
        if (Files.exists(normalized)) {
            return normalized.toRealPath();
        }
        return realPathForMissingPath(normalized);
    }

    private @Nullable Path realPathForMissingPath(Path normalized) throws IOException {
        Path existing = nearestExistingPath(normalized);
        return existing == null
                ? null
                : existing.toRealPath().resolve(existing.relativize(normalized)).normalize();
    }

    private @Nullable Path symbolicLinkTargetForComparison(Path link, int symlinkDepth) throws IOException {
        Path target = Files.readSymbolicLink(link);
        Path resolved = link.resolveSibling(target);
        return realPathForComparison(resolved, symlinkDepth + 1);
    }

    private @Nullable Path nearestExistingPath(Path path) {
        Path current = path;
        while (current != null) {
            if (Files.exists(current)) {
                return current;
            }
            current = current.getParent();
        }
        return null;
    }

    private boolean sameCaseInsensitiveFileName(String fileName, String internalFileName, @Nullable Path parent) {
        return fileName.equalsIgnoreCase(internalFileName) && isCaseInsensitive(parent);
    }

    private boolean isCaseInsensitive(@Nullable Path path) {
        Path directory = nearestExistingDirectory(path);
        return directory == null ? isLikelyCaseInsensitiveOs() : directoryIsCaseInsensitive(directory);
    }

    private boolean directoryIsCaseInsensitive(Path directory) {
        try {
            Path probe = Files.createTempFile(directory, ".crap-java-case-", ".tmp");
            try {
                return caseVariantExists(probe);
            } finally {
                Files.deleteIfExists(probe);
            }
        } catch (IOException | SecurityException exception) {
            return isLikelyCaseInsensitiveOs();
        }
    }

    private @Nullable Path nearestExistingDirectory(@Nullable Path path) {
        Path start = path == null ? Path.of(".").toAbsolutePath().normalize() : path.toAbsolutePath().normalize();
        return ancestors(start).filter(Files::isDirectory).findFirst().orElse(null);
    }

    private Stream<Path> ancestors(Path path) {
        return Stream.iterate(path, Objects::nonNull, Path::getParent);
    }

    private boolean caseVariantExists(Path probe) {
        Path fileName = probe.getFileName();
        if (fileName == null) {
            return false;
        }
        return caseVariantExists(probe, fileName.toString());
    }

    private boolean caseVariantExists(Path probe, String name) {
        Path variant = probe.resolveSibling(name.toUpperCase(Locale.ROOT));
        Path variantFileName = variant.getFileName();
        if (variantFileName == null) {
            return false;
        }
        return differentExistingVariant(name, variantFileName.toString(), variant);
    }

    private boolean differentExistingVariant(String name, String variantName, Path variant) {
        return !name.equals(variantName) && Files.exists(variant);
    }

    static boolean isLikelyCaseInsensitiveOs() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        return os.startsWith("windows");
    }

    private void cleanupStaleReports(@Nullable Path currentOutputPath, @Nullable Path currentJunitReportPath)
            throws Exception {
        deleteMovedOutput(currentOutputPath, currentJunitReportPath);
        deleteMovedJunitReport(currentJunitReportPath, currentOutputPath);
        deleteDisabledJunitReport(currentOutputPath);
    }

    private void rememberReportState(@Nullable Path currentOutputPath, @Nullable Path currentJunitReportPath)
            throws Exception {
        rememberOutputPath(currentOutputPath);
        rememberJunitReportPath(currentJunitReportPath);
    }

    private void rememberChangedReportState(
            @Nullable Path currentOutputPath,
            @Nullable Path currentJunitReportPath,
            ReportSnapshot outputBefore,
            ReportSnapshot junitBefore
    ) throws Exception {
        @Nullable RememberedReport rememberedOutput = rememberedOutputPath();
        @Nullable RememberedReport rememberedJunitReport = rememberedJunitReportPath();
        deleteNewUnrememberedChangedReport(currentOutputPath, outputBefore, rememberedOutput);
        deleteNewUnrememberedChangedReport(currentJunitReportPath, junitBefore, rememberedJunitReport);
        if (shouldRememberChangedReport(currentOutputPath, outputBefore, rememberedOutput)) {
            rememberOutputPath(currentOutputPath);
        }
        if (shouldRememberChangedReport(currentJunitReportPath, junitBefore, rememberedJunitReport)) {
            rememberJunitReportPath(currentJunitReportPath);
        }
    }

    private void deleteNewUnrememberedChangedReport(
            @Nullable Path reportPath,
            ReportSnapshot before,
            @Nullable RememberedReport rememberedReport
    ) throws IOException {
        if (isNewUnrememberedChangedReport(reportPath, before, rememberedReport)) {
            Files.deleteIfExists(reportPath);
        }
    }

    private boolean isNewUnrememberedChangedReport(
            @Nullable Path reportPath,
            ReportSnapshot before,
            @Nullable RememberedReport rememberedReport
    ) throws IOException {
        return rememberedReport != null
                && !before.exists()
                && !isCurrentRememberedPath(rememberedReport, reportPath)
                && reportPath != null
                && reportChanged(reportPath, before);
    }

    private boolean shouldRememberChangedReport(
            @Nullable Path reportPath,
            ReportSnapshot before,
            @Nullable RememberedReport rememberedReport
    ) throws IOException {
        return reportChanged(reportPath, before)
                && (rememberedReport == null || isCurrentRememberedPath(rememberedReport, reportPath));
    }

    private boolean reportChanged(@Nullable Path reportPath, ReportSnapshot before) throws IOException {
        return reportPath != null && !reportSnapshot(reportPath).equals(before);
    }

    private ReportSnapshot reportSnapshot(@Nullable Path reportPath) throws IOException {
        if (reportPath == null || !Files.isRegularFile(reportPath)) {
            return ReportSnapshot.missing();
        }
        BasicFileAttributes attributes = Files.readAttributes(reportPath, BasicFileAttributes.class);
        return new ReportSnapshot(
                true,
                attributes.lastModifiedTime().to(TimeUnit.NANOSECONDS),
                attributes.size()
        );
    }

    private Path stateLockPath() {
        return stateLock.get().getAsFile()
                .toPath()
                .toAbsolutePath()
                .normalize();
    }

    private void writeExecutionMarker() throws Exception {
        Path markerPath = executionMarkerPath();
        Files.createDirectories(Objects.requireNonNull(markerPath.getParent()));
        Files.writeString(markerPath, "ok\n");
    }

    private Path executionMarkerPath() {
        return getExecutionMarkerOutput().get().getAsFile().toPath().toAbsolutePath().normalize();
    }

    private void deleteMovedOutput(@Nullable Path currentPath, @Nullable Path otherCurrentPath) throws Exception {
        @Nullable RememberedReport rememberedReport = rememberedOutputPath();
        deleteRememberedOutputIfMoved(rememberedReport, currentPath, otherCurrentPath);
        deleteOutputStateIfUnset(currentPath);
    }

    private void deleteRememberedOutputIfMoved(
            @Nullable RememberedReport rememberedReport,
            @Nullable Path currentPath,
            @Nullable Path otherCurrentPath
    ) throws Exception {
        if (shouldKeepRememberedReport(rememberedReport, currentPath, otherCurrentPath)) {
            return;
        }
        deleteRememberedReport(rememberedReport);
    }

    private boolean shouldKeepRememberedReport(
            @Nullable RememberedReport rememberedReport,
            @Nullable Path currentPath,
            @Nullable Path otherCurrentPath
    ) throws IOException {
        return rememberedReport == null
                || isCurrentRememberedPath(rememberedReport, currentPath)
                || isCurrentRememberedPath(rememberedReport, otherCurrentPath);
    }

    private boolean isCurrentRememberedPath(RememberedReport rememberedReport, @Nullable Path currentPath)
            throws IOException {
        return currentPath != null && sameReportTarget(rememberedReport.path(), currentPath);
    }

    private void deleteOutputStateIfUnset(@Nullable Path currentPath) throws Exception {
        if (currentPath == null) {
            deleteReportState(outputStatePath());
        }
    }

    private void deleteMovedJunitReport(@Nullable Path currentPath, @Nullable Path otherCurrentPath)
            throws Exception {
        if (currentPath == null) {
            return;
        }
        @Nullable RememberedReport rememberedReport = rememberedJunitReportPath();
        if (!shouldKeepRememberedReport(rememberedReport, currentPath, otherCurrentPath)) {
            deleteRememberedReport(rememberedReport);
        }
    }

    private @Nullable Path outputPath() {
        if (!getOutput().isPresent()) {
            return null;
        }
        return getOutput().get().getAsFile().toPath().toAbsolutePath().normalize();
    }

    private @Nullable Path junitReportPath() {
        if (!getJunit().get()) {
            return null;
        }
        return getJunitReport().get().getAsFile().toPath().toAbsolutePath().normalize();
    }

    private void deleteDisabledJunitReport(@Nullable Path currentOutputPath) throws Exception {
        if (getJunit().get()) {
            return;
        }
        @Nullable RememberedReport rememberedReport = rememberedJunitReportPath();
        if (!shouldKeepRememberedReport(rememberedReport, currentOutputPath, null)) {
            deleteRememberedReport(rememberedReport);
        }
        deleteReportState(junitReportStatePath());
    }

    private void deleteRememberedReport(@Nullable RememberedReport rememberedReport) throws Exception {
        if (rememberedReport == null) {
            return;
        }
        if (!isOwnedRememberedReport(rememberedReport)) {
            return;
        }
        Files.deleteIfExists(rememberedReport.path());
    }

    private boolean isOwnedRememberedReport(@Nullable RememberedReport rememberedReport) throws Exception {
        if (!hasRegularRememberedReport(rememberedReport)) {
            return false;
        }
        if (!hasCurrentOwnerLink(rememberedReport)) {
            return false;
        }
        if (hasOtherOwnerLink(rememberedReport)) {
            return false;
        }
        return hasCurrentOwnership(rememberedReport);
    }

    private boolean hasRegularRememberedReport(@Nullable RememberedReport rememberedReport) {
        return rememberedReport != null && Files.isRegularFile(rememberedReport.path());
    }

    private boolean hasCurrentOwnerLink(@Nullable RememberedReport rememberedReport) throws IOException {
        if (rememberedReport == null) {
            return false;
        }
        if (!rememberedReport.ownership().startsWith(LINK_OWNERSHIP + "\t")) {
            return false;
        }
        if (!Files.exists(rememberedReport.ownerLink())) {
            return false;
        }
        return Files.isSameFile(rememberedReport.path(), rememberedReport.ownerLink());
    }

    private boolean hasCurrentOwnership(@Nullable RememberedReport rememberedReport) throws Exception {
        if (rememberedReport == null) {
            return false;
        }
        return rememberedReport.ownership().equals(ownership(rememberedReport.path()));
    }

    private boolean hasOtherOwnerLink(@Nullable RememberedReport rememberedReport) throws IOException {
        if (rememberedReport == null) {
            return false;
        }
        Path stateRoot = projectCacheRoot(getProject()).resolve("crap-java");
        return isOtherOwnerLinkPresent(stateRoot, rememberedReport);
    }

    private boolean isOtherOwnerLinkPresent(Path stateRoot, RememberedReport rememberedReport)
            throws IOException {
        if (!Files.isDirectory(stateRoot)) {
            return false;
        }
        try (Stream<Path> paths = Files.walk(stateRoot)) {
            for (Path path : paths.filter(this::isOwnerLink).toList()) {
                if (isOtherOwnerLink(path, rememberedReport)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean isOtherOwnerLink(Path path, RememberedReport rememberedReport) throws IOException {
        return !path.equals(rememberedReport.ownerLink()) && sameExistingFile(path, rememberedReport.path());
    }

    private boolean isOwnerLink(Path path) {
        return path.getFileName() != null && path.getFileName().toString().endsWith(".owner");
    }

    private String defaultJunitReportRelativePath() {
        if ("crap-java-check".equals(getName())) {
            return "reports/crap-java/TEST-crap-java.xml";
        }
        return "reports/crap-java/" + getName() + "/TEST-crap-java.xml";
    }

    private void rememberOutputPath(@Nullable Path path) throws Exception {
        if (path == null) {
            Files.deleteIfExists(outputStatePath());
            return;
        }
        rememberReportPath(outputStatePath(), path);
    }

    private @Nullable RememberedReport rememberedOutputPath() throws Exception {
        return rememberedReportPath(outputStatePath());
    }

    private Path outputStatePath() {
        return outputState.get().getAsFile()
                .toPath()
                .toAbsolutePath()
                .normalize();
    }

    private void rememberJunitReportPath(@Nullable Path path) throws Exception {
        if (path == null) {
            return;
        }
        rememberReportPath(junitReportStatePath(), path);
    }

    private void rememberReportPath(Path statePath, Path reportPath) throws Exception {
        Files.createDirectories(Objects.requireNonNull(statePath.getParent()));
        Path ownerLink = ownerLinkPath(statePath);
        Files.deleteIfExists(ownerLink);
        String ownership = ownership(reportPath, ownerLink);
        if (ownership.isBlank()) {
            Files.deleteIfExists(statePath);
            return;
        }
        Files.writeString(statePath, encodeRememberedReportPath(reportPath) + "\n" + ownership + "\n");
    }

    private String encodeRememberedReportPath(Path reportPath) {
        String encoded = Base64.getEncoder()
                .encodeToString(reportPath.toString().getBytes(StandardCharsets.UTF_8));
        return ENCODED_PATH_PREFIX + encoded;
    }

    private String ownership(Path reportPath, Path ownerLink) throws Exception {
        try {
            Files.createLink(ownerLink, reportPath);
            return ownership(reportPath);
        } catch (IOException | SecurityException | UnsupportedOperationException exception) {
            getLogger().warn(
                    "crap-java could not remember ownership for {}; stale cleanup for that report path is disabled.",
                    reportPath);
            return "";
        }
    }

    private String ownership(Path reportPath) throws Exception {
        BasicFileAttributes attributes = Files.readAttributes(reportPath, BasicFileAttributes.class);
        return LINK_OWNERSHIP + "\t"
                + attributes.lastModifiedTime().to(TimeUnit.NANOSECONDS) + "\t"
                + attributes.size();
    }

    private void deleteReportState(Path statePath) throws Exception {
        Files.deleteIfExists(ownerLinkPath(statePath));
        Files.deleteIfExists(statePath);
    }

    private Path ownerLinkPath(Path statePath) {
        String fileName = Objects.requireNonNull(statePath.getFileName()).toString();
        String ownerFileName = fileName.endsWith(".path")
                ? fileName.substring(0, fileName.length() - ".path".length()) + ".owner"
                : fileName + ".owner";
        return statePath.resolveSibling(ownerFileName);
    }

    private @Nullable RememberedReport rememberedJunitReportPath() throws Exception {
        return rememberedReportPath(junitReportStatePath());
    }

    private @Nullable RememberedReport rememberedReportPath(Path statePath) throws Exception {
        if (!Files.isRegularFile(statePath)) {
            return null;
        }
        return parseRememberedReport(statePath, Files.readAllLines(statePath));
    }

    private @Nullable RememberedReport parseRememberedReport(Path statePath, List<String> lines) {
        if (!hasRememberedReport(lines)) {
            return null;
        }
        Path reportPath = parseRememberedReportPath(lines.get(0));
        if (reportPath == null) {
            return null;
        }
        return new RememberedReport(
                reportPath,
                lines.get(1),
                ownerLinkPath(statePath)
        );
    }

    private @Nullable Path parseRememberedReportPath(String line) {
        try {
            String path = line.startsWith(ENCODED_PATH_PREFIX)
                    ? decodeRememberedReportPath(line.substring(ENCODED_PATH_PREFIX.length()))
                    : line;
            return path == null ? null : Path.of(path).toAbsolutePath().normalize();
        } catch (IllegalArgumentException | SecurityException exception) {
            return null;
        }
    }

    private @Nullable String decodeRememberedReportPath(String encoded) {
        try {
            byte[] decoded = Base64.getDecoder().decode(encoded);
            return new String(decoded, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private boolean hasRememberedReport(List<String> lines) {
        return lines.size() >= 2 && !lines.get(0).isBlank() && !lines.get(1).isBlank();
    }

    private Path junitReportStatePath() {
        return junitReportState.get().getAsFile()
                .toPath()
                .toAbsolutePath()
                .normalize();
    }

    private Provider<RegularFile> localStateFileProvider(String fileName) {
        return getProject().getLayout().file(getProject().getProviders().provider(() -> localStateFile(fileName)));
    }

    private Provider<RegularFile> globalStateFileProvider(String fileName) {
        return getProject().getLayout().file(getProject().getProviders().provider(() -> globalStateFile(fileName)));
    }

    private File localStateFile(String fileName) {
        return localStateRoot(getProject())
                .resolve(getName())
                .resolve(fileName)
                .toFile();
    }

    private File globalStateFile(String fileName) {
        return projectCacheRoot(getProject())
                .resolve("crap-java")
                .resolve(fileName)
                .toFile();
    }

    private Path localStateRoot(Project project) {
        Path stateRoot = projectCacheRoot(project).resolve("crap-java");
        if (hasCustomProjectCacheDir(project)) {
            stateRoot = stateRoot.resolve(rootProjectStateName(project));
        }
        return stateRoot.resolve(projectStateName(project));
    }

    private Path projectCacheRoot(Project project) {
        File projectCacheDir = project.getGradle().getStartParameter().getProjectCacheDir();
        if (projectCacheDir != null) {
            return projectCacheDir.toPath().toAbsolutePath().normalize();
        }
        return project.getRootProject().getProjectDir().toPath().resolve(".gradle").toAbsolutePath().normalize();
    }

    private boolean hasCustomProjectCacheDir(Project project) {
        return project.getGradle().getStartParameter().getProjectCacheDir() != null;
    }

    private String rootProjectStateName(Project project) {
        String rootPath = project.getRootProject().getProjectDir().toPath().toAbsolutePath().normalize().toString();
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(rootPath.getBytes(StandardCharsets.UTF_8));
            return "workspace-" + HexFormat.of().formatHex(hash, 0, 12);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private String projectStateName(Project project) {
        String projectPath = project.getPath();
        if (":".equals(projectPath)) {
            return "root";
        }
        return projectPath
                .replace("%", "%25")
                .replace(":", "%3A");
    }

    private boolean sameReportTarget(Path first, Path second) throws IOException {
        if (first.equals(second)) {
            return true;
        }
        return sameExistingFile(first, second)
                || sameRealPath(first, second)
                || sameParentAndFileName(first, second);
    }

    private boolean sameExistingFile(Path first, Path second) throws IOException {
        return Files.exists(first) && Files.exists(second) && Files.isSameFile(first, second);
    }

    private boolean sameParentAndFileName(Path first, Path second) throws IOException {
        Path firstParent = first.getParent();
        Path secondParent = second.getParent();
        return sameParent(firstParent, secondParent) && sameFileName(first, second, firstParent);
    }

    private boolean sameParent(@Nullable Path firstParent, @Nullable Path secondParent) throws IOException {
        return (firstParent == null || secondParent == null)
                ? firstParent == secondParent
                : sameNonNullParent(firstParent, secondParent);
    }

    private boolean sameNonNullParent(Path firstParent, Path secondParent) throws IOException {
        return firstParent.equals(secondParent)
                || sameAliasedParent(firstParent, secondParent);
    }

    private boolean sameAliasedParent(Path firstParent, Path secondParent) throws IOException {
        return sameExistingFile(firstParent, secondParent)
                || sameRealPath(firstParent, secondParent)
                || sameCaseInsensitivePath(firstParent, secondParent);
    }

    private boolean sameRealPath(Path first, Path second) {
        @Nullable Path firstRealPath = realPathForComparison(first);
        @Nullable Path secondRealPath = realPathForComparison(second);
        return firstRealPath != null && firstRealPath.equals(secondRealPath);
    }

    private boolean sameCaseInsensitivePath(Path first, Path second) {
        return first.toString().equalsIgnoreCase(second.toString()) && isCaseInsensitive(first);
    }

    private boolean sameFileName(Path first, Path second, @Nullable Path parent) {
        Path firstFileName = first.getFileName();
        Path secondFileName = second.getFileName();
        if (firstFileName == null || secondFileName == null) {
            return false;
        }
        String firstName = firstFileName.toString();
        String secondName = secondFileName.toString();
        return firstName.equals(secondName) || sameCaseInsensitiveFileName(firstName, secondName, parent);
    }

    private record RememberedReport(Path path, String ownership, Path ownerLink) {
    }

    private record ReportSnapshot(boolean exists, long modifiedNanos, long size) {

        private static ReportSnapshot missing() {
            return new ReportSnapshot(false, 0, 0);
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
            List<Path> moduleSources = Objects.requireNonNull(
                    sourceFilesByModule.get(modulePath),
                    () -> "Missing source bucket for module " + modulePath
            );
            moduleSources.add(sourceFile);
        }

        List<Main.ResolvedCoverageModule> modules = new ArrayList<>();
        for (String modulePath : orderedModulePaths) {
            List<Path> moduleSources = Objects.requireNonNull(
                    sourceFilesByModule.get(modulePath),
                    () -> "Missing source bucket for module " + modulePath
            );
            if (moduleSources.isEmpty()) {
                continue;
            }
            String coverageReport = Objects.requireNonNull(
                    configuredModules.get(modulePath),
                    () -> "Missing coverage report for module " + modulePath
            );
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

