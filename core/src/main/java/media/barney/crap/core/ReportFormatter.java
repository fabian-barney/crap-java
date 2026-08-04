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
        return format(report, format, false, false);
    }

    static String format(CrapReport report, ReportFormat format, boolean failuresOnly, boolean omitRedundancy) {
        return format(report, format, failuresOnly, omitRedundancy, true);
    }

    static String format(CrapReport report,
                         ReportFormat format,
                         boolean failuresOnly,
                         boolean omitRedundancy,
                         boolean includeExclusionAudit) {
        if (format == ReportFormat.NONE) {
            return "";
        }
        return formatFull(failuresOnly ? failuresOnly(report) : report, format, omitRedundancy, includeExclusionAudit);
    }

    private static String formatFull(CrapReport report,
                                     ReportFormat format,
                                     boolean omitRedundancy,
                                     boolean includeExclusionAudit) {
        if (format == ReportFormat.TOON) {
            return JToon.encodeJson(formatJson(report, omitRedundancy, includeExclusionAudit));
        }
        return formatNonToon(report, format, omitRedundancy, includeExclusionAudit);
    }

    private static String formatNonToon(CrapReport report,
                                        ReportFormat format,
                                        boolean omitRedundancy,
                                        boolean includeExclusionAudit) {
        if (format == ReportFormat.JSON) {
            return formatJson(report, omitRedundancy, includeExclusionAudit);
        }
        if (format == ReportFormat.TEXT) {
            return formatText(report, omitRedundancy, includeExclusionAudit);
        }
        if (format == ReportFormat.JUNIT) {
            return formatJunit(report, omitRedundancy, includeExclusionAudit);
        }
        throw new IllegalStateException("Unhandled report format: " + format);
    }

    private static String formatText(CrapReport report, boolean omitRedundancy, boolean includeExclusionAudit) {
        List<CrapReport.MethodReport> sorted = sortedMethods(report.methods());
        StringBuilder builder = new StringBuilder();
        builder.append("CRAP Report\n");
        builder.append("===========\n");
        builder.append("Status: ").append(report.status()).append('\n');
        builder.append("Threshold: ").append(formatDisplayNumber(report.threshold())).append('\n');
        if (includeExclusionAudit && hasExclusionAudit(report.exclusions())) {
            appendExclusionAudit(builder, report.exclusions());
        }
        appendMethodTable(builder, omitRedundancy ? methodTextColumns() : fullTextColumns(), sorted);
        return builder.toString();
    }

    private static void appendExclusionAudit(StringBuilder builder, SourceExclusionAudit audit) {
        builder.append("Exclusions:\n");
        builder.append("  Candidate files: ").append(audit.candidateFiles()).append('\n');
        builder.append("  Analyzed files: ").append(audit.analyzedFiles()).append('\n');
        builder.append("  Excluded files: ").append(audit.excludedFileCount()).append('\n');
        for (SourceExclusionAudit.ExclusionCount count : audit.excludedFiles()) {
            builder.append("    ").append(count.reason()).append(": ").append(count.count()).append('\n');
        }
        builder.append("  Excluded classes: ").append(audit.excludedClassCount()).append('\n');
        for (SourceExclusionAudit.ExclusionCount count : audit.excludedClasses()) {
            builder.append("    ").append(count.reason()).append(": ").append(count.count()).append('\n');
        }
    }

    private static List<TableColumn> fullTextColumns() {
        List<TableColumn> columns = new ArrayList<>();
        columns.add(new TableColumn("Status", Alignment.LEFT, method -> method.status().value()));
        columns.addAll(methodTextColumns());
        return columns;
    }

    private static List<TableColumn> methodTextColumns() {
        return List.of(
                new TableColumn("Method", Alignment.LEFT, CrapReport.MethodReport::methodName),
                new TableColumn("Src", Alignment.LEFT, CrapReport.MethodReport::sourcePath),
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

    private static String formatJson(CrapReport report, boolean omitRedundancy, boolean includeExclusionAudit) {
        return writeJson(jsonReport(report, omitRedundancy, includeExclusionAudit));
    }

    private static String formatJunit(CrapReport report, boolean omitRedundancy, boolean includeExclusionAudit) {
        return writeXml(junitTestSuites(report, omitRedundancy, includeExclusionAudit));
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

    private static JsonReport jsonReport(CrapReport report, boolean omitRedundancy, boolean includeExclusionAudit) {
        return new JsonReport(
                report.status(),
                report.threshold(),
                includeExclusionAudit && hasExclusionAudit(report.exclusions()) ? jsonExclusions(report.exclusions()) : null,
                sortedMethods(report.methods()).stream()
                        .map(method -> jsonMethod(method, omitRedundancy))
                        .toList()
        );
    }

    private static JsonExclusions jsonExclusions(SourceExclusionAudit audit) {
        return new JsonExclusions(
                audit.candidateFiles(),
                audit.analyzedFiles(),
                audit.excludedFileCount(),
                audit.excludedClassCount(),
                audit.excludedFiles(),
                audit.excludedClasses()
        );
    }

    private static JsonMethod jsonMethod(CrapReport.MethodReport method, boolean omitRedundancy) {
        return new JsonMethod(
                omitRedundancy ? null : method.status().value(),
                method.crapScore(),
                method.complexity(),
                method.coveragePercent(),
                method.coverageKind(),
                method.methodName(),
                method.sourcePath(),
                method.startLine(),
                method.endLine()
        );
    }

    private static String writeJson(JsonReport report) {
        try {
            return JSON_WRITER.writeValueAsString(report) + '\n';
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Unable to format JSON report", ex);
        }
    }

    private static JunitTestSuites junitTestSuites(CrapReport report,
                                                   boolean omitRedundancy,
                                                   boolean includeExclusionAudit) {
        List<CrapReport.MethodReport> methods = sortedMethods(report.methods());
        int failed = countStatus(methods, MethodStatus.FAILED);
        int skipped = countStatus(methods, MethodStatus.SKIPPED);
        String suiteTime = formatTime(report.elapsedSeconds());
        String testCaseTime = formatTime(methods.isEmpty() ? 0.0 : report.elapsedSeconds() / methods.size());
        JunitTestSuite testSuite = new JunitTestSuite(
                "crap-java",
                methods.size(),
                failed,
                0,
                skipped,
                suiteTime,
                new JunitProperties(junitSuiteProperties(report, includeExclusionAudit)),
                methods.stream()
                        .map(method -> junitTestCase(method, report.threshold(), omitRedundancy, testCaseTime))
                        .toList()
        );
        return new JunitTestSuites(methods.size(), failed, 0, skipped, suiteTime, List.of(testSuite));
    }

    private static JunitTestCase junitTestCase(CrapReport.MethodReport method,
                                               double threshold,
                                               boolean omitRedundancy,
                                               String time) {
        String diagnosticText = junitDiagnosticText(method, threshold);
        return new JunitTestCase(
                method.sourcePath(),
                testcaseName(method),
                method.sourcePath(),
                method.startLine(),
                time,
                junitProperties(method, omitRedundancy),
                diagnosticText,
                junitFailure(method, threshold, diagnosticText),
                junitSkipped(method, diagnosticText)
        );
    }

    private static List<JunitProperty> junitSuiteProperties(CrapReport report, boolean includeExclusionAudit) {
        List<JunitProperty> properties = new ArrayList<>();
        properties.add(new JunitProperty("threshold", number(report.threshold())));
        if (includeExclusionAudit && hasExclusionAudit(report.exclusions())) {
            SourceExclusionAudit audit = report.exclusions();
            properties.add(new JunitProperty("exclusion.candidateFiles", Integer.toString(audit.candidateFiles())));
            properties.add(new JunitProperty("exclusion.analyzedFiles", Integer.toString(audit.analyzedFiles())));
            properties.add(new JunitProperty("exclusion.excludedFiles", Integer.toString(audit.excludedFileCount())));
            properties.add(new JunitProperty("exclusion.excludedClasses", Integer.toString(audit.excludedClassCount())));
            addCountProperties(properties, "exclusion.file", audit.excludedFiles());
            addCountProperties(properties, "exclusion.class", audit.excludedClasses());
        }
        return properties;
    }

    private static void addCountProperties(List<JunitProperty> properties,
                                           String prefix,
                                           List<SourceExclusionAudit.ExclusionCount> counts) {
        for (SourceExclusionAudit.ExclusionCount count : counts) {
            properties.add(new JunitProperty(prefix + "." + count.reason(), Integer.toString(count.count())));
        }
    }

    private static JunitProperties junitProperties(CrapReport.MethodReport method, boolean omitRedundancy) {
        List<JunitProperty> properties = new ArrayList<>();
        if (!omitRedundancy) {
            properties.add(new JunitProperty("status", method.status().value()));
        }
        properties.addAll(List.of(
                new JunitProperty("methodName", method.methodName()),
                new JunitProperty("className", method.className()),
                new JunitProperty("sourcePath", method.sourcePath()),
                new JunitProperty("startLine", Integer.toString(method.startLine())),
                new JunitProperty("endLine", Integer.toString(method.endLine())),
                new JunitProperty("complexity", Integer.toString(method.complexity())),
                new JunitProperty("coverageKind", method.coverageKind()),
                new JunitProperty("coveragePercent", nullableProperty(method.coveragePercent())),
                new JunitProperty("crapScore", nullableProperty(method.crapScore()))
        ));
        return new JunitProperties(properties);
    }

    private static @Nullable JunitFailure junitFailure(CrapReport.MethodReport method,
                                                       double threshold,
                                                       String diagnosticText) {
        if (method.status() != MethodStatus.FAILED) {
            return null;
        }
        String message = "CRAP threshold exceeded: "
                + formatDisplayNumber(method.crapScore()) + " > " + formatDisplayNumber(threshold);
        return new JunitFailure(message, "crap-java.threshold", diagnosticText);
    }

    private static @Nullable JunitSkipped junitSkipped(CrapReport.MethodReport method, String diagnosticText) {
        if (method.status() != MethodStatus.SKIPPED) {
            return null;
        }
        return new JunitSkipped(
                "CRAP score unavailable",
                diagnosticText
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
        return String.format(
                Locale.ROOT,
                "%s:%d [CRAP=%s, CC=%d, Cov=%s (%s)]",
                method.methodName(),
                method.startLine(),
                formatDisplayNumber(method.crapScore()),
                method.complexity(),
                formatCoverage(method.coveragePercent()),
                method.coverageKind()
        );
    }

    private static String junitDiagnosticText(CrapReport.MethodReport method, double threshold) {
        return String.join("\n",
                "CRAP score: " + formatDisplayNumber(method.crapScore()),
                "Threshold: " + formatDisplayNumber(threshold),
                "Coverage: " + formatCoverage(method.coveragePercent()) + " (" + method.coverageKind() + ")",
                "Source: " + method.sourcePath() + ":" + method.startLine() + "-" + method.endLine(),
                "Method: " + method.methodName(),
                "Complexity: " + method.complexity()
        );
    }

    private static List<CrapReport.MethodReport> sortedMethods(List<CrapReport.MethodReport> entries) {
        List<CrapReport.MethodReport> sorted = new ArrayList<>(entries);
        sorted.sort(Comparator
                .comparing((CrapReport.MethodReport e) -> e.crapScore() == null)
                .thenComparing(e -> e.crapScore() == null ? 0.0 : -e.crapScore())
                .thenComparing(CrapReport.MethodReport::sourcePath)
                .thenComparing(CrapReport.MethodReport::methodName)
                .thenComparingInt(CrapReport.MethodReport::startLine));
        return sorted;
    }

    private static CrapReport failuresOnly(CrapReport report) {
        List<CrapReport.MethodReport> failedMethods = report.methods().stream()
                .filter(method -> method.status() == MethodStatus.FAILED)
                .toList();
        return new CrapReport(
                report.status(),
                report.threshold(),
                failedMethods,
                report.exclusions(),
                report.elapsedSeconds()
        );
    }

    private static boolean hasExclusionAudit(SourceExclusionAudit audit) {
        return audit.candidateFiles() != 0
                || audit.analyzedFiles() != 0
                || audit.excludedFileCount() != 0
                || audit.excludedClassCount() != 0;
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

    private static String formatTime(double elapsedSeconds) {
        return String.format(Locale.ROOT, "%.1f", Math.max(0.0, elapsedSeconds));
    }

    private record JsonReport(
            String status,
            double threshold,
            @JsonInclude(JsonInclude.Include.NON_NULL)
            @Nullable JsonExclusions exclusions,
            List<JsonMethod> methods
    ) {
    }

    private record JsonExclusions(
            int candidateFiles,
            int analyzedFiles,
            int excludedFiles,
            int excludedClasses,
            List<SourceExclusionAudit.ExclusionCount> excludedFileReasons,
            List<SourceExclusionAudit.ExclusionCount> excludedClassReasons
    ) {
    }

    private record JsonMethod(
            @JsonInclude(JsonInclude.Include.NON_NULL)
            @Nullable String status,
            @Nullable Double crap,
            int cc,
            @Nullable Double cov,
            String covKind,
            String method,
            String src,
            int lineStart,
            int lineEnd
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
            @JacksonXmlProperty(localName = "system-out") String systemOut,
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
