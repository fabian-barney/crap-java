package media.barney.crap.core;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;

final class ChangedFileDetector {

    private ChangedFileDetector() {
    }

    static List<Path> changedJavaFiles(Path projectRoot) throws IOException, InterruptedException {
        Process process = new ProcessBuilder("git", "-C", projectRoot.toString(), "status", "--porcelain=v1", "-z", "--untracked-files=all")
                .redirectErrorStream(true)
                .start();

        byte[] outputBytes = process.getInputStream().readAllBytes();
        int exit = process.waitFor();
        String output = new String(outputBytes, StandardCharsets.UTF_8);
        if (exit != 0) {
            throw new IllegalStateException("git status failed: " + output);
        }

        List<Path> files = new ArrayList<>();
        List<String> entries = nullDelimitedEntries(output);
        for (int index = 0; index < entries.size(); index++) {
            StatusEntry entry = parseStatusEntry(entries.get(index));
            if (entry == null) {
                continue;
            }
            if (entry.hasOriginalPathToken()) {
                index++;
            }
            Path file = entry.toPath(projectRoot);
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

    private static List<String> nullDelimitedEntries(String output) {
        List<String> entries = new ArrayList<>();
        int start = 0;
        while (start < output.length()) {
            int separator = output.indexOf('\0', start);
            if (separator < 0) {
                break;
            }
            entries.add(output.substring(start, separator));
            start = separator + 1;
        }
        return entries;
    }

    private static @Nullable StatusEntry parseStatusEntry(String entry) {
        if (entry.length() < 4 || entry.charAt(2) != ' ') {
            return null;
        }
        String status = entry.substring(0, 2);
        String path = entry.substring(3);
        return new StatusEntry(status, path, hasOriginalPathToken(status));
    }

    private static boolean hasOriginalPathToken(String status) {
        return status.indexOf('R') >= 0 || status.indexOf('C') >= 0;
    }

    private static boolean isJavaPath(String path) {
        return path.endsWith(".java");
    }

    private record StatusEntry(String status, String path, boolean hasOriginalPathToken) {

        private @Nullable Path toPath(Path root) {
            if (!isCandidate() || !isJavaPath(path)) {
                return null;
            }
            return root.resolve(path).normalize();
        }

        private boolean isCandidate() {
            if ("!!".equals(status) || status.indexOf('D') >= 0) {
                return false;
            }
            return "??".equals(status) || status.charAt(0) != ' ' || status.charAt(1) != ' ';
        }
    }
}

