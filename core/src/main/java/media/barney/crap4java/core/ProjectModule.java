package media.barney.crap4java.core;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

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
        if (isWindows() && Files.exists(executionRoot.resolve("mvnw.cmd"))) {
            return "mvnw.cmd";
        }
        if (!isWindows() && Files.exists(executionRoot.resolve("mvnw"))) {
            return "./mvnw";
        }
        return "mvn";
    }

    private String gradleLauncher() {
        if (isWindows() && Files.exists(executionRoot.resolve("gradlew.bat"))) {
            return "gradlew.bat";
        }
        if (!isWindows() && Files.exists(executionRoot.resolve("gradlew"))) {
            return "./gradlew";
        }
        return "gradle";
    }

    private static boolean isWindows() {
        return System.getProperty("os.name").toLowerCase().startsWith("windows");
    }
}
