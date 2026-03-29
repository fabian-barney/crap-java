package media.barney.crapjava.core;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

final class ChangedFileDetector {

    private ChangedFileDetector() {
    }

    static List<Path> changedJavaFiles(Path projectRoot) throws IOException, InterruptedException {
        Process process = new ProcessBuilder("git", "-C", projectRoot.toString(), "status", "--porcelain", "--untracked-files=all")
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

    static List<Path> changedJavaFilesUnderSourceRoots(Path projectRoot) throws IOException, InterruptedException {
        return changedJavaFiles(projectRoot).stream()
                .filter(ProductionSourceRoots::isUnderProductionSourceRoot)
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
