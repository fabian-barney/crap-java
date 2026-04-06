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
        private @Nullable Path projectRoot;
        private int exitCode;
        private @Nullable Exception failure;

        @Override
        public int run(boolean useExistingCoverage, String[] args, Path projectRoot, java.io.PrintStream out, java.io.PrintStream err)
                throws Exception {
            invoked = true;
            this.useExistingCoverage = useExistingCoverage;
            this.projectRoot = projectRoot;
            if (failure != null) {
                throw failure;
            }
            return exitCode;
        }
    }

    private static final class SilentLog implements Log {

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
}

