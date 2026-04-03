package media.barney.crapjava.core;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.Locale;
import org.jspecify.annotations.Nullable;

final class ProjectModuleResolver {

    private ProjectModuleResolver() {
    }

    static ProjectModule resolve(Path workspaceRoot, Path file, BuildToolSelection selection) {
        Path normalizedWorkspaceRoot = workspaceRoot.normalize();
        Path candidate = Files.isDirectory(file) ? file.normalize() : file.normalize().getParent();
        while (candidate != null && candidate.startsWith(normalizedWorkspaceRoot)) {
            EnumSet<BuildTool> detected = BuildTool.detect(candidate);
            if (!detected.isEmpty()) {
                BuildTool buildTool = selectBuildTool(candidate, detected, selection);
                return new ProjectModule(candidate, executionRoot(normalizedWorkspaceRoot, candidate, buildTool), buildTool);
            }
            candidate = candidate.getParent();
        }
        throw new IllegalArgumentException("No Maven or Gradle module found for " + file.normalize());
    }

    private static BuildTool selectBuildTool(Path moduleRoot,
                                             EnumSet<BuildTool> detected,
                                             BuildToolSelection selection) {
        if (selection == BuildToolSelection.AUTO) {
            if (detected.size() == 1) {
                return detected.iterator().next();
            }
            throw new IllegalArgumentException(
                    "Ambiguous build tool for module " + moduleRoot
                            + ". Found both Maven and Gradle markers. Use --build-tool maven or --build-tool gradle."
            );
        }

        BuildTool requested = selection.toBuildTool();
        if (detected.contains(requested)) {
            return requested;
        }
        throw new IllegalArgumentException(
                "Requested build tool " + selection.name().toLowerCase(Locale.ROOT)
                        + " does not match the detected module at " + moduleRoot + "."
        );
    }

    private static Path executionRoot(Path workspaceRoot, Path moduleRoot, BuildTool buildTool) {
        return switch (buildTool) {
            case MAVEN -> mavenExecutionRoot(workspaceRoot, moduleRoot);
            case GRADLE -> gradleExecutionRoot(workspaceRoot, moduleRoot);
        };
    }

    private static Path mavenExecutionRoot(Path workspaceRoot, Path moduleRoot) {
        return topmostAncestor(workspaceRoot, moduleRoot, ProjectModuleResolver::hasMavenMarker);
    }

    private static Path gradleExecutionRoot(Path workspaceRoot, Path moduleRoot) {
        Path settingsOrWrapper = topmostAncestorOrNull(workspaceRoot, moduleRoot, ProjectModuleResolver::hasGradleSettingsOrWrapper);
        if (settingsOrWrapper != null) {
            return settingsOrWrapper;
        }
        return topmostAncestor(workspaceRoot, moduleRoot, ProjectModuleResolver::hasGradleBuildFile);
    }

    private static boolean hasMavenMarker(Path directory) {
        return Files.exists(directory.resolve("mvnw"))
                || Files.exists(directory.resolve("mvnw.cmd"))
                || Files.exists(directory.resolve("pom.xml"));
    }

    private static boolean hasGradleSettingsOrWrapper(Path directory) {
        return Files.exists(directory.resolve("settings.gradle"))
                || Files.exists(directory.resolve("settings.gradle.kts"))
                || Files.exists(directory.resolve("gradlew"))
                || Files.exists(directory.resolve("gradlew.bat"));
    }

    private static boolean hasGradleBuildFile(Path directory) {
        return Files.exists(directory.resolve("build.gradle"))
                || Files.exists(directory.resolve("build.gradle.kts"));
    }

    private static Path topmostAncestor(Path workspaceRoot, Path start, DirectoryPredicate predicate) {
        Path match = topmostAncestorOrNull(workspaceRoot, start, predicate);
        return match != null ? match : start.normalize();
    }

    private static @Nullable Path topmostAncestorOrNull(Path workspaceRoot, Path start, DirectoryPredicate predicate) {
        Path normalizedWorkspaceRoot = workspaceRoot.normalize();
        Path current = start.normalize();
        Path match = null;
        while (current != null && current.startsWith(normalizedWorkspaceRoot)) {
            if (predicate.test(current)) {
                match = current;
            }
            current = current.getParent();
        }
        return match;
    }

    @FunctionalInterface
    private interface DirectoryPredicate {
        boolean test(Path directory);
    }
}
