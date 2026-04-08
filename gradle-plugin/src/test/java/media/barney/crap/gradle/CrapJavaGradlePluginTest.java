package media.barney.crap.gradle;

import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CrapJavaGradlePluginTest {

    @TempDir
    Path tempDir;

    @Test
    void applyRegistersVerificationTaskForJavaProjects() {
        Project project = ProjectBuilder.builder().withProjectDir(tempDir.toFile()).build();

        project.getPluginManager().apply("java");
        project.getPluginManager().apply(CrapJavaGradlePlugin.class);

        CrapJavaCheckTask checkTask = (CrapJavaCheckTask) project.getTasks().getByName("crap-java-check");

        assertEquals("verification", checkTask.getGroup());
        assertEquals("Runs the crap-java CRAP metric gate.", checkTask.getDescription());
        Set<String> dependencyNames = checkTask.getTaskDependencies().getDependencies(checkTask).stream()
                .map(Task::getName)
                .collect(Collectors.toSet());
        assertEquals(Set.of("test", "jacocoTestReport"), dependencyNames);
        assertEquals(Map.of(".", "build/reports/jacoco/test/jacocoTestReport.xml"), checkTask.getModuleCoverageReports().get());
        assertNotNull(project.getTasks().findByName("jacocoTestReport"));
    }

    @Test
    void runCheckAnalyzesConfiguredSourcesWithExistingCoverage() throws Exception {
        Path projectRoot = tempDir.toRealPath();
        Project project = ProjectBuilder.builder().withProjectDir(projectRoot.toFile()).build();
        Files.writeString(projectRoot.resolve("build.gradle"), "plugins { id 'java' }");
        Path source = projectRoot.resolve("src/main/java/demo/Sample.java");
        Files.createDirectories(source.getParent());
        Files.writeString(source, """
                package demo;

                class Sample {
                    int alpha() {
                        return 1;
                    }
                }
                """);
        Path jacocoXml = projectRoot.resolve("build/reports/jacoco/test/jacocoTestReport.xml");
        Files.createDirectories(jacocoXml.getParent());
        Files.writeString(jacocoXml, """
                <report name="demo">
                  <package name="demo">
                    <class name="demo/Sample" sourcefilename="Sample.java">
                      <method name="alpha" desc="()I" line="4">
                        <counter type="INSTRUCTION" missed="0" covered="1"/>
                      </method>
                    </class>
                  </package>
                </report>
                """);

        CrapJavaCheckTask task = project.getTasks().register("crap-java-check", CrapJavaCheckTask.class).get();
        task.getAnalysisRoot().fileValue(projectRoot.toFile());
        task.getAnalysisSources().from(source);
        task.getCoverageReports().from(jacocoXml);
        task.getModuleCoverageReports().put(".", "build/reports/jacoco/test/jacocoTestReport.xml");

        task.runCheck();

        assertTrue(Files.exists(jacocoXml));
    }
}

