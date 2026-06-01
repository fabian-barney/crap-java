package media.barney.crap.gradle;

import org.gradle.api.GradleException;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

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
        assertEquals(List.of(), extension.getExcludes().get());
        assertEquals(List.of(), extension.getExcludeClasses().get());
        assertEquals(List.of(), extension.getExcludeAnnotations().get());
        assertTrue(extension.getUseDefaultExclusions().get());
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
        assertEquals(List.of(), checkTask.getExcludes().get());
        assertEquals(List.of(), checkTask.getExcludeClasses().get());
        assertEquals(List.of(), checkTask.getExcludeAnnotations().get());
        assertTrue(checkTask.getUseDefaultExclusions().get());
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
        extension.getExcludes().set(List.of("module-a/**"));
        extension.getExcludeClasses().set(List.of(".*MapperImpl$"));
        extension.getExcludeAnnotations().set(List.of("Generated"));
        extension.getUseDefaultExclusions().set(false);

        CrapJavaCheckTask checkTask = (CrapJavaCheckTask) project.getTasks().getByName("crap-java-check");

        assertEquals("json", checkTask.getFormat().get());
        assertTrue(checkTask.getAgent().get());
        assertFalse(checkTask.getFailuresOnly().get());
        assertTrue(checkTask.getOmitRedundancy().get());
        assertEquals(output.normalize(), checkTask.getOutput().get().getAsFile().toPath().normalize());
        assertFalse(checkTask.getJunit().get());
        assertEquals(junitReport.normalize(), checkTask.getJunitReport().get().getAsFile().toPath().normalize());
        assertEquals(List.of("module-a/**"), checkTask.getExcludes().get());
        assertEquals(List.of(".*MapperImpl$"), checkTask.getExcludeClasses().get());
        assertEquals(List.of("Generated"), checkTask.getExcludeAnnotations().get());
        assertFalse(checkTask.getUseDefaultExclusions().get());
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
        assertEquals(List.of(), checkTask.getExcludes().get());
        assertEquals(List.of(), checkTask.getExcludeClasses().get());
        assertEquals(List.of(), checkTask.getExcludeAnnotations().get());
        assertTrue(checkTask.getUseDefaultExclusions().get());
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
        Files.createDirectories(parentOf(source));
        Files.writeString(source, """
                package demo;

                class Sample {
                    int alpha() {
                        return 1;
                    }
                }
                """);
        Path jacocoXml = projectRoot.resolve("build/reports/jacoco/test/jacocoTestReport.xml");
        Files.createDirectories(parentOf(jacocoXml));
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
        assertTrue(Files.readString(junitReport)
                .contains("<testsuites tests=\"1\" failures=\"0\" errors=\"0\" skipped=\"0\" time=\""));
    }

    @Test
    void runCheckAppliesConfiguredSourceExclusions() throws Exception {
        Path projectRoot = tempDir.toRealPath();
        Project project = ProjectBuilder.builder().withProjectDir(projectRoot.toFile()).build();
        Path source = projectRoot.resolve("src/main/java/demo/Sample.java");
        Files.createDirectories(parentOf(source));
        Files.writeString(source, """
                package demo;

                class Sample {
                    int alpha(boolean a) {
                        if (a) {
                            return 1;
                        }
                        return 0;
                    }
                }
                """);
        Path jacocoXml = projectRoot.resolve("build/reports/jacoco/test/jacocoTestReport.xml");
        Files.createDirectories(parentOf(jacocoXml));
        Files.writeString(jacocoXml, """
                <report name="demo">
                  <package name="demo">
                    <class name="demo/Sample" sourcefilename="Sample.java">
                      <method name="alpha" desc="(Z)I" line="4">
                        <counter type="INSTRUCTION" missed="4" covered="0"/>
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
        task.getThreshold().set(1.0);
        task.getFormat().set("none");
        task.getAgent().set(false);
        task.getFailuresOnly().set(false);
        task.getOmitRedundancy().set(false);
        task.getUseDefaultExclusions().set(false);
        task.getExcludes().set(List.of("src/main/java/demo/**"));
        Path junitReport = projectRoot.resolve("build/reports/crap-java/TEST-crap-java.xml");
        task.getJunitReport().fileValue(junitReport.toFile());

        task.runCheck();

        assertTrue(Files.readString(junitReport)
                .contains("<testsuites tests=\"0\" failures=\"0\" errors=\"0\" skipped=\"0\" time=\""));
    }

    @Test
    void movedJunitReportDoesNotDeleteUnownedDefaultSidecar() throws Exception {
        Path projectRoot = tempDir.toRealPath();
        Project project = ProjectBuilder.builder().withProjectDir(projectRoot.toFile()).build();
        Path defaultJunitReport = projectRoot.resolve("build/reports/crap-java/TEST-crap-java.xml");
        Path customJunitReport = projectRoot.resolve("custom-junit.xml");
        Files.createDirectories(parentOf(defaultJunitReport));
        Files.writeString(defaultJunitReport, "user-managed");
        CrapJavaCheckTask task = project.getTasks().register("crap-java-check", CrapJavaCheckTask.class).get();
        task.getAnalysisRoot().fileValue(projectRoot.toFile());
        task.getModuleCoverageReports().set(Map.of());
        task.getJunitReport().fileValue(customJunitReport.toFile());

        task.runCheck();

        assertTrue(Files.exists(customJunitReport));
        assertEquals("user-managed", Files.readString(defaultJunitReport));
    }

    @Test
    void disabledJunitDoesNotDeleteUnownedDefaultSidecar() throws Exception {
        Path projectRoot = tempDir.toRealPath();
        Project project = ProjectBuilder.builder().withProjectDir(projectRoot.toFile()).build();
        Path defaultJunitReport = projectRoot.resolve("build/reports/crap-java/TEST-crap-java.xml");
        Files.createDirectories(parentOf(defaultJunitReport));
        Files.writeString(defaultJunitReport, "user-managed");
        CrapJavaCheckTask task = project.getTasks().register("crap-java-check", CrapJavaCheckTask.class).get();
        task.getAnalysisRoot().fileValue(projectRoot.toFile());
        task.getModuleCoverageReports().set(Map.of());
        task.getJunit().set(false);

        task.runCheck();

        assertEquals("user-managed", Files.readString(defaultJunitReport));
    }

    @Test
    void movedJunitReportDeletesOwnedRememberedDefaultSidecar() throws Exception {
        Path projectRoot = tempDir.toRealPath();
        assumeHardLinksAvailable(projectRoot);
        Path defaultJunitReport = projectRoot.resolve("build/reports/crap-java/TEST-crap-java.xml");
        Path customJunitReport = projectRoot.resolve("custom-junit.xml");
        CrapJavaCheckTask firstTask = newCheckTask(projectRoot);
        firstTask.runCheck();
        assertTrue(Files.exists(defaultJunitReport));

        CrapJavaCheckTask secondTask = newCheckTask(projectRoot);
        secondTask.getJunitReport().fileValue(customJunitReport.toFile());

        secondTask.runCheck();

        assertTrue(Files.exists(customJunitReport));
        assertFalse(Files.exists(defaultJunitReport));
    }

    @Test
    void disabledJunitDeletesOwnedRememberedDefaultSidecar() throws Exception {
        Path projectRoot = tempDir.toRealPath();
        assumeHardLinksAvailable(projectRoot);
        Path defaultJunitReport = projectRoot.resolve("build/reports/crap-java/TEST-crap-java.xml");
        CrapJavaCheckTask firstTask = newCheckTask(projectRoot);
        firstTask.runCheck();
        assertTrue(Files.exists(defaultJunitReport));

        CrapJavaCheckTask secondTask = newCheckTask(projectRoot);
        secondTask.getJunit().set(false);

        secondTask.runCheck();

        assertFalse(Files.exists(defaultJunitReport));
    }

    @Test
    void disabledJunitDeletesOwnedRememberedExternalSidecar() throws Exception {
        Path projectRoot = tempDir.toRealPath();
        assumeHardLinksAvailable(projectRoot);
        Project project = ProjectBuilder.builder().withProjectDir(projectRoot.toFile()).build();
        Path junitReport = projectRoot.resolve("outside-junit.xml");
        CrapJavaCheckTask task = project.getTasks().register("crap-java-check", CrapJavaCheckTask.class).get();
        task.getAnalysisRoot().fileValue(projectRoot.toFile());
        task.getModuleCoverageReports().set(Map.of());
        task.getJunitReport().fileValue(junitReport.toFile());
        task.runCheck();
        assertTrue(Files.exists(junitReport));

        task.getJunit().set(false);
        task.runCheck();

        assertFalse(Files.exists(junitReport));
    }

    @Test
    void disabledJunitKeepsRecreatedRememberedExternalSidecar() throws Exception {
        Path projectRoot = tempDir.toRealPath();
        Project project = ProjectBuilder.builder().withProjectDir(projectRoot.toFile()).build();
        Path junitReport = projectRoot.resolve("outside-junit.xml");
        CrapJavaCheckTask task = project.getTasks().register("crap-java-check", CrapJavaCheckTask.class).get();
        task.getAnalysisRoot().fileValue(projectRoot.toFile());
        task.getModuleCoverageReports().set(Map.of());
        task.getJunitReport().fileValue(junitReport.toFile());
        task.runCheck();
        Files.writeString(junitReport, "unrelated");

        task.getJunit().set(false);
        task.runCheck();

        assertEquals("unrelated", Files.readString(junitReport));
    }

    @Test
    void disabledJunitKeepsRecreatedIdenticalRememberedExternalSidecar() throws Exception {
        Path projectRoot = tempDir.toRealPath();
        Project project = ProjectBuilder.builder().withProjectDir(projectRoot.toFile()).build();
        Path junitReport = projectRoot.resolve("outside-junit.xml");
        CrapJavaCheckTask task = project.getTasks().register("crap-java-check", CrapJavaCheckTask.class).get();
        task.getAnalysisRoot().fileValue(projectRoot.toFile());
        task.getModuleCoverageReports().set(Map.of());
        task.getJunitReport().fileValue(junitReport.toFile());
        task.runCheck();
        String originalReport = Files.readString(junitReport);
        FileTime originalModified = Files.getLastModifiedTime(junitReport);
        Files.delete(junitReport);
        Files.writeString(junitReport, originalReport);
        Files.setLastModifiedTime(junitReport, FileTime.fromMillis(originalModified.toMillis() + 5_000));

        task.getJunit().set(false);
        task.runCheck();

        assertEquals(originalReport, Files.readString(junitReport));
    }

    @Test
    void movedOutputDoesNotDeleteCurrentJunitReportAtPreviousOutputPath() throws Exception {
        Path projectRoot = tempDir.toRealPath();
        Project project = ProjectBuilder.builder().withProjectDir(projectRoot.toFile()).build();
        Path sharedReport = projectRoot.resolve("shared-report.xml");
        Files.writeString(sharedReport, "<testsuites tests=\"0\"/>");
        CrapJavaCheckTask task = project.getTasks().register("crap-java-check", CrapJavaCheckTask.class).get();
        task.getAnalysisRoot().fileValue(projectRoot.toFile());
        task.getModuleCoverageReports().set(Map.of());
        rememberOwnedReport(
                projectRoot.resolve(".gradle/crap-java/root/crap-java-check/primary-output.path"),
                sharedReport
        );

        invokeCleanupStaleReports(task, null, sharedReport);

        assertTrue(Files.exists(sharedReport));
    }

    @Test
    void failedJunitPublishRemembersWrittenPrimaryOutput() throws Exception {
        Path projectRoot = tempDir.toRealPath();
        assumeHardLinksAvailable(projectRoot);
        Project project = ProjectBuilder.builder().withProjectDir(projectRoot.toFile()).build();
        Path output = projectRoot.resolve("primary.json");
        Path junitParentFile = projectRoot.resolve("junit-parent-file");
        Path badJunitReport = junitParentFile.resolve("TEST-crap-java.xml");
        Files.writeString(junitParentFile, "not a directory");
        CrapJavaCheckTask task = project.getTasks().register("crap-java-check", CrapJavaCheckTask.class).get();
        task.getAnalysisRoot().fileValue(projectRoot.toFile());
        task.getModuleCoverageReports().set(Map.of());
        task.getFormat().set("json");
        task.getOutput().fileValue(output.toFile());
        task.getJunitReport().fileValue(badJunitReport.toFile());

        assertThrows(Exception.class, task::runCheck);

        assertTrue(Files.exists(output));
        assertTrue(Files.exists(projectRoot.resolve(".gradle/crap-java/root/crap-java-check/primary-output.path")));
    }

    @Test
    void invalidReportPathDoesNotDeleteRememberedJunitSidecar() throws Exception {
        Path projectRoot = tempDir.toRealPath();
        Project project = ProjectBuilder.builder().withProjectDir(projectRoot.toFile()).build();
        Path junitReport = projectRoot.resolve("outside-junit.xml");
        CrapJavaCheckTask task = project.getTasks().register("crap-java-check", CrapJavaCheckTask.class).get();
        task.getAnalysisRoot().fileValue(projectRoot.toFile());
        task.getModuleCoverageReports().set(Map.of());
        task.getJunitReport().fileValue(junitReport.toFile());
        task.runCheck();
        assertTrue(Files.exists(junitReport));

        task.getJunit().set(false);
        task.getOutput().fileValue(projectRoot.resolve(".gradle/crap-java/root/other-task/primary-output.path").toFile());

        GradleException exception = assertThrows(GradleException.class, task::runCheck);

        assertTrue(exception.getMessage().contains("output must not point to a crap-java internal task file"));
        assertTrue(Files.exists(junitReport));
    }

    @Test
    void invalidFormatDoesNotDeleteRememberedOutput() throws Exception {
        Path projectRoot = tempDir.toRealPath();
        Path output = projectRoot.resolve("outside-report.json");
        CrapJavaCheckTask firstTask = newCheckTask(projectRoot);
        firstTask.getFormat().set("json");
        firstTask.getOutput().fileValue(output.toFile());
        firstTask.runCheck();
        assertTrue(Files.exists(output));

        CrapJavaCheckTask secondTask = newCheckTask(projectRoot);
        secondTask.getFormat().set("invalid");

        GradleException exception = assertThrows(GradleException.class, secondTask::runCheck);

        assertTrue(exception.getMessage().contains("Unknown report format: invalid"));
        assertTrue(Files.exists(output));
    }

    @Test
    void invalidThresholdDoesNotDeleteRememberedOutput() throws Exception {
        Path projectRoot = tempDir.toRealPath();
        Path output = projectRoot.resolve("outside-report.json");
        CrapJavaCheckTask firstTask = newCheckTask(projectRoot);
        firstTask.getFormat().set("json");
        firstTask.getOutput().fileValue(output.toFile());
        firstTask.runCheck();
        assertTrue(Files.exists(output));

        CrapJavaCheckTask secondTask = newCheckTask(projectRoot);
        secondTask.getThreshold().set(0.0);

        GradleException exception = assertThrows(GradleException.class, secondTask::runCheck);

        assertTrue(exception.getMessage().contains("Threshold must be a finite number greater than 0"));
        assertTrue(Files.exists(output));
    }

    @Test
    void failedReplacementOutputDoesNotDeleteRememberedOutput() throws Exception {
        Path projectRoot = tempDir.toRealPath();
        Path oldOutput = projectRoot.resolve("outside-report.json");
        Path replacementOutput = projectRoot.resolve("replacement-report.json");
        CrapJavaCheckTask firstTask = newCheckTask(projectRoot);
        firstTask.getFormat().set("json");
        firstTask.getOutput().fileValue(oldOutput.toFile());
        firstTask.runCheck();
        assertTrue(Files.exists(oldOutput));
        Files.createDirectories(replacementOutput);

        CrapJavaCheckTask secondTask = newCheckTask(projectRoot);
        secondTask.getFormat().set("json");
        secondTask.getOutput().fileValue(replacementOutput.toFile());

        assertThrows(Exception.class, secondTask::runCheck);

        assertTrue(Files.exists(oldOutput));
    }

    @Test
    void failedMovedJunitReplacementDoesNotForgetRememberedOutput() throws Exception {
        Path projectRoot = tempDir.toRealPath();
        assumeHardLinksAvailable(projectRoot);
        Path oldOutput = projectRoot.resolve("outside-report.json");
        Path replacementOutput = projectRoot.resolve("replacement-report.json");
        Path junitParentFile = projectRoot.resolve("junit-parent-file");
        Path badJunitReport = junitParentFile.resolve("TEST-crap-java.xml");
        CrapJavaCheckTask firstTask = newCheckTask(projectRoot);
        firstTask.getFormat().set("json");
        firstTask.getOutput().fileValue(oldOutput.toFile());
        firstTask.runCheck();
        assertTrue(Files.exists(oldOutput));
        Files.writeString(junitParentFile, "not a directory");

        CrapJavaCheckTask secondTask = newCheckTask(projectRoot);
        secondTask.getFormat().set("json");
        secondTask.getOutput().fileValue(replacementOutput.toFile());
        secondTask.getJunitReport().fileValue(badJunitReport.toFile());

        assertThrows(Exception.class, secondTask::runCheck);
        assertTrue(Files.exists(oldOutput));
        assertFalse(Files.exists(replacementOutput));

        CrapJavaCheckTask thirdTask = newCheckTask(projectRoot);
        thirdTask.getFormat().set("json");
        thirdTask.getOutput().fileValue(replacementOutput.toFile());

        thirdTask.runCheck();

        assertFalse(Files.exists(oldOutput));
        assertTrue(Files.exists(replacementOutput));
    }

    @Test
    void movedOutputDoesNotDeleteReportStillOwnedByAnotherTask() throws Exception {
        Path projectRoot = tempDir.toRealPath();
        assumeHardLinksAvailable(projectRoot);
        Path sharedOutput = projectRoot.resolve("shared-report.json");
        Path movedOutput = projectRoot.resolve("moved-report.json");
        CrapJavaCheckTask firstTask = newCheckTask(projectRoot, "first-crap-java-check");
        firstTask.getFormat().set("json");
        firstTask.getOutput().fileValue(sharedOutput.toFile());
        firstTask.runCheck();
        CrapJavaCheckTask secondTask = newCheckTask(projectRoot, "second-crap-java-check");
        secondTask.getFormat().set("json");
        secondTask.getOutput().fileValue(sharedOutput.toFile());
        secondTask.runCheck();

        CrapJavaCheckTask movedFirstTask = newCheckTask(projectRoot, "first-crap-java-check");
        movedFirstTask.getFormat().set("json");
        movedFirstTask.getOutput().fileValue(movedOutput.toFile());

        movedFirstTask.runCheck();

        assertTrue(Files.exists(sharedOutput));
        assertTrue(Files.exists(movedOutput));
    }

    @Test
    void aliasedFutureReportPathCollisionDoesNotDeleteRememberedOutput() throws Exception {
        Path projectRoot = tempDir.toRealPath();
        Path output = projectRoot.resolve("outside-report.json");
        CrapJavaCheckTask firstTask = newCheckTask(projectRoot);
        firstTask.getFormat().set("json");
        firstTask.getOutput().fileValue(output.toFile());
        firstTask.runCheck();
        assertTrue(Files.exists(output));

        Path reportDirectory = projectRoot.resolve("reports");
        Files.createDirectories(reportDirectory);
        Path reportAlias = createDirectorySymlinkOrSkip(projectRoot.resolve("reports-alias"), reportDirectory);
        CrapJavaCheckTask secondTask = newCheckTask(projectRoot);
        secondTask.getOutput().fileValue(reportDirectory.resolve("collision.xml").toFile());
        secondTask.getJunitReport().fileValue(reportAlias.resolve("collision.xml").toFile());

        GradleException exception = assertThrows(GradleException.class, secondTask::runCheck);

        assertTrue(exception.getMessage().contains("output and junitReport must not point to the same file"));
        assertTrue(Files.exists(output));
    }

    @Test
    void runCheckRejectsDanglingSymlinkedPrimaryAndJunitReportPath() throws Exception {
        Path projectRoot = tempDir.toRealPath();
        Path report = projectRoot.resolve("reports/collision.xml");
        Files.createDirectories(parentOf(report));
        Path outputLink = createFileSymlinkOrSkip(projectRoot.resolve("output-report.xml"), report);
        Path junitLink = createFileSymlinkOrSkip(projectRoot.resolve("junit-report.xml"), report);
        CrapJavaCheckTask task = newCheckTask(projectRoot);
        task.getOutput().fileValue(outputLink.toFile());
        task.getJunitReport().fileValue(junitLink.toFile());

        GradleException exception = assertThrows(GradleException.class, task::runCheck);

        assertTrue(exception.getMessage().contains("output and junitReport must not point to the same file"));
    }

    @Test
    void runCheckRejectsOtherTaskInternalStatePath() throws Exception {
        Path projectRoot = tempDir.toRealPath();
        Project project = ProjectBuilder.builder().withProjectDir(projectRoot.toFile()).build();
        CrapJavaCheckTask task = project.getTasks().register("crap-java-check", CrapJavaCheckTask.class).get();
        task.getAnalysisRoot().fileValue(projectRoot.toFile());
        task.getModuleCoverageReports().set(Map.of());
        task.getOutput().fileValue(projectRoot.resolve(".gradle/crap-java/root/other-task/primary-output.path").toFile());

        GradleException exception = assertThrows(GradleException.class, task::runCheck);

        assertTrue(exception.getMessage().contains("output must not point to a crap-java internal task file"));
    }

    @Test
    void runCheckRejectsOtherTaskInternalJunitReportPath() throws Exception {
        Path projectRoot = tempDir.toRealPath();
        Project project = ProjectBuilder.builder().withProjectDir(projectRoot.toFile()).build();
        CrapJavaCheckTask task = project.getTasks().register("crap-java-check", CrapJavaCheckTask.class).get();
        task.getAnalysisRoot().fileValue(projectRoot.toFile());
        task.getModuleCoverageReports().set(Map.of());
        task.getJunitReport().fileValue(projectRoot.resolve(".gradle/crap-java/root/other-task/junit-report.path").toFile());

        GradleException exception = assertThrows(GradleException.class, task::runCheck);

        assertTrue(exception.getMessage().contains("junitReport must not point to a crap-java internal task file"));
    }

    @Test
    void runCheckRejectsOtherTaskInternalOwnerPath() throws Exception {
        Path projectRoot = tempDir.toRealPath();
        Project project = ProjectBuilder.builder().withProjectDir(projectRoot.toFile()).build();
        CrapJavaCheckTask task = project.getTasks().register("crap-java-check", CrapJavaCheckTask.class).get();
        task.getAnalysisRoot().fileValue(projectRoot.toFile());
        task.getModuleCoverageReports().set(Map.of());
        task.getOutput().fileValue(projectRoot.resolve(".gradle/crap-java/root/other-task/primary-output.owner").toFile());

        GradleException exception = assertThrows(GradleException.class, task::runCheck);

        assertTrue(exception.getMessage().contains("output must not point to a crap-java internal task file"));
    }

    @Test
    void runCheckRejectsOtherTaskInternalLockPath() throws Exception {
        Path projectRoot = tempDir.toRealPath();
        Project project = ProjectBuilder.builder().withProjectDir(projectRoot.toFile()).build();
        CrapJavaCheckTask task = project.getTasks().register("crap-java-check", CrapJavaCheckTask.class).get();
        task.getAnalysisRoot().fileValue(projectRoot.toFile());
        task.getModuleCoverageReports().set(Map.of());
        task.getOutput().fileValue(projectRoot.resolve(".gradle/crap-java/root/other-task/state.lock").toFile());

        GradleException exception = assertThrows(GradleException.class, task::runCheck);

        assertTrue(exception.getMessage().contains("output must not point to a crap-java internal task file"));
    }

    @Test
    void runCheckRejectsRootReportPath() throws Exception {
        Path projectRoot = tempDir.toRealPath();
        Project project = ProjectBuilder.builder().withProjectDir(projectRoot.toFile()).build();
        Path root = rootOf(projectRoot);
        CrapJavaCheckTask task = project.getTasks().register("crap-java-check", CrapJavaCheckTask.class).get();
        task.getAnalysisRoot().fileValue(projectRoot.toFile());
        task.getModuleCoverageReports().set(Map.of());
        task.getOutput().fileValue(root.toFile());

        GradleException exception = assertThrows(GradleException.class, task::runCheck);

        assertTrue(exception.getMessage().contains("output must not point to a filesystem root"));
    }

    @Test
    void runCheckRejectsDirectoryPrimaryReportPath() throws Exception {
        Path projectRoot = tempDir.toRealPath();
        Path outputDirectory = projectRoot.resolve("reports/output.json");
        Files.createDirectories(outputDirectory);
        CrapJavaCheckTask task = newCheckTask(projectRoot);
        task.getOutput().fileValue(outputDirectory.toFile());

        GradleException exception = assertThrows(GradleException.class, task::runCheck);

        assertTrue(exception.getMessage().contains("output must not point to a directory"));
    }

    @Test
    void runCheckRejectsDirectoryJunitReportPath() throws Exception {
        Path projectRoot = tempDir.toRealPath();
        Path junitDirectory = projectRoot.resolve("reports/TEST-crap-java.xml");
        Files.createDirectories(junitDirectory);
        CrapJavaCheckTask task = newCheckTask(projectRoot);
        task.getJunitReport().fileValue(junitDirectory.toFile());

        GradleException exception = assertThrows(GradleException.class, task::runCheck);

        assertTrue(exception.getMessage().contains("junitReport must not point to a directory"));
    }

    @Test
    void runCheckRejectsInternalExecutionMarkerRootPath() throws Exception {
        Path projectRoot = tempDir.toRealPath();
        Path markerPath = projectRoot.resolve("build/tmp/crap-java/crap-java-check/report.xml");
        Project project = ProjectBuilder.builder().withProjectDir(projectRoot.toFile()).build();
        CrapJavaCheckTask task = project.getTasks().register("crap-java-check", CrapJavaCheckTask.class).get();
        task.getAnalysisRoot().fileValue(projectRoot.toFile());
        task.getModuleCoverageReports().set(Map.of());
        task.getOutput().fileValue(markerPath.toFile());

        GradleException exception = assertThrows(GradleException.class, task::runCheck);

        assertTrue(exception.getMessage().contains("output must not point to a crap-java internal task file"));
    }

    @Test
    void runCheckRejectsInternalStateRootPath() throws Exception {
        Path projectRoot = tempDir.toRealPath();
        Path statePath = projectRoot.resolve(".gradle/crap-java/root/other-task/report.xml");
        Project project = ProjectBuilder.builder().withProjectDir(projectRoot.toFile()).build();
        CrapJavaCheckTask task = project.getTasks().register("crap-java-check", CrapJavaCheckTask.class).get();
        task.getAnalysisRoot().fileValue(projectRoot.toFile());
        task.getModuleCoverageReports().set(Map.of());
        task.getOutput().fileValue(statePath.toFile());

        GradleException exception = assertThrows(GradleException.class, task::runCheck);

        assertTrue(exception.getMessage().contains("output must not point to a crap-java internal task file"));
    }

    @Test
    void runCheckRejectsSymlinkedInternalStatePath() throws Exception {
        Path projectRoot = tempDir.toRealPath();
        Path stateRoot = projectRoot.resolve(".gradle/crap-java/root/other-task");
        Files.createDirectories(stateRoot);
        Path stateAlias = createDirectorySymlinkOrSkip(projectRoot.resolve("state-alias"), stateRoot);
        Project project = ProjectBuilder.builder().withProjectDir(projectRoot.toFile()).build();
        CrapJavaCheckTask task = project.getTasks().register("crap-java-check", CrapJavaCheckTask.class).get();
        task.getAnalysisRoot().fileValue(projectRoot.toFile());
        task.getModuleCoverageReports().set(Map.of());
        task.getOutput().fileValue(stateAlias.resolve("primary-output.path").toFile());

        GradleException exception = assertThrows(GradleException.class, task::runCheck);

        assertTrue(exception.getMessage().contains("output must not point to a crap-java internal task file"));
    }

    @Test
    void runCheckRejectsSymlinkedInternalExecutionMarkerPath() throws Exception {
        Path projectRoot = tempDir.toRealPath();
        Path markerRoot = projectRoot.resolve("build/tmp/crap-java");
        Files.createDirectories(markerRoot.resolve("crap-java-check"));
        Path markerAlias = createDirectorySymlinkOrSkip(projectRoot.resolve("marker-alias"), markerRoot);
        Project project = ProjectBuilder.builder().withProjectDir(projectRoot.toFile()).build();
        CrapJavaCheckTask task = project.getTasks().register("crap-java-check", CrapJavaCheckTask.class).get();
        task.getAnalysisRoot().fileValue(projectRoot.toFile());
        task.getModuleCoverageReports().set(Map.of());
        task.getOutput().fileValue(markerAlias.resolve("crap-java-check/execution.marker").toFile());

        GradleException exception = assertThrows(GradleException.class, task::runCheck);

        assertTrue(exception.getMessage().contains("output must not point to a crap-java internal task file"));
    }

    @Test
    void runCheckRejectsFileSymlinkAliasToInternalStatePath() throws Exception {
        Path projectRoot = tempDir.toRealPath();
        Path statePath = projectRoot.resolve(".gradle/crap-java/root/other-task/primary-output.path");
        Files.createDirectories(parentOf(statePath));
        Files.writeString(statePath, "state");
        Path stateAlias = createFileSymlinkOrSkip(projectRoot.resolve("state-report.xml"), statePath);
        Project project = ProjectBuilder.builder().withProjectDir(projectRoot.toFile()).build();
        CrapJavaCheckTask task = project.getTasks().register("crap-java-check", CrapJavaCheckTask.class).get();
        task.getAnalysisRoot().fileValue(projectRoot.toFile());
        task.getModuleCoverageReports().set(Map.of());
        task.getOutput().fileValue(stateAlias.toFile());

        GradleException exception = assertThrows(GradleException.class, task::runCheck);

        assertTrue(exception.getMessage().contains("output must not point to a crap-java internal task file"));
    }

    @Test
    void runCheckRejectsDanglingFileSymlinkAliasToInternalStatePath() throws Exception {
        Path projectRoot = tempDir.toRealPath();
        Path statePath = projectRoot.resolve(".gradle/crap-java/root/other-task/primary-output.path");
        Files.createDirectories(parentOf(statePath));
        Path stateAlias = createFileSymlinkOrSkip(projectRoot.resolve("state-report.xml"), statePath);
        CrapJavaCheckTask task = newCheckTask(projectRoot);
        task.getOutput().fileValue(stateAlias.toFile());

        GradleException exception = assertThrows(GradleException.class, task::runCheck);

        assertTrue(exception.getMessage().contains("output must not point to a crap-java internal task file"));
    }

    @Test
    void runCheckRejectsFileSymlinkAliasToInternalExecutionMarkerPath() throws Exception {
        Path projectRoot = tempDir.toRealPath();
        Path markerPath = projectRoot.resolve("build/tmp/crap-java/crap-java-check/execution.marker");
        Files.createDirectories(parentOf(markerPath));
        Files.writeString(markerPath, "ok");
        Path markerAlias = createFileSymlinkOrSkip(projectRoot.resolve("marker-report.xml"), markerPath);
        Project project = ProjectBuilder.builder().withProjectDir(projectRoot.toFile()).build();
        CrapJavaCheckTask task = project.getTasks().register("crap-java-check", CrapJavaCheckTask.class).get();
        task.getAnalysisRoot().fileValue(projectRoot.toFile());
        task.getModuleCoverageReports().set(Map.of());
        task.getOutput().fileValue(markerAlias.toFile());

        GradleException exception = assertThrows(GradleException.class, task::runCheck);

        assertTrue(exception.getMessage().contains("output must not point to a crap-java internal task file"));
    }

    @Test
    void runCheckRejectsDanglingFileSymlinkAliasToInternalExecutionMarkerPath() throws Exception {
        Path projectRoot = tempDir.toRealPath();
        Path markerPath = projectRoot.resolve("build/tmp/crap-java/crap-java-check/execution.marker");
        Files.createDirectories(parentOf(markerPath));
        Path markerAlias = createFileSymlinkOrSkip(projectRoot.resolve("marker-report.xml"), markerPath);
        CrapJavaCheckTask task = newCheckTask(projectRoot);
        task.getOutput().fileValue(markerAlias.toFile());

        GradleException exception = assertThrows(GradleException.class, task::runCheck);

        assertTrue(exception.getMessage().contains("output must not point to a crap-java internal task file"));
    }

    @Test
    void runCheckRejectsHardLinkAliasToInternalStatePath() throws Exception {
        Path projectRoot = tempDir.toRealPath();
        Path statePath = projectRoot.resolve(".gradle/crap-java/root/other-task/primary-output.path");
        Files.createDirectories(parentOf(statePath));
        Files.writeString(statePath, "state");
        Path stateAlias = createHardLinkOrSkip(projectRoot.resolve("state-report.xml"), statePath);
        Project project = ProjectBuilder.builder().withProjectDir(projectRoot.toFile()).build();
        CrapJavaCheckTask task = project.getTasks().register("crap-java-check", CrapJavaCheckTask.class).get();
        task.getAnalysisRoot().fileValue(projectRoot.toFile());
        task.getModuleCoverageReports().set(Map.of());
        task.getOutput().fileValue(stateAlias.toFile());

        GradleException exception = assertThrows(GradleException.class, task::runCheck);

        assertTrue(exception.getMessage().contains("output must not point to a crap-java internal task file"));
    }

    @Test
    void runCheckRejectsSubprojectInternalExecutionMarkerPath() throws Exception {
        Path projectRoot = tempDir.toRealPath();
        Files.createDirectories(projectRoot.resolve("sub"));
        Project root = ProjectBuilder.builder().withProjectDir(projectRoot.toFile()).build();
        Project subproject = ProjectBuilder.builder()
                .withName("sub")
                .withParent(root)
                .withProjectDir(projectRoot.resolve("sub").toFile())
                .build();
        CrapJavaCheckTask task = root.getTasks().register("crap-java-check", CrapJavaCheckTask.class).get();
        task.getAnalysisRoot().fileValue(projectRoot.toFile());
        task.getModuleCoverageReports().set(Map.of());
        task.getOutput().fileValue(subproject.getLayout().getBuildDirectory().get().getAsFile().toPath()
                .resolve("tmp/crap-java/crap-java-check/execution.marker")
                .toFile());

        GradleException exception = assertThrows(GradleException.class, task::runCheck);

        assertTrue(exception.getMessage().contains("output must not point to a crap-java internal task file"));
    }

    @Test
    void runCheckRejectsSubprojectInternalStatePathInRootCache() throws Exception {
        Path projectRoot = tempDir.toRealPath();
        Files.createDirectories(projectRoot.resolve("sub"));
        Project root = ProjectBuilder.builder().withProjectDir(projectRoot.toFile()).build();
        ProjectBuilder.builder()
                .withName("sub")
                .withParent(root)
                .withProjectDir(projectRoot.resolve("sub").toFile())
                .build();
        CrapJavaCheckTask task = root.getTasks().register("crap-java-check", CrapJavaCheckTask.class).get();
        task.getAnalysisRoot().fileValue(projectRoot.toFile());
        task.getModuleCoverageReports().set(Map.of());
        task.getOutput().fileValue(projectRoot.resolve(".gradle/crap-java/sub/crap-java-check/state.lock").toFile());

        GradleException exception = assertThrows(GradleException.class, task::runCheck);

        assertTrue(exception.getMessage().contains("output must not point to a crap-java internal task file"));
    }

    @Test
    void runCheckAllowsNormalReportPathWithInternalFileName() throws Exception {
        Path projectRoot = tempDir.toRealPath();
        Project project = ProjectBuilder.builder().withProjectDir(projectRoot.toFile()).build();
        Path output = projectRoot.resolve("reports/crap-java/primary-output.path");
        CrapJavaCheckTask task = project.getTasks().register("crap-java-check", CrapJavaCheckTask.class).get();
        task.getAnalysisRoot().fileValue(projectRoot.toFile());
        task.getModuleCoverageReports().set(Map.of());
        task.getFormat().set("json");
        task.getOutput().fileValue(output.toFile());

        task.runCheck();

        assertTrue(Files.exists(output));
    }

    @Test
    void rememberedStateUsesGradleProjectCacheDir() throws Exception {
        Path projectRoot = tempDir.toRealPath();
        assumeHardLinksAvailable(projectRoot);
        Path projectCacheDir = projectRoot.resolve("custom-project-cache");
        Project project = ProjectBuilder.builder().withProjectDir(projectRoot.toFile()).build();
        project.getGradle().getStartParameter().setProjectCacheDir(projectCacheDir.toFile());
        CrapJavaCheckTask task = project.getTasks().register("crap-java-check", CrapJavaCheckTask.class).get();
        task.getAnalysisRoot().fileValue(projectRoot.toFile());
        task.getModuleCoverageReports().set(Map.of());

        task.runCheck();

        Path statePath = junitReportStatePath(task);
        assertTrue(Files.exists(statePath));
        assertTrue(statePath.startsWith(projectCacheDir.resolve("crap-java")));
        assertTrue(fileNameOf(parentOf(parentOf(parentOf(statePath)))).toString().startsWith("workspace-"));
        assertFalse(Files.exists(projectRoot.resolve(".gradle/crap-java/root/crap-java-check/junit-report.path")));
    }

    @Test
    void rememberedStateNamespacesSharedCustomProjectCacheByRootProject() throws Exception {
        Path projectRoot = tempDir.toRealPath();
        Path sharedProjectCacheDir = projectRoot.resolve("shared-project-cache");
        Path firstRoot = projectRoot.resolve("first");
        Path secondRoot = projectRoot.resolve("second");
        Files.createDirectories(firstRoot);
        Files.createDirectories(secondRoot);
        assumeHardLinksAvailable(projectRoot);
        CrapJavaCheckTask firstTask = newCheckTask(firstRoot, "crap-java-check", sharedProjectCacheDir);
        CrapJavaCheckTask secondTask = newCheckTask(secondRoot, "crap-java-check", sharedProjectCacheDir);

        firstTask.runCheck();
        secondTask.runCheck();

        Path firstStatePath = junitReportStatePath(firstTask);
        Path secondStatePath = junitReportStatePath(secondTask);
        assertTrue(Files.exists(firstStatePath));
        assertTrue(Files.exists(secondStatePath));
        assertFalse(firstStatePath.equals(secondStatePath));
        assertFalse(parentOf(parentOf(parentOf(firstStatePath)))
                .equals(parentOf(parentOf(parentOf(secondStatePath)))));
    }

    @Test
    void reportStateLockIsSharedAcrossTasksInProjectCache() throws Exception {
        Path projectRoot = tempDir.toRealPath();
        CrapJavaCheckTask firstTask = newCheckTask(projectRoot, "first-crap-java-check");
        CrapJavaCheckTask secondTask = newCheckTask(projectRoot, "second-crap-java-check");
        Path expectedLockPath = projectRoot.resolve(".gradle/crap-java/state.lock").toAbsolutePath().normalize();

        assertEquals(expectedLockPath, stateLockPath(firstTask));
        assertEquals(expectedLockPath, stateLockPath(secondTask));
    }

    @Test
    void rememberedStateFallsBackToRootProjectGradleDirForSubprojects() throws Exception {
        Path projectRoot = tempDir.toRealPath();
        assumeHardLinksAvailable(projectRoot);
        Path subprojectRoot = projectRoot.resolve("sub");
        Files.createDirectories(subprojectRoot);
        Project root = ProjectBuilder.builder().withProjectDir(projectRoot.toFile()).build();
        Project subproject = ProjectBuilder.builder()
                .withName("sub")
                .withParent(root)
                .withProjectDir(subprojectRoot.toFile())
                .build();
        CrapJavaCheckTask task = subproject.getTasks().register("crap-java-check", CrapJavaCheckTask.class).get();
        task.getAnalysisRoot().fileValue(projectRoot.toFile());
        task.getModuleCoverageReports().set(Map.of());

        task.runCheck();

        assertTrue(Files.exists(projectRoot.resolve(".gradle/crap-java/%3Asub/crap-java-check/junit-report.path")));
        assertFalse(Files.exists(subprojectRoot.resolve(".gradle/crap-java/%3Asub/crap-java-check/junit-report.path")));
    }

    @Test
    void rememberedStateEscapesProjectPathSeparators() throws Exception {
        Path projectRoot = tempDir.toRealPath();
        assumeHardLinksAvailable(projectRoot);
        Path projectCacheDir = projectRoot.resolve("custom-project-cache");
        Project root = ProjectBuilder.builder().withProjectDir(projectRoot.toFile()).build();
        root.getGradle().getStartParameter().setProjectCacheDir(projectCacheDir.toFile());
        Files.createDirectories(projectRoot.resolve("a-b"));
        Files.createDirectories(projectRoot.resolve("a/b"));
        Files.createDirectories(projectRoot.resolve("root"));
        Project dashProject = ProjectBuilder.builder()
                .withName("a-b")
                .withParent(root)
                .withProjectDir(projectRoot.resolve("a-b").toFile())
                .build();
        Project nestedParent = ProjectBuilder.builder()
                .withName("a")
                .withParent(root)
                .withProjectDir(projectRoot.resolve("a").toFile())
                .build();
        Project nestedProject = ProjectBuilder.builder()
                .withName("b")
                .withParent(nestedParent)
                .withProjectDir(projectRoot.resolve("a/b").toFile())
                .build();
        Project rootNamedProject = ProjectBuilder.builder()
                .withName("root")
                .withParent(root)
                .withProjectDir(projectRoot.resolve("root").toFile())
                .build();
        CrapJavaCheckTask dashTask = dashProject.getTasks().register("crap-java-check", CrapJavaCheckTask.class).get();
        CrapJavaCheckTask nestedTask = nestedProject.getTasks().register("crap-java-check", CrapJavaCheckTask.class).get();
        CrapJavaCheckTask rootNamedTask = rootNamedProject.getTasks()
                .register("crap-java-check", CrapJavaCheckTask.class)
                .get();
        dashTask.getAnalysisRoot().fileValue(projectRoot.toFile());
        dashTask.getModuleCoverageReports().set(Map.of());
        nestedTask.getAnalysisRoot().fileValue(projectRoot.toFile());
        nestedTask.getModuleCoverageReports().set(Map.of());
        rootNamedTask.getAnalysisRoot().fileValue(projectRoot.toFile());
        rootNamedTask.getModuleCoverageReports().set(Map.of());

        dashTask.runCheck();
        nestedTask.runCheck();
        rootNamedTask.runCheck();

        Path stateNamespace = parentOf(parentOf(parentOf(junitReportStatePath(dashTask))));
        assertTrue(stateNamespace.startsWith(projectCacheDir.resolve("crap-java")));
        assertTrue(fileNameOf(stateNamespace).toString().startsWith("workspace-"));
        assertTrue(Files.exists(stateNamespace.resolve("%3Aa-b/crap-java-check/junit-report.path")));
        assertTrue(Files.exists(stateNamespace.resolve("%3Aa%3Ab/crap-java-check/junit-report.path")));
        assertTrue(Files.exists(stateNamespace.resolve("%3Aroot/crap-java-check/junit-report.path")));
        assertFalse(Files.exists(stateNamespace.resolve("root/crap-java-check/junit-report.path")));
    }

    @Test
    void rememberedStatePreservesTrailingSpacesInStoredPath() throws Exception {
        assumeTrue(!isWindows(), "Windows does not support filenames ending with spaces");
        Path projectRoot = tempDir.toRealPath();
        Path statePath = projectRoot.resolve(".gradle/crap-java/root/crap-java-check/primary-output.path");
        Path storedPath = projectRoot.resolve("report.json ");
        Files.createDirectories(parentOf(statePath));
        Files.writeString(statePath, storedPath + "\nlink\t1\t2\n");
        CrapJavaCheckTask task = newCheckTask(projectRoot);

        Path rememberedPath = rememberedReportPath(task, statePath);

        assertEquals(storedPath.toAbsolutePath().normalize(), rememberedPath);
    }

    @Test
    void rememberedStatePreservesNewlinesInStoredPath() throws Exception {
        assumeTrue(!isWindows(), "Windows does not support filenames containing newlines");
        Path projectRoot = tempDir.toRealPath();
        Path output = projectRoot.resolve("report\nname.json");
        Path statePath = projectRoot.resolve(".gradle/crap-java/root/crap-java-check/primary-output.path");
        CrapJavaCheckTask task = newCheckTask(projectRoot);
        task.getFormat().set("json");
        task.getOutput().fileValue(output.toFile());

        task.runCheck();

        List<String> lines = Files.readAllLines(statePath);
        assertEquals(2, lines.size());
        assertTrue(lines.get(0).startsWith("path-base64\t"));
        assertEquals(output.toAbsolutePath().normalize(), rememberedReportPath(task, statePath));
    }

    @Test
    void rememberedStateIgnoresMalformedStoredPath() throws Exception {
        Path projectRoot = tempDir.toRealPath();
        Path statePath = projectRoot.resolve(".gradle/crap-java/root/crap-java-check/primary-output.path");
        Files.createDirectories(parentOf(statePath));
        Files.writeString(statePath, "\u0000\nlink\t1\t2\n");
        CrapJavaCheckTask task = newCheckTask(projectRoot);

        assertNull(rememberedReport(task, statePath));
    }

    @Test
    void disabledCustomTaskDoesNotDeleteUnownedDefaultSidecars() throws Exception {
        Path projectRoot = tempDir.toRealPath();
        Project project = ProjectBuilder.builder().withProjectDir(projectRoot.toFile()).build();
        Path builtInJunit = projectRoot.resolve("build/reports/crap-java/TEST-crap-java.xml");
        Path customJunit = projectRoot.resolve("build/reports/crap-java/custom-crap-java-check/TEST-crap-java.xml");
        Files.createDirectories(parentOf(builtInJunit));
        Files.createDirectories(parentOf(customJunit));
        Files.writeString(builtInJunit, "<testsuites tests=\"1\"/>");
        Files.writeString(customJunit, "<testsuites tests=\"2\"/>");

        CrapJavaCheckTask task = project.getTasks().register("custom-crap-java-check", CrapJavaCheckTask.class).get();
        task.getAnalysisRoot().fileValue(projectRoot.toFile());
        task.getModuleCoverageReports().set(Map.of());
        task.getJunit().set(false);

        task.runCheck();

        assertTrue(Files.exists(builtInJunit));
        assertEquals("<testsuites tests=\"2\"/>", Files.readString(customJunit));
    }

    @Test
    void disabledCustomTaskOnlyDeletesOwnedDefaultSidecar() throws Exception {
        Path projectRoot = tempDir.toRealPath();
        assumeHardLinksAvailable(projectRoot);
        Project project = ProjectBuilder.builder().withProjectDir(projectRoot.toFile()).build();
        Path builtInJunit = projectRoot.resolve("build/reports/crap-java/TEST-crap-java.xml");
        Path customJunit = projectRoot.resolve("build/reports/crap-java/custom-crap-java-check/TEST-crap-java.xml");
        Files.createDirectories(parentOf(builtInJunit));
        Files.writeString(builtInJunit, "<testsuites tests=\"1\"/>");
        CrapJavaCheckTask firstTask = project.getTasks().register("custom-crap-java-check", CrapJavaCheckTask.class).get();
        firstTask.getAnalysisRoot().fileValue(projectRoot.toFile());
        firstTask.getModuleCoverageReports().set(Map.of());
        firstTask.runCheck();
        assertTrue(Files.exists(customJunit));

        Project secondProject = ProjectBuilder.builder().withProjectDir(projectRoot.toFile()).build();
        CrapJavaCheckTask secondTask = secondProject.getTasks()
                .register("custom-crap-java-check", CrapJavaCheckTask.class)
                .get();
        secondTask.getAnalysisRoot().fileValue(projectRoot.toFile());
        secondTask.getModuleCoverageReports().set(Map.of());
        secondTask.getJunit().set(false);

        secondTask.runCheck();

        assertTrue(Files.exists(builtInJunit));
        assertFalse(Files.exists(customJunit));
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

    private Path createDirectorySymlinkOrSkip(Path link, Path target) throws Exception {
        try {
            return Files.createSymbolicLink(link, target);
        } catch (UnsupportedOperationException | IOException | SecurityException exception) {
            assumeTrue(false, "Directory symbolic links are unavailable: " + exception.getMessage());
            return link;
        }
    }

    private Path createFileSymlinkOrSkip(Path link, Path target) throws Exception {
        try {
            return Files.createSymbolicLink(link, target);
        } catch (UnsupportedOperationException | IOException | SecurityException exception) {
            assumeTrue(false, "File symbolic links are unavailable: " + exception.getMessage());
            return link;
        }
    }

    private CrapJavaCheckTask newCheckTask(Path projectRoot) {
        return newCheckTask(projectRoot, "crap-java-check");
    }

    private CrapJavaCheckTask newCheckTask(Path projectRoot, String name) {
        Project project = ProjectBuilder.builder().withProjectDir(projectRoot.toFile()).build();
        return newCheckTask(project, projectRoot, name);
    }

    private CrapJavaCheckTask newCheckTask(Path projectRoot, String name, Path projectCacheDir) {
        Project project = ProjectBuilder.builder().withProjectDir(projectRoot.toFile()).build();
        project.getGradle().getStartParameter().setProjectCacheDir(projectCacheDir.toFile());
        return newCheckTask(project, projectRoot, name);
    }

    private CrapJavaCheckTask newCheckTask(Project project, Path projectRoot, String name) {
        CrapJavaCheckTask task = project.getTasks().register(name, CrapJavaCheckTask.class).get();
        task.getAnalysisRoot().fileValue(projectRoot.toFile());
        task.getModuleCoverageReports().set(Map.of());
        return task;
    }

    private void rememberOwnedReport(Path statePath, Path reportPath) throws Exception {
        Files.createDirectories(parentOf(statePath));
        Path ownerPath = statePath.resolveSibling("primary-output.owner");
        createHardLinkOrSkip(ownerPath, reportPath);
        Files.writeString(statePath, reportPath + "\n" + ownership(reportPath) + "\n");
    }

    private void assumeHardLinksAvailable(Path directory) throws Exception {
        Path target = Files.createTempFile(directory, ".crap-java-hard-link-target-", ".tmp");
        Path link = target.resolveSibling(fileNameOf(target) + ".link");
        try {
            createHardLinkOrSkip(link, target);
        } finally {
            Files.deleteIfExists(link);
            Files.deleteIfExists(target);
        }
    }

    private Path createHardLinkOrSkip(Path link, Path target) throws Exception {
        try {
            return Files.createLink(link, target);
        } catch (UnsupportedOperationException | IOException | SecurityException exception) {
            assumeTrue(false, "Hard links are unavailable: " + exception.getMessage());
            return link;
        }
    }

    private String ownership(Path reportPath) throws Exception {
        BasicFileAttributes attributes = Files.readAttributes(reportPath, BasicFileAttributes.class);
        return "link\t"
                + attributes.lastModifiedTime().to(TimeUnit.NANOSECONDS) + "\t"
                + attributes.size();
    }

    private void invokeCleanupStaleReports(
            CrapJavaCheckTask task,
            Path currentOutputPath,
            Path currentJunitReportPath
    ) throws Exception {
        Method cleanup = CrapJavaCheckTask.class.getDeclaredMethod(
                "cleanupStaleReports",
                Path.class,
                Path.class
        );
        cleanup.setAccessible(true);
        cleanup.invoke(task, currentOutputPath, currentJunitReportPath);
    }

    private Path stateLockPath(CrapJavaCheckTask task) throws Exception {
        Method stateLockPath = CrapJavaCheckTask.class.getDeclaredMethod("stateLockPath");
        stateLockPath.setAccessible(true);
        return (Path) stateLockPath.invoke(task);
    }

    private Path junitReportStatePath(CrapJavaCheckTask task) throws Exception {
        Method junitReportStatePath = CrapJavaCheckTask.class.getDeclaredMethod("junitReportStatePath");
        junitReportStatePath.setAccessible(true);
        return (Path) junitReportStatePath.invoke(task);
    }

    private Path rememberedReportPath(CrapJavaCheckTask task, Path statePath) throws Exception {
        Object rememberedReport = rememberedReport(task, statePath);
        assertNotNull(rememberedReport);
        Method path = rememberedReport.getClass().getDeclaredMethod("path");
        path.setAccessible(true);
        return (Path) path.invoke(rememberedReport);
    }

    private Object rememberedReport(CrapJavaCheckTask task, Path statePath) throws Exception {
        Method rememberedReportPath = CrapJavaCheckTask.class.getDeclaredMethod("rememberedReportPath", Path.class);
        rememberedReportPath.setAccessible(true);
        return rememberedReportPath.invoke(task, statePath);
    }

    private boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }

    private Path parentOf(Path path) {
        return Objects.requireNonNull(path.getParent(), () -> "Expected parent path for " + path);
    }

    private Path fileNameOf(Path path) {
        return Objects.requireNonNull(path.getFileName(), () -> "Expected file name for " + path);
    }

    private Path rootOf(Path path) {
        return Objects.requireNonNull(path.getRoot(), () -> "Expected filesystem root for " + path);
    }
}

