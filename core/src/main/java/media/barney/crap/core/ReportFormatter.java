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
        return format(report, format, false);
    }

    static String format(CrapReport report, ReportFormat format, boolean agent) {
        return agent ? formatAgent(report, format) : formatFull(report, format);
    }

    private static String formatFull(CrapReport report, ReportFormat format) {
        return switch (format) {
            case TOON -> JToon.encodeJson(formatJson(report));
            case JSON -> formatJson(report);
            case TEXT -> formatText(report);
            case JUNIT -> formatJunit(report);
        };
    }

    private static String formatAgent(CrapReport report, ReportFormat format) {
        CrapReport failures = failuresOnly(report);
        return switch (format) {
            case TOON -> JToon.encodeJson(formatJson(failures));
            case JSON -> formatJson(failures);
            case TEXT -> formatAgentText(report);
            case JUNIT -> throw new IllegalArgumentException("--agent cannot be combined with --format junit");
        };
    }

    private static String formatText(CrapReport report) {
        List<CrapReport.MethodReport> sorted = sortedMethods(report.methods());
        StringBuilder builder = new StringBuilder();
        builder.append("CRAP Report\n");
        builder.append("===========\n");
        builder.append("Status: ").append(report.status()).append('\n');
        builder.append("Threshold: ").append(formatDisplayNumber(report.threshold())).append('\n');
        appendMethodTable(builder, fullTextColumns(), sorted);
        return builder.toString();
    }

    private static String formatAgentText(CrapReport report) {
        List<CrapReport.MethodReport> failedMethods = sortedMethods(failuresOnly(report).methods());
        StringBuilder builder = new StringBuilder();
        builder.append("Status: ").append(report.status()).append('\n');
        builder.append("Threshold: ").append(formatDisplayNumber(report.threshold())).append('\n');
        if (failedMethods.isEmpty()) {
            return builder.toString();
        }
        appendMethodTable(builder, agentTextColumns(), failedMethods);
        return builder.toString();
    }

    private static List<TableColumn> fullTextColumns() {
        List<TableColumn> columns = new ArrayList<>();
        columns.add(new TableColumn("Status", Alignment.LEFT, method -> method.status().value()));
        columns.addAll(methodTextColumns());
        return columns;
    }

    private static List<TableColumn> agentTextColumns() {
        return methodTextColumns();
    }

    private static List<TableColumn> methodTextColumns() {
        return List.of(
                new TableColumn("Method", Alignment.LEFT, CrapReport.MethodReport::methodName),
                new TableColumn("Class", Alignment.LEFT, CrapReport.MethodReport::className),
                new TableColumn("CC", Alignment.RIGHT, method -> Integer.toString(method.complexity())),
                new TableColumn("Cov%", Alignment.RIGHT, method -> formatCoverage(method.coveragePercent())),
                new TableColumn("CovKind", Alignment.LEFT, CrapReport.MethodReport::coverageKind),
                new TableColumn("CRAP", Alignment.RIGHT, method -> formatDisplayNumber(method.crapScore()))
        );
    }

    private static void appendMethodTable(StringBuilder builder,
                                          List<TableColumn> columns,
                                          List<CrapReport.MethodReport> methods) {
        List<List<String>> rows = methods.stream()
                .map(method -> row(columns, method))
                .toList();
        List<Integer> widths = columnWidths(columns, rows);
        appendTableRow(builder, columns, columnHeaders(columns), widths);
        appendSeparator(builder, widths);
        for (List<String> row : rows) {
            appendTableRow(builder, columns, row, widths);
        }
    }

    private static List<String> row(List<TableColumn> columns, CrapReport.MethodReport method) {
        List<String> values = new ArrayList<>();
        for (TableColumn column : columns) {
            values.add(column.value(method));
        }
        return values;
    }

    private static List<String> columnHeaders(List<TableColumn> columns) {
        return columns.stream()
                .map(TableColumn::header)
                .toList();
    }

    private static List<Integer> columnWidths(List<TableColumn> columns, List<List<String>> rows) {
        List<Integer> widths = columns.stream()
                .map(column -> column.header().length())
                .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
        for (List<String> row : rows) {
            for (int index = 0; index < row.size(); index++) {
                widths.set(index, Math.max(widths.get(index), row.get(index).length()));
            }
        }
        return widths;
    }

    private static void appendSeparator(StringBuilder builder, List<Integer> widths) {
        for (int index = 0; index < widths.size(); index++) {
            if (index > 0) {
                builder.append("  ");
            }
            builder.append("-".repeat(widths.get(index)));
        }
        builder.append('\n');
    }

    private static void appendTableRow(StringBuilder builder,
                                       List<TableColumn> columns,
                                       List<String> row,
                                       List<Integer> widths) {
        for (int index = 0; index < row.size(); index++) {
            if (index > 0) {
                builder.append("  ");
            }
            builder.append(columns.get(index).align(row.get(index), widths.get(index)));
        }
        builder.append('\n');
    }

    private static String formatJson(CrapReport report) {
        StringBuilder builder = new StringBuilder();
        builder.append("{\n");
        field(builder, 1, "status", quote(report.status()), true);
        field(builder, 1, "threshold", number(report.threshold()), true);
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
        field(builder, 3, "coverageKind", quote(method.coverageKind()), true);
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
        int failed = countStatus(report.methods(), MethodStatus.FAILED);
        int skipped = countStatus(report.methods(), MethodStatus.SKIPPED);
        builder.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        builder.append("<testsuites tests=\"").append(report.methods().size())
                .append("\" failures=\"").append(failed)
                .append("\" errors=\"0\" skipped=\"").append(skipped)
                .append("\" time=\"0\">\n");
        builder.append("  <testsuite name=\"crap-java\" tests=\"").append(report.methods().size())
                .append("\" failures=\"").append(failed)
                .append("\" errors=\"0\" skipped=\"").append(skipped)
                .append("\" time=\"0\">\n");
        builder.append("    <properties>\n");
        property(builder, 3, "threshold", number(report.threshold()));
        builder.append("    </properties>\n");
        for (CrapReport.MethodReport method : sortedMethods(report.methods())) {
            testcase(builder, method, report.threshold());
        }
        builder.append("  </testsuite>\n");
        builder.append("</testsuites>\n");
        return builder.toString();
    }

    private static int countStatus(List<CrapReport.MethodReport> methods, MethodStatus status) {
        int count = 0;
        for (CrapReport.MethodReport method : methods) {
            if (method.status() == status) {
                count++;
            }
        }
        return count;
    }

    private static void testcase(StringBuilder builder, CrapReport.MethodReport method, double threshold) {
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
        property(builder, 4, "coverageKind", method.coverageKind());
        property(builder, 4, "coveragePercent", nullableProperty(method.coveragePercent()));
        property(builder, 4, "crapScore", nullableProperty(method.crapScore()));
        builder.append("      </properties>\n");
        if (method.status() == MethodStatus.FAILED) {
            String message = "CRAP threshold exceeded: "
                    + formatDisplayNumber(method.crapScore()) + " > " + formatDisplayNumber(threshold);
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

    private static CrapReport failuresOnly(CrapReport report) {
        List<CrapReport.MethodReport> failedMethods = report.methods().stream()
                .filter(method -> method.status() == MethodStatus.FAILED)
                .toList();
        return new CrapReport(report.status(), report.threshold(), failedMethods);
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

    private record TableColumn(String header, Alignment alignment, CellValue cellValue) {
        private String align(String value, int width) {
            return alignment.align(value, width);
        }

        private String value(CrapReport.MethodReport method) {
            return cellValue.value(method);
        }
    }

    private interface CellValue {
        String value(CrapReport.MethodReport method);
    }

    private enum Alignment {
        LEFT {
            @Override
            String align(String value, int width) {
                return value + " ".repeat(width - value.length());
            }
        },
        RIGHT {
            @Override
            String align(String value, int width) {
                return " ".repeat(width - value.length()) + value;
            }
        };

        abstract String align(String value, int width);
    }
}
