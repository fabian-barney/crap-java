package media.barney.crap4java.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Locale;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ProcessCommandExecutorTest {

    @TempDir
    Path tempDir;

    @Test
    void returnsExitCodeFromLaunchedProcess() throws Exception {
        int exit = new ProcessCommandExecutor().run(exitCommand(7), tempDir);

        assertEquals(7, exit);
    }

    private static List<String> exitCommand(int exitCode) {
        if (System.getProperty("os.name").toLowerCase(Locale.ROOT).startsWith("windows")) {
            return List.of("cmd", "/c", "exit " + exitCode);
        }
        return List.of("sh", "-c", "exit " + exitCode);
    }
}
