package media.barney.crap.gradle;

import org.gradle.testkit.runner.BuildResult;
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
import java.util.List;

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
                    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
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
                        "testImplementation"("org.junit.jupiter:junit-jupiter:5.10.2")
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
    void singleModuleProjectReusesConfigurationCache() throws Exception {
        writeSingleModuleProject();

        BuildResult first = runBuild("--configuration-cache", "crap-java-check");
        BuildResult second = runBuild("--configuration-cache", "crap-java-check");

        assertTrue(first.getOutput().contains("Configuration cache entry stored."));
        assertTrue(second.getOutput().contains("Configuration cache entry reused."));
        TaskOutcome outcome = second.task(":crap-java-check").getOutcome();
        assertTrue(outcome == TaskOutcome.SUCCESS || outcome == TaskOutcome.UP_TO_DATE);
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
        assertTrue(primaryReport.contains("\"threshold\": 8.0"));
        assertTrue(primaryReport.contains("\"method\": \"alpha\""));
        assertFalse(primaryReport.contains("      \"status\":"));
        assertTrue(junitReport.contains("<testsuites tests=\"1\" failures=\"0\" errors=\"0\" skipped=\"0\" time=\"0\">"));
        assertTrue(junitReport.contains("<property name=\"status\" value=\"passed\"/>"));
    }

    @Test
    void disabledJunitRemovesStaleSidecarAndDoesNotWriteNewSidecar() throws Exception {
        Path defaultJunit = tempDir.resolve("build/reports/crap-java/TEST-crap-java.xml");
        Path customJunit = tempDir.resolve("build/reports/crap-java/custom-junit.xml");
        Files.createDirectories(defaultJunit.getParent());
        Files.writeString(defaultJunit, "<testsuites tests=\"99\"/>");
        Files.writeString(customJunit, "<testsuites tests=\"88\"/>");
        writeSingleModuleProject("""

                crapJava {
                    junit.set(false)
                    junitReport.set(layout.buildDirectory.file("reports/crap-java/custom-junit.xml"))
                }
                """);

        BuildResult result = runBuild("crap-java-check");

        assertEquals(TaskOutcome.SUCCESS, result.task(":crap-java-check").getOutcome());
        assertFalse(Files.exists(defaultJunit));
        assertFalse(Files.exists(customJunit));
        assertFalse(result.getOutput().contains("<testsuites"));
        assertFalse(result.getOutput().contains("CRAP Report"));
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

    private BuildResult runBuild(String... arguments) {
        List<String> gradleArguments = new ArrayList<>();
        gradleArguments.add("-Dgradle.user.home=" + tempDir.resolve("gradle-user-home"));
        gradleArguments.add("-Dorg.gradle.daemon=false");
        gradleArguments.addAll(List.of(arguments));
        return GradleRunner.create()
                .withProjectDir(tempDir.toFile())
                .withArguments(gradleArguments)
                .withPluginClasspath()
                .build();
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
                    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
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

    private void writeFile(String relativePath, String content) throws IOException {
        Path file = tempDir.resolve(relativePath);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
    }

    private List<String> reportFileNames(String relativePath) throws IOException {
        try (var files = Files.list(tempDir.resolve(relativePath))) {
            return files
                    .map(path -> path.getFileName().toString())
                    .sorted(Comparator.naturalOrder())
                    .toList();
        }
    }
}

