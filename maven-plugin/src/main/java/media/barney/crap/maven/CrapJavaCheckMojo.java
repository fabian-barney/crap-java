package media.barney.crap.maven;

import media.barney.crap.core.Main;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.jspecify.annotations.Nullable;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Collectors;

@Mojo(name = "check", defaultPhase = LifecyclePhase.VERIFY, aggregator = true, threadSafe = true)
public class CrapJavaCheckMojo extends AbstractMojo {

    private final CrapJavaRunner runner;

    @Parameter(defaultValue = "${session}", readonly = true, required = true)
    private @Nullable MavenSession session;

    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    private @Nullable MavenProject project;

    @Parameter(property = "crapJava.format", defaultValue = "none")
    private String format = "none";

    @Parameter(property = "crapJava.agent", defaultValue = "false")
    private boolean agent;

    @Parameter(property = "crapJava.failuresOnly")
    private @Nullable Boolean failuresOnly;

    @Parameter(property = "crapJava.omitRedundancy")
    private @Nullable Boolean omitRedundancy;

    @Parameter(property = "crapJava.output")
    private @Nullable File output;

    @Parameter(property = "crapJava.junit", defaultValue = "true")
    private boolean junit = true;

    @Parameter(property = "crapJava.junitReport")
    private @Nullable File junitReport;

    @Parameter(property = "crapJava.threshold", defaultValue = "6.0")
    private double threshold = Main.DEFAULT_THRESHOLD;

    @Parameter
    private List<String> excludes = new ArrayList<>();

    @Parameter(property = "crapJava.excludes")
    private @Nullable String excludesProperty;

    @Parameter
    private List<String> excludeClasses = new ArrayList<>();

    @Parameter(property = "crapJava.excludeClasses")
    private @Nullable String excludeClassesProperty;

    @Parameter
    private List<String> excludeAnnotations = new ArrayList<>();

    @Parameter(property = "crapJava.excludeAnnotations")
    private @Nullable String excludeAnnotationsProperty;

    @Parameter(property = "crapJava.useDefaultExclusions", defaultValue = "true")
    private boolean useDefaultExclusions = true;

    public CrapJavaCheckMojo() {
        this((useExistingCoverage, args, projectRoot, out, err) -> useExistingCoverage
                ? Main.runWithExistingCoverage(args, projectRoot, out, err)
                : Main.run(args, projectRoot, out, err));
    }

    CrapJavaCheckMojo(CrapJavaRunner runner) {
        this.runner = Objects.requireNonNull(runner, "runner");
    }

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        Path executionRoot = executionRoot();
        MavenProject project = project();
        if (!isFinalReactorProject(project)) {
            getLog().debug("Skipping crap-java check before final reactor project " + project.getArtifactId());
            return;
        }
        ensureCoverageReportsExist();
        runCheck(executionRoot);
    }

    private boolean isFinalReactorProject(MavenProject project) {
        List<MavenProject> projects = reactorProjects();
        MavenProject finalProject = projects.get(projects.size() - 1);
        return project.getBasedir().toPath().normalize().equals(finalProject.getBasedir().toPath().normalize());
    }

    private void runCheck(Path executionRoot) throws MojoExecutionException, MojoFailureException {
        try (PrintStream out = logPrintStream(getLog(), false);
             PrintStream err = logPrintStream(getLog(), true)) {
            int exit = runner.run(true, reportArgs(executionRoot), executionRoot, out, err);
            handleExitCode(exit);
        } catch (MojoFailureException | MojoExecutionException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new MojoExecutionException("Failed to execute crap-java", ex);
        }
    }

    private String[] reportArgs(Path executionRoot) {
        List<String> args = new ArrayList<>();
        addReportControls(args);
        addExclusionControls(args, executionRoot);
        addOutputArgument(args, executionRoot);
        addThresholdArgument(args);
        addJunitReportArgument(args, executionRoot);
        return args.toArray(String[]::new);
    }

    private void addReportControls(List<String> args) {
        args.add("--format");
        args.add(format);
        if (agent) {
            args.add("--agent");
        }
        if (failuresOnly != null) {
            args.add("--failures-only=" + failuresOnly);
        }
        if (omitRedundancy != null) {
            args.add("--omit-redundancy=" + omitRedundancy);
        }
    }

    private void addExclusionControls(List<String> args, Path executionRoot) {
        addRepeated(args, "--exclude", excludesProperty, excludes);
        addRepeated(args, "--exclude-class", excludeClassesProperty, excludeClasses);
        addRepeated(args, "--exclude-annotation", excludeAnnotationsProperty, excludeAnnotations);
        addRepeated(args, "--source-root", null, sourceRootArguments(executionRoot));
        if (!useDefaultExclusions) {
            args.add("--use-default-exclusions=false");
        }
    }

    private void addOutputArgument(List<String> args, Path executionRoot) {
        if (output != null) {
            args.add("--output");
            args.add(configuredPath(executionRoot, output).toString());
        }
    }

    private void addThresholdArgument(List<String> args) {
        args.add("--threshold");
        args.add(Double.toString(threshold));
    }

    private void addJunitReportArgument(List<String> args, Path executionRoot) {
        if (junit) {
            args.add("--junit-report");
            args.add(junitReportPath(executionRoot).toString());
        }
    }

    private static void addRepeated(List<String> args, String option, @Nullable String propertyValue, List<String> values) {
        for (String value : configuredValues(propertyValue, values)) {
            args.add(option);
            args.add(value);
        }
    }

    private static List<String> configuredValues(@Nullable String propertyValue, List<String> values) {
        List<String> configured = new ArrayList<>();
        configured.addAll(commaSeparatedPropertyValues(propertyValue));
        configured.addAll(configuredListValues(values));
        return configured;
    }

    private static List<String> commaSeparatedPropertyValues(@Nullable String propertyValue) {
        if (propertyValue == null) {
            return List.of();
        }
        return splitEscapedCommaValues(propertyValue).stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .toList();
    }

    private static List<String> splitEscapedCommaValues(String propertyValue) {
        List<String> values = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (int index = 0; index < propertyValue.length(); index++) {
            char character = propertyValue.charAt(index);
            if (isEscapedComma(propertyValue, index, character)) {
                current.append(',');
                index++;
                continue;
            }
            if (character == ',') {
                values.add(current.toString());
                current.setLength(0);
                continue;
            }
            current.append(character);
        }
        values.add(current.toString());
        return values;
    }

    private static boolean isEscapedComma(String propertyValue, int index, char character) {
        return character == '\\' && index + 1 < propertyValue.length() && propertyValue.charAt(index + 1) == ',';
    }

    private static List<String> configuredListValues(List<String> values) {
        if (values == null) {
            return List.of();
        }
        return values.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .toList();
    }

    private Path junitReportPath(Path executionRoot) {
        File configured = junitReport;
        if (configured != null) {
            return configuredPath(executionRoot, configured);
        }
        return executionRoot.resolve("target/crap-java/TEST-crap-java.xml").normalize();
    }

    private static Path configuredPath(Path executionRoot, File configured) {
        Path path = configured.toPath().normalize();
        return path.isAbsolute() ? path : executionRoot.resolve(path).normalize();
    }

    private void ensureCoverageReportsExist() throws MojoFailureException {
        List<Path> missingReports = missingCoverageReports();
        if (missingReports.isEmpty()) {
            return;
        }
        throw new MojoFailureException(
                "Missing JaCoCo XML reports. Configure jacoco-maven-plugin to generate target/site/jacoco/jacoco.xml before crap-java:check: "
                        + missingReports.stream().map(Path::toString).collect(Collectors.joining(", "))
        );
    }

    private void handleExitCode(int exit) throws MojoExecutionException, MojoFailureException {
        if (exit == 2) {
            throw new MojoFailureException("crap-java threshold exceeded");
        }
        if (exit != 0) {
            throw new MojoExecutionException("crap-java check failed with exit " + exit);
        }
    }

    private static PrintStream logPrintStream(Log log, boolean errorByDefault) {
        return new PrintStream(new LogOutputStream(log, errorByDefault), true, StandardCharsets.UTF_8);
    }

    private static final class LogOutputStream extends OutputStream {
        private final Log log;
        private final boolean errorByDefault;
        private final ByteArrayOutputStream line = new ByteArrayOutputStream();

        private LogOutputStream(Log log, boolean errorByDefault) {
            this.log = log;
            this.errorByDefault = errorByDefault;
        }

        @Override
        public void write(int value) {
            if (value == '\n') {
                logLine();
                return;
            }
            if (value != '\r') {
                line.write(value);
            }
        }

        @Override
        public void write(byte[] buffer, int offset, int length) {
            Objects.checkFromIndexSize(offset, length, buffer.length);
            for (int index = offset; index < offset + length; index++) {
                write(buffer[index]);
            }
        }

        @Override
        public void flush() {
            logLine();
        }

        @Override
        public void close() throws IOException {
            flush();
            super.close();
        }

        private void logLine() {
            if (line.size() == 0) {
                return;
            }
            String message = line.toString(StandardCharsets.UTF_8);
            line.reset();
            if (message.startsWith("Warning: ")) {
                log.warn(message);
            } else if (message.startsWith("Error: ") || errorByDefault) {
                log.error(message);
            } else {
                log.info(message);
            }
        }
    }

    private List<Path> missingCoverageReports() {
        List<Path> missingReports = new ArrayList<>();
        Path executionRoot = executionRoot();
        for (MavenProject reactorProject : reactorProjects()) {
            Path basedir = reactorProject.getBasedir().toPath().normalize();
            if (!analyzableCompileSourceRoots(reactorProject, executionRoot).isEmpty()
                    && !Files.exists(basedir.resolve("target/site/jacoco/jacoco.xml"))) {
                missingReports.add(basedir.resolve("target/site/jacoco/jacoco.xml"));
            }
        }
        return missingReports;
    }

    private List<MavenProject> reactorProjects() {
        List<MavenProject> projects = session().getProjects();
        return projects == null || projects.isEmpty() ? List.of(project()) : projects;
    }

    private List<String> sourceRootArguments(Path executionRoot) {
        if (!hasCustomCompileSourceRoot(executionRoot)) {
            return List.of();
        }
        return reactorProjects().stream()
                .flatMap(project -> analyzableCompileSourceRoots(project, executionRoot).stream())
                .map(Path::toString)
                .distinct()
                .toList();
    }

    private boolean hasCustomCompileSourceRoot(Path executionRoot) {
        return reactorProjects().stream()
                .anyMatch(project -> analyzableCompileSourceRoots(project, executionRoot).stream()
                        .anyMatch(sourceRoot -> !sourceRoot.equals(defaultSourceRoot(project))));
    }

    private static List<Path> analyzableCompileSourceRoots(MavenProject project, Path executionRoot) {
        return compileSourceRoots(project).stream()
                .filter(Files::isDirectory)
                .filter(sourceRoot -> isUnder(sourceRoot, executionRoot))
                .filter(sourceRoot -> !isGeneratedOrBuildOutputRoot(project, sourceRoot))
                .toList();
    }

    private static List<Path> compileSourceRoots(MavenProject project) {
        List<String> roots = project.getCompileSourceRoots();
        if (roots == null || roots.isEmpty()) {
            return List.of(defaultSourceRoot(project));
        }
        return roots.stream()
                .filter(Objects::nonNull)
                .map(Path::of)
                .map(path -> path.isAbsolute() ? path.normalize() : project.getBasedir().toPath().resolve(path).normalize())
                .toList();
    }

    private static Path defaultSourceRoot(MavenProject project) {
        return project.getBasedir().toPath().resolve("src/main/java").normalize();
    }

    private static boolean isGeneratedOrBuildOutputRoot(MavenProject project, Path sourceRoot) {
        Path basedir = project.getBasedir().toPath().normalize();
        return isUnder(sourceRoot, basedir.resolve("target").normalize())
                || isUnder(sourceRoot, basedir.resolve("build").normalize())
                || isUnder(sourceRoot, basedir.resolve("out").normalize())
                || hasGeneratedSegment(basedir, sourceRoot);
    }

    private static boolean isUnder(Path path, Path parent) {
        Path normalizedPath = path.toAbsolutePath().normalize();
        Path normalizedParent = parent.toAbsolutePath().normalize();
        return normalizedPath.equals(normalizedParent) || normalizedPath.startsWith(normalizedParent);
    }

    private static boolean hasGeneratedSegment(Path basedir, Path sourceRoot) {
        Path relativeRoot = sourceRoot.startsWith(basedir) ? basedir.relativize(sourceRoot) : sourceRoot;
        for (Path segment : relativeRoot) {
            if (segment.toString().toLowerCase(Locale.ROOT).contains("generated")) {
                return true;
            }
        }
        return false;
    }

    private Path executionRoot() {
        java.io.File multiModuleRoot = session().getRequest().getMultiModuleProjectDirectory();
        if (multiModuleRoot != null) {
            return multiModuleRoot.toPath().normalize();
        }
        return project().getBasedir().toPath().normalize();
    }

    private MavenSession session() {
        return Objects.requireNonNull(session, "Maven session must be injected");
    }

    private MavenProject project() {
        return Objects.requireNonNull(project, "Maven project must be injected");
    }

    @FunctionalInterface
    interface CrapJavaRunner {
        int run(boolean useExistingCoverage, String[] args, Path projectRoot, PrintStream out, PrintStream err) throws Exception;
    }
}

