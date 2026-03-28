package media.barney.crap4java.core;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CliArgumentsParserTest {

    @Test
    void noArgsMeansAllSrcFiles() {
        CliArguments args = CliArgumentsParser.parse(new String[]{});
        assertEquals(CliMode.ALL_SRC, args.mode());
        assertEquals(BuildToolSelection.AUTO, args.buildToolSelection());
    }

    @Test
    void changedFlagMeansChangedSrcFiles() {
        CliArguments args = CliArgumentsParser.parse(new String[]{"--changed"});
        assertEquals(CliMode.CHANGED_SRC, args.mode());
        assertEquals(BuildToolSelection.AUTO, args.buildToolSelection());
    }

    @Test
    void fileNamesMeanExplicitFiles() {
        CliArguments args = CliArgumentsParser.parse(new String[]{"src/main/java/demo/A.java", "src/main/java/demo/B.java"});
        assertEquals(CliMode.EXPLICIT_FILES, args.mode());
        assertEquals(BuildToolSelection.AUTO, args.buildToolSelection());
        assertEquals(List.of("src/main/java/demo/A.java", "src/main/java/demo/B.java"), args.fileArgs());
    }

    @Test
    void unknownFlagsAreIgnoredWhenCollectingExplicitFiles() {
        CliArguments args = CliArgumentsParser.parse(new String[]{"src/main/java/demo/A.java", "--bogus", "src/main/java/demo/B.java"});

        assertEquals(CliMode.EXPLICIT_FILES, args.mode());
        assertEquals(List.of("src/main/java/demo/A.java", "src/main/java/demo/B.java"), args.fileArgs());
    }

    @Test
    void buildToolFlagIsParsed() {
        CliArguments args = CliArgumentsParser.parse(new String[]{"--build-tool", "gradle", "--changed"});

        assertEquals(CliMode.CHANGED_SRC, args.mode());
        assertEquals(BuildToolSelection.GRADLE, args.buildToolSelection());
    }

    @Test
    void buildToolRequiresKnownValue() {
        assertThrows(IllegalArgumentException.class,
                () -> CliArgumentsParser.parse(new String[]{"--build-tool", "ant"}));
    }

    @Test
    void buildToolRequiresValue() {
        assertThrows(IllegalArgumentException.class,
                () -> CliArgumentsParser.parse(new String[]{"--build-tool"}));
    }

    @Test
    void buildToolCanOnlyBeProvidedOnce() {
        assertThrows(IllegalArgumentException.class,
                () -> CliArgumentsParser.parse(new String[]{"--build-tool", "maven", "--build-tool", "gradle"}));
    }

    @Test
    void helpPrintsUsageMode() {
        CliArguments args = CliArgumentsParser.parse(new String[]{"--help"});
        assertEquals(CliMode.HELP, args.mode());
    }

    @Test
    void changedCannotBeCombinedWithFiles() {
        assertThrows(IllegalArgumentException.class,
                () -> CliArgumentsParser.parse(new String[]{"--changed", "src/main/java/demo/A.java"}));
    }

    @Test
    void plainFilesDoNotTriggerChangedMode() {
        CliArguments args = CliArgumentsParser.parse(new String[]{"src/main/java/demo/A.java"});

        assertEquals(CliMode.EXPLICIT_FILES, args.mode());
        assertEquals(List.of("src/main/java/demo/A.java"), args.fileArgs());
    }
}
