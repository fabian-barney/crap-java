package crap4java;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

final class ChangedFileDetector {

    private ChangedFileDetector() {
    }

    static List<Path> changedJavaFiles(Path projectRoot) throws IOException, InterruptedException {
        Process process = new ProcessBuilder("git", "-C", projectRoot.toString(), "status", "--porcelain")
                .redirectErrorStream(true)
                .start();

        int exit = process.waitFor();
        if (exit != 0) {
            String output = new String(process.getInputStream().readAllBytes());
            throw new IllegalStateException("git status failed: " + output);
        }

        String output = new String(process.getInputStream().readAllBytes());
        List<Path> files = new ArrayList<>();
        for (String line : output.split("\\R")) {
            Path file = parseStatusLine(projectRoot, line);
            if (file != null) {
                files.add(file);
            }
        }
        files.sort(Path::compareTo);
        return files;
    }

    static List<Path> changedJavaFilesUnderSrc(Path projectRoot) throws IOException, InterruptedException {
        return changedJavaFiles(projectRoot).stream()
                .filter(path -> path.normalize().startsWith(projectRoot.resolve("src").normalize()))
                .toList();
    }

    private static Path parseStatusLine(Path root, String line) {
        if (!isCandidateLine(line)) {
            return null;
        }
        String pathPart = line.substring(3).trim();
        String finalPath = renameTarget(pathPart);
        if (!isJavaPath(finalPath)) {
            return null;
        }
        return root.resolve(finalPath).normalize();
    }

    static boolean isCandidateLine(String line) {
        if (line == null) {
            return false;
        }
        if (line.isBlank()) {
            return false;
        }
        return line.length() >= 4;
    }

    static String renameTarget(String pathPart) {
        int index = pathPart.indexOf(" -> ");
        if (index < 0) {
            return pathPart;
        }
        return pathPart.substring(index + 4);
    }

    private static boolean isJavaPath(String path) {
        return path.endsWith(".java");
    }
}

/* mutate4java-manifest
version=1
moduleHash=d6a200db9e9478a6b5be1b8ae4ba417ef0bb3feb428bf334a5029462e76fce49
scope.0.id=Y2xhc3M6Q2hhbmdlZEZpbGVEZXRlY3RvciNDaGFuZ2VkRmlsZURldGVjdG9yOjg
scope.0.kind=class
scope.0.startLine=8
scope.0.endLine=75
scope.0.semanticHash=314b1b7d383824f09865a4814f458ca28b3bff55ca405f8d6a14db73bce3d80e
scope.1.id=bWV0aG9kOkNoYW5nZWRGaWxlRGV0ZWN0b3IjY2hhbmdlZEphdmFGaWxlcygxKToxMw
scope.1.kind=method
scope.1.startLine=13
scope.1.endLine=34
scope.1.semanticHash=8e8554ef51ed29e8fbc41e16c0b2279540d453c92ca270f25f1bf1ea7adf058b
scope.2.id=bWV0aG9kOkNoYW5nZWRGaWxlRGV0ZWN0b3IjY2hhbmdlZEphdmFGaWxlc1VuZGVyU3JjKDEpOjM2
scope.2.kind=method
scope.2.startLine=36
scope.2.endLine=40
scope.2.semanticHash=964da5ec5598ea16203adfa61e65258cb353959cca24ffbf3e7016a015ef84c4
scope.3.id=bWV0aG9kOkNoYW5nZWRGaWxlRGV0ZWN0b3IjY3RvcigwKToxMA
scope.3.kind=method
scope.3.startLine=10
scope.3.endLine=11
scope.3.semanticHash=8fe3abf26e99f2bd7186b30f9fe28659467f583d27b11adf97718417babfbefb
scope.4.id=bWV0aG9kOkNoYW5nZWRGaWxlRGV0ZWN0b3IjaXNDYW5kaWRhdGVMaW5lKDEpOjU0
scope.4.kind=method
scope.4.startLine=54
scope.4.endLine=62
scope.4.semanticHash=b63e199ff7b921628fb65cc3b934e3938d92ce5d153227ef5e3e061968408b06
scope.5.id=bWV0aG9kOkNoYW5nZWRGaWxlRGV0ZWN0b3IjaXNKYXZhUGF0aCgxKTo3Mg
scope.5.kind=method
scope.5.startLine=72
scope.5.endLine=74
scope.5.semanticHash=d6a306fcbb03b875c827caa8ff9b65330919721e0247ffc8f67de36907d6b621
scope.6.id=bWV0aG9kOkNoYW5nZWRGaWxlRGV0ZWN0b3IjcGFyc2VTdGF0dXNMaW5lKDIpOjQy
scope.6.kind=method
scope.6.startLine=42
scope.6.endLine=52
scope.6.semanticHash=23c700724f3dd1a6161c3357c22492ad34072b011e35c655633737ff4af4434c
scope.7.id=bWV0aG9kOkNoYW5nZWRGaWxlRGV0ZWN0b3IjcmVuYW1lVGFyZ2V0KDEpOjY0
scope.7.kind=method
scope.7.startLine=64
scope.7.endLine=70
scope.7.semanticHash=29da8c6dd6483a1004615c3364a5b239b115ea8298fe813a0bd5c64f88f8bd2b
*/
