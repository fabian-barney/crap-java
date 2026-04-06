package media.barney.crap.core;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.XMLConstants;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.io.StringReader;
import org.jspecify.annotations.Nullable;

final class JacocoCoverageParser {

    private JacocoCoverageParser() {
    }

    static Map<String, CoverageData> parse(@Nullable Path jacocoXmlPath) {
        if (jacocoXmlPath == null || !Files.exists(jacocoXmlPath)) {
            return Map.of();
        }

        try {
            DocumentBuilderFactory factory = newSecureFactory();

            var builder = factory.newDocumentBuilder();
            builder.setEntityResolver((publicId, systemId) -> new InputSource(new StringReader("")));

            Document document = builder.parse(jacocoXmlPath.toFile());
            NodeList classes = document.getElementsByTagName("class");
            Map<String, CoverageData> coverage = new HashMap<>();

            for (int i = 0; i < classes.getLength(); i++) {
                Element classNode = (Element) classes.item(i);
                String className = classNode.getAttribute("name").replace('/', '.');
                readClassMethods(classNode, className, coverage);
            }

            return coverage;
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

    private static void readClassMethods(Element classNode, String className, Map<String, CoverageData> coverage) {
        for (Node node = classNode.getFirstChild(); node != null; node = node.getNextSibling()) {
            if (!(node instanceof Element method) || !"method".equals(method.getTagName())) {
                continue;
            }
            CoverageData data = readInstructionCoverage(method);
            if (data == null) {
                continue;
            }
            String methodName = method.getAttribute("name");
            int line = parseInt(method.getAttribute("line"));
            String key = className + "#" + methodName + ":" + line;
            coverage.put(key, data);
        }
    }

    private static @Nullable CoverageData readInstructionCoverage(Element method) {
        for (Node node = method.getFirstChild(); node != null; node = node.getNextSibling()) {
            if (!(node instanceof Element counter) || !"counter".equals(counter.getTagName())) {
                continue;
            }
            if (!"INSTRUCTION".equals(counter.getAttribute("type"))) {
                continue;
            }
            int missed = parseInt(counter.getAttribute("missed"));
            int covered = parseInt(counter.getAttribute("covered"));
            return new CoverageData(missed, covered);
        }
        return null;
    }

    private static int parseInt(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ex) {
            return 0;
        }
    }
}

