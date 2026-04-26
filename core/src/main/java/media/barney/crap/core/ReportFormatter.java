package media.barney.crap.core;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.util.DefaultIndenter;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlText;
import com.fasterxml.jackson.dataformat.xml.ser.ToXmlGenerator;
import dev.toonformat.jtoon.JToon;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import org.jspecify.annotations.Nullable;

final class ReportFormatter {

    private static final ObjectWriter JSON_WRITER = JsonMapper.builder()
            .build()
            .writer(new JsonPrettyPrinter());
    private static final ObjectWriter XML_WRITER = xmlMapper()
            .writerWithDefaultPrettyPrinter();

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
        return writeJson(jsonReport(report));
    }

    private static String formatJunit(CrapReport report) {
        return writeXml(junitTestSuites(report));
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

    private static JsonReport jsonReport(CrapReport report) {
        return new JsonReport(
                report.status(),
                report.threshold(),
                sortedMethods(report.methods()).stream()
                        .map(ReportFormatter::jsonMethod)
                        .toList()
        );
    }

    private static JsonMethod jsonMethod(CrapReport.MethodReport method) {
        return new JsonMethod(
                method.status().value(),
                method.methodName(),
                method.className(),
                method.sourcePath(),
                method.startLine(),
                method.endLine(),
                method.complexity(),
                method.coveragePercent(),
                method.coverageKind(),
                method.crapScore()
        );
    }

    private static String writeJson(JsonReport report) {
        try {
            return JSON_WRITER.writeValueAsString(report) + '\n';
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Unable to format JSON report", ex);
        }
    }

    private static JunitTestSuites junitTestSuites(CrapReport report) {
        List<CrapReport.MethodReport> methods = sortedMethods(report.methods());
        int failed = countStatus(methods, MethodStatus.FAILED);
        int skipped = countStatus(methods, MethodStatus.SKIPPED);
        JunitTestSuite testSuite = new JunitTestSuite(
                "crap-java",
                methods.size(),
                failed,
                0,
                skipped,
                "0",
                new JunitProperties(List.of(new JunitProperty("threshold", number(report.threshold())))),
                methods.stream()
                        .map(method -> junitTestCase(method, report.threshold()))
                        .toList()
        );
        return new JunitTestSuites(methods.size(), failed, 0, skipped, "0", List.of(testSuite));
    }

    private static JunitTestCase junitTestCase(CrapReport.MethodReport method, double threshold) {
        return new JunitTestCase(
                method.className(),
                testcaseName(method),
                method.sourcePath(),
                method.startLine(),
                "0",
                new JunitProperties(List.of(
                        new JunitProperty("status", method.status().value()),
                        new JunitProperty("methodName", method.methodName()),
                        new JunitProperty("className", method.className()),
                        new JunitProperty("sourcePath", method.sourcePath()),
                        new JunitProperty("startLine", Integer.toString(method.startLine())),
                        new JunitProperty("endLine", Integer.toString(method.endLine())),
                        new JunitProperty("complexity", Integer.toString(method.complexity())),
                        new JunitProperty("coverageKind", method.coverageKind()),
                        new JunitProperty("coveragePercent", nullableProperty(method.coveragePercent())),
                        new JunitProperty("crapScore", nullableProperty(method.crapScore()))
                )),
                junitFailure(method, threshold),
                junitSkipped(method)
        );
    }

    private static @Nullable JunitFailure junitFailure(CrapReport.MethodReport method, double threshold) {
        if (method.status() != MethodStatus.FAILED) {
            return null;
        }
        String message = "CRAP threshold exceeded: "
                + formatDisplayNumber(method.crapScore()) + " > " + formatDisplayNumber(threshold);
        return new JunitFailure(message, "crap-java.threshold", message);
    }

    private static @Nullable JunitSkipped junitSkipped(CrapReport.MethodReport method) {
        if (method.status() != MethodStatus.SKIPPED) {
            return null;
        }
        return new JunitSkipped(
                "CRAP score unavailable",
                "Coverage data unavailable for " + method.className() + "#" + method.methodName()
        );
    }

    private static String writeXml(JunitTestSuites testSuites) {
        try {
            return XML_WRITER.writeValueAsString(testSuites) + '\n';
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Unable to format JUnit XML report", ex);
        }
    }

    private static ObjectMapper xmlMapper() {
        XmlMapper mapper = XmlMapper.builder()
                .configure(ToXmlGenerator.Feature.WRITE_XML_DECLARATION, true)
                .build();
        mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        return mapper;
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

    private static String number(double value) {
        return Double.toString(value);
    }

    private record JsonReport(
            String status,
            double threshold,
            List<JsonMethod> methods
    ) {
    }

    private record JsonMethod(
            String status,
            String methodName,
            String className,
            String sourcePath,
            int startLine,
            int endLine,
            int complexity,
            @Nullable Double coveragePercent,
            String coverageKind,
            @Nullable Double crapScore
    ) {
    }

    @JacksonXmlRootElement(localName = "testsuites")
    private record JunitTestSuites(
            @JacksonXmlProperty(isAttribute = true) int tests,
            @JacksonXmlProperty(isAttribute = true) int failures,
            @JacksonXmlProperty(isAttribute = true) int errors,
            @JacksonXmlProperty(isAttribute = true) int skipped,
            @JacksonXmlProperty(isAttribute = true) String time,
            @JacksonXmlElementWrapper(useWrapping = false)
            @JacksonXmlProperty(localName = "testsuite") List<JunitTestSuite> testSuites
    ) {
    }

    private record JunitTestSuite(
            @JacksonXmlProperty(isAttribute = true) String name,
            @JacksonXmlProperty(isAttribute = true) int tests,
            @JacksonXmlProperty(isAttribute = true) int failures,
            @JacksonXmlProperty(isAttribute = true) int errors,
            @JacksonXmlProperty(isAttribute = true) int skipped,
            @JacksonXmlProperty(isAttribute = true) String time,
            @JacksonXmlProperty(localName = "properties") JunitProperties properties,
            @JacksonXmlElementWrapper(useWrapping = false)
            @JacksonXmlProperty(localName = "testcase") List<JunitTestCase> testCases
    ) {
    }

    private record JunitTestCase(
            @JacksonXmlProperty(isAttribute = true, localName = "classname") String className,
            @JacksonXmlProperty(isAttribute = true) String name,
            @JacksonXmlProperty(isAttribute = true) String file,
            @JacksonXmlProperty(isAttribute = true) int line,
            @JacksonXmlProperty(isAttribute = true) String time,
            @JacksonXmlProperty(localName = "properties") JunitProperties properties,
            @Nullable JunitFailure failure,
            @Nullable JunitSkipped skipped
    ) {
    }

    private record JunitProperties(
            @JacksonXmlElementWrapper(useWrapping = false)
            @JacksonXmlProperty(localName = "property") List<JunitProperty> property
    ) {
    }

    private record JunitProperty(
            @JacksonXmlProperty(isAttribute = true) String name,
            @JacksonXmlProperty(isAttribute = true) String value
    ) {
    }

    private record JunitFailure(
            @JacksonXmlProperty(isAttribute = true) String message,
            @JacksonXmlProperty(isAttribute = true) String type,
            @JacksonXmlText String text
    ) {
    }

    private record JunitSkipped(
            @JacksonXmlProperty(isAttribute = true) String message,
            @JacksonXmlText String text
    ) {
    }

    private static final class JsonPrettyPrinter extends DefaultPrettyPrinter {
        private JsonPrettyPrinter() {
            indentObjectsWith(new DefaultIndenter("  ", "\n"));
            indentArraysWith(new DefaultIndenter("  ", "\n"));
        }

        private JsonPrettyPrinter(JsonPrettyPrinter base) {
            super(base);
        }

        @Override
        public DefaultPrettyPrinter createInstance() {
            return new JsonPrettyPrinter(this);
        }

        @Override
        public void writeObjectFieldValueSeparator(JsonGenerator generator) throws IOException {
            generator.writeRaw(": ");
        }
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
