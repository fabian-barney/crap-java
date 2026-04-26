package media.barney.crap.core;

import org.junit.jupiter.api.Test;
import org.jspecify.annotations.Nullable;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReportFormatterTest {

    @Test
    void formatsTextReportWithStatusAndCoverageKind() {
        String report = ReportFormatter.format(report(
                metric("foo", "demo.Sample", 4, 3, 85.0, 4.5),
                metric("bar", "demo.Sample", 9, 2, null, null)
        ), ReportFormat.TEXT);

        assertTrue(report.contains("Status: passed"));
        assertTrue(report.contains("CovKind"));
        assertTrue(report.contains("passed"));
        assertTrue(report.contains("skipped"));
        assertTrue(report.contains("instruction"));
        assertTrue(report.contains("85.0%"));
        assertTrue(report.contains("N/A"));
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
                      "threshold": 8.0,
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
                      "threshold": 8.0,
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
        assertTrue(report.contains("methods[2]{status,methodName,className,sourcePath,startLine,endLine,complexity,coveragePercent,coverageKind,threshold,crapScore}:"));
        assertTrue(report.contains("passed,foo,demo.Sample,src/main/java/demo/Sample.java,4,6,3,85,instruction,8,4.5"));
        assertTrue(report.contains("skipped,bar,demo.Sample,src/main/java/demo/Sample.java,9,11,2,null,N/A,8,null"));
    }

    @Test
    void formatsJunitReportWithFailuresSkippedAndProperties() {
        String report = ReportFormatter.format(report(
                metric("danger", "demo.Sample", 4, 5, 10.0, 9.645),
                metric("unknown", "demo.Sample", 20, 2, null, null)
        ), ReportFormat.JUNIT);

        assertTrue(report.contains("<testsuites tests=\"2\" failures=\"1\" errors=\"0\" skipped=\"1\" time=\"0\">"));
        assertTrue(report.contains("<property name=\"coverageKind\" value=\"instruction\"/>"));
        assertTrue(report.contains("<property name=\"coverageKind\" value=\"N/A\"/>"));
        assertTrue(report.contains("<property name=\"threshold\" value=\"8.0\"/>"));
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
        return CrapReport.from(List.of(metrics), ReportPublisher.THRESHOLD);
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
