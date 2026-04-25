package media.barney.crap.core;

import java.io.InputStream;
import java.io.PrintStream;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

final class ProcessCommandExecutor implements CommandExecutor {

    private static final Duration TERMINATION_TIMEOUT = Duration.ofSeconds(5);

    private final Duration timeout;
    private final PrintStream processOutput;

    ProcessCommandExecutor() {
        this(Duration.ofMinutes(10), System.err);
    }

    ProcessCommandExecutor(Duration timeout) {
        this(timeout, System.err);
    }

    ProcessCommandExecutor(Duration timeout, PrintStream processOutput) {
        this.timeout = timeout;
        this.processOutput = processOutput;
    }

    @Override
    public int run(List<String> command, Path directory) throws Exception {
        Process process = new ProcessBuilder(command)
                .directory(directory.toFile())
                .start();
        process.getOutputStream().close();
        Thread stdout = pipe(process.getInputStream());
        Thread stderr = pipe(process.getErrorStream());
        if (!process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
            process.destroyForcibly();
            if (!process.waitFor(TERMINATION_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)) {
                throw new IllegalStateException(
                        "Command timed out after " + timeout + " and could not be terminated within "
                                + TERMINATION_TIMEOUT + ": " + String.join(" ", command));
            }
            throw new IllegalStateException("Command timed out after " + timeout + ": " + String.join(" ", command));
        }
        stdout.join();
        stderr.join();
        return process.exitValue();
    }

    private Thread pipe(InputStream input) {
        Thread thread = new Thread(() -> {
            try (input) {
                input.transferTo(processOutput);
            } catch (Exception ex) {
                processOutput.println("Failed to read process output: " + ex.getMessage());
            }
        }, "crap-java-process-output");
        thread.setDaemon(true);
        thread.start();
        return thread;
    }
}

