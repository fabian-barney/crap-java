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
        assertTrue(report.contains("Threshold: 6.0"));
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
        assertTrue(failed.contains("src/main/java/demo/Sample.java"));
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
                  "threshold": 6.0,
                  "methods": [
                    {
                      "status": "failed",
                      "crap": 9.645,
                      "cc": 5,
                      "cov": 10.0,
                      "covKind": "instruction",
                      "method": "danger",
                      "src": "src/main/java/demo/Sample.java",
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
                      "src": "src/main/java/demo/Sample.java",
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
        assertTrue(report.contains("threshold: 6"));
        assertTrue(report.contains("methods[2]{status,crap,cc,cov,covKind,method,src,lineStart,lineEnd}:"));
        assertTrue(report.contains("passed,4.5,3,85,instruction,foo,src/main/java/demo/Sample.java,4,6"));
        assertTrue(report.contains("skipped,null,2,null,N/A,bar,src/main/java/demo/Sample.java,9,11"));
    }

    @Test
    void formatsNoneReportAsEmptyContent() {
        String report = ReportFormatter.format(report(
                metric("danger", "demo.Sample", 4, 5, 10.0, 9.645)
        ), ReportFormat.NONE, true, true);

        assertEquals("", report);
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
                  "threshold": 6.0,
                  "methods": [
                    {
                      "crap": 9.645,
                      "cc": 5,
                      "cov": 10.0,
                      "covKind": "instruction",
                      "method": "danger",
                      "src": "src/main/java/demo/Sample.java",
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
                  "threshold": 6.0,
                  "methods": [
                    {
                      "status": "failed",
                      "crap": 9.645,
                      "cc": 5,
                      "cov": 10.0,
                      "covKind": "instruction",
                      "method": "danger",
                      "src": "src/main/java/demo/Sample.java",
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

        assertTrue(report.startsWith("CRAP Report\n===========\nStatus: failed\nThreshold: 6.0\n"));
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

        Document document = parseXml(report);
        Element root = document.getDocumentElement();
        Element danger = testcaseByName(document, "danger:4 [CRAP=9.6, CC=5, Cov=10.0% (instruction)]");

        assertEquals("1", root.getAttribute("tests"));
        assertEquals("1", root.getAttribute("failures"));
        assertEquals("0", root.getAttribute("skipped"));
        assertEquals("src/main/java/demo/Sample.java", danger.getAttribute("classname"));
        assertEquals("src/main/java/demo/Sample.java", danger.getAttribute("file"));
        assertEquals("0.0", danger.getAttribute("time"));
        assertTrue(report.contains("<property name=\"threshold\" value=\"6.0\"/>"));
        assertFalse(hasTestcaseNameStartingWith(document, "safe:9 "));
        assertFalse(hasTestcaseNameStartingWith(document, "unknown:20 "));
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
                  "threshold": 6.0,
                  "methods": [
                    {
                      "crap": 9.645,
                      "cc": 5,
                      "cov": 10.0,
                      "covKind": "instruction",
                      "method": "danger",
                      "src": "src/main/java/demo/Sample.java",
                      "lineStart": 4,
                      "lineEnd": 6
                    },
                    {
                      "crap": null,
                      "cc": 2,
                      "cov": null,
                      "covKind": "N/A",
                      "method": "unknown",
                      "src": "src/main/java/demo/Sample.java",
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
        assertTrue(report.contains("threshold: 6"));
        assertTrue(report.contains("methods[2]{crap,cc,cov,covKind,method,src,lineStart,lineEnd}:"));
        assertTrue(report.contains("4.5,3,85,instruction,foo,src/main/java/demo/Sample.java,4,6"));
        assertTrue(report.contains("null,2,null,N/A,bar,src/main/java/demo/Sample.java,9,11"));
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

        assertTrue(report.startsWith("CRAP Report\n===========\nStatus: failed\nThreshold: 6.0\n"));
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
        assertTrue(report.contains("<property name=\"threshold\" value=\"6.0\"/>"));
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
        assertTrue(report.contains("threshold: 6"));
        assertTrue(report.contains("methods[1]{crap,cc,cov,covKind,method,src,lineStart,lineEnd}:"));
        assertTrue(report.contains("9.645,5,10,instruction,danger,src/main/java/demo/Sample.java,4,6"));
    }

    @Test
    void formatsFailuresOnlyOmitRedundancyTextWithOnlyFailures() {
        String report = ReportFormatter.format(report(
                metric("danger", "demo.Sample", 4, 5, 10.0, 9.645),
                metric("safe", "demo.Sample", 9, 1, 100.0, 1.0),
                metric("unknown", "demo.Sample", 20, 2, null, null)
        ), ReportFormat.TEXT, true, true);

        assertTrue(report.startsWith("CRAP Report\n===========\nStatus: failed\nThreshold: 6.0\n"));
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

        assertTrue(report.startsWith("CRAP Report\n===========\nStatus: passed\nThreshold: 6.0\n"));
        assertTrue(report.contains("Method"));
        assertFalse(report.contains("safe"));
        assertFalse(report.contains("unknown"));
    }

    @Test
    void formatsJunitReportWithFailuresSkippedAndProperties() throws Exception {
        String report = ReportFormatter.format(reportWithElapsed(1.2,
                metric("danger", "demo.Sample", 4, 5, 10.0, 9.645),
                metric("unknown", "demo.Sample", 20, 2, null, null)
        ), ReportFormat.JUNIT);

        Document document = parseXml(report);
        Element root = document.getDocumentElement();
        Element suite = (Element) document.getElementsByTagName("testsuite").item(0);
        Element failure = (Element) document.getElementsByTagName("failure").item(0);
        Element skipped = (Element) document.getElementsByTagName("skipped").item(0);
        Element danger = testcaseByName(document, "danger:4 [CRAP=9.6, CC=5, Cov=10.0% (instruction)]");
        Element unknown = testcaseByName(document, "unknown:20 [CRAP=N/A, CC=2, Cov=N/A (N/A)]");
        String dangerSystemOut = childText(danger, "system-out");
        String unknownSystemOut = childText(unknown, "system-out");

        assertEquals("testsuites", root.getNodeName());
        assertEquals("2", root.getAttribute("tests"));
        assertEquals("1", root.getAttribute("failures"));
        assertEquals("0", root.getAttribute("errors"));
        assertEquals("1", root.getAttribute("skipped"));
        assertEquals("1.2", root.getAttribute("time"));
        assertEquals("1.2", suite.getAttribute("time"));
        assertTrue(report.contains("    <property name=\"threshold\" value=\"6.0\"/>"));
        assertTrue(report.contains("<property name=\"coverageKind\" value=\"instruction\"/>"));
        assertTrue(report.contains("<property name=\"coverageKind\" value=\"N/A\"/>"));
        assertEquals("src/main/java/demo/Sample.java", danger.getAttribute("classname"));
        assertEquals("src/main/java/demo/Sample.java", danger.getAttribute("file"));
        assertEquals("0.6", danger.getAttribute("time"));
        assertEquals("src/main/java/demo/Sample.java", unknown.getAttribute("classname"));
        assertEquals("src/main/java/demo/Sample.java", unknown.getAttribute("file"));
        assertEquals("0.6", unknown.getAttribute("time"));
        assertEquals("CRAP threshold exceeded: 9.6 > 6.0", failure.getAttribute("message"));
        assertEquals("crap-java.threshold", failure.getAttribute("type"));
        assertTrue(failure.getTextContent().contains("CRAP score: 9.6"));
        assertTrue(failure.getTextContent().contains("Threshold: 6.0"));
        assertTrue(failure.getTextContent().contains("Coverage: 10.0% (instruction)"));
        assertTrue(failure.getTextContent().contains("Source: src/main/java/demo/Sample.java:4-6"));
        assertTrue(dangerSystemOut.contains("CRAP score: 9.6"));
        assertTrue(dangerSystemOut.contains("Threshold: 6.0"));
        assertTrue(dangerSystemOut.contains("Coverage: 10.0% (instruction)"));
        assertTrue(dangerSystemOut.contains("Source: src/main/java/demo/Sample.java:4-6"));
        assertEquals("CRAP score unavailable", skipped.getAttribute("message"));
        assertTrue(skipped.getTextContent().contains("CRAP score: N/A"));
        assertTrue(skipped.getTextContent().contains("Threshold: 6.0"));
        assertTrue(skipped.getTextContent().contains("Coverage: N/A (N/A)"));
        assertTrue(skipped.getTextContent().contains("Source: src/main/java/demo/Sample.java:20-22"));
        assertTrue(unknownSystemOut.contains("CRAP score: N/A"));
        assertTrue(unknownSystemOut.contains("Threshold: 6.0"));
        assertTrue(unknownSystemOut.contains("Coverage: N/A (N/A)"));
        assertTrue(unknownSystemOut.contains("Source: src/main/java/demo/Sample.java:20-22"));
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
        String name = "amp&apos'quote\"lt<gt>:1 [CRAP=1.0, CC=1, Cov=100.0% (instruction)]";

        assertEquals("amp&apos'quote\"lt<gt>", propertyValue(parseXml(junit), "methodName"));
        assertEquals(name, testcaseByName(parseXml(junit), name).getAttribute("name"));
    }

    @Test
    void fullReportsCanIncludeExclusionAudit() throws Exception {
        CrapReport report = CrapReport.from(
                List.of(metric("safe", "demo.Sample", 4, 1, 100.0, 1.0)),
                Main.DEFAULT_THRESHOLD,
                new SourceExclusionAudit(
                        2,
                        1,
                        List.of(new SourceExclusionAudit.ExclusionCount("default:path:generated-directory", 1)),
                        List.of(new SourceExclusionAudit.ExclusionCount("default:annotation:Generated", 1))
                )
        );

        String json = ReportFormatter.format(report, ReportFormat.JSON);
        String text = ReportFormatter.format(report, ReportFormat.TEXT);
        String junit = ReportFormatter.format(report, ReportFormat.JUNIT);

        assertTrue(json.contains("\"exclusions\""));
        assertTrue(json.contains("\"excludedFiles\": 1"));
        assertTrue(text.contains("Exclusions:"));
        assertEquals("1", propertyValue(parseXml(junit), "exclusion.excludedFiles"));
        assertEquals("1", propertyValue(parseXml(junit), "exclusion.excludedClasses"));
    }

    @Test
    void optimizedPrimaryReportsCanOmitExclusionAudit() {
        CrapReport report = CrapReport.from(
                List.of(metric("danger", "demo.Sample", 4, 5, 10.0, 9.645)),
                Main.DEFAULT_THRESHOLD,
                new SourceExclusionAudit(
                        2,
                        1,
                        List.of(new SourceExclusionAudit.ExclusionCount("default:path:generated-directory", 1)),
                        List.of()
                )
        );

        String json = ReportFormatter.format(report, ReportFormat.JSON, true, true, false);

        assertFalse(json.contains("\"exclusions\""));
    }

    private static CrapReport report(MethodMetrics... metrics) {
        return CrapReport.from(List.of(metrics), Main.DEFAULT_THRESHOLD);
    }

    private static CrapReport reportWithElapsed(double elapsedSeconds, MethodMetrics... metrics) {
        CrapReport report = CrapReport.from(List.of(metrics), Main.DEFAULT_THRESHOLD);
        return new CrapReport(
                report.status(),
                report.threshold(),
                report.methods(),
                report.exclusions(),
                elapsedSeconds
        );
    }

    private static int tableHeaderIndex(List<String> lines, String firstColumn) {
        for (int index = 0; index < lines.size(); index++) {
            String line = lines.get(index);
            if (line.startsWith(firstColumn) && line.contains("Src") && line.contains("CovKind")) {
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

    private static boolean hasTestcaseNameStartingWith(Document document, String prefix) {
        NodeList testcases = document.getElementsByTagName("testcase");
        for (int index = 0; index < testcases.getLength(); index++) {
            Element testcase = (Element) testcases.item(index);
            if (testcase.getAttribute("name").startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    private static String childText(Element element, String tagName) {
        NodeList children = element.getElementsByTagName(tagName);
        if (children.getLength() == 0) {
            throw new AssertionError("Missing XML child: " + tagName);
        }
        return children.item(0).getTextContent();
    }

    private static Element testcaseByName(Document document, String name) {
        NodeList testcases = document.getElementsByTagName("testcase");
        for (int index = 0; index < testcases.getLength(); index++) {
            Element testcase = (Element) testcases.item(index);
            if (name.equals(testcase.getAttribute("name"))) {
                return testcase;
            }
        }
        throw new AssertionError("Missing XML testcase: " + name);
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
