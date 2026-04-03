package media.barney.crapjava.core;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.jspecify.annotations.Nullable;

record ProjectModule(Path moduleRoot, Path executionRoot, BuildTool buildTool) {

    private static final String JACOCO_MAVEN_PLUGIN_VERSION = "0.8.13";

    ProjectModule {
        moduleRoot = moduleRoot.normalize();
        executionRoot = executionRoot.normalize();
    }

    Path jacocoXmlPath() {
        return switch (buildTool) {
            case MAVEN -> moduleRoot.resolve("target/site/jacoco/jacoco.xml");
            case GRADLE -> moduleRoot.resolve("build/reports/jacoco/test/jacocoTestReport.xml");
        };
    }

    List<Path> staleCoveragePaths() {
        return switch (buildTool) {
            case MAVEN -> List.of(
                    moduleRoot.resolve("target/site/jacoco"),
                    moduleRoot.resolve("target/jacoco.exec")
            );
            case GRADLE -> List.of(
                    moduleRoot.resolve("build/reports/jacoco/test"),
                    moduleRoot.resolve("build/jacoco")
            );
        };
    }

    List<String> coverageCommand() {
        return switch (buildTool) {
            case MAVEN -> mavenCoverageCommand();
            case GRADLE -> gradleCoverageCommand();
        };
    }

    private List<String> mavenCoverageCommand() {
        List<String> command = new ArrayList<>();
        command.add(mavenLauncher());
        command.add("-q");
        if (!executionRoot.equals(moduleRoot)) {
            command.add("-pl");
            command.add(moduleSelector());
            command.add("-am");
        }
        command.add("org.jacoco:jacoco-maven-plugin:" + JACOCO_MAVEN_PLUGIN_VERSION + ":prepare-agent");
        command.add("test");
        command.add("org.jacoco:jacoco-maven-plugin:" + JACOCO_MAVEN_PLUGIN_VERSION + ":report");
        return List.copyOf(command);
    }

    private List<String> gradleCoverageCommand() {
        List<String> command = new ArrayList<>();
        command.add(gradleLauncher());
        command.add("--no-daemon");
        command.add("-q");
        command.add(taskName("test"));
        command.add(taskName("jacocoTestReport"));
        return List.copyOf(command);
    }

    private String taskName(String baseTask) {
        if (executionRoot.equals(moduleRoot)) {
            return baseTask;
        }
        return gradleProjectPath() + ":" + baseTask;
    }

    private String gradleProjectPath() {
        Path relative = executionRoot.relativize(moduleRoot);
        StringBuilder builder = new StringBuilder();
        for (Path segment : relative) {
            builder.append(':').append(segment.toString().replace('\\', '/'));
        }
        return builder.isEmpty() ? ":" : builder.toString();
    }

    private String moduleSelector() {
        return executionRoot.relativize(moduleRoot).toString().replace('\\', '/');
    }

    private String mavenLauncher() {
        return launcher("mvnw.cmd", "mvnw", isWindows() ? "mvn.cmd" : "mvn");
    }

    private String gradleLauncher() {
        return launcher("gradlew.bat", "gradlew", "gradle");
    }

    private String launcher(String windowsWrapper, String unixWrapper, String fallback) {
        Path wrapper = wrapperPath(windowsWrapper, unixWrapper);
        return wrapper != null ? wrapper.toAbsolutePath().normalize().toString() : fallback;
    }

    private @Nullable Path wrapperPath(String windowsWrapper, String unixWrapper) {
        return isWindows() ? existingPath(windowsWrapper) : existingPath(unixWrapper);
    }

    private @Nullable Path existingPath(String fileName) {
        Path wrapper = executionRoot.resolve(fileName);
        return Files.exists(wrapper) ? wrapper : null;
    }

    private static boolean isWindows() {
        return System.getProperty("os.name").toLowerCase(Locale.ROOT).startsWith("windows");
    }
}
