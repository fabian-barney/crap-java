package media.barney.crap.core;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.XMLConstants;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

final class JacocoCoverageParser {
    private static final Pattern JAVAC_LAMBDA_METHOD = Pattern.compile("^lambda\\$.*\\$\\d+$");

    private JacocoCoverageParser() {
    }

    static CoverageIndex parse(@Nullable Path jacocoXmlPath) {
        if (jacocoXmlPath == null || !Files.exists(jacocoXmlPath)) {
            return CoverageIndex.empty();
        }

        try {
            DocumentBuilderFactory factory = newSecureFactory();

            var builder = factory.newDocumentBuilder();
            builder.setEntityResolver((publicId, systemId) -> new InputSource(new StringReader("")));

            Document document = builder.parse(jacocoXmlPath.toFile());
            NodeList classes = document.getElementsByTagName("class");
            CoverageIndex.Builder coverage = CoverageIndex.builder();

            for (int i = 0; i < classes.getLength(); i++) {
                Element classNode = (Element) classes.item(i);
                String className = classNode.getAttribute("name").replace('/', '.');
                readClassMethods(classNode, className, coverage);
            }

            return coverage.build();
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to parse JaCoCo XML: " + jacocoXmlPath, ex);
        }
    }

    static DocumentBuilderFactory newSecureFactory() throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", false);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);
        return factory;
    }

    private static void readClassMethods(Element classNode, String className, CoverageIndex.Builder coverage) {
        for (Node node = classNode.getFirstChild(); node != null; node = node.getNextSibling()) {
            if (!(node instanceof Element method) || !"method".equals(method.getTagName())) {
                continue;
            }
            String methodName = method.getAttribute("name");
            if (JAVAC_LAMBDA_METHOD.matcher(methodName).matches()) {
                continue;
            }
            CoverageData data = readCoverage(method);
            if (data == null) {
                continue;
            }
            int line = parseInt("line", method.getAttribute("line"));
            coverage.add(className, methodName, line, data);
        }
    }

    private static @Nullable CoverageData readCoverage(Element method) {
        CoverageCounter instructionCoverage = null;
        CoverageCounter branchCoverage = null;
        for (Node node = method.getFirstChild(); node != null; node = node.getNextSibling()) {
            if (!(node instanceof Element counter) || !"counter".equals(counter.getTagName())) {
                continue;
            }
            String type = counter.getAttribute("type");
            if ("INSTRUCTION".equals(type)) {
                instructionCoverage = readCounter(counter);
            } else if ("BRANCH".equals(type)) {
                branchCoverage = readCounter(counter);
            }
        }
        if (instructionCoverage == null) {
            return null;
        }
        return new CoverageData(instructionCoverage, branchCoverage);
    }

    private static CoverageCounter readCounter(Element counter) {
        int missed = parseInt("missed", counter.getAttribute("missed"));
        int covered = parseInt("covered", counter.getAttribute("covered"));
        return new CoverageCounter(missed, covered);
    }

    private static int parseInt(String attributeName, String value) {
        try {
            int parsed = Integer.parseInt(value);
            if (parsed < 0) {
                throw new NumberFormatException("negative value");
            }
            return parsed;
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException(
                    "Invalid JaCoCo integer attribute " + attributeName + "=\"" + value + "\"", ex);
        }
    }
}

