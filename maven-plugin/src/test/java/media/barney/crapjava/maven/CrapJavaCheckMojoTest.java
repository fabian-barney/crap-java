package media.barney.crapjava.maven;

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
    void skipsNonRootProject() throws Exception {
        Path root = tempDir.resolve("root");
        Path module = root.resolve("module-a");
        Files.createDirectories(module);

        RecordingRunner runner = new RecordingRunner();
        CrapJavaCheckMojo mojo = mojo(runner);
        setField(mojo, "session", session(List.of(project(root, "root"), project(module, "module-a")), root));
        setField(mojo, "project", project(module, "module-a"));

        mojo.execute();

        assertFalse(runner.invoked);
    }

    @Test
    void usesExistingCoverageWhenAllReportsExist() throws Exception {
        Path root = tempDir.resolve("root");
        Files.createDirectories(root.resolve("src/main/java/demo"));
        Files.createDirectories(root.resolve("target/site/jacoco"));
        Files.writeString(root.resolve("target/site/jacoco/jacoco.xml"), "<report/>");

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
    void generatesCoverageWhenAnyReportIsMissing() throws Exception {
        Path root = tempDir.resolve("root");
        Files.createDirectories(root.resolve("src/main/java/demo"));

        RecordingRunner runner = new RecordingRunner();
        CrapJavaCheckMojo mojo = mojo(runner);
        setField(mojo, "session", session(List.of(project(root, "root")), root));
        setField(mojo, "project", project(root, "root"));

        mojo.execute();

        assertTrue(runner.invoked);
        assertFalse(runner.useExistingCoverage);
    }

    @Test
    void fallsBackToProjectBasedirWhenSessionHasNoMultiModuleRoot() throws Exception {
        Path root = tempDir.resolve("root");
        Files.createDirectories(root.resolve("src/main/java/demo"));

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
        Files.createDirectories(root.resolve("src/main/java/demo"));

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
        Files.createDirectories(root.resolve("src/main/java/demo"));

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
        Files.createDirectories(root.resolve("src/main/java/demo"));

        RecordingRunner runner = new RecordingRunner();
        runner.failure = new IllegalStateException("boom");
        CrapJavaCheckMojo mojo = mojo(runner);
        setField(mojo, "session", session(List.of(project(root, "root")), root));
        setField(mojo, "project", project(root, "root"));

        MojoExecutionException ex = assertThrows(MojoExecutionException.class, mojo::execute);

        assertEquals("Failed to execute crap-java", ex.getMessage());
        assertInstanceOf(IllegalStateException.class, ex.getCause());
    }

    private static MavenSession session(List<MavenProject> projects, @Nullable Path multiModuleRoot) {
        DefaultMavenExecutionRequest request = new DefaultMavenExecutionRequest();
        request.setMultiModuleProjectDirectory(multiModuleRoot == null ? null : multiModuleRoot.toFile());

        MavenSession session = new MavenSession(null, request, new DefaultMavenExecutionResult(), projects);
        session.setProjects(projects);
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
