package media.barney.crap4java.gradle;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.testing.Test;
import org.gradle.language.base.plugins.LifecycleBasePlugin;
import org.gradle.testing.jacoco.tasks.JacocoReport;

import java.util.Collection;
import java.util.List;

public class Crap4JavaGradlePlugin implements Plugin<Project> {

    @Override
    public void apply(Project project) {
        TaskProvider<Crap4JavaCheckTask> checkTask = project.getTasks().register(
                "crap4javaCheck",
                Crap4JavaCheckTask.class,
                task -> {
                    task.setGroup(LifecycleBasePlugin.VERIFICATION_GROUP);
                    task.setDescription("Runs the crap4java CRAP metric gate.");
                    task.getAnalysisRoot().set(project.getLayout().getProjectDirectory());
                }
        );

        for (Project candidate : projectsToConfigure(project)) {
            candidate.getPluginManager().withPlugin("java", ignored -> configureJavaProject(candidate, checkTask));
        }
    }

    private Collection<Project> projectsToConfigure(Project project) {
        if (project.equals(project.getRootProject())) {
            return project.getAllprojects();
        }
        return List.of(project);
    }

    private void configureJavaProject(Project candidate, TaskProvider<Crap4JavaCheckTask> checkTask) {
        candidate.getPluginManager().apply("jacoco");

        TaskProvider<Test> testTask = candidate.getTasks().named("test", Test.class);
        TaskProvider<JacocoReport> jacocoReportTask = candidate.getTasks().named("jacocoTestReport", JacocoReport.class, report ->
                report.getReports().getXml().getRequired().set(true)
        );

        checkTask.configure(task -> {
            task.dependsOn(testTask);
            task.dependsOn(jacocoReportTask);
        });
    }
}
