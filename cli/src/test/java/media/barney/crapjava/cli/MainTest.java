package media.barney.crapjava.cli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MainTest {

    @TempDir
    Path tempDir;

    @Test
    void helpWritesUsageToStdout() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();

        int exit = Main.run(new String[]{"--help"}, tempDir, new PrintStream(out), new PrintStream(err));

        assertEquals(0, exit);
        assertTrue(utf8(out).contains("Usage:"));
        assertEquals("", utf8(err));
    }

    @Test
    void invalidArgumentsReturnExitOne() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();

        int exit = Main.run(
                new String[]{"--changed", "src/main/java/demo/Sample.java"},
                tempDir,
                new PrintStream(out),
                new PrintStream(err)
        );

        assertEquals(1, exit);
        assertTrue(utf8(err).contains("--changed cannot be combined with file arguments"));
    }

    private static String utf8(ByteArrayOutputStream output) {
        return output.toString(StandardCharsets.UTF_8);
    }
}
