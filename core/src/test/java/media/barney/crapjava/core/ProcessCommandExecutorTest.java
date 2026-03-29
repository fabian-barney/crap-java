package media.barney.crapjava.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProcessCommandExecutorTest {

    @TempDir
    Path tempDir;

    @Test
    void returnsExitCodeFromLaunchedProcess() throws Exception {
        int exit = new ProcessCommandExecutor().run(exitCommand(7), tempDir);

        assertEquals(7, exit);
    }

    @Test
    void timesOutLongRunningProcesses() {
        ProcessCommandExecutor executor = new ProcessCommandExecutor(Duration.ofMillis(100));

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> executor.run(sleepCommand(), tempDir));

        String message = Objects.requireNonNull(error.getMessage());
        assertTrue(message.contains("Command timed out"));
        assertTrue(message.contains(Duration.ofMillis(100).toString()));
        assertTrue(message.contains(String.join(" ", sleepCommand())));
    }

    private static List<String> exitCommand(int exitCode) {
        if (System.getProperty("os.name").toLowerCase(Locale.ROOT).startsWith("windows")) {
            return List.of("cmd", "/c", "exit " + exitCode);
        }
        return List.of("sh", "-c", "exit " + exitCode);
    }

    private static List<String> sleepCommand() {
        if (System.getProperty("os.name").toLowerCase(Locale.ROOT).startsWith("windows")) {
            return List.of("powershell", "-Command", "Start-Sleep -Seconds 5");
        }
        return List.of("sh", "-c", "sleep 5");
    }
}
