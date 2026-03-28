package media.barney.crap4java.core;

import java.util.Locale;

enum BuildToolSelection {
    AUTO,
    MAVEN,
    GRADLE;

    static BuildToolSelection parse(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("--build-tool requires one of: auto, maven, gradle");
        }
        return switch (value.toLowerCase(Locale.ROOT)) {
            case "auto" -> AUTO;
            case "maven" -> MAVEN;
            case "gradle" -> GRADLE;
            default -> throw new IllegalArgumentException("--build-tool requires one of: auto, maven, gradle");
        };
    }

    BuildTool toBuildTool() {
        return switch (this) {
            case MAVEN -> BuildTool.MAVEN;
            case GRADLE -> BuildTool.GRADLE;
            case AUTO -> throw new IllegalStateException("AUTO does not map to a concrete build tool");
        };
    }
}
