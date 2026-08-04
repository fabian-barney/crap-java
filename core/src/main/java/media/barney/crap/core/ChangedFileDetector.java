package media.barney.crap.core;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;

final class ChangedFileDetector {

    private static final Duration GIT_STATUS_TIMEOUT = Duration.ofMinutes(10);
    private static final List<String> GIT_COMMAND = List.of("git");

    private ChangedFileDetector() {
    }

    static List<Path> changedJavaFiles(Path projectRoot) throws IOException, InterruptedException {
        return changedJavaFiles(projectRoot, GIT_COMMAND, GIT_STATUS_TIMEOUT);
    }

    static List<Path> changedJavaFiles(Path projectRoot,
                                       List<String> gitCommand,
                                       Duration timeout) throws IOException, InterruptedException {
        List<String> command = gitStatusCommand(projectRoot, gitCommand);
        ProcessTimeout.validate(timeout);
        Process process = new ProcessBuilder(command)
                .redirectErrorStream(true)
                .start();

        process.getOutputStream().close();
        OutputReader outputReader = readOutput(process.getInputStream());
        int exit;
        byte[] outputBytes;
        try {
            exit = ProcessTimeout.waitForOrTerminate(process, command, timeout, "Changed-file detection command");
            outputBytes = outputReader.bytes();
        } catch (IOException | InterruptedException | RuntimeException ex) {
            outputReader.close();
            throw ex;
        }
        String output = new String(outputBytes, StandardCharsets.UTF_8);
        if (exit != 0) {
            throw new IllegalStateException(
                    "Changed-file detection command failed (" + String.join(" ", command) + "): " + output);
        }

        List<Path> files = changedFiles(projectRoot, output);
        files.sort(Path::compareTo);
        return files;
    }

    private static List<Path> changedFiles(Path projectRoot, String output) {
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
            addPath(files, projectRoot, entry);
        }
        return files;
    }

    private static void addPath(List<Path> files, Path projectRoot, StatusEntry entry) {
        Path file = entry.toPath(projectRoot);
        if (file != null) {
            files.add(file);
        }
    }

    private static List<String> gitStatusCommand(Path projectRoot, List<String> gitCommand) {
        List<String> command = new ArrayList<>(gitCommand);
        command.add("-C");
        command.add(projectRoot.toString());
        command.add("status");
        command.add("--porcelain=v1");
        command.add("-z");
        command.add("--untracked-files=all");
        return command;
    }

    private static OutputReader readOutput(InputStream input) {
        OutputReader reader = new OutputReader(input);
        reader.start();
        return reader;
    }

    static List<Path> changedJavaFilesUnderSourceRoots(Path projectRoot) throws IOException, InterruptedException {
        return changedJavaFilesUnderSourceRoots(projectRoot, List.of());
    }

    static List<Path> changedJavaFilesUnderSourceRoots(Path projectRoot, List<Path> sourceRoots)
            throws IOException, InterruptedException {
        return changedJavaFiles(projectRoot).stream()
                .filter(path -> ProductionSourceRoots.isUnderSourceRoot(path, sourceRoots))
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

    private static final class OutputReader {
        private final InputStream input;
        private final Thread thread;
        private byte[] output = new byte[0];
        private @Nullable IOException failure;

        private OutputReader(InputStream input) {
            this.input = input;
            this.thread = new Thread(this::read, "crap-java-git-status-output");
            this.thread.setDaemon(true);
        }

        private void start() {
            thread.start();
        }

        private void read() {
            try (input) {
                output = input.readAllBytes();
            } catch (IOException ex) {
                failure = ex;
            }
        }

        private byte[] bytes() throws IOException, InterruptedException {
            thread.join();
            if (failure != null) {
                throw new IOException("Unable to read git status output", failure);
            }
            return output;
        }

        private void close() {
            try {
                input.close();
            } catch (IOException ex) {
                // Nothing useful can be done while already failing the git status call.
            }
            thread.interrupt();
            try {
                thread.join(ProcessTimeout.CLEANUP_TIMEOUT.toMillis());
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
        }
    }
}

