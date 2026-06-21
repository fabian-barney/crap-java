package media.barney.crap.maven;

import org.apache.maven.execution.DefaultMavenExecutionRequest;
import org.apache.maven.execution.DefaultMavenExecutionResult;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.MavenProject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.jspecify.annotations.Nullable;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CrapJavaCheckMojoTest {

    @TempDir
    Path tempDir;

    @Test
    void skipsNonFinalReactorProject() throws Exception {
        Path root = tempDir.resolve("root");
        Path moduleA = root.resolve("module-a");
        Path moduleB = root.resolve("module-b");
        Files.createDirectories(moduleA);
        Files.createDirectories(moduleB);

        RecordingRunner runner = new RecordingRunner();
        CrapJavaCheckMojo mojo = mojo(runner);
        setField(mojo, "session", session(List.of(project(root, "root"), project(moduleA, "module-a"), project(moduleB, "module-b")), root));
        setField(mojo, "project", project(moduleA, "module-a"));

        mojo.execute();

        assertFalse(runner.invoked);
    }

    @Test
    void usesExistingCoverageWhenAllReportsExist() throws Exception {
        Path root = tempDir.resolve("root");
        writeCoverageReport(root);

        RecordingRunner runner = new RecordingRunner();
        CrapJavaCheckMojo mojo = mojo(runner);
        setField(mojo, "session", session(List.of(project(root, "root")), root));
        setField(mojo, "project", project(root, "root"));

        mojo.execute();

        assertTrue(runner.invoked);
        assertTrue(runner.useExistingCoverage);
        assertEquals(root, runner.projectRoot);
        assertEquals(List.of(
                "--format",
                "none",
                "--threshold",
                "6.0",
                "--junit-report",
                root.resolve("target/crap-java/TEST-crap-java.xml").toString()
        ), List.of(runner.args));
    }

    @Test
    void passesConfiguredCompileSourceRootsToCli() throws Exception {
        Path root = tempDir.resolve("root");
        Path sourceRoot = root.resolve("src/java");
        Files.createDirectories(sourceRoot);
        writeCoverageReport(root);

        RecordingRunner runner = new RecordingRunner();
        CrapJavaCheckMojo mojo = mojo(runner);
        MavenProject project = project(root, "root");
        project.addCompileSourceRoot(sourceRoot.toString());
        setField(mojo, "session", session(List.of(project), root));
        setField(mojo, "project", project);

        mojo.execute();

        assertEquals(List.of(
                "--format",
                "none",
                "--source-root",
                sourceRoot.toString(),
                "--threshold",
                "6.0",
                "--junit-report",
                root.resolve("target/crap-java/TEST-crap-java.xml").toString()
        ), List.of(runner.args));
    }

    @Test
    void ignoresGeneratedCompileSourceRootsWhenPassingSourceRootsToCli() throws Exception {
        Path root = tempDir.resolve("root");
        Path defaultSourceRoot = root.resolve("src/main/java");
        Path generatedSourceRoot = root.resolve("target/generated-sources/annotations");
        Files.createDirectories(defaultSourceRoot);
        Files.createDirectories(generatedSourceRoot);
        writeCoverageReport(root);

        RecordingRunner runner = new RecordingRunner();
        CrapJavaCheckMojo mojo = mojo(runner);
        MavenProject project = project(root, "root");
        project.addCompileSourceRoot(defaultSourceRoot.toString());
        project.addCompileSourceRoot(generatedSourceRoot.toString());
        setField(mojo, "session", session(List.of(project), root));
        setField(mojo, "project", project);

        mojo.execute();

        assertEquals(List.of(
                "--format",
                "none",
                "--threshold",
                "6.0",
                "--junit-report",
                root.resolve("target/crap-java/TEST-crap-java.xml").toString()
        ), List.of(runner.args));
    }

    @Test
    void passesOnlyAnalyzableConfiguredCompileSourceRootsToCli() throws Exception {
        Path root = tempDir.resolve("root");
        Path sourceRoot = root.resolve("src/java");
        Path generatedSourceRoot = root.resolve("target/generated-sources/annotations");
        Files.createDirectories(sourceRoot);
        Files.createDirectories(generatedSourceRoot);
        writeCoverageReport(root);

        RecordingRunner runner = new RecordingRunner();
        CrapJavaCheckMojo mojo = mojo(runner);
        MavenProject project = project(root, "root");
        project.addCompileSourceRoot(sourceRoot.toString());
        project.addCompileSourceRoot(generatedSourceRoot.toString());
        setField(mojo, "session", session(List.of(project), root));
        setField(mojo, "project", project);

        mojo.execute();

        assertEquals(List.of(
                "--format",
                "none",
                "--source-root",
                sourceRoot.toString(),
                "--threshold",
                "6.0",
                "--junit-report",
                root.resolve("target/crap-java/TEST-crap-java.xml").toString()
        ), List.of(runner.args));
    }

    @Test
    void filtersCompileSourceRootsOutsideExecutionRootWhenPassingSourceRootsToCli() throws Exception {
        Path root = tempDir.resolve("root");
        Path module = root.resolve("module-a");
        Path sourceRoot = module.resolve("src/java");
        Path externalModule = tempDir.resolve("external-module");
        Path externalSourceRoot = externalModule.resolve("src/java");
        Files.createDirectories(sourceRoot);
        Files.createDirectories(externalSourceRoot);
        writeCoverageReport(module);

        RecordingRunner runner = new RecordingRunner();
        CrapJavaCheckMojo mojo = mojo(runner);
        MavenProject moduleProject = project(module, "module-a");
        moduleProject.addCompileSourceRoot(sourceRoot.toString());
        MavenProject externalProject = project(externalModule, "external-module");
        externalProject.addCompileSourceRoot(externalSourceRoot.toString());
        setField(mojo, "session", session(List.of(project(root, "root"), moduleProject, externalProject), root));
        setField(mojo, "project", externalProject);

        mojo.execute();

        assertEquals(List.of(
                "--format",
                "none",
                "--source-root",
                sourceRoot.toString(),
                "--threshold",
                "6.0",
                "--junit-report",
                root.resolve("target/crap-java/TEST-crap-java.xml").toString()
        ), List.of(runner.args));
    }

    @Test
    void routesRunnerOutputThroughMavenLog() throws Exception {
        Path root = tempDir.resolve("root");
        writeCoverageReport(root);

        RecordingRunner runner = new RecordingRunner();
        runner.emitOutput = true;
        RecordingLog log = new RecordingLog();
        CrapJavaCheckMojo mojo = mojo(runner);
        mojo.setLog(log);
        setField(mojo, "session", session(List.of(project(root, "root")), root));
        setField(mojo, "project", project(root, "root"));

        mojo.execute();

        assertEquals(List.of("Report line", "partial report"), log.infoMessages);
        assertEquals(List.of("Warning: generated coverage missing"), log.warnMessages);
        assertEquals(List.of("Execution failed"), log.errorMessages);
    }

    @Test
    void usesConfiguredJunitReport() throws Exception {
        Path root = tempDir.resolve("root");
        writeCoverageReport(root);
        Path report = root.resolve("custom/crap.xml");

        RecordingRunner runner = new RecordingRunner();
        CrapJavaCheckMojo mojo = mojo(runner);
        setField(mojo, "session", session(List.of(project(root, "root")), root));
        setField(mojo, "project", project(root, "root"));
        setField(mojo, "junitReport", report.toFile());

        mojo.execute();

        assertEquals(List.of("--format", "none", "--threshold", "6.0", "--junit-report", report.toString()), List.of(runner.args));
    }

    @Test
    void usesConfiguredReportControls() throws Exception {
        Path root = tempDir.resolve("root");
        writeCoverageReport(root);
        Path output = root.resolve("target/crap-java/report.json");

        RecordingRunner runner = new RecordingRunner();
        CrapJavaCheckMojo mojo = mojo(runner);
        setField(mojo, "session", session(List.of(project(root, "root")), root));
        setField(mojo, "project", project(root, "root"));
        setField(mojo, "format", "json");
        setField(mojo, "agent", true);
        setField(mojo, "failuresOnly", false);
        setField(mojo, "omitRedundancy", true);
        setField(mojo, "output", output.toFile());

        mojo.execute();

        assertEquals(List.of(
                "--format",
                "json",
                "--agent",
                "--failures-only=false",
                "--omit-redundancy=true",
                "--output",
                output.toString(),
                "--threshold",
                "6.0",
                "--junit-report",
                root.resolve("target/crap-java/TEST-crap-java.xml").toString()
        ), List.of(runner.args));
    }

    @Test
    void usesConfiguredExclusionControls() throws Exception {
        Path root = tempDir.resolve("root");
        writeCoverageReport(root);

        RecordingRunner runner = new RecordingRunner();
        CrapJavaCheckMojo mojo = mojo(runner);
        setField(mojo, "session", session(List.of(project(root, "root")), root));
        setField(mojo, "project", project(root, "root"));
        setField(mojo, "excludesProperty", "module-a/**, module-b/**");
        setField(mojo, "excludes", List.of("**/custom/**"));
        setField(mojo, "excludeClassesProperty", ".*MapperImpl$, demo.Name{1\\,3}$, demo.\\d+$");
        setField(mojo, "excludeClasses", List.of("demo.Other{1,3}$"));
        setField(mojo, "excludeAnnotationsProperty", "Generated");
        setField(mojo, "excludeAnnotations", List.of("com.acme.Generated"));
        setField(mojo, "useDefaultExclusions", false);

        mojo.execute();

        assertEquals(List.of(
                "--format",
                "none",
                "--exclude",
                "module-a/**",
                "--exclude",
                "module-b/**",
                "--exclude",
                "**/custom/**",
                "--exclude-class",
                ".*MapperImpl$",
                "--exclude-class",
                "demo.Name{1,3}$",
                "--exclude-class",
                "demo.\\d+$",
                "--exclude-class",
                "demo.Other{1,3}$",
                "--exclude-annotation",
                "Generated",
                "--exclude-annotation",
                "com.acme.Generated",
                "--use-default-exclusions=false",
                "--threshold",
                "6.0",
                "--junit-report",
                root.resolve("target/crap-java/TEST-crap-java.xml").toString()
        ), List.of(runner.args));
    }

    @Test
    void disablesJunitReport() throws Exception {
        Path root = tempDir.resolve("root");
        writeCoverageReport(root);

        RecordingRunner runner = new RecordingRunner();
        CrapJavaCheckMojo mojo = mojo(runner);
        setField(mojo, "session", session(List.of(project(root, "root")), root));
        setField(mojo, "project", project(root, "root"));
        setField(mojo, "junit", false);

        mojo.execute();

        assertEquals(List.of("--format", "none", "--threshold", "6.0"), List.of(runner.args));
    }

    @Test
    void resolvesConfiguredRelativeReportPathsAgainstExecutionRoot() throws Exception {
        Path root = tempDir.resolve("root");
        writeCoverageReport(root);

        RecordingRunner runner = new RecordingRunner();
        CrapJavaCheckMojo mojo = mojo(runner);
        setField(mojo, "session", session(List.of(project(root, "root")), root));
        setField(mojo, "project", project(root, "root"));
        setField(mojo, "output", Path.of("target/crap-java/report.json").toFile());
        setField(mojo, "junitReport", Path.of("target/crap-java/custom-junit.xml").toFile());

        mojo.execute();

        assertEquals(List.of(
                "--format",
                "none",
                "--output",
                root.resolve("target/crap-java/report.json").toString(),
                "--threshold",
                "6.0",
                "--junit-report",
                root.resolve("target/crap-java/custom-junit.xml").toString()
        ), List.of(runner.args));
    }

    @Test
    void usesConfiguredThreshold() throws Exception {
        Path root = tempDir.resolve("root");
        writeCoverageReport(root);

        RecordingRunner runner = new RecordingRunner();
        CrapJavaCheckMojo mojo = mojo(runner);
        setField(mojo, "session", session(List.of(project(root, "root")), root));
        setField(mojo, "project", project(root, "root"));
        setField(mojo, "threshold", 8.0);

        mojo.execute();

        assertEquals(List.of(
                "--format",
                "none",
                "--threshold",
                "8.0",
                "--junit-report",
                root.resolve("target/crap-java/TEST-crap-java.xml").toString()
        ), List.of(runner.args));
    }

    @Test
    void finalReactorProjectRunsAgainstExecutionRootWithExistingCoverage() throws Exception {
        Path root = tempDir.resolve("root");
        Path moduleA = root.resolve("module-a");
        Path moduleB = root.resolve("module-b");
        writeCoverageReport(moduleA);
        writeCoverageReport(moduleB);

        RecordingRunner runner = new RecordingRunner();
        CrapJavaCheckMojo mojo = mojo(runner);
        setField(mojo, "session", session(List.of(project(root, "root"), project(moduleA, "module-a"), project(moduleB, "module-b")), root));
        setField(mojo, "project", project(moduleB, "module-b"));

        mojo.execute();

        assertTrue(runner.invoked);
        assertTrue(runner.useExistingCoverage);
        assertEquals(root, runner.projectRoot);
    }

    @Test
    void missingCoverageReportsFailClearly() throws Exception {
        Path root = tempDir.resolve("root");
        Files.createDirectories(root.resolve("src/main/java/demo"));

        RecordingRunner runner = new RecordingRunner();
        CrapJavaCheckMojo mojo = mojo(runner);
        setField(mojo, "session", session(List.of(project(root, "root")), root));
        setField(mojo, "project", project(root, "root"));

        MojoFailureException ex = assertThrows(MojoFailureException.class, mojo::execute);

        assertEquals(
                "Missing JaCoCo XML reports. Configure jacoco-maven-plugin to generate target/site/jacoco/jacoco.xml before crap-java:check: "
                        + root.resolve("target/site/jacoco/jacoco.xml"),
                ex.getMessage()
        );
        assertFalse(runner.invoked);
    }

    @Test
    void ignoresModulesWithoutProductionJavaSourcesWhenCheckingCoverageReports() throws Exception {
        Path root = tempDir.resolve("root");
        Path moduleA = root.resolve("module-a");
        Path moduleB = root.resolve("module-b");
        Files.createDirectories(moduleA.resolve("src/test/java/demo"));
        writeCoverageReport(moduleB);

        RecordingRunner runner = new RecordingRunner();
        CrapJavaCheckMojo mojo = mojo(runner);
        setField(mojo, "session", session(List.of(project(root, "root"), project(moduleA, "module-a"), project(moduleB, "module-b")), root));
        setField(mojo, "project", project(moduleB, "module-b"));

        mojo.execute();

        assertTrue(runner.invoked);
        assertTrue(runner.useExistingCoverage);
        assertEquals(root, runner.projectRoot);
    }

    @Test
    void checksCoverageReportsForConfiguredCompileSourceRoots() throws Exception {
        Path root = tempDir.resolve("root");
        Path sourceRoot = root.resolve("src/java");
        Files.createDirectories(sourceRoot);

        RecordingRunner runner = new RecordingRunner();
        CrapJavaCheckMojo mojo = mojo(runner);
        MavenProject project = project(root, "root");
        project.addCompileSourceRoot(sourceRoot.toString());
        setField(mojo, "session", session(List.of(project), root));
        setField(mojo, "project", project);

        MojoFailureException ex = assertThrows(MojoFailureException.class, mojo::execute);

        assertEquals(
                "Missing JaCoCo XML reports. Configure jacoco-maven-plugin to generate target/site/jacoco/jacoco.xml before crap-java:check: "
                        + root.resolve("target/site/jacoco/jacoco.xml"),
                ex.getMessage()
        );
        assertFalse(runner.invoked);
    }

    @Test
    void ignoresGeneratedCompileSourceRootsWhenCheckingCoverageReports() throws Exception {
        Path root = tempDir.resolve("root");
        Path generatedSourceRoot = root.resolve("target/generated-sources/annotations");
        Files.createDirectories(generatedSourceRoot);

        RecordingRunner runner = new RecordingRunner();
        CrapJavaCheckMojo mojo = mojo(runner);
        MavenProject project = project(root, "root");
        project.addCompileSourceRoot(generatedSourceRoot.toString());
        setField(mojo, "session", session(List.of(project), root));
        setField(mojo, "project", project);

        mojo.execute();

        assertTrue(runner.invoked);
        assertEquals(List.of(
                "--format",
                "none",
                "--threshold",
                "6.0",
                "--junit-report",
                root.resolve("target/crap-java/TEST-crap-java.xml").toString()
        ), List.of(runner.args));
    }

    @Test
    void fallsBackToProjectBasedirWhenSessionHasNoMultiModuleRoot() throws Exception {
        Path root = tempDir.resolve("root");
        writeCoverageReport(root);

        RecordingRunner runner = new RecordingRunner();
        CrapJavaCheckMojo mojo = mojo(runner);
        setField(mojo, "session", session(List.of(project(root, "root")), null));
        setField(mojo, "project", project(root, "root"));

        mojo.execute();

        assertEquals(root, runner.projectRoot);
    }

    @Test
    void exitCodeTwoFailsTheBuild() throws Exception {
        Path root = tempDir.resolve("root");
        writeCoverageReport(root);

        RecordingRunner runner = new RecordingRunner();
        runner.exitCode = 2;
        CrapJavaCheckMojo mojo = mojo(runner);
        setField(mojo, "session", session(List.of(project(root, "root")), root));
        setField(mojo, "project", project(root, "root"));

        MojoFailureException ex = assertThrows(MojoFailureException.class, mojo::execute);

        assertEquals("crap-java threshold exceeded", ex.getMessage());
    }

    @Test
    void nonZeroExitCodeRaisesExecutionError() throws Exception {
        Path root = tempDir.resolve("root");
        writeCoverageReport(root);

        RecordingRunner runner = new RecordingRunner();
        runner.exitCode = 1;
        CrapJavaCheckMojo mojo = mojo(runner);
        setField(mojo, "session", session(List.of(project(root, "root")), root));
        setField(mojo, "project", project(root, "root"));

        MojoExecutionException ex = assertThrows(MojoExecutionException.class, mojo::execute);

        assertEquals("crap-java check failed with exit 1", ex.getMessage());
    }

    @Test
    void unexpectedExceptionsAreWrapped() throws Exception {
        Path root = tempDir.resolve("root");
        writeCoverageReport(root);

        RecordingRunner runner = new RecordingRunner();
        runner.failure = new IllegalStateException("boom");
        CrapJavaCheckMojo mojo = mojo(runner);
        setField(mojo, "session", session(List.of(project(root, "root")), root));
        setField(mojo, "project", project(root, "root"));

        MojoExecutionException ex = assertThrows(MojoExecutionException.class, mojo::execute);

        assertEquals("Failed to execute crap-java", ex.getMessage());
        assertInstanceOf(IllegalStateException.class, ex.getCause());
    }

    private static void writeCoverageReport(Path projectRoot) throws Exception {
        Files.createDirectories(projectRoot.resolve("src/main/java/demo"));
        Files.createDirectories(projectRoot.resolve("target/site/jacoco"));
        Files.writeString(projectRoot.resolve("target/site/jacoco/jacoco.xml"), "<report/>");
    }

    private static MavenSession session(List<MavenProject> projects, @Nullable Path multiModuleRoot) {
        DefaultMavenExecutionRequest request = new DefaultMavenExecutionRequest();
        request.setMultiModuleProjectDirectory(multiModuleRoot == null ? null : multiModuleRoot.toFile());

        List<MavenProject> reactorProjects = new ArrayList<>(projects);
        MavenSession session = new MavenSession(null, request, new DefaultMavenExecutionResult(), reactorProjects);
        session.setProjects(reactorProjects);
        return session;
    }

    private static MavenProject project(Path basedir, String artifactId) {
        MavenProject project = new MavenProject();
        project.setArtifactId(artifactId);
        project.setFile(basedir.resolve("pom.xml").toFile());
        return project;
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static CrapJavaCheckMojo mojo(CrapJavaCheckMojo.CrapJavaRunner runner) {
        CrapJavaCheckMojo mojo = new CrapJavaCheckMojo(runner);
        mojo.setLog(new SilentLog());
        return mojo;
    }

    private static final class RecordingRunner implements CrapJavaCheckMojo.CrapJavaRunner {
        private boolean invoked;
        private boolean useExistingCoverage;
        private String[] args = new String[0];
        private @Nullable Path projectRoot;
        private int exitCode;
        private @Nullable Exception failure;
        private boolean emitOutput;

        @Override
        public int run(boolean useExistingCoverage, String[] args, Path projectRoot, java.io.PrintStream out, java.io.PrintStream err)
                throws Exception {
            invoked = true;
            this.useExistingCoverage = useExistingCoverage;
            this.args = args.clone();
            this.projectRoot = projectRoot;
            if (failure != null) {
                throw failure;
            }
            if (emitOutput) {
                out.println("Report line");
                out.print("partial report");
                out.flush();
                err.println("Warning: generated coverage missing");
                err.println("Execution failed");
            }
            return exitCode;
        }
    }

    private static class SilentLog implements Log {

        @Override
        public boolean isDebugEnabled() {
            return false;
        }

        @Override
        public void debug(CharSequence content) {
        }

        @Override
        public void debug(CharSequence content, Throwable error) {
        }

        @Override
        public void debug(Throwable error) {
        }

        @Override
        public boolean isInfoEnabled() {
            return false;
        }

        @Override
        public void info(CharSequence content) {
        }

        @Override
        public void info(CharSequence content, Throwable error) {
        }

        @Override
        public void info(Throwable error) {
        }

        @Override
        public boolean isWarnEnabled() {
            return false;
        }

        @Override
        public void warn(CharSequence content) {
        }

        @Override
        public void warn(CharSequence content, Throwable error) {
        }

        @Override
        public void warn(Throwable error) {
        }

        @Override
        public boolean isErrorEnabled() {
            return false;
        }

        @Override
        public void error(CharSequence content) {
        }

        @Override
        public void error(CharSequence content, Throwable error) {
        }

        @Override
        public void error(Throwable error) {
        }
    }

    private static final class RecordingLog extends SilentLog {
        private final List<String> infoMessages = new ArrayList<>();
        private final List<String> warnMessages = new ArrayList<>();
        private final List<String> errorMessages = new ArrayList<>();

        @Override
        public void info(CharSequence content) {
            infoMessages.add(content.toString());
        }

        @Override
        public void warn(CharSequence content) {
            warnMessages.add(content.toString());
        }

        @Override
        public void error(CharSequence content) {
            errorMessages.add(content.toString());
        }
    }
}

