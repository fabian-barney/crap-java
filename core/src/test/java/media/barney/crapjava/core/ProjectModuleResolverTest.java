package media.barney.crapjava.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProjectModuleResolverTest {

    @TempDir
    Path tempDir;

    @Test
    void resolvesNearestMavenModuleForExplicitFile() throws Exception {
        Path moduleRoot = tempDir.resolve("services/orders");
        Path source = moduleRoot.resolve("src/main/java/demo/Sample.java");
        Files.createDirectories(source.getParent());
        Files.writeString(moduleRoot.resolve("pom.xml"), "<project/>");
        Files.writeString(source, "class Sample {}");

        ProjectModule module = ProjectModuleResolver.resolve(tempDir, source, BuildToolSelection.AUTO);

        assertEquals(moduleRoot, module.moduleRoot());
        assertEquals(moduleRoot, module.executionRoot());
        assertEquals(BuildTool.MAVEN, module.buildTool());
    }

    @Test
    void resolvesGradleSubmoduleThroughSettingsRoot() throws Exception {
        Files.writeString(tempDir.resolve("settings.gradle"), "rootProject.name = 'demo'");
        Files.writeString(tempDir.resolve("gradlew"), "#!/bin/sh");
        Files.writeString(tempDir.resolve("gradlew.bat"), "@echo off");
        Path moduleRoot = tempDir.resolve("apps/demo");
        Path source = moduleRoot.resolve("src/main/java/demo/Sample.java");
        Files.createDirectories(source.getParent());
        Files.writeString(moduleRoot.resolve("build.gradle.kts"), "plugins { java }");
        Files.writeString(source, "class Sample {}");

        ProjectModule module = ProjectModuleResolver.resolve(tempDir, source, BuildToolSelection.AUTO);

        assertEquals(moduleRoot, module.moduleRoot());
        assertEquals(tempDir, module.executionRoot());
        assertEquals(BuildTool.GRADLE, module.buildTool());
        assertEquals(List.of(gradleWrapperCommand(tempDir), "--no-daemon", "-q", ":apps:demo:test", ":apps:demo:jacocoTestReport"),
                module.coverageCommand());
        assertEquals(moduleRoot.resolve("build/reports/jacoco/test/jacocoTestReport.xml"), module.jacocoXmlPath());
    }

    @Test
    void resolvesGradleWrapperToAbsolutePathForRelativeWorkspaceRoots() throws Exception {
        Files.writeString(tempDir.resolve("settings.gradle"), "rootProject.name = 'demo'");
        Files.writeString(tempDir.resolve("gradlew"), "#!/bin/sh");
        Files.writeString(tempDir.resolve("gradlew.bat"), "@echo off");
        Path moduleRoot = tempDir.resolve("apps/demo");
        Path source = moduleRoot.resolve("src/main/java/demo/Sample.java");
        Files.createDirectories(source.getParent());
        Files.writeString(moduleRoot.resolve("build.gradle.kts"), "plugins { java }");
        Files.writeString(source, "class Sample {}");

        Path currentDirectory = Path.of("").toAbsolutePath().normalize();
        Path relativeWorkspaceRoot = currentDirectory.relativize(tempDir);
        Path relativeSource = currentDirectory.relativize(source);

        ProjectModule module = ProjectModuleResolver.resolve(relativeWorkspaceRoot, relativeSource, BuildToolSelection.AUTO);

        assertEquals(gradleWrapperCommand(tempDir), module.coverageCommand().get(0));
    }

    @Test
    void ambiguousModuleRequiresExplicitBuildTool() throws Exception {
        Path moduleRoot = tempDir.resolve("demo");
        Path source = moduleRoot.resolve("src/main/java/demo/Sample.java");
        Files.createDirectories(source.getParent());
        Files.writeString(moduleRoot.resolve("pom.xml"), "<project/>");
        Files.writeString(moduleRoot.resolve("build.gradle"), "plugins { id 'java' }");
        Files.writeString(source, "class Sample {}");

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> ProjectModuleResolver.resolve(tempDir, source, BuildToolSelection.AUTO));

        assertEquals(
                "Ambiguous build tool for module " + moduleRoot
                        + ". Found both Maven and Gradle markers. Use --build-tool maven or --build-tool gradle.",
                error.getMessage()
        );
    }

    @Test
    void explicitBuildToolResolvesAmbiguousModule() throws Exception {
        Path moduleRoot = tempDir.resolve("demo");
        Path source = moduleRoot.resolve("src/main/java/demo/Sample.java");
        Files.createDirectories(source.getParent());
        Files.writeString(moduleRoot.resolve("pom.xml"), "<project/>");
        Files.writeString(moduleRoot.resolve("build.gradle"), "plugins { id 'java' }");
        Files.writeString(source, "class Sample {}");

        ProjectModule module = ProjectModuleResolver.resolve(tempDir, source, BuildToolSelection.GRADLE);

        assertEquals(BuildTool.GRADLE, module.buildTool());
        assertEquals(moduleRoot, module.moduleRoot());
    }

    @Test
    void mismatchedExplicitBuildToolFailsClearly() throws Exception {
        Path moduleRoot = tempDir.resolve("demo");
        Path source = moduleRoot.resolve("src/main/java/demo/Sample.java");
        Files.createDirectories(source.getParent());
        Files.writeString(moduleRoot.resolve("pom.xml"), "<project/>");
        Files.writeString(source, "class Sample {}");

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> ProjectModuleResolver.resolve(tempDir, source, BuildToolSelection.GRADLE));

        assertEquals("Requested build tool gradle does not match the detected module at " + moduleRoot + ".", error.getMessage());
    }

    private static String gradleWrapperCommand(Path executionRoot) {
        if (System.getProperty("os.name").toLowerCase(Locale.ROOT).startsWith("windows")) {
            return executionRoot.resolve("gradlew.bat").toString();
        }
        return executionRoot.resolve("gradlew").toString();
    }
}
