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
import static org.junit.jupiter.api.Assertions.assertFalse;
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
        CrapJavaExtension extension = project.getExtensions().getByType(CrapJavaExtension.class);

        assertEquals("verification", checkTask.getGroup());
        assertEquals("Runs the crap-java CRAP metric gate.", checkTask.getDescription());
        assertEquals(8.0, extension.getThreshold().get());
        assertFalse(extension.getAgent().get());
        assertEquals("none", extension.getFormat().get());
        assertFalse(extension.getFailuresOnly().get());
        assertFalse(extension.getOmitRedundancy().get());
        assertFalse(extension.getOutput().isPresent());
        assertTrue(extension.getJunit().get());
        assertTrue(extension.getJunitReport().get().getAsFile().toPath().normalize().toString()
                .replace('\\', '/')
                .endsWith("build/reports/crap-java/TEST-crap-java.xml"));
        assertEquals(8.0, checkTask.getThreshold().get());
        assertEquals("none", checkTask.getFormat().get());
        assertFalse(checkTask.getAgent().get());
        assertFalse(checkTask.getFailuresOnly().get());
        assertFalse(checkTask.getOmitRedundancy().get());
        assertFalse(checkTask.getOutput().isPresent());
        assertTrue(checkTask.getJunit().get());
        Set<String> dependencyNames = checkTask.getTaskDependencies().getDependencies(checkTask).stream()
                .map(Task::getName)
                .collect(Collectors.toSet());
        assertEquals(Set.of("test", "jacocoTestReport"), dependencyNames);
        assertEquals(Map.of(".", "build/reports/jacoco/test/jacocoTestReport.xml"), checkTask.getModuleCoverageReports().get());
        assertTrue(checkTask.getJunitReport().get().getAsFile().toPath().normalize().toString()
                .replace('\\', '/')
                .endsWith("build/reports/crap-java/TEST-crap-java.xml"));
        assertTrue(checkTask.getJunitReportOutput().isPresent());
        assertNotNull(project.getTasks().findByName("jacocoTestReport"));
    }

    @Test
    void configuredExtensionThresholdFlowsToCheckTask() {
        Project project = ProjectBuilder.builder().withProjectDir(tempDir.toFile()).build();

        project.getPluginManager().apply("java");
        project.getPluginManager().apply(CrapJavaGradlePlugin.class);
        project.getExtensions().getByType(CrapJavaExtension.class).getThreshold().set(6.0);

        CrapJavaCheckTask checkTask = (CrapJavaCheckTask) project.getTasks().getByName("crap-java-check");

        assertEquals(6.0, checkTask.getThreshold().get());
    }

    @Test
    void configuredExtensionReportControlsFlowToCheckTask() {
        Project project = ProjectBuilder.builder().withProjectDir(tempDir.toFile()).build();

        project.getPluginManager().apply("java");
        project.getPluginManager().apply(CrapJavaGradlePlugin.class);
        CrapJavaExtension extension = project.getExtensions().getByType(CrapJavaExtension.class);
        Path output = tempDir.resolve("build/reports/crap-java/report.json");
        Path junitReport = tempDir.resolve("build/reports/crap-java/custom-junit.xml");
        extension.getFormat().set("json");
        extension.getAgent().set(true);
        extension.getFailuresOnly().set(false);
        extension.getOmitRedundancy().set(true);
        extension.getOutput().fileValue(output.toFile());
        extension.getJunit().set(false);
        extension.getJunitReport().fileValue(junitReport.toFile());

        CrapJavaCheckTask checkTask = (CrapJavaCheckTask) project.getTasks().getByName("crap-java-check");

        assertEquals("json", checkTask.getFormat().get());
        assertTrue(checkTask.getAgent().get());
        assertFalse(checkTask.getFailuresOnly().get());
        assertTrue(checkTask.getOmitRedundancy().get());
        assertEquals(output.normalize(), checkTask.getOutput().get().getAsFile().toPath().normalize());
        assertFalse(checkTask.getJunit().get());
        assertEquals(junitReport.normalize(), checkTask.getJunitReport().get().getAsFile().toPath().normalize());
        assertFalse(checkTask.getJunitReportOutput().isPresent());
    }

    @Test
    void directlyRegisteredCheckTaskHasReportControlDefaults() {
        Project project = ProjectBuilder.builder().withProjectDir(tempDir.toFile()).build();

        CrapJavaCheckTask checkTask = project.getTasks().register("custom-crap-java-check", CrapJavaCheckTask.class).get();

        assertEquals(8.0, checkTask.getThreshold().get());
        assertFalse(checkTask.getAgent().get());
        assertEquals("none", checkTask.getFormat().get());
        assertFalse(checkTask.getFailuresOnly().get());
        assertFalse(checkTask.getOmitRedundancy().get());
        assertFalse(checkTask.getOutput().isPresent());
        assertTrue(checkTask.getJunit().get());
        assertTrue(checkTask.getJunitReport().get().getAsFile().toPath().normalize().toString()
                .replace('\\', '/')
                .endsWith("build/reports/crap-java/custom-crap-java-check/TEST-crap-java.xml"));
        assertTrue(checkTask.getJunitReportOutput().isPresent());
    }

    @Test
    void agentExtensionComposesPrimaryDefaultsWhenControlsAreUnset() {
        Project project = ProjectBuilder.builder().withProjectDir(tempDir.toFile()).build();

        project.getPluginManager().apply("java");
        project.getPluginManager().apply(CrapJavaGradlePlugin.class);
        project.getExtensions().getByType(CrapJavaExtension.class).getAgent().set(true);

        CrapJavaCheckTask checkTask = (CrapJavaCheckTask) project.getTasks().getByName("crap-java-check");
        CrapJavaExtension extension = project.getExtensions().getByType(CrapJavaExtension.class);

        assertEquals("toon", extension.getFormat().get());
        assertTrue(extension.getFailuresOnly().get());
        assertTrue(extension.getOmitRedundancy().get());
        assertEquals("toon", checkTask.getFormat().get());
        assertTrue(checkTask.getFailuresOnly().get());
        assertTrue(checkTask.getOmitRedundancy().get());
    }

    @Test
    void agentTaskComposesPrimaryDefaultsWhenControlsAreUnset() {
        Project project = ProjectBuilder.builder().withProjectDir(tempDir.toFile()).build();

        project.getPluginManager().apply("java");
        project.getPluginManager().apply(CrapJavaGradlePlugin.class);
        CrapJavaCheckTask checkTask = (CrapJavaCheckTask) project.getTasks().getByName("crap-java-check");
        checkTask.getAgent().set(true);

        assertEquals("toon", checkTask.getFormat().get());
        assertTrue(checkTask.getFailuresOnly().get());
        assertTrue(checkTask.getOmitRedundancy().get());
    }

    @Test
    void taskAgentFalseOverridesExtensionAgentComposedDefaults() {
        Project project = ProjectBuilder.builder().withProjectDir(tempDir.toFile()).build();

        project.getPluginManager().apply("java");
        project.getPluginManager().apply(CrapJavaGradlePlugin.class);
        project.getExtensions().getByType(CrapJavaExtension.class).getAgent().set(true);
        CrapJavaCheckTask checkTask = (CrapJavaCheckTask) project.getTasks().getByName("crap-java-check");
        checkTask.getAgent().set(false);

        assertEquals("none", checkTask.getFormat().get());
        assertFalse(checkTask.getFailuresOnly().get());
        assertFalse(checkTask.getOmitRedundancy().get());
    }

    @Test
    void configuredTaskReportControlsOverrideExtensionDefaults() {
        Project project = ProjectBuilder.builder().withProjectDir(tempDir.toFile()).build();

        project.getPluginManager().apply("java");
        project.getPluginManager().apply(CrapJavaGradlePlugin.class);
        CrapJavaCheckTask checkTask = (CrapJavaCheckTask) project.getTasks().getByName("crap-java-check");
        Path output = tempDir.resolve("build/reports/crap-java/task-report.json");
        Path junitReport = tempDir.resolve("build/reports/crap-java/task-junit.xml");
        checkTask.getAgent().set(true);
        checkTask.getFormat().set("json");
        checkTask.getFailuresOnly().set(false);
        checkTask.getOmitRedundancy().set(true);
        checkTask.getOutput().fileValue(output.toFile());
        checkTask.getJunit().set(false);
        checkTask.getJunitReport().fileValue(junitReport.toFile());

        assertEquals("json", checkTask.getFormat().get());
        assertTrue(checkTask.getAgent().get());
        assertFalse(checkTask.getFailuresOnly().get());
        assertTrue(checkTask.getOmitRedundancy().get());
        assertEquals(output.normalize(), checkTask.getOutput().get().getAsFile().toPath().normalize());
        assertFalse(checkTask.getJunit().get());
        assertEquals(junitReport.normalize(), checkTask.getJunitReport().get().getAsFile().toPath().normalize());
        assertFalse(checkTask.getJunitReportOutput().isPresent());
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
        task.getThreshold().set(8.0);
        task.getFormat().set("none");
        task.getAgent().set(false);
        task.getFailuresOnly().set(false);
        task.getOmitRedundancy().set(false);
        task.getJunit().set(true);
        Path junitReport = projectRoot.resolve("build/reports/crap-java/TEST-crap-java.xml");
        task.getJunitReport().fileValue(junitReport.toFile());

        task.runCheck();

        assertTrue(Files.exists(jacocoXml));
        assertTrue(Files.readString(junitReport).contains("<testsuites tests=\"1\" failures=\"0\" errors=\"0\" skipped=\"0\" time=\"0\">"));
    }

    @Test
    void rootModuleMatchesEverySourcePath() {
        assertTrue(CrapJavaCheckTask.matchesModulePath("app/src/main/java/demo/Sample.java", "."));
    }

    @Test
    void modulePathMatchesExactModuleRoot() {
        assertTrue(CrapJavaCheckTask.matchesModulePath("app", "app"));
    }

    @Test
    void modulePathMatchesNestedSourcePath() {
        assertTrue(CrapJavaCheckTask.matchesModulePath("app/src/main/java/demo/Sample.java", "app"));
    }

    @Test
    void modulePathDoesNotMatchPartialPathSegment() {
        assertFalse(CrapJavaCheckTask.matchesModulePath("application/src/main/java/demo/Sample.java", "app"));
    }
}

