package media.barney.crap.core;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CliArgumentsParserTest {

    @Test
    void noArgsMeansAllSrcFiles() {
        CliArguments args = CliArgumentsParser.parse(new String[]{});
        assertEquals(CliMode.ALL_SRC, args.mode());
        assertEquals(BuildToolSelection.AUTO, args.buildToolSelection());
        assertEquals(ReportFormat.TOON, args.reportFormat());
        assertEquals(Main.DEFAULT_THRESHOLD, args.threshold());
        assertFalse(args.agent());
        assertFalse(args.failuresOnly());
        assertFalse(args.omitRedundancy());
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
        assertEquals(ReportFormat.TOON, args.reportFormat());
        assertEquals(List.of("src/main/java/demo/A.java", "src/main/java/demo/B.java"), args.fileArgs());
    }

    @Test
    void unknownFlagsFailFast() {
        assertThrows(IllegalArgumentException.class,
                () -> CliArgumentsParser.parse(new String[]{"src/main/java/demo/A.java", "--bogus"}));
    }

    @Test
    void buildToolFlagIsParsed() {
        CliArguments args = CliArgumentsParser.parse(new String[]{"--build-tool", "gradle", "--changed"});

        assertEquals(CliMode.CHANGED_SRC, args.mode());
        assertEquals(BuildToolSelection.GRADLE, args.buildToolSelection());
    }

    @Test
    void reportOptionsAreParsed() {
        CliArguments args = CliArgumentsParser.parse(new String[]{
                "--format", "json",
                "--output", "target/crap-java/report.json",
                "--junit-report", "target/crap-java/TEST-crap-java.xml",
                "src/main/java/demo/A.java"
        });

        assertEquals(ReportFormat.JSON, args.reportFormat());
        assertEquals(Main.DEFAULT_THRESHOLD, args.threshold());
        assertFalse(args.agent());
        assertFalse(args.failuresOnly());
        assertFalse(args.omitRedundancy());
        assertEquals("target/crap-java/report.json", args.outputPath());
        assertEquals("target/crap-java/TEST-crap-java.xml", args.junitReportPath());
        assertEquals(List.of("src/main/java/demo/A.java"), args.fileArgs());
    }

    @Test
    void agentModeDefaultsToToon() {
        CliArguments args = CliArgumentsParser.parse(new String[]{"--agent", "--changed"});

        assertEquals(CliMode.CHANGED_SRC, args.mode());
        assertEquals(ReportFormat.TOON, args.reportFormat());
        assertTrue(args.agent());
    }

    @Test
    void agentModeAllowsNonJunitPrimaryFormatsJunitSidecarAndThreshold() {
        CliArguments args = CliArgumentsParser.parse(new String[]{
                "--agent",
                "--format", "text",
                "--junit-report", "target/crap-java/TEST-crap-java.xml",
                "--threshold", "6.0",
                "--changed"
        });

        assertEquals(ReportFormat.TEXT, args.reportFormat());
        assertEquals("target/crap-java/TEST-crap-java.xml", args.junitReportPath());
        assertEquals(6.0, args.threshold());
        assertTrue(args.agent());
    }

    @Test
    void agentModeRejectsJunitPrimaryFormat() {
        assertThrows(IllegalArgumentException.class,
                () -> CliArgumentsParser.parse(new String[]{"--agent", "--format", "junit"}));
        assertThrows(IllegalArgumentException.class,
                () -> CliArgumentsParser.parse(new String[]{"--format", "junit", "--agent"}));
    }

    @Test
    void agentModeCanOnlyBeProvidedOnce() {
        assertThrows(IllegalArgumentException.class,
                () -> CliArgumentsParser.parse(new String[]{"--agent", "--agent"}));
    }

    @Test
    void agentModeRejectsExplicitFailuresOnlyFalse() {
        assertThrows(IllegalArgumentException.class,
                () -> CliArgumentsParser.parse(new String[]{"--agent", "--failures-only=false"}));
        assertThrows(IllegalArgumentException.class,
                () -> CliArgumentsParser.parse(new String[]{"--failures-only=false", "--agent"}));
    }

    @Test
    void failuresOnlyFlagDefaultsToTrueWhenUnassigned() {
        CliArguments args = CliArgumentsParser.parse(new String[]{"--failures-only", "--changed"});

        assertEquals(CliMode.CHANGED_SRC, args.mode());
        assertTrue(args.failuresOnly());
    }

    @Test
    void failuresOnlyFlagAcceptsExplicitBooleanAssignments() {
        CliArguments enabled = CliArgumentsParser.parse(new String[]{"--failures-only=true", "--changed"});
        CliArguments disabled = CliArgumentsParser.parse(new String[]{"--failures-only=false", "--changed"});
        CliArguments enabledUppercase = CliArgumentsParser.parse(new String[]{"--failures-only=TRUE", "--changed"});
        CliArguments disabledUppercase = CliArgumentsParser.parse(new String[]{"--failures-only=FALSE", "--changed"});

        assertTrue(enabled.failuresOnly());
        assertFalse(disabled.failuresOnly());
        assertTrue(enabledUppercase.failuresOnly());
        assertFalse(disabledUppercase.failuresOnly());
    }

    @Test
    void failuresOnlyFlagRejectsInvalidAssignments() {
        assertThrows(IllegalArgumentException.class,
                () -> CliArgumentsParser.parse(new String[]{"--failures-only=yes", "--changed"}));
        assertThrows(IllegalArgumentException.class,
                () -> CliArgumentsParser.parse(new String[]{"--failures-only=", "--changed"}));
    }

    @Test
    void failuresOnlyFlagCanOnlyBeProvidedOnce() {
        assertThrows(IllegalArgumentException.class,
                () -> CliArgumentsParser.parse(new String[]{"--failures-only", "--failures-only=false"}));
    }

    @Test
    void omitRedundancyFlagDefaultsToTrueWhenUnassigned() {
        CliArguments args = CliArgumentsParser.parse(new String[]{"--omit-redundancy", "--changed"});

        assertEquals(CliMode.CHANGED_SRC, args.mode());
        assertTrue(args.omitRedundancy());
    }

    @Test
    void omitRedundancyFlagAcceptsExplicitBooleanAssignments() {
        CliArguments enabled = CliArgumentsParser.parse(new String[]{"--omit-redundancy=true", "--changed"});
        CliArguments disabled = CliArgumentsParser.parse(new String[]{"--omit-redundancy=false", "--changed"});
        CliArguments enabledUppercase = CliArgumentsParser.parse(new String[]{"--omit-redundancy=TRUE", "--changed"});
        CliArguments disabledUppercase = CliArgumentsParser.parse(new String[]{"--omit-redundancy=FALSE", "--changed"});

        assertTrue(enabled.omitRedundancy());
        assertFalse(disabled.omitRedundancy());
        assertTrue(enabledUppercase.omitRedundancy());
        assertFalse(disabledUppercase.omitRedundancy());
    }

    @Test
    void omitRedundancyFlagRejectsInvalidAssignments() {
        assertThrows(IllegalArgumentException.class,
                () -> CliArgumentsParser.parse(new String[]{"--omit-redundancy=yes", "--changed"}));
        assertThrows(IllegalArgumentException.class,
                () -> CliArgumentsParser.parse(new String[]{"--omit-redundancy=", "--changed"}));
    }

    @Test
    void omitRedundancyFlagCanOnlyBeProvidedOnce() {
        assertThrows(IllegalArgumentException.class,
                () -> CliArgumentsParser.parse(new String[]{"--omit-redundancy", "--omit-redundancy=false"}));
    }

    @Test
    void reportFormatRequiresKnownValue() {
        assertThrows(IllegalArgumentException.class,
                () -> CliArgumentsParser.parse(new String[]{"--format", "yaml"}));
    }

    @Test
    void reportFormatRequiresValue() {
        assertThrows(IllegalArgumentException.class,
                () -> CliArgumentsParser.parse(new String[]{"--format"}));
    }

    @Test
    void reportFormatCanOnlyBeProvidedOnce() {
        assertThrows(IllegalArgumentException.class,
                () -> CliArgumentsParser.parse(new String[]{"--format", "json", "--format", "toon"}));
    }

    @Test
    void thresholdIsParsed() {
        CliArguments args = CliArgumentsParser.parse(new String[]{"--threshold", "6.0", "--changed"});

        assertEquals(CliMode.CHANGED_SRC, args.mode());
        assertEquals(6.0, args.threshold());
    }

    @Test
    void thresholdRequiresValue() {
        assertThrows(IllegalArgumentException.class,
                () -> CliArgumentsParser.parse(new String[]{"--threshold"}));
    }

    @Test
    void thresholdCanOnlyBeProvidedOnce() {
        assertThrows(IllegalArgumentException.class,
                () -> CliArgumentsParser.parse(new String[]{"--threshold", "6", "--threshold", "8"}));
    }

    @Test
    void thresholdRequiresFinitePositiveNumber() {
        assertThrows(IllegalArgumentException.class,
                () -> CliArgumentsParser.parse(new String[]{"--threshold", "0"}));
        assertThrows(IllegalArgumentException.class,
                () -> CliArgumentsParser.parse(new String[]{"--threshold", "-1"}));
        assertThrows(IllegalArgumentException.class,
                () -> CliArgumentsParser.parse(new String[]{"--threshold", "NaN"}));
        assertThrows(IllegalArgumentException.class,
                () -> CliArgumentsParser.parse(new String[]{"--threshold", "Infinity"}));
        assertThrows(IllegalArgumentException.class,
                () -> CliArgumentsParser.parse(new String[]{"--threshold", "low"}));
    }

    @Test
    void outputRequiresValue() {
        assertThrows(IllegalArgumentException.class,
                () -> CliArgumentsParser.parse(new String[]{"--output"}));
    }

    @Test
    void outputCanOnlyBeProvidedOnce() {
        assertThrows(IllegalArgumentException.class,
                () -> CliArgumentsParser.parse(new String[]{"--output", "one.json", "--output", "two.json"}));
    }

    @Test
    void junitReportRequiresValue() {
        assertThrows(IllegalArgumentException.class,
                () -> CliArgumentsParser.parse(new String[]{"--junit-report"}));
    }

    @Test
    void junitReportCanOnlyBeProvidedOnce() {
        assertThrows(IllegalArgumentException.class,
                () -> CliArgumentsParser.parse(new String[]{"--junit-report", "one.xml", "--junit-report", "two.xml"}));
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

