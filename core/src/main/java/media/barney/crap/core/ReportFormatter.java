package media.barney.crap.core;

import dev.toonformat.jtoon.JToon;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import org.jspecify.annotations.Nullable;

final class ReportFormatter {

    private ReportFormatter() {
    }

    static String format(CrapReport report, ReportFormat format) {
        return switch (format) {
            case TOON -> JToon.encodeJson(formatJson(report));
            case JSON -> formatJson(report);
            case TEXT -> formatText(report);
            case JUNIT -> formatJunit(report);
        };
    }

    private static String formatText(CrapReport report) {
        List<CrapReport.MethodReport> sorted = sortedMethods(report.methods());
        String header = String.format("%-8s %-30s %-35s %4s %7s %8s", "Status", "Method", "Class", "CC", "Cov%", "CRAP");
        String separator = "-".repeat(header.length());
        StringBuilder builder = new StringBuilder();
        builder.append("CRAP Report\n");
        builder.append("===========\n");
        builder.append("Coverage kind: ").append(report.coverageKind()).append('\n');
        builder.append(String.format(Locale.ROOT, "Summary: %d total, %d passed, %d failed, %d skipped%n",
                report.summary().total(),
                report.summary().passed(),
                report.summary().failed(),
                report.summary().skipped()));
        builder.append(header).append('\n');
        builder.append(separator).append('\n');

        for (CrapReport.MethodReport entry : sorted) {
            builder.append(String.format(Locale.ROOT, "%-8s %-30s %-35s %4d %7s %8s",
                    entry.status().value(),
                    entry.methodName(),
                    entry.className(),
                    entry.complexity(),
                    formatCoverage(entry.coveragePercent()),
                    formatDisplayNumber(entry.crapScore())));
            builder.append('\n');
        }

        return builder.toString();
    }

    private static String formatJson(CrapReport report) {
        StringBuilder builder = new StringBuilder();
        builder.append("{\n");
        field(builder, 1, "schemaVersion", Integer.toString(report.schemaVersion()), true);
        field(builder, 1, "tool", quote(report.tool()), true);
        field(builder, 1, "threshold", number(report.threshold()), true);
        field(builder, 1, "coverageKind", quote(report.coverageKind()), true);
        builder.append("  \"summary\": {\n");
        field(builder, 2, "status", quote(report.summary().status()), true);
        field(builder, 2, "total", Integer.toString(report.summary().total()), true);
        field(builder, 2, "passed", Integer.toString(report.summary().passed()), true);
        field(builder, 2, "failed", Integer.toString(report.summary().failed()), true);
        field(builder, 2, "skipped", Integer.toString(report.summary().skipped()), true);
        field(builder, 2, "maxCrapScore", nullableNumber(report.summary().maxCrapScore()), false);
        builder.append("  },\n");
        builder.append("  \"methods\": [\n");
        List<CrapReport.MethodReport> methods = sortedMethods(report.methods());
        for (int index = 0; index < methods.size(); index++) {
            appendMethodJson(builder, methods.get(index), index < methods.size() - 1);
        }
        builder.append("  ]\n");
        builder.append("}\n");
        return builder.toString();
    }

    private static void appendMethodJson(StringBuilder builder, CrapReport.MethodReport method, boolean comma) {
        builder.append("    {\n");
        field(builder, 3, "status", quote(method.status().value()), true);
        field(builder, 3, "methodName", quote(method.methodName()), true);
        field(builder, 3, "className", quote(method.className()), true);
        field(builder, 3, "sourcePath", quote(method.sourcePath()), true);
        field(builder, 3, "startLine", Integer.toString(method.startLine()), true);
        field(builder, 3, "endLine", Integer.toString(method.endLine()), true);
        field(builder, 3, "complexity", Integer.toString(method.complexity()), true);
        field(builder, 3, "coveragePercent", nullableNumber(method.coveragePercent()), true);
        field(builder, 3, "crapScore", nullableNumber(method.crapScore()), false);
        builder.append("    }");
        if (comma) {
            builder.append(',');
        }
        builder.append('\n');
    }

    private static void field(StringBuilder builder, int indent, String name, String value, boolean comma) {
        builder.append("  ".repeat(indent))
                .append(quote(name))
                .append(": ")
                .append(value);
        if (comma) {
            builder.append(',');
        }
        builder.append('\n');
    }

    private static String formatJunit(CrapReport report) {
        StringBuilder builder = new StringBuilder();
        builder.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        builder.append("<testsuites tests=\"").append(report.summary().total())
                .append("\" failures=\"").append(report.summary().failed())
                .append("\" errors=\"0\" skipped=\"").append(report.summary().skipped())
                .append("\" time=\"0\">\n");
        builder.append("  <testsuite name=\"crap-java\" tests=\"").append(report.summary().total())
                .append("\" failures=\"").append(report.summary().failed())
                .append("\" errors=\"0\" skipped=\"").append(report.summary().skipped())
                .append("\" time=\"0\">\n");
        builder.append("    <properties>\n");
        property(builder, 3, "schemaVersion", Integer.toString(report.schemaVersion()));
        property(builder, 3, "threshold", number(report.threshold()));
        property(builder, 3, "coverageKind", report.coverageKind());
        builder.append("    </properties>\n");
        for (CrapReport.MethodReport method : sortedMethods(report.methods())) {
            testcase(builder, report, method);
        }
        builder.append("  </testsuite>\n");
        builder.append("</testsuites>\n");
        return builder.toString();
    }

    private static void testcase(StringBuilder builder, CrapReport report, CrapReport.MethodReport method) {
        builder.append("    <testcase classname=\"").append(xml(method.className()))
                .append("\" name=\"").append(xml(testcaseName(method)))
                .append("\" file=\"").append(xml(method.sourcePath()))
                .append("\" line=\"").append(method.startLine())
                .append("\" time=\"0\">\n");
        builder.append("      <properties>\n");
        property(builder, 4, "status", method.status().value());
        property(builder, 4, "methodName", method.methodName());
        property(builder, 4, "className", method.className());
        property(builder, 4, "sourcePath", method.sourcePath());
        property(builder, 4, "startLine", Integer.toString(method.startLine()));
        property(builder, 4, "endLine", Integer.toString(method.endLine()));
        property(builder, 4, "complexity", Integer.toString(method.complexity()));
        property(builder, 4, "coverageKind", report.coverageKind());
        property(builder, 4, "coveragePercent", nullableProperty(method.coveragePercent()));
        property(builder, 4, "crapScore", nullableProperty(method.crapScore()));
        property(builder, 4, "threshold", number(report.threshold()));
        builder.append("      </properties>\n");
        if (method.status() == MethodStatus.FAILED) {
            String message = "CRAP threshold exceeded: "
                    + formatDisplayNumber(method.crapScore()) + " > " + formatDisplayNumber(report.threshold());
            builder.append("      <failure message=\"").append(xml(message))
                    .append("\" type=\"crap-java.threshold\">")
                    .append(xml(message))
                    .append("</failure>\n");
        } else if (method.status() == MethodStatus.SKIPPED) {
            builder.append("      <skipped message=\"CRAP score unavailable\">")
                    .append("Coverage data unavailable for ")
                    .append(xml(method.className()))
                    .append("#")
                    .append(xml(method.methodName()))
                    .append("</skipped>\n");
        }
        builder.append("    </testcase>\n");
    }

    private static void property(StringBuilder builder, int indent, String name, String value) {
        builder.append("  ".repeat(indent))
                .append("<property name=\"")
                .append(xml(name))
                .append("\" value=\"")
                .append(xml(value))
                .append("\"/>\n");
    }

    private static String testcaseName(CrapReport.MethodReport method) {
        return method.status().value().toUpperCase(Locale.ROOT)
                + " " + method.methodName()
                + ":" + method.startLine()
                + " CRAP " + formatDisplayNumber(method.crapScore());
    }

    private static List<CrapReport.MethodReport> sortedMethods(List<CrapReport.MethodReport> entries) {
        List<CrapReport.MethodReport> sorted = new ArrayList<>(entries);
        sorted.sort(Comparator
                .comparing((CrapReport.MethodReport e) -> e.crapScore() == null)
                .thenComparing(e -> e.crapScore() == null ? 0.0 : -e.crapScore())
                .thenComparing(CrapReport.MethodReport::className)
                .thenComparing(CrapReport.MethodReport::methodName)
                .thenComparingInt(CrapReport.MethodReport::startLine));
        return sorted;
    }

    private static String formatCoverage(@Nullable Double coverage) {
        if (coverage == null) {
            return "N/A";
        }
        return String.format(Locale.ROOT, "%.1f%%", coverage);
    }

    private static String formatDisplayNumber(@Nullable Double score) {
        if (score == null) {
            return "N/A";
        }
        return String.format(Locale.ROOT, "%.1f", score);
    }

    private static String nullableProperty(@Nullable Double value) {
        return value == null ? "N/A" : number(value);
    }

    private static String nullableNumber(@Nullable Double value) {
        return value == null ? "null" : number(value);
    }

    private static String number(double value) {
        return Double.toString(value);
    }

    private static String quote(String value) {
        StringBuilder builder = new StringBuilder();
        builder.append('"');
        for (int index = 0; index < value.length(); index++) {
            builder.append(jsonEscape(value.charAt(index)));
        }
        builder.append('"');
        return builder.toString();
    }

    private static String jsonEscape(char ch) {
        return switch (ch) {
            case '"' -> "\\\"";
            case '\\' -> "\\\\";
            case '\n' -> "\\n";
            case '\r' -> "\\r";
            case '\t' -> "\\t";
            default -> ch < 0x20 ? String.format(Locale.ROOT, "\\u%04x", (int) ch) : Character.toString(ch);
        };
    }

    private static String xml(String value) {
        StringBuilder builder = new StringBuilder();
        for (int index = 0; index < value.length(); index++) {
            char ch = value.charAt(index);
            switch (ch) {
                case '&' -> builder.append("&amp;");
                case '<' -> builder.append("&lt;");
                case '>' -> builder.append("&gt;");
                case '"' -> builder.append("&quot;");
                case '\'' -> builder.append("&apos;");
                default -> builder.append(ch);
            }
        }
        return builder.toString();
    }
}
