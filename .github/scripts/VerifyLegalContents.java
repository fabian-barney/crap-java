import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import javax.xml.parsers.DocumentBuilderFactory;

final class VerifyLegalContents {
    private static final String PROJECT_LICENSE = "META-INF/LICENSE-crap-java";

    private VerifyLegalContents() {
    }

    public static void main(String[] args) throws Exception {
        Path repository = Path.of("").toAbsolutePath().normalize();
        String version = projectVersion(repository.resolve("pom.xml"));
        byte[] projectLicense = Files.readAllBytes(repository.resolve("LICENSE"));
        List<String> failures = new ArrayList<>();

        verifyProjectLicense(repository.resolve("core/target/crap-java-core-" + version + ".jar"), projectLicense, failures);
        Path cliJar = repository.resolve("cli/target/crap-java-cli-" + version + ".jar");
        verifyProjectLicense(cliJar, projectLicense, failures);
        verifyProjectLicense(repository.resolve("maven-plugin/target/crap-java-maven-plugin-" + version + ".jar"), projectLicense, failures);
        verifyProjectLicense(repository.resolve("gradle-plugin/build/libs/crap-java-gradle-plugin-" + version + ".jar"), projectLicense, failures);
        verifyCliLegalResources(cliJar, repository, failures);

        if (!failures.isEmpty()) {
            failures.forEach(failure -> System.err.println("ERROR: " + failure));
            throw new IllegalStateException("Legal archive verification failed with " + failures.size() + " error(s)");
        }
        System.out.println("Verified project and third-party legal content in all four binary JARs.");
    }

    private static String projectVersion(Path pom) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        var document = factory.newDocumentBuilder().parse(pom.toFile());
        return document.getDocumentElement().getElementsByTagName("version").item(0).getTextContent().trim();
    }

    private static void verifyProjectLicense(Path jarPath, byte[] expected, List<String> failures) {
        withJar(jarPath, failures, jar -> {
            requireSingleEntry(jar, PROJECT_LICENSE, failures);
            byte[] actual = readEntry(jar, PROJECT_LICENSE);
            if (!Arrays.equals(expected, actual)) {
                failures.add(jarPath + " does not contain the exact root Apache-2.0 license");
            }
        });
    }

    private static void verifyCliLegalResources(Path jarPath, Path repository, List<String> failures) {
        withJar(jarPath, failures, jar -> {
            requireFile(jar, "META-INF/LICENSE-jtoon",
                    repository.resolve("cli/src/main/resources/META-INF/LICENSE-jtoon"), failures);
            requireFile(jar, "META-INF/LICENSE-stax2-api",
                    repository.resolve("cli/src/main/resources/META-INF/LICENSE-stax2-api"), failures);
            requireSha256(jar, "META-INF/FastDoubleParser-LICENSE",
                    "59067508E44346BF93DC9C5E4D673A67ECD77988C2020BB5E0F8667EB199F396", failures);
            requireSha256(jar, "META-INF/FastDoubleParser-ThirdParty-LICENSE",
                    "DBC36F9422A40D58D1A214080583667CFD37D61C2FAE1657D981EDDA2A60727F", failures);
            requireSha256(jar, "META-INF/Schubfach-LICENSE",
                    "12D39A6615219D73202300715E365BEC2B958F858D1E837C8F2DACE57F905969", failures);
            requireText(jar, "META-INF/NOTICE", "Jackson 2.x", failures);
            requireText(jar, "META-INF/NOTICE", "Jackson 3.x", failures);
            requireText(jar, "META-INF/NOTICE", "FastDoubleParser", failures);
            requireText(jar, "META-INF/NOTICE", "Schubfach", failures);
        });
    }

    private static void requireFile(JarFile jar, String name, Path source, List<String> failures) throws IOException {
        requireSingleEntry(jar, name, failures);
        if (!Arrays.equals(Files.readAllBytes(source), readEntry(jar, name))) {
            failures.add(jar.getName() + " entry " + name + " differs from " + source);
        }
    }

    private static void requireSha256(JarFile jar, String name, String expected, List<String> failures) throws Exception {
        requireSingleEntry(jar, name, failures);
        String actual = HexFormat.of().withUpperCase()
                .formatHex(MessageDigest.getInstance("SHA-256").digest(readEntry(jar, name)));
        if (!expected.equals(actual)) {
            failures.add(jar.getName() + " entry " + name + " has unexpected SHA-256 " + actual);
        }
    }

    private static void requireText(JarFile jar, String name, String marker, List<String> failures) throws IOException {
        requireSingleEntry(jar, name, failures);
        JarEntry entry = jar.getJarEntry(name);
        if (entry == null) {
            return;
        }
        String content = new String(readEntry(jar, name), java.nio.charset.StandardCharsets.UTF_8);
        if (!content.contains(marker)) {
            failures.add(jar.getName() + " entry " + name + " is missing required attribution: " + marker);
        }
    }

    private static void requireSingleEntry(JarFile jar, String name, List<String> failures) {
        long count = jar.stream().map(JarEntry::getName).filter(name::equals).count();
        if (count != 1) {
            failures.add(jar.getName() + " must contain exactly one " + name + " entry, found " + count);
        }
    }

    private static byte[] readEntry(JarFile jar, String name) throws IOException {
        JarEntry entry = jar.getJarEntry(name);
        if (entry == null) {
            return new byte[0];
        }
        try (InputStream input = jar.getInputStream(entry)) {
            return input.readAllBytes();
        }
    }

    private static void withJar(Path jarPath, List<String> failures, JarCheck check) {
        if (!Files.isRegularFile(jarPath)) {
            failures.add("missing binary JAR: " + jarPath);
            return;
        }
        try (JarFile jar = new JarFile(jarPath.toFile())) {
            check.verify(jar);
        } catch (Exception exception) {
            failures.add("could not inspect " + jarPath + ": " + exception.getMessage());
        }
    }

    @FunctionalInterface
    private interface JarCheck {
        void verify(JarFile jar) throws Exception;
    }
}
