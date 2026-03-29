package media.barney.crapjava.core;

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

/* mutate4java-manifest
version=1
moduleHash=89c8255a0561b87fdf14286d330be4817d8fb08a347ee2a574ac82c65607fe49
scope.0.id=Y2xhc3M6SmFjb2NvQ292ZXJhZ2VQYXJzZXIjSmFjb2NvQ292ZXJhZ2VQYXJzZXI6MTc
scope.0.kind=class
scope.0.startLine=17
scope.0.endLine=99
scope.0.semanticHash=7a041ae74f905520042a21029844c38d2e98b358cb11a7984966f3d275e08f6e
scope.1.id=bWV0aG9kOkphY29jb0NvdmVyYWdlUGFyc2VyI2N0b3IoMCk6MTk
scope.1.kind=method
scope.1.startLine=19
scope.1.endLine=20
scope.1.semanticHash=61be911b081ef883e381fd6346be6a373947794f858c51ca4541b93bc3741a6d
scope.2.id=bWV0aG9kOkphY29jb0NvdmVyYWdlUGFyc2VyI25ld1NlY3VyZUZhY3RvcnkoMCk6NDk
scope.2.kind=method
scope.2.startLine=49
scope.2.endLine=59
scope.2.semanticHash=c9dc905126becb0e2b7db2c2c6eafc9ea5ddcef64b11ed9fbe0b3531a9ae948e
scope.3.id=bWV0aG9kOkphY29jb0NvdmVyYWdlUGFyc2VyI3BhcnNlKDEpOjIy
scope.3.kind=method
scope.3.startLine=22
scope.3.endLine=47
scope.3.semanticHash=b20f6f004048d7d1a50f968e14d702c8384dd41e1ccbb634774217993407f650
scope.4.id=bWV0aG9kOkphY29jb0NvdmVyYWdlUGFyc2VyI3BhcnNlSW50KDEpOjky
scope.4.kind=method
scope.4.startLine=92
scope.4.endLine=98
scope.4.semanticHash=4fb4b91404f9947d53aba51c47969c26e995c14a9f5bac278d8fff8e64566c4b
scope.5.id=bWV0aG9kOkphY29jb0NvdmVyYWdlUGFyc2VyI3JlYWRDbGFzc01ldGhvZHMoMyk6NjE
scope.5.kind=method
scope.5.startLine=61
scope.5.endLine=75
scope.5.semanticHash=7f3eae1e1b53a1b95e7055fb10d760c7435722007057cc9bd2732afe218a1060
scope.6.id=bWV0aG9kOkphY29jb0NvdmVyYWdlUGFyc2VyI3JlYWRJbnN0cnVjdGlvbkNvdmVyYWdlKDEpOjc3
scope.6.kind=method
scope.6.startLine=77
scope.6.endLine=90
scope.6.semanticHash=5203c9e5151a091f31321e790016a36e0df58cfffe959b853983686f37aa1a86
*/
