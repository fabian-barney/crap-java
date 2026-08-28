package media.barney.crap.gradle;

import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.BuildTask;
import org.gradle.testkit.runner.GradleRunner;
import org.gradle.testkit.runner.TaskOutcome;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.CleanupMode;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CrapJavaGradlePluginFunctionalTest {

    @TempDir(cleanup = CleanupMode.NEVER)
    Path tempDir;

    @Test
    void singleModuleProjectRunsCrapJavaCheck() throws Exception {
        writeSingleModuleProject();

        BuildResult result = runBuild("crap-java-check");

        assertEquals(TaskOutcome.SUCCESS, result.task(":crap-java-check").getOutcome());
        assertEquals(TaskOutcome.SUCCESS, result.task(":jacocoTestReport").getOutcome());
        assertTrue(Files.exists(tempDir.resolve("build/reports/crap-java/TEST-crap-java.xml")));
        assertEquals(List.of("TEST-crap-java.xml"), reportFileNames("build/reports/crap-java"));
        assertFalse(result.getOutput().contains("CRAP Report"));
        assertFalse(result.getOutput().contains("\"status\""));
        assertFalse(result.getOutput().contains("status:"));
        assertFalse(result.getOutput().contains("<testsuites"));
    }

    @Test
    void singleModuleProjectUsesConfiguredMainJavaSourceSet() throws Exception {
        writeFile("settings.gradle.kts", "rootProject.name = \"demo\"");
        writeFile("build.gradle.kts", """
                plugins {
                    java
                    id("media.barney.crap-java")
                }

                repositories {
                    mavenCentral()
                }

                sourceSets {
                    main {
                        java.setSrcDirs(listOf("src/java"))
                    }
                    test {
                        java.setSrcDirs(listOf("test/java"))
                    }
                }

                dependencies {
                    testImplementation("org.junit.jupiter:junit-jupiter:6.0.3")
                    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
                }

                tasks.test {
                    useJUnitPlatform()
                }
                """);
        writeFile("src/java/demo/Sample.java", """
                package demo;

                public class Sample {
                    public int alpha(boolean value) {
                        if (value) {
                            return 1;
                        }
                        return 0;
                    }
                }
                """);
        writeFile("test/java/demo/SampleTest.java", """
                package demo;

                import org.junit.jupiter.api.Test;

                import static org.junit.jupiter.api.Assertions.assertEquals;

                class SampleTest {
                    @Test
                    void alphaReturnsOneForTrue() {
                        assertEquals(1, new Sample().alpha(true));
                    }
                }
                """);

        BuildResult result = runBuild("crap-java-check");

        Path junitReport = tempDir.resolve("build/reports/crap-java/TEST-crap-java.xml");
        assertEquals(TaskOutcome.SUCCESS, result.task(":crap-java-check").getOutcome());
        assertTrue(Files.exists(junitReport));
        assertTrue(Files.readString(junitReport).contains("methodName\" value=\"alpha\""));
    }

    @Test
    void rootTaskAggregatesSubprojectCoverageForMultiModuleBuilds() throws Exception {
        writeFile("settings.gradle.kts", """
                rootProject.name = "workspace"
                include("app", "lib")
                """);
        writeFile("build.gradle.kts", """
                plugins {
                    java
                    id("media.barney.crap-java")
                }

                repositories {
                    mavenCentral()
                }

                dependencies {
                    testImplementation("org.junit.jupiter:junit-jupiter:6.0.3")
                    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
                }

                tasks.test {
                    useJUnitPlatform()
                }

                subprojects {
                    apply(plugin = "java")

                    repositories {
                        mavenCentral()
                    }

                    dependencies {
                        "testImplementation"("org.junit.jupiter:junit-jupiter:6.0.3")
                        "testRuntimeOnly"("org.junit.platform:junit-platform-launcher")
                    }

                    tasks.withType<org.gradle.api.tasks.testing.Test>().configureEach {
                        useJUnitPlatform()
                    }
                }
                """);
        writeFile("src/main/java/demo/root/RootSample.java", """
                package demo.root;

                public class RootSample {
                    public int zero() {
                        return 0;
                    }
                }
                """);
        writeFile("src/test/java/demo/root/RootSampleTest.java", """
                package demo.root;

                import org.junit.jupiter.api.Test;

                import static org.junit.jupiter.api.Assertions.assertEquals;

                class RootSampleTest {
                    @Test
                    void zeroReturnsZero() {
                        assertEquals(0, new RootSample().zero());
                    }
                }
                """);
        writeFile("app/src/main/java/demo/app/AppSample.java", """
                package demo.app;

                public class AppSample {
                    public int alpha() {
                        return 1;
                    }
                }
                """);
        writeFile("app/src/test/java/demo/app/AppSampleTest.java", """
                package demo.app;

                import org.junit.jupiter.api.Test;

                import static org.junit.jupiter.api.Assertions.assertEquals;

                class AppSampleTest {
                    @Test
                    void alphaReturnsOne() {
                        assertEquals(1, new AppSample().alpha());
                    }
                }
                """);
        writeFile("lib/src/main/java/demo/lib/LibSample.java", """
                package demo.lib;

                public class LibSample {
                    public int beta() {
                        return 2;
                    }
                }
                """);
        writeFile("lib/src/test/java/demo/lib/LibSampleTest.java", """
                package demo.lib;

                import org.junit.jupiter.api.Test;

                import static org.junit.jupiter.api.Assertions.assertEquals;

                class LibSampleTest {
                    @Test
                    void betaReturnsTwo() {
                        assertEquals(2, new LibSample().beta());
                    }
                }
                """);

        BuildResult result = runBuild("crap-java-check");

        assertEquals(TaskOutcome.SUCCESS, result.task(":crap-java-check").getOutcome());
        assertTrue(Files.exists(tempDir.resolve("build/reports/jacoco/test/jacocoTestReport.xml")));
        assertTrue(Files.exists(tempDir.resolve("app/build/reports/jacoco/test/jacocoTestReport.xml")));
        assertTrue(Files.exists(tempDir.resolve("lib/build/reports/jacoco/test/jacocoTestReport.xml")));
        assertTrue(Files.exists(tempDir.resolve("build/reports/crap-java/TEST-crap-java.xml")));
    }

    @Test
    void parallelSubprojectChecksSerializeReportStateAccess() throws Exception {
        writeFile("gradle.properties", "org.gradle.parallel=true\n");
        writeFile("settings.gradle", """
                rootProject.name = "workspace"
                include("app", "lib")
                """);
        writeFile("build.gradle", """
                plugins {
                    id 'base'
                    id 'media.barney.crap-java' apply false
                }

                subprojects {
                    apply plugin: 'java'
                    apply plugin: 'media.barney.crap-java'

                    repositories {
                        mavenCentral()
                    }

                    dependencies {
                        testImplementation 'org.junit.jupiter:junit-jupiter:6.0.3'
                        testRuntimeOnly 'org.junit.platform:junit-platform-launcher'
                    }

                    tasks.withType(Test).configureEach {
                        useJUnitPlatform()
                    }

                    crapJava {
                        format = 'json'
                        output = layout.buildDirectory.file('reports/crap-java/primary.json')
                        junitReport = layout.buildDirectory.file('reports/crap-java/junit.xml')
                    }
                }

                tasks.register('quality') {
                    dependsOn subprojects.collect { it.tasks.named('crap-java-check') }
                }
                """);
        writeParallelSubproject("app", "AppSample", "alpha", 1);
        writeParallelSubproject("lib", "LibSample", "beta", 2);

        BuildResult result = runBuild("--parallel", "--max-workers=2", "quality");

        assertEquals(TaskOutcome.SUCCESS, result.task(":app:crap-java-check").getOutcome());
        assertEquals(TaskOutcome.SUCCESS, result.task(":lib:crap-java-check").getOutcome());
        assertTrue(Files.exists(tempDir.resolve("app/build/reports/crap-java/primary.json")));
        assertTrue(Files.exists(tempDir.resolve("app/build/reports/crap-java/junit.xml")));
        assertTrue(Files.exists(tempDir.resolve("lib/build/reports/crap-java/primary.json")));
        assertTrue(Files.exists(tempDir.resolve("lib/build/reports/crap-java/junit.xml")));
        assertFalse(result.getOutput().contains("OverlappingFileLockException"));
    }

    @Test
    void customJacocoOutputLocationFeedsCrapJavaCheck() throws Exception {
        writeSingleModuleProject("""

                layout.buildDirectory.set(layout.projectDirectory.dir("custom-build"))

                tasks.named<org.gradle.testing.jacoco.tasks.JacocoReport>("jacocoTestReport") {
                    reports.xml.outputLocation.set(layout.buildDirectory.file("reports/custom/jacoco.xml"))
                }
                """);

        BuildResult result = runBuild("crap-java-check");

        Path jacocoReport = tempDir.resolve("custom-build/reports/custom/jacoco.xml");
        Path junitReport = tempDir.resolve("custom-build/reports/crap-java/TEST-crap-java.xml");
        assertEquals(TaskOutcome.SUCCESS, result.task(":crap-java-check").getOutcome());
        assertEquals(TaskOutcome.SUCCESS, result.task(":jacocoTestReport").getOutcome());
        assertTrue(Files.exists(jacocoReport));
        assertTrue(Files.exists(junitReport));
        assertFalse(Files.exists(tempDir.resolve("build/reports/jacoco/test/jacocoTestReport.xml")));
        assertTrue(Files.readString(junitReport)
                .contains("<testsuites tests=\"1\" failures=\"0\" errors=\"0\" skipped=\"0\" time=\""));
    }

    @Test
    void centralPublishingTasksRemainVisibleAndSkipWithoutCredentials() {
        Path pluginProject = pluginProjectDirectory();

        BuildResult taskListing = runPluginBuild(pluginProject, "tasks", "--all");
        assertTrue(taskListing.getOutput().contains("publishAllPublicationsToCentralPortalOssrhStagingRepository"));

        BuildResult publish = runPluginBuild(
                pluginProject,
                "publishAllPublicationsToCentralPortalOssrhStagingRepository"
        );
        List<BuildTask> centralPublicationTasks = publish.getTasks().stream()
                .filter(task -> task.getPath().contains("ToCentralPortalOssrhStagingRepository"))
                .filter(task -> !task.getPath().endsWith("publishAllPublicationsToCentralPortalOssrhStagingRepository"))
                .toList();
        assertFalse(centralPublicationTasks.isEmpty());
        assertTrue(centralPublicationTasks.stream()
                .allMatch(task -> task.getOutcome() == TaskOutcome.SKIPPED));

        BuildResult blankCredentials = runPluginBuild(
                pluginProject,
                "-PmavenCentralTokenUsername=",
                "-PmavenCentralTokenPassword=",
                "publishAllPublicationsToCentralPortalOssrhStagingRepository"
        );
        List<BuildTask> blankCredentialPublicationTasks = blankCredentials.getTasks().stream()
                .filter(task -> task.getPath().contains("ToCentralPortalOssrhStagingRepository"))
                .filter(task -> !task.getPath().endsWith("publishAllPublicationsToCentralPortalOssrhStagingRepository"))
                .toList();
        assertFalse(blankCredentialPublicationTasks.isEmpty());
        assertTrue(blankCredentialPublicationTasks.stream()
                .allMatch(task -> task.getOutcome() == TaskOutcome.SKIPPED));
    }

    @Test
    void singleModuleProjectReusesConfigurationCache() throws Exception {
        writeSingleModuleProject();

        BuildResult first = runBuild("--configuration-cache", "crap-java-check");
        assertEquals(TaskOutcome.SUCCESS, first.task(":crap-java-check").getOutcome());
        Set<String> firstConfigurationCacheState = configurationCacheState();
        assertFalse(firstConfigurationCacheState.isEmpty());

        BuildResult second = runBuild("--configuration-cache", "crap-java-check");

        assertEquals(firstConfigurationCacheState, configurationCacheState());
        TaskOutcome outcome = second.task(":crap-java-check").getOutcome();
        assertTrue(outcome == TaskOutcome.SUCCESS || outcome == TaskOutcome.UP_TO_DATE);
    }

    @Test
    void configuredReportControlsReuseConfigurationCache() throws Exception {
        writeSingleModuleProject("""

                crapJava {
                    agent.set(true)
                    format.set("json")
                    output.set(layout.buildDirectory.file("reports/crap-java/report.json"))
                    junit.set(false)
                    junitReport.set(layout.buildDirectory.file("reports/crap-java/custom-junit.xml"))
                }
                """);

        BuildResult first = runBuild("--configuration-cache", "crap-java-check");
        assertEquals(TaskOutcome.SUCCESS, first.task(":crap-java-check").getOutcome());
        Set<String> firstConfigurationCacheState = configurationCacheState();
        assertFalse(firstConfigurationCacheState.isEmpty());

        BuildResult second = runBuild("--configuration-cache", "crap-java-check");

        assertEquals(firstConfigurationCacheState, configurationCacheState());
        TaskOutcome outcome = second.task(":crap-java-check").getOutcome();
        assertTrue(outcome == TaskOutcome.SUCCESS || outcome == TaskOutcome.UP_TO_DATE);
        assertTrue(Files.exists(tempDir.resolve("build/reports/crap-java/report.json")));
        assertFalse(Files.exists(tempDir.resolve("build/reports/crap-java/custom-junit.xml")));
        assertFalse(Files.exists(tempDir.resolve("build/reports/crap-java/TEST-crap-java.xml")));
    }

    @Test
    void configuredThresholdIsWrittenToJunitReport() throws Exception {
        writeSingleModuleProject("""

                crapJava {
                    threshold.set(6.0)
                }
                """);

        BuildResult result = runBuild("crap-java-check");

        assertEquals(TaskOutcome.SUCCESS, result.task(":crap-java-check").getOutcome());
        assertTrue(Files.readString(tempDir.resolve("build/reports/crap-java/TEST-crap-java.xml"))
                .contains("<property name=\"threshold\" value=\"6.0\"/>"));
    }

    @Test
    void configuredReportControlsWritePrimaryReportAndFullJunitSidecar() throws Exception {
        writeSingleModuleProject("""

                crapJava {
                    format.set("json")
                    agent.set(true)
                    failuresOnly.set(false)
                    omitRedundancy.set(true)
                    output.set(layout.buildDirectory.file("reports/crap-java/report.json"))
                    junit.set(true)
                    junitReport.set(layout.buildDirectory.file("reports/crap-java/custom-junit.xml"))
                }
                """);

        BuildResult result = runBuild("crap-java-check");

        Path primary = tempDir.resolve("build/reports/crap-java/report.json");
        Path junit = tempDir.resolve("build/reports/crap-java/custom-junit.xml");
        String primaryReport = Files.readString(primary);
        String junitReport = Files.readString(junit);
        assertEquals(TaskOutcome.SUCCESS, result.task(":crap-java-check").getOutcome());
        assertTrue(Files.exists(primary));
        assertTrue(Files.exists(junit));
        assertFalse(Files.exists(tempDir.resolve("build/reports/crap-java/TEST-crap-java.xml")));
        assertTrue(primaryReport.contains("\"status\": \"passed\""));
        assertTrue(primaryReport.contains("\"threshold\": 6.0"));
        assertTrue(primaryReport.contains("\"method\": \"alpha\""));
        assertFalse(primaryReport.contains("      \"status\":"));
        assertTrue(junitReport.contains("<testsuites tests=\"1\" failures=\"0\" errors=\"0\" skipped=\"0\" time=\""));
        assertTrue(junitReport.contains("<property name=\"status\" value=\"passed\"/>"));
    }

    @Test
    void disabledJunitRemovesStaleSidecarAndDoesNotWriteNewSidecar() throws Exception {
        Path defaultJunit = tempDir.resolve("build/reports/crap-java/TEST-crap-java.xml");
        writeSingleModuleProject();
        BuildResult firstResult = runBuild("crap-java-check");
        assertEquals(TaskOutcome.SUCCESS, firstResult.task(":crap-java-check").getOutcome());
        assertTrue(Files.exists(defaultJunit));
        writeSingleModuleProject("""

                crapJava {
                    junit.set(false)
                }
                """);

        BuildResult result = runBuild("crap-java-check");

        assertEquals(TaskOutcome.SUCCESS, result.task(":crap-java-check").getOutcome());
        assertFalse(Files.exists(defaultJunit));
        assertFalse(result.getOutput().contains("<testsuites"));
        assertFalse(result.getOutput().contains("CRAP Report"));
    }

    @Test
    void disabledJunitWithoutPrimaryOutputCanBeUpToDate() throws Exception {
        writeSingleModuleProject("""

                crapJava {
                    junit.set(false)
                }
                """);

        BuildResult firstResult = runBuild("crap-java-check");
        BuildResult secondResult = runBuild("crap-java-check");

        assertEquals(TaskOutcome.SUCCESS, firstResult.task(":crap-java-check").getOutcome());
        assertEquals(TaskOutcome.UP_TO_DATE, secondResult.task(":crap-java-check").getOutcome());
        assertFalse(Files.exists(tempDir.resolve("build/reports/crap-java/TEST-crap-java.xml")));
        assertFalse(secondResult.getOutput().contains("CRAP Report"));
    }

    @Test
    void disabledJunitRemovesLastWrittenNonDefaultSidecar() throws Exception {
        Path oldJunit = tempDir.resolve("build/reports/crap-java/old-junit.xml");
        Path newJunit = tempDir.resolve("build/reports/crap-java/new-junit.xml");
        writeSingleModuleProject("""

                crapJava {
                    junitReport.set(layout.buildDirectory.file("reports/crap-java/old-junit.xml"))
                }
                """);
        BuildResult firstResult = runBuild("crap-java-check");
        assertEquals(TaskOutcome.SUCCESS, firstResult.task(":crap-java-check").getOutcome());
        assertTrue(Files.exists(oldJunit));
        writeSingleModuleProject("""

                crapJava {
                    junit.set(false)
                    junitReport.set(layout.buildDirectory.file("reports/crap-java/new-junit.xml"))
                }
                """);

        BuildResult secondResult = runBuild("crap-java-check");

        assertEquals(TaskOutcome.SUCCESS, secondResult.task(":crap-java-check").getOutcome());
        assertFalse(Files.exists(oldJunit));
        assertFalse(Files.exists(newJunit));
        assertFalse(secondResult.getOutput().contains("<testsuites"));
    }

    @Test
    void enabledJunitRemovesPreviousSidecarWhenReportPathChanges() throws Exception {
        Path oldJunit = tempDir.resolve("build/reports/crap-java/old-junit.xml");
        Path newJunit = tempDir.resolve("build/reports/crap-java/new-junit.xml");
        writeSingleModuleProject("""

                crapJava {
                    junitReport.set(layout.buildDirectory.file("reports/crap-java/old-junit.xml"))
                }
                """);
        BuildResult firstResult = runBuild("crap-java-check");
        assertEquals(TaskOutcome.SUCCESS, firstResult.task(":crap-java-check").getOutcome());
        assertTrue(Files.exists(oldJunit));
        writeSingleModuleProject("""

                crapJava {
                    junitReport.set(layout.buildDirectory.file("reports/crap-java/new-junit.xml"))
                }
                """);

        BuildResult secondResult = runBuild("crap-java-check");

        assertEquals(TaskOutcome.SUCCESS, secondResult.task(":crap-java-check").getOutcome());
        assertFalse(Files.exists(oldJunit));
        assertTrue(Files.exists(newJunit));
    }

    @Test
    void disabledJunitRemovesLastWrittenSidecarAfterClean() throws Exception {
        Path oldJunit = tempDir.resolve("outside-junit.xml");
        writeSingleModuleProject("""

                crapJava {
                    junitReport.set(layout.projectDirectory.file("outside-junit.xml"))
                }
                """);
        BuildResult firstResult = runBuild("crap-java-check");
        assertEquals(TaskOutcome.SUCCESS, firstResult.task(":crap-java-check").getOutcome());
        assertTrue(Files.exists(oldJunit));
        writeSingleModuleProject("""

                crapJava {
                    junit.set(false)
                }
                """);

        BuildResult secondResult = runBuild("clean", "crap-java-check");

        assertEquals(TaskOutcome.SUCCESS, secondResult.task(":crap-java-check").getOutcome());
        assertFalse(Files.exists(oldJunit));
        assertFalse(secondResult.getOutput().contains("<testsuites"));
    }

    @Test
    void disabledJunitReportPathChangeInvalidatesTaskWithoutDeletingUnownedFile() throws Exception {
        Path newJunit = tempDir.resolve("build/reports/crap-java/new-junit.xml");
        writeSingleModuleProject("""

                crapJava {
                    junit.set(false)
                    junitReport.set(layout.buildDirectory.file("reports/crap-java/old-junit.xml"))
                }
                """);
        BuildResult firstResult = runBuild("crap-java-check");
        assertEquals(TaskOutcome.SUCCESS, firstResult.task(":crap-java-check").getOutcome());
        Files.createDirectories(parentOf(newJunit));
        Files.writeString(newJunit, "<testsuites tests=\"99\"/>");
        writeSingleModuleProject("""

                crapJava {
                    junit.set(false)
                    junitReport.set(layout.buildDirectory.file("reports/crap-java/new-junit.xml"))
                }
                """);

        BuildResult secondResult = runBuild("crap-java-check");

        assertEquals(TaskOutcome.SUCCESS, secondResult.task(":crap-java-check").getOutcome());
        assertTrue(Files.exists(newJunit));
    }

    @Test
    void primaryOutputCleanupFollowsConfiguredOutputPath() throws Exception {
        Path oldOutput = tempDir.resolve("build/reports/crap-java/old-report.json");
        Path newOutput = tempDir.resolve("build/reports/crap-java/new-report.json");
        writeSingleModuleProject("""

                crapJava {
                    format.set("json")
                    output.set(layout.buildDirectory.file("reports/crap-java/old-report.json"))
                }
                """);
        BuildResult firstResult = runBuild("crap-java-check");
        assertEquals(TaskOutcome.SUCCESS, firstResult.task(":crap-java-check").getOutcome());
        assertTrue(Files.exists(oldOutput));
        writeSingleModuleProject("""

                crapJava {
                    format.set("json")
                    output.set(layout.buildDirectory.file("reports/crap-java/new-report.json"))
                }
                """);
        BuildResult secondResult = runBuild("crap-java-check");
        assertEquals(TaskOutcome.SUCCESS, secondResult.task(":crap-java-check").getOutcome());
        assertFalse(Files.exists(oldOutput));
        assertTrue(Files.exists(newOutput));
        writeSingleModuleProject("""

                crapJava {
                    format.set("json")
                }
                """);

        BuildResult thirdResult = runBuild("crap-java-check");

        assertEquals(TaskOutcome.SUCCESS, thirdResult.task(":crap-java-check").getOutcome());
        assertFalse(Files.exists(newOutput));
    }

    @Test
    void primaryOutputCleanupRemovesLastWrittenExternalOutputAfterClean() throws Exception {
        Path oldOutput = tempDir.resolve("outside-report.json");
        writeSingleModuleProject("""

                crapJava {
                    format.set("json")
                    output.set(layout.projectDirectory.file("outside-report.json"))
                }
                """);
        BuildResult firstResult = runBuild("crap-java-check");
        assertEquals(TaskOutcome.SUCCESS, firstResult.task(":crap-java-check").getOutcome());
        assertTrue(Files.exists(oldOutput));
        writeSingleModuleProject();

        BuildResult secondResult = runBuild("clean", "crap-java-check");

        assertEquals(TaskOutcome.SUCCESS, secondResult.task(":crap-java-check").getOutcome());
        assertFalse(Files.exists(oldOutput));
    }

    @Test
    void primaryOutputCleanupDoesNotDeleteRecreatedExternalOutputAfterClean() throws Exception {
        Path oldOutput = tempDir.resolve("outside-report.json");
        writeSingleModuleProject("""

                crapJava {
                    format.set("json")
                    output.set(layout.projectDirectory.file("outside-report.json"))
                }
                """);
        BuildResult firstResult = runBuild("crap-java-check");
        assertEquals(TaskOutcome.SUCCESS, firstResult.task(":crap-java-check").getOutcome());
        Files.writeString(oldOutput, "unrelated");
        writeSingleModuleProject();

        BuildResult secondResult = runBuild("clean", "crap-java-check");

        assertEquals(TaskOutcome.SUCCESS, secondResult.task(":crap-java-check").getOutcome());
        assertEquals("unrelated", Files.readString(oldOutput));
    }

    @Test
    void disabledJunitDoesNotDeleteRecreatedExternalSidecarAfterClean() throws Exception {
        Path oldJunit = tempDir.resolve("outside-junit.xml");
        writeSingleModuleProject("""

                crapJava {
                    junitReport.set(layout.projectDirectory.file("outside-junit.xml"))
                }
                """);
        BuildResult firstResult = runBuild("crap-java-check");
        assertEquals(TaskOutcome.SUCCESS, firstResult.task(":crap-java-check").getOutcome());
        Files.writeString(oldJunit, "unrelated");
        writeSingleModuleProject("""

                crapJava {
                    junit.set(false)
                }
                """);

        BuildResult secondResult = runBuild("clean", "crap-java-check");

        assertEquals(TaskOutcome.SUCCESS, secondResult.task(":crap-java-check").getOutcome());
        assertEquals("unrelated", Files.readString(oldJunit));
    }

    @Test
    void invalidReportPathDoesNotDeletePreviousOutput() throws Exception {
        Path oldOutput = tempDir.resolve("outside-report.json");
        writeSingleModuleProject("""

                crapJava {
                    format.set("json")
                    output.set(layout.projectDirectory.file("outside-report.json"))
                }
                """);
        BuildResult firstResult = runBuild("crap-java-check");
        assertEquals(TaskOutcome.SUCCESS, firstResult.task(":crap-java-check").getOutcome());
        writeSingleModuleProject("""

                crapJava {
                    output.set(layout.buildDirectory.file("reports/crap-java/collision.xml"))
                    junitReport.set(layout.buildDirectory.file("reports/crap-java/collision.xml"))
                }
                """);

        BuildResult secondResult = runBuildAndFail("crap-java-check");

        assertTrue(secondResult.getOutput().contains("output and junitReport must not point to the same file"));
        assertTrue(Files.exists(oldOutput));
    }

    @Test
    void reportPathsMustNotUseInternalTaskFiles() throws Exception {
        writeSingleModuleProject("""

                crapJava {
                    output.set(layout.buildDirectory.file("tmp/crap-java/crap-java-check/execution.marker"))
                }
                """);

        BuildResult result = runBuildAndFail("crap-java-check");

        assertTrue(result.getOutput().contains("output must not point to a crap-java internal task file"));
    }

    private BuildResult runBuild(String... arguments) {
        return gradleRunner(arguments).build();
    }

    private BuildResult runBuildAndFail(String... arguments) {
        return gradleRunner(arguments).buildAndFail();
    }

    private BuildResult runPluginBuild(Path projectDir, String... arguments) {
        List<String> gradleArguments = new ArrayList<>();
        gradleArguments.add("-Dgradle.user.home=" + tempDir.resolve("publishing-gradle-user-home"));
        gradleArguments.add("-Dorg.gradle.daemon=false");
        gradleArguments.addAll(List.of(arguments));
        Map<String, String> environment = new HashMap<>(System.getenv());
        environment.remove("MAVEN_CENTRAL_TOKEN_USERNAME");
        environment.remove("MAVEN_CENTRAL_TOKEN_PASSWORD");
        return GradleRunner.create()
                .withProjectDir(projectDir.toFile())
                .withArguments(gradleArguments)
                .withEnvironment(environment)
                .build();
    }

    private GradleRunner gradleRunner(String... arguments) {
        List<String> gradleArguments = new ArrayList<>();
        gradleArguments.add("-Dgradle.user.home=" + tempDir.resolve("gradle-user-home"));
        gradleArguments.add("-Dorg.gradle.daemon=false");
        gradleArguments.addAll(List.of(arguments));
        return GradleRunner.create()
                .withProjectDir(tempDir.toFile())
                .withArguments(gradleArguments)
                .withPluginClasspath();
    }

    private Path pluginProjectDirectory() {
        Path currentDirectory = Path.of("").toAbsolutePath().normalize();
        if (Files.exists(currentDirectory.resolve("build.gradle.kts"))) {
            return currentDirectory;
        }
        return currentDirectory.resolve("gradle-plugin");
    }

    private Set<String> configurationCacheState() throws IOException {
        Path configurationCacheDir = tempDir.resolve(".gradle/configuration-cache");
        if (!Files.isDirectory(configurationCacheDir)) {
            return Set.of();
        }
        Set<String> state = new TreeSet<>();
        try (var entries = Files.list(configurationCacheDir)) {
            entries.map(configurationCacheDir::relativize)
                    .map(Path::toString)
                    .forEach(state::add);
        }
        return state;
    }

    private void writeSingleModuleProject() throws IOException {
        writeSingleModuleProject("");
    }

    private void writeSingleModuleProject(String extraBuildScript) throws IOException {
        writeFile("settings.gradle.kts", "rootProject.name = \"demo\"");
        writeFile("build.gradle.kts", """
                plugins {
                    java
                    id("media.barney.crap-java")
                }

                repositories {
                    mavenCentral()
                }

                dependencies {
                    testImplementation("org.junit.jupiter:junit-jupiter:6.0.3")
                    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
                }

                tasks.test {
                    useJUnitPlatform()
                }
                """ + extraBuildScript);
        writeFile("src/main/java/demo/Sample.java", """
                package demo;

                public class Sample {
                    public int alpha(boolean value) {
                        if (value) {
                            return 1;
                        }
                        return 0;
                    }
                }
                """);
        writeFile("src/test/java/demo/SampleTest.java", """
                package demo;

                import org.junit.jupiter.api.Test;

                import static org.junit.jupiter.api.Assertions.assertEquals;

                class SampleTest {
                    @Test
                    void alphaReturnsOneForTrue() {
                        assertEquals(1, new Sample().alpha(true));
                    }
                }
                """);
    }

    private void writeParallelSubproject(String name, String className, String methodName, int value)
            throws IOException {
        String packageName = "demo." + name;
        writeFile(
                name + "/src/main/java/" + packageName.replace('.', '/') + "/" + className + ".java",
                String.join(
                        System.lineSeparator(),
                        "package " + packageName + ";",
                        "",
                        "public class " + className + " {",
                        "    public int " + methodName + "() {",
                        "        return " + value + ";",
                        "    }",
                        "}"
                )
        );
        writeFile(
                name + "/src/test/java/" + packageName.replace('.', '/') + "/" + className + "Test.java",
                String.join(
                        System.lineSeparator(),
                        "package " + packageName + ";",
                        "",
                        "import org.junit.jupiter.api.Test;",
                        "",
                        "import static org.junit.jupiter.api.Assertions.assertEquals;",
                        "",
                        "class " + className + "Test {",
                        "    @Test",
                        "    void " + methodName + "ReturnsExpectedValue() {",
                        "        assertEquals(" + value + ", new " + className + "()." + methodName + "());",
                        "    }",
                        "}"
                )
        );
    }

    private void writeFile(String relativePath, String content) throws IOException {
        Path file = tempDir.resolve(relativePath);
        Files.createDirectories(parentOf(file));
        Files.writeString(file, content);
    }

    private List<String> reportFileNames(String relativePath) throws IOException {
        try (var files = Files.list(tempDir.resolve(relativePath))) {
            return files
                    .map(path -> fileNameOf(path).toString())
                    .sorted(Comparator.naturalOrder())
                    .toList();
        }
    }

    private Path parentOf(Path path) {
        return Objects.requireNonNull(path.getParent(), () -> "Expected parent path for " + path);
    }

    private Path fileNameOf(Path path) {
        return Objects.requireNonNull(path.getFileName(), () -> "Expected file name for " + path);
    }
}

