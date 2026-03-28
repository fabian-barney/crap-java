package media.barney.crap4java.core;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;

enum BuildTool {
    MAVEN,
    GRADLE;

    static EnumSet<BuildTool> detect(Path directory) {
        EnumSet<BuildTool> detected = EnumSet.noneOf(BuildTool.class);
        if (Files.exists(directory.resolve("pom.xml"))) {
            detected.add(MAVEN);
        }
        if (Files.exists(directory.resolve("build.gradle")) || Files.exists(directory.resolve("build.gradle.kts"))) {
            detected.add(GRADLE);
        }
        return detected;
    }
}
