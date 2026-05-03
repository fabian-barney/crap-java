package media.barney.crap.core;

import java.util.Locale;

enum ReportFormat {
    TOON,
    JSON,
    TEXT,
    JUNIT,
    NONE;

    static ReportFormat parse(String value) {
        return switch (value.toLowerCase(Locale.ROOT)) {
            case "toon" -> TOON;
            case "json" -> JSON;
            case "text" -> TEXT;
            case "junit" -> JUNIT;
            case "none" -> NONE;
            default -> throw new IllegalArgumentException("Unknown report format: " + value);
        };
    }
}
