package media.barney.crap4java.maven;

import media.barney.crap4java.core.Main;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

@Mojo(name = "check", defaultPhase = LifecyclePhase.VERIFY, aggregator = true, threadSafe = true)
public class Crap4JavaCheckMojo extends AbstractMojo {

    @Parameter(defaultValue = "${session}", readonly = true, required = true)
    private MavenSession session;

    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    private MavenProject project;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        Path executionRoot = executionRoot();
        if (!project.getBasedir().toPath().normalize().equals(executionRoot)) {
            getLog().debug("Skipping crap4java check for non-root project " + project.getArtifactId());
            return;
        }
        try {
            int exit = hasExistingCoverageReports()
                    ? Main.runWithExistingCoverage(new String[0], executionRoot, System.out, System.err)
                    : Main.run(new String[0], executionRoot, System.out, System.err);
            if (exit == 2) {
                throw new MojoFailureException("crap4java threshold exceeded");
            }
            if (exit != 0) {
                throw new MojoExecutionException("crap4java check failed with exit " + exit);
            }
        } catch (MojoFailureException | MojoExecutionException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new MojoExecutionException("Failed to execute crap4java", ex);
        }
    }

    private boolean hasExistingCoverageReports() {
        for (MavenProject reactorProject : reactorProjects()) {
            Path basedir = reactorProject.getBasedir().toPath();
            if (Files.exists(basedir.resolve("src")) && !Files.exists(basedir.resolve("target/site/jacoco/jacoco.xml"))) {
                return false;
            }
        }
        return true;
    }

    private List<MavenProject> reactorProjects() {
        List<MavenProject> projects = session.getProjects();
        return projects == null || projects.isEmpty() ? List.of(project) : projects;
    }

    private Path executionRoot() {
        java.io.File multiModuleRoot = session.getRequest().getMultiModuleProjectDirectory();
        if (multiModuleRoot != null) {
            return multiModuleRoot.toPath().normalize();
        }
        return project.getBasedir().toPath().normalize();
    }
}
