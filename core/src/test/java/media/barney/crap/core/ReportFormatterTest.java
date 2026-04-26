package media.barney.crap.core;

import org.junit.jupiter.api.Test;
import org.jspecify.annotations.Nullable;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReportFormatterTest {

    @Test
    void formatsTextReportWithStatusAndCoverageKind() {
        String report = ReportFormatter.format(report(
                metric("foo", "demo.Sample", 4, 3, 85.0, 4.5),
                metric("bar", "demo.Sample", 9, 2, null, null)
        ), ReportFormat.TEXT);

        assertTrue(report.contains("Status: passed"));
        assertTrue(report.contains("Threshold: 8.0"));
        assertTrue(report.contains("CovKind"));
        assertTrue(report.contains("passed"));
        assertTrue(report.contains("skipped"));
        assertTrue(report.contains("instruction"));
        assertTrue(report.contains("85.0%"));
        assertTrue(report.contains("N/A"));
    }

    @Test
    void formatsTextReportWithDynamicColumnWidths() {
        String report = ReportFormatter.format(report(
                metric("veryLongMethodNameForWideColumn", "demo.really.LongClassNameForWideColumn", 4, 12, 5.0, 45.25),
                metric("ok", "demo.S", 9, 2, 100.0, 2.0)
        ), ReportFormat.TEXT);

        List<String> lines = report.lines().toList();
        int headerIndex = tableHeaderIndex(lines, "Status");
        String header = lines.get(headerIndex);
        String separator = lines.get(headerIndex + 1);
        String failed = lines.get(headerIndex + 2);
        String passed = lines.get(headerIndex + 3);

        assertTrue(failed.contains("veryLongMethodNameForWideColumn"));
        assertTrue(failed.contains("demo.really.LongClassNameForWideColumn"));
        assertEquals(header.length(), separator.length());
        assertEquals(header.length(), failed.length());
        assertEquals(header.length(), passed.length());

        int complexityColumn = header.indexOf("CC");
        assertEquals("12", failed.substring(complexityColumn, complexityColumn + 2));
        assertEquals(" 2", passed.substring(complexityColumn, complexityColumn + 2));
    }

    @Test
    void formatsJsonReportWithSchemaSummaryAndMethods() {
        String report = ReportFormatter.format(report(
                metric("danger", "demo.Sample", 4, 5, 10.0, 9.645),
                metric("unknown", "demo.Sample", 20, 2, null, null)
        ), ReportFormat.JSON);

        String expected = """
                {
                  "status": "failed",
                  "threshold": 8.0,
                  "methods": [
                    {
                      "status": "failed",
                      "methodName": "danger",
                      "className": "demo.Sample",
                      "sourcePath": "src/main/java/demo/Sample.java",
                      "startLine": 4,
                      "endLine": 6,
                      "complexity": 5,
                      "coveragePercent": 10.0,
                      "coverageKind": "instruction",
                      "crapScore": 9.645
                    },
                    {
                      "status": "skipped",
                      "methodName": "unknown",
                      "className": "demo.Sample",
                      "sourcePath": "src/main/java/demo/Sample.java",
                      "startLine": 20,
                      "endLine": 22,
                      "complexity": 2,
                      "coveragePercent": null,
                      "coverageKind": "N/A",
                      "crapScore": null
                    }
                  ]
                }
                """;

        assertEquals(expected, report);
    }

    @Test
    void formatsToonReportByTranscodingJson() {
        String report = ReportFormatter.format(report(
                metric("foo", "demo.Sample", 4, 3, 85.0, 4.5),
                metric("bar", "demo.Sample", 9, 2, null, null)
        ), ReportFormat.TOON);

        assertTrue(report.contains("status: passed"));
        assertTrue(report.contains("threshold: 8"));
        assertTrue(report.contains("methods[2]{status,methodName,className,sourcePath,startLine,endLine,complexity,coveragePercent,coverageKind,crapScore}:"));
        assertTrue(report.contains("passed,foo,demo.Sample,src/main/java/demo/Sample.java,4,6,3,85,instruction,4.5"));
        assertTrue(report.contains("skipped,bar,demo.Sample,src/main/java/demo/Sample.java,9,11,2,null,N/A,null"));
    }

    @Test
    void formatsAgentJsonWithOnlyFailuresAndGlobalStatusThreshold() {
        String report = ReportFormatter.format(report(
                metric("danger", "demo.Sample", 4, 5, 10.0, 9.645),
                metric("safe", "demo.Sample", 9, 1, 100.0, 1.0),
                metric("unknown", "demo.Sample", 20, 2, null, null)
        ), ReportFormat.JSON, true);

        String expected = """
                {
                  "status": "failed",
                  "threshold": 8.0,
                  "methods": [
                    {
                      "status": "failed",
                      "methodName": "danger",
                      "className": "demo.Sample",
                      "sourcePath": "src/main/java/demo/Sample.java",
                      "startLine": 4,
                      "endLine": 6,
                      "complexity": 5,
                      "coveragePercent": 10.0,
                      "coverageKind": "instruction",
                      "crapScore": 9.645
                    }
                  ]
                }
                """;

        assertEquals(expected, report);
    }

    @Test
    void formatsAgentToonWithOnlyFailures() {
        String report = ReportFormatter.format(report(
                metric("danger", "demo.Sample", 4, 5, 10.0, 9.645),
                metric("safe", "demo.Sample", 9, 1, 100.0, 1.0)
        ), ReportFormat.TOON, true);

        assertTrue(report.contains("status: failed"));
        assertTrue(report.contains("threshold: 8"));
        assertTrue(report.contains("methods[1]{status,methodName,className,sourcePath,startLine,endLine,complexity,coveragePercent,coverageKind,crapScore}:"));
        assertTrue(report.contains("failed,danger,demo.Sample,src/main/java/demo/Sample.java,4,6,5,10,instruction,9.645"));
    }

    @Test
    void formatsAgentTextWithOnlyFailures() {
        String report = ReportFormatter.format(report(
                metric("danger", "demo.Sample", 4, 5, 10.0, 9.645),
                metric("safe", "demo.Sample", 9, 1, 100.0, 1.0),
                metric("unknown", "demo.Sample", 20, 2, null, null)
        ), ReportFormat.TEXT, true);

        assertTrue(report.startsWith("Status: failed\nThreshold: 8.0\n"));
        assertTrue(report.contains("Method"));
        assertTrue(report.contains("danger"));
        assertTrue(report.contains("9.6"));
        assertFalse(report.contains("safe"));
        assertFalse(report.contains("unknown"));
        assertFalse(report.contains("CRAP Report"));

        List<String> lines = report.lines().toList();
        int headerIndex = tableHeaderIndex(lines, "Method");
        assertEquals(lines.get(headerIndex).length(), lines.get(headerIndex + 1).length());
        assertEquals(lines.get(headerIndex).length(), lines.get(headerIndex + 2).length());
    }

    @Test
    void formatsAgentTextWithoutMethodRowsWhenPassed() {
        String report = ReportFormatter.format(report(
                metric("safe", "demo.Sample", 9, 1, 100.0, 1.0),
                metric("unknown", "demo.Sample", 20, 2, null, null)
        ), ReportFormat.TEXT, true);

        assertEquals("""
                Status: passed
                Threshold: 8.0
                """, report);
    }

    @Test
    void formatsJunitReportWithFailuresSkippedAndProperties() {
        String report = ReportFormatter.format(report(
                metric("danger", "demo.Sample", 4, 5, 10.0, 9.645),
                metric("unknown", "demo.Sample", 20, 2, null, null)
        ), ReportFormat.JUNIT);

        assertTrue(report.contains("<testsuites tests=\"2\" failures=\"1\" errors=\"0\" skipped=\"1\" time=\"0\">"));
        assertTrue(report.contains("    <property name=\"threshold\" value=\"8.0\"/>"));
        assertTrue(report.contains("<property name=\"coverageKind\" value=\"instruction\"/>"));
        assertTrue(report.contains("<property name=\"coverageKind\" value=\"N/A\"/>"));
        assertTrue(report.contains("<testcase classname=\"demo.Sample\" name=\"FAILED danger:4 CRAP 9.6\""));
        assertTrue(report.contains("<failure message=\"CRAP threshold exceeded: 9.6 &gt; 8.0\""));
        assertTrue(report.contains("<testcase classname=\"demo.Sample\" name=\"SKIPPED unknown:20 CRAP N/A\""));
        assertTrue(report.contains("<skipped message=\"CRAP score unavailable\">"));
    }

    @Test
    void escapesJsonSpecialCharacters() {
        MethodMetrics metric = new MethodMetrics(
                "quote\"slash\\line\nreturn\rtab\tcontrol\u0001",
                "demo.Special",
                "src/main/java/demo/Special.java",
                1,
                2,
                1,
                100.0,
                "instruction",
                1.0
        );

        String json = ReportFormatter.format(report(metric), ReportFormat.JSON);

        assertTrue(json.contains("\"methodName\": \"quote\\\"slash\\\\line\\nreturn\\rtab\\tcontrol\\u0001\""));
    }

    @Test
    void escapesXmlSpecialCharacters() {
        MethodMetrics metric = new MethodMetrics(
                "amp&apos'quote\"lt<gt>",
                "demo.Special",
                "src/main/java/demo/Special.java",
                1,
                2,
                1,
                100.0,
                "instruction",
                1.0
        );

        String junit = ReportFormatter.format(report(metric), ReportFormat.JUNIT);

        assertTrue(junit.contains("amp&amp;apos&apos;quote&quot;lt&lt;gt&gt;"));
    }

    private static CrapReport report(MethodMetrics... metrics) {
        return CrapReport.from(List.of(metrics), Main.DEFAULT_THRESHOLD);
    }

    private static int tableHeaderIndex(List<String> lines, String firstColumn) {
        for (int index = 0; index < lines.size(); index++) {
            String line = lines.get(index);
            if (line.startsWith(firstColumn) && line.contains("Class") && line.contains("CovKind")) {
                return index;
            }
        }
        throw new AssertionError("Missing text table header");
    }

    private static MethodMetrics metric(String method,
                                        String className,
                                        int startLine,
                                        int complexity,
                                        @Nullable Double coverage,
                                        @Nullable Double score) {
        return new MethodMetrics(
                method,
                className,
                "src/main/java/demo/Sample.java",
                startLine,
                startLine + 2,
                complexity,
                coverage,
                coverage == null ? "N/A" : "instruction",
                score
        );
    }
}
