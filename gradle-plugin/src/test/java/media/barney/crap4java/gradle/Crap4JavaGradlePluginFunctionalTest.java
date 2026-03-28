package media.barney.crap4java.gradle;

import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.gradle.testkit.runner.TaskOutcome;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Crap4JavaGradlePluginFunctionalTest {

    @TempDir
    Path tempDir;

    @Test
    void singleModuleProjectRunsCrap4JavaCheck() throws Exception {
        writeFile("settings.gradle.kts", "rootProject.name = \"demo\"");
        writeFile("build.gradle.kts", """
                plugins {
                    java
                    id("media.barney.crap4java")
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
                """);
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

        BuildResult result = runBuild("crap4javaCheck");

        assertEquals(TaskOutcome.SUCCESS, result.task(":crap4javaCheck").getOutcome());
        assertEquals(TaskOutcome.SUCCESS, result.task(":jacocoTestReport").getOutcome());
    }

    @Test
    void rootTaskAggregatesSubprojectCoverageForMultiModuleBuilds() throws Exception {
        writeFile("settings.gradle.kts", """
                rootProject.name = "workspace"
                include("app", "lib")
                """);
        writeFile("build.gradle.kts", """
                plugins {
                    id("media.barney.crap4java")
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

        BuildResult result = runBuild("crap4javaCheck");

        assertEquals(TaskOutcome.SUCCESS, result.task(":crap4javaCheck").getOutcome());
        assertTrue(Files.exists(tempDir.resolve("app/build/reports/jacoco/test/jacocoTestReport.xml")));
        assertTrue(Files.exists(tempDir.resolve("lib/build/reports/jacoco/test/jacocoTestReport.xml")));
    }

    private BuildResult runBuild(String... arguments) {
        return GradleRunner.create()
                .withProjectDir(tempDir.toFile())
                .withArguments(arguments)
                .withPluginClasspath()
                .build();
    }

    private void writeFile(String relativePath, String content) throws IOException {
        Path file = tempDir.resolve(relativePath);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
    }
}
