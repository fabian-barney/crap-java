package media.barney.crapjava.gradle;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.testing.Test;
import org.gradle.language.base.plugins.LifecycleBasePlugin;
import org.gradle.testing.jacoco.tasks.JacocoReport;

import java.util.Collection;
import java.util.List;

public class CrapJavaGradlePlugin implements Plugin<Project> {

    @Override
    public void apply(Project project) {
        TaskProvider<CrapJavaCheckTask> checkTask = project.getTasks().register(
                "crap-java-check",
                CrapJavaCheckTask.class,
                task -> {
                    task.setGroup(LifecycleBasePlugin.VERIFICATION_GROUP);
                    task.setDescription("Runs the crap-java CRAP metric gate.");
                    task.getAnalysisRoot().set(project.getLayout().getProjectDirectory());
                    task.getAnalysisMetadata().from(
                            project.getLayout().getProjectDirectory().file("settings.gradle"),
                            project.getLayout().getProjectDirectory().file("settings.gradle.kts"),
                            project.getLayout().getProjectDirectory().file("build.gradle"),
                            project.getLayout().getProjectDirectory().file("build.gradle.kts"),
                            project.getLayout().getProjectDirectory().file("gradlew"),
                            project.getLayout().getProjectDirectory().file("gradlew.bat")
                    );
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

    private void configureJavaProject(Project candidate, TaskProvider<CrapJavaCheckTask> checkTask) {
        candidate.getPluginManager().apply("jacoco");

        TaskProvider<Test> testTask = candidate.getTasks().named("test", Test.class);
        TaskProvider<JacocoReport> jacocoReportTask = candidate.getTasks().named("jacocoTestReport", JacocoReport.class, report ->
                report.getReports().getXml().getRequired().set(true)
        );

        checkTask.configure(task -> {
            task.dependsOn(testTask);
            task.dependsOn(jacocoReportTask);
            task.getAnalysisSources().from(candidate.fileTree(candidate.getProjectDir(), tree ->
                    tree.include("src/main/java/**/*.java")
            ));
            task.getCoverageReports().from(candidate.getLayout().getBuildDirectory().file("reports/jacoco/test/jacocoTestReport.xml"));
            task.getAnalysisMetadata().from(
                    candidate.getLayout().getProjectDirectory().file("build.gradle"),
                    candidate.getLayout().getProjectDirectory().file("build.gradle.kts")
            );
        });
    }
}
