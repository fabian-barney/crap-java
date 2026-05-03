package media.barney.crap.gradle;

import media.barney.crap.core.Main;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.testing.Test;
import org.gradle.language.base.plugins.LifecycleBasePlugin;
import org.gradle.testing.jacoco.tasks.JacocoReport;

import java.nio.file.Path;
import java.util.Collection;
import java.util.List;

public class CrapJavaGradlePlugin implements Plugin<Project> {

    private static final String JACOCO_XML_RELATIVE_PATH = "build/reports/jacoco/test/jacocoTestReport.xml";

    @Override
    public void apply(Project project) {
        CrapJavaExtension extension = project.getExtensions().create("crapJava", CrapJavaExtension.class);
        extension.getThreshold().convention(Main.DEFAULT_THRESHOLD);
        extension.getAgent().convention(false);
        extension.getFormat().convention(extension.getAgent().map(agent -> agent ? "toon" : "none"));
        extension.getFailuresOnly().convention(extension.getAgent());
        extension.getOmitRedundancy().convention(extension.getAgent());
        extension.getJunit().convention(true);
        extension.getJunitReport().convention(project.getLayout().getBuildDirectory()
                .file("reports/crap-java/TEST-crap-java.xml"));

        TaskProvider<CrapJavaCheckTask> checkTask = project.getTasks().register(
                "crap-java-check",
                CrapJavaCheckTask.class,
                task -> {
                    task.setGroup(LifecycleBasePlugin.VERIFICATION_GROUP);
                    task.setDescription("Runs the crap-java CRAP metric gate.");
                    task.getAnalysisRoot().set(project.getLayout().getProjectDirectory());
                    task.getThreshold().convention(extension.getThreshold());
                    task.getAgent().convention(extension.getAgent());
                    task.getFormat().convention(taskFormatDefault(project, task, extension));
                    task.getFailuresOnly().convention(taskPrimaryFlagDefault(project, task, extension, extension.getFailuresOnly()));
                    task.getOmitRedundancy().convention(taskPrimaryFlagDefault(project, task, extension, extension.getOmitRedundancy()));
                    task.getOutput().convention(extension.getOutput());
                    task.getJunit().convention(extension.getJunit());
                    task.getJunitReport().convention(extension.getJunitReport());
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

    private static Provider<String> taskFormatDefault(Project project,
                                                      CrapJavaCheckTask task,
                                                      CrapJavaExtension extension) {
        return project.getProviders().provider(() -> {
            boolean extensionAgent = extension.getAgent().getOrElse(false);
            boolean taskAgent = task.getAgent().getOrElse(extensionAgent);
            String extensionFormat = extension.getFormat().getOrElse(extensionAgent ? "toon" : "none");
            if (taskAgent != extensionAgent && isDefaultAgentFormat(extensionAgent, extensionFormat)) {
                return taskAgent ? "toon" : "none";
            }
            return extensionFormat;
        });
    }

    private static Provider<Boolean> taskPrimaryFlagDefault(Project project,
                                                           CrapJavaCheckTask task,
                                                           CrapJavaExtension extension,
                                                           Property<Boolean> extensionControl) {
        return project.getProviders().provider(() -> {
            boolean extensionAgent = extension.getAgent().getOrElse(false);
            boolean extensionValue = extensionControl.getOrElse(extensionAgent);
            boolean taskAgent = task.getAgent().getOrElse(extensionAgent);
            if (taskAgent != extensionAgent && extensionValue == extensionAgent) {
                return taskAgent;
            }
            return extensionValue;
        });
    }

    private static boolean isDefaultAgentFormat(boolean agent, String format) {
        return agent ? "toon".equals(format) : "none".equals(format);
    }
}

