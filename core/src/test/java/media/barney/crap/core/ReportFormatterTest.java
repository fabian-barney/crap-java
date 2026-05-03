package media.barney.crap.core;

import org.junit.jupiter.api.Test;
import org.jspecify.annotations.Nullable;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import java.io.StringReader;
import java.util.List;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;

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
                      "crap": 9.645,
                      "cc": 5,
                      "cov": 10.0,
                      "covKind": "instruction",
                      "method": "danger",
                      "src": "demo.Sample",
                      "lineStart": 4,
                      "lineEnd": 6
                    },
                    {
                      "status": "skipped",
                      "crap": null,
                      "cc": 2,
                      "cov": null,
                      "covKind": "N/A",
                      "method": "unknown",
                      "src": "demo.Sample",
                      "lineStart": 20,
                      "lineEnd": 22
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
        assertTrue(report.contains("methods[2]{status,crap,cc,cov,covKind,method,src,lineStart,lineEnd}:"));
        assertTrue(report.contains("passed,4.5,3,85,instruction,foo,demo.Sample,4,6"));
        assertTrue(report.contains("skipped,null,2,null,N/A,bar,demo.Sample,9,11"));
    }

    @Test
    void formatsFailuresOnlyOmitRedundancyJsonWithOnlyFailuresAndGlobalStatusThreshold() {
        String report = ReportFormatter.format(report(
                metric("danger", "demo.Sample", 4, 5, 10.0, 9.645),
                metric("safe", "demo.Sample", 9, 1, 100.0, 1.0),
                metric("unknown", "demo.Sample", 20, 2, null, null)
        ), ReportFormat.JSON, true, true);

        String expected = """
                {
                  "status": "failed",
                  "threshold": 8.0,
                  "methods": [
                    {
                      "crap": 9.645,
                      "cc": 5,
                      "cov": 10.0,
                      "covKind": "instruction",
                      "method": "danger",
                      "src": "demo.Sample",
                      "lineStart": 4,
                      "lineEnd": 6
                    }
                  ]
                }
                """;

        assertEquals(expected, report);
    }

    @Test
    void formatsFailuresOnlyJsonWithOnlyFailedMethods() {
        String report = ReportFormatter.format(report(
                metric("danger", "demo.Sample", 4, 5, 10.0, 9.645),
                metric("safe", "demo.Sample", 9, 1, 100.0, 1.0),
                metric("unknown", "demo.Sample", 20, 2, null, null)
        ), ReportFormat.JSON, true, false);

        String expected = """
                {
                  "status": "failed",
                  "threshold": 8.0,
                  "methods": [
                    {
                      "status": "failed",
                      "crap": 9.645,
                      "cc": 5,
                      "cov": 10.0,
                      "covKind": "instruction",
                      "method": "danger",
                      "src": "demo.Sample",
                      "lineStart": 4,
                      "lineEnd": 6
                    }
                  ]
                }
                """;

        assertEquals(expected, report);
    }

    @Test
    void formatsFailuresOnlyTextWithOnlyFailedMethods() {
        String report = ReportFormatter.format(report(
                metric("danger", "demo.Sample", 4, 5, 10.0, 9.645),
                metric("safe", "demo.Sample", 9, 1, 100.0, 1.0),
                metric("unknown", "demo.Sample", 20, 2, null, null)
        ), ReportFormat.TEXT, true, false);

        assertTrue(report.startsWith("CRAP Report\n===========\nStatus: failed\nThreshold: 8.0\n"));
        assertTrue(report.contains("Status"));
        assertTrue(report.contains("failed"));
        assertTrue(report.contains("danger"));
        assertFalse(report.contains("safe"));
        assertFalse(report.contains("unknown"));
    }

    @Test
    void formatsFailuresOnlyJunitWithOnlyFailedMethods() throws Exception {
        String report = ReportFormatter.format(report(
                metric("danger", "demo.Sample", 4, 5, 10.0, 9.645),
                metric("safe", "demo.Sample", 9, 1, 100.0, 1.0),
                metric("unknown", "demo.Sample", 20, 2, null, null)
        ), ReportFormat.JUNIT, true, false);

        Element root = parseXml(report).getDocumentElement();

        assertEquals("1", root.getAttribute("tests"));
        assertEquals("1", root.getAttribute("failures"));
        assertEquals("0", root.getAttribute("skipped"));
        assertTrue(report.contains("FAILED danger"));
        assertTrue(report.contains("<property name=\"threshold\" value=\"8.0\"/>"));
        assertFalse(report.contains("PASSED safe"));
        assertFalse(report.contains("SKIPPED unknown"));
    }

    @Test
    void formatsOmitRedundancyJsonWithoutMethodStatus() {
        String report = ReportFormatter.format(report(
                metric("danger", "demo.Sample", 4, 5, 10.0, 9.645),
                metric("unknown", "demo.Sample", 20, 2, null, null)
        ), ReportFormat.JSON, false, true);

        String expected = """
                {
                  "status": "failed",
                  "threshold": 8.0,
                  "methods": [
                    {
                      "crap": 9.645,
                      "cc": 5,
                      "cov": 10.0,
                      "covKind": "instruction",
                      "method": "danger",
                      "src": "demo.Sample",
                      "lineStart": 4,
                      "lineEnd": 6
                    },
                    {
                      "crap": null,
                      "cc": 2,
                      "cov": null,
                      "covKind": "N/A",
                      "method": "unknown",
                      "src": "demo.Sample",
                      "lineStart": 20,
                      "lineEnd": 22
                    }
                  ]
                }
                """;

        assertEquals(expected, report);
    }

    @Test
    void formatsOmitRedundancyToonWithoutMethodStatus() {
        String report = ReportFormatter.format(report(
                metric("foo", "demo.Sample", 4, 3, 85.0, 4.5),
                metric("bar", "demo.Sample", 9, 2, null, null)
        ), ReportFormat.TOON, false, true);

        assertTrue(report.contains("status: passed"));
        assertTrue(report.contains("threshold: 8"));
        assertTrue(report.contains("methods[2]{crap,cc,cov,covKind,method,src,lineStart,lineEnd}:"));
        assertTrue(report.contains("4.5,3,85,instruction,foo,demo.Sample,4,6"));
        assertTrue(report.contains("null,2,null,N/A,bar,demo.Sample,9,11"));
        assertFalse(report.contains("methods[2]{status,"));
    }

    @Test
    void formatsOmitRedundancyTextWithoutMethodStatusColumn() {
        String report = ReportFormatter.format(report(
                metric("danger", "demo.Sample", 4, 5, 10.0, 9.645),
                metric("safe", "demo.Sample", 9, 1, 100.0, 1.0)
        ), ReportFormat.TEXT, false, true);

        List<String> lines = report.lines().toList();
        int headerIndex = tableHeaderIndex(lines, "Method");

        assertTrue(report.startsWith("CRAP Report\n===========\nStatus: failed\nThreshold: 8.0\n"));
        assertTrue(lines.get(headerIndex).startsWith("Method"));
        assertFalse(lines.get(headerIndex).startsWith("Status"));
        assertTrue(report.contains("danger"));
        assertTrue(report.contains("safe"));
    }

    @Test
    void formatsOmitRedundancyJunitWithoutStatusProperty() throws Exception {
        String report = ReportFormatter.format(report(
                metric("danger", "demo.Sample", 4, 5, 10.0, 9.645),
                metric("unknown", "demo.Sample", 20, 2, null, null)
        ), ReportFormat.JUNIT, false, true);

        Element root = parseXml(report).getDocumentElement();

        assertEquals("2", root.getAttribute("tests"));
        assertTrue(report.contains("<property name=\"threshold\" value=\"8.0\"/>"));
        assertTrue(report.contains("<property name=\"methodName\" value=\"danger\"/>"));
        assertFalse(report.contains("<property name=\"status\""));
    }

    @Test
    void formatsFailuresOnlyOmitRedundancyToonWithOnlyFailures() {
        String report = ReportFormatter.format(report(
                metric("danger", "demo.Sample", 4, 5, 10.0, 9.645),
                metric("safe", "demo.Sample", 9, 1, 100.0, 1.0)
        ), ReportFormat.TOON, true, true);

        assertTrue(report.contains("status: failed"));
        assertTrue(report.contains("threshold: 8"));
        assertTrue(report.contains("methods[1]{crap,cc,cov,covKind,method,src,lineStart,lineEnd}:"));
        assertTrue(report.contains("9.645,5,10,instruction,danger,demo.Sample,4,6"));
    }

    @Test
    void formatsFailuresOnlyOmitRedundancyTextWithOnlyFailures() {
        String report = ReportFormatter.format(report(
                metric("danger", "demo.Sample", 4, 5, 10.0, 9.645),
                metric("safe", "demo.Sample", 9, 1, 100.0, 1.0),
                metric("unknown", "demo.Sample", 20, 2, null, null)
        ), ReportFormat.TEXT, true, true);

        assertTrue(report.startsWith("CRAP Report\n===========\nStatus: failed\nThreshold: 8.0\n"));
        assertTrue(report.contains("Method"));
        assertTrue(report.contains("danger"));
        assertTrue(report.contains("9.6"));
        assertFalse(report.contains("safe"));
        assertFalse(report.contains("unknown"));

        List<String> lines = report.lines().toList();
        int headerIndex = tableHeaderIndex(lines, "Method");
        assertTrue(lines.get(headerIndex).startsWith("Method"));
        assertEquals(lines.get(headerIndex).length(), lines.get(headerIndex + 1).length());
        assertEquals(lines.get(headerIndex).length(), lines.get(headerIndex + 2).length());
    }

    @Test
    void formatsFailuresOnlyOmitRedundancyTextWithoutMethodRowsWhenPassed() {
        String report = ReportFormatter.format(report(
                metric("safe", "demo.Sample", 9, 1, 100.0, 1.0),
                metric("unknown", "demo.Sample", 20, 2, null, null)
        ), ReportFormat.TEXT, true, true);

        assertTrue(report.startsWith("CRAP Report\n===========\nStatus: passed\nThreshold: 8.0\n"));
        assertTrue(report.contains("Method"));
        assertFalse(report.contains("safe"));
        assertFalse(report.contains("unknown"));
    }

    @Test
    void formatsJunitReportWithFailuresSkippedAndProperties() throws Exception {
        String report = ReportFormatter.format(report(
                metric("danger", "demo.Sample", 4, 5, 10.0, 9.645),
                metric("unknown", "demo.Sample", 20, 2, null, null)
        ), ReportFormat.JUNIT);

        Document document = parseXml(report);
        Element root = document.getDocumentElement();
        Element failure = (Element) document.getElementsByTagName("failure").item(0);
        Element skipped = (Element) document.getElementsByTagName("skipped").item(0);

        assertEquals("testsuites", root.getNodeName());
        assertEquals("2", root.getAttribute("tests"));
        assertEquals("1", root.getAttribute("failures"));
        assertEquals("0", root.getAttribute("errors"));
        assertEquals("1", root.getAttribute("skipped"));
        assertEquals("0", root.getAttribute("time"));
        assertTrue(report.contains("    <property name=\"threshold\" value=\"8.0\"/>"));
        assertTrue(report.contains("<property name=\"coverageKind\" value=\"instruction\"/>"));
        assertTrue(report.contains("<property name=\"coverageKind\" value=\"N/A\"/>"));
        assertTrue(report.contains("<testcase classname=\"demo.Sample\" name=\"FAILED danger:4 CRAP 9.6\""));
        assertTrue(report.contains("<testcase classname=\"demo.Sample\" name=\"SKIPPED unknown:20 CRAP N/A\""));
        assertEquals("CRAP threshold exceeded: 9.6 > 8.0", failure.getAttribute("message"));
        assertEquals("crap-java.threshold", failure.getAttribute("type"));
        assertEquals("CRAP threshold exceeded: 9.6 > 8.0", failure.getTextContent());
        assertEquals("CRAP score unavailable", skipped.getAttribute("message"));
        assertEquals("Coverage data unavailable for demo.Sample#unknown", skipped.getTextContent());
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

        assertTrue(json.contains("\"method\": \"quote\\\"slash\\\\line\\nreturn\\rtab\\tcontrol\\u0001\""));
    }

    @Test
    void escapesXmlSpecialCharacters() throws Exception {
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

        assertEquals("amp&apos'quote\"lt<gt>", propertyValue(parseXml(junit), "methodName"));
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

    private static Document parseXml(String xml) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        return factory.newDocumentBuilder().parse(new InputSource(new StringReader(xml)));
    }

    private static String propertyValue(Document document, String name) {
        NodeList properties = document.getElementsByTagName("property");
        for (int index = 0; index < properties.getLength(); index++) {
            Element property = (Element) properties.item(index);
            if (name.equals(property.getAttribute("name"))) {
                return property.getAttribute("value");
            }
        }
        throw new AssertionError("Missing XML property: " + name);
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
