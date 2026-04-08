package media.barney.crap.gradle;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.testing.Test;
import org.gradle.language.base.plugins.LifecycleBasePlugin;
import org.gradle.testing.jacoco.tasks.JacocoReport;

import java.util.Collection;
import java.util.List;
import java.nio.file.Path;

public class CrapJavaGradlePlugin implements Plugin<Project> {

    private static final String JACOCO_XML_RELATIVE_PATH = "build/reports/jacoco/test/jacocoTestReport.xml";

    @Override
    public void apply(Project project) {
        TaskProvider<CrapJavaCheckTask> checkTask = project.getTasks().register(
                "crap-java-check",
                CrapJavaCheckTask.class,
                task -> {
                    task.setGroup(LifecycleBasePlugin.VERIFICATION_GROUP);
                    task.setDescription("Runs the crap-java CRAP metric gate.");
                    task.getAnalysisRoot().set(project.getLayout().getProjectDirectory());
                }
        );

        for (Project candidate : projectsToConfigure(project)) {
            candidate.getPluginManager().withPlugin("java", ignored -> configureJavaProject(project, candidate, checkTask));
        }
    }

    private Collection<Project> projectsToConfigure(Project project) {
        if (project.equals(project.getRootProject())) {
            return project.getAllprojects();
        }
        return List.of(project);
    }

    private void configureJavaProject(Project analysisProject,
                                      Project candidate,
                                      TaskProvider<CrapJavaCheckTask> checkTask) {
        candidate.getPluginManager().apply("jacoco");

        TaskProvider<Test> testTask = candidate.getTasks().named("test", Test.class);
        TaskProvider<JacocoReport> jacocoReportTask = candidate.getTasks().named("jacocoTestReport", JacocoReport.class, report ->
                report.getReports().getXml().getRequired().set(true)
        );
        String modulePath = relativeModulePath(analysisProject, candidate);

        checkTask.configure(task -> {
            task.dependsOn(testTask);
            task.dependsOn(jacocoReportTask);
            task.getAnalysisSources().from(candidate.fileTree(candidate.getProjectDir(), tree ->
                    tree.include("src/main/java/**/*.java")
            ));
            task.getCoverageReports().from(candidate.getLayout().getBuildDirectory().file("reports/jacoco/test/jacocoTestReport.xml"));
            task.getModuleCoverageReports().put(modulePath, coverageReportPath(modulePath));
        });
    }

    private static String relativeModulePath(Project analysisProject, Project candidate) {
        Path analysisRoot = analysisProject.getProjectDir().toPath().toAbsolutePath().normalize();
        Path candidateRoot = candidate.getProjectDir().toPath().toAbsolutePath().normalize();
        if (analysisRoot.equals(candidateRoot)) {
            return ".";
        }
        return analysisRoot.relativize(candidateRoot).toString().replace('\\', '/');
    }

    private static String coverageReportPath(String modulePath) {
        if (".".equals(modulePath)) {
            return JACOCO_XML_RELATIVE_PATH;
        }
        return modulePath + "/" + JACOCO_XML_RELATIVE_PATH;
    }
}

