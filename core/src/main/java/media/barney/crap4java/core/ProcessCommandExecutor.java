package media.barney.crap4java.core;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

final class ProcessCommandExecutor implements CommandExecutor {

    private final Duration timeout;

    ProcessCommandExecutor() {
        this(Duration.ofMinutes(10));
    }

    ProcessCommandExecutor(Duration timeout) {
        this.timeout = timeout;
    }

    @Override
    public int run(List<String> command, Path directory) throws Exception {
        Process process = new ProcessBuilder(command)
                .directory(directory.toFile())
                .inheritIO()
                .start();
        if (!process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
            process.destroyForcibly();
            throw new IllegalStateException("Command timed out after " + timeout.toSeconds() + " seconds: " + command.get(0));
        }
        return process.exitValue();
    }
}
