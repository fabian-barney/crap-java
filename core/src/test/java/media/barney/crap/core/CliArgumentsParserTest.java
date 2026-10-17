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
    void buildAndReportOptionsAcceptInlineAssignments() {
        CliArguments args = CliArgumentsParser.parse(new String[]{
                "--build-tool=maven",
                "--format=json",
                "--output=target/crap-java/report.json",
                "--junit-report=target/crap-java/TEST-crap-java.xml",
                "--changed"
        });

        assertEquals(CliMode.CHANGED_SRC, args.mode());
        assertEquals(BuildToolSelection.MAVEN, args.buildToolSelection());
        assertEquals(ReportFormat.JSON, args.reportFormat());
        assertEquals("target/crap-java/report.json", args.outputPath());
        assertEquals("target/crap-java/TEST-crap-java.xml", args.junitReportPath());
    }

    @Test
    void thresholdAcceptsInlineAssignment() {
        CliArguments args = CliArgumentsParser.parse(new String[]{"--threshold=6.0", "--changed"});

        assertEquals(CliMode.CHANGED_SRC, args.mode());
        assertEquals(6.0, args.threshold(), 1.0e-9);
    }

    @Test
    void sourceRootsAreParsed() {
        CliArguments args = CliArgumentsParser.parse(new String[]{
                "--source-root=src/java",
                "--source-root",
                "module-a/src/main/custom-java",
                "--changed"
        });

        assertEquals(List.of("src/java", "module-a/src/main/custom-java"), args.sourceRoots());
    }

    @Test
    void sourceExclusionOptionsAcceptInlineAssignments() {
        CliArguments args = CliArgumentsParser.parse(new String[]{
                "--exclude=module-a/**",
                "--exclude-class=.*MapperImpl$",
                "--exclude-annotation=Generated",
                "--changed"
        });

        assertEquals(List.of("module-a/**"), args.exclusionOptions().excludes());
        assertEquals(List.of(".*MapperImpl$"), args.exclusionOptions().excludeClasses());
        assertEquals(List.of("Generated"), args.exclusionOptions().excludeAnnotations());
    }

    @Test
    void sourceExclusionOptionsAreParsed() {
        CliArguments args = CliArgumentsParser.parse(new String[]{
                "--exclude", "module-a/**",
                "--exclude", "**/generated/**",
                "--exclude-class", ".*MapperImpl$",
                "--exclude-annotation", "Generated",
                "--use-default-exclusions=false",
                "--changed"
        });

        assertEquals(List.of("module-a/**", "**/generated/**"), args.exclusionOptions().excludes());
        assertEquals(List.of(".*MapperImpl$"), args.exclusionOptions().excludeClasses());
        assertEquals(List.of("Generated"), args.exclusionOptions().excludeAnnotations());
        assertFalse(args.exclusionOptions().useDefaultExclusions());
    }

    @Test
    void agentModeDefaultsToToon() {
        CliArguments args = CliArgumentsParser.parse(new String[]{"--agent", "--changed"});

        assertEquals(CliMode.CHANGED_SRC, args.mode());
        assertEquals(ReportFormat.TOON, args.reportFormat());
        assertTrue(args.agent());
        assertTrue(args.failuresOnly());
        assertTrue(args.omitRedundancy());
    }

    @Test
    void agentModeAllowsPrimaryFormatJunitSidecarAndThresholdOverrides() {
        CliArguments args = CliArgumentsParser.parse(new String[]{
                "--agent",
                "--format", "junit",
                "--junit-report", "target/crap-java/TEST-crap-java.xml",
                "--threshold", "6.0",
                "--changed"
        });

        assertEquals(ReportFormat.JUNIT, args.reportFormat());
        assertEquals("target/crap-java/TEST-crap-java.xml", args.junitReportPath());
        assertEquals(6.0, args.threshold());
        assertTrue(args.agent());
        assertTrue(args.failuresOnly());
        assertTrue(args.omitRedundancy());
    }

    @Test
    void agentModeAllowsExplicitBooleanOverrides() {
        CliArguments args = CliArgumentsParser.parse(new String[]{
                "--agent",
                "--failures-only=false",
                "--omit-redundancy=false",
                "--changed"
        });

        assertTrue(args.agent());
        assertFalse(args.failuresOnly());
        assertFalse(args.omitRedundancy());
    }

    @Test
    void agentModeCanOnlyBeProvidedOnce() {
        assertThrows(IllegalArgumentException.class,
                () -> CliArgumentsParser.parse(new String[]{"--agent", "--agent"}));
    }

    @Test
    void agentModeAllowsExplicitFailuresOnlyFalseBeforeAgent() {
        CliArguments args = CliArgumentsParser.parse(new String[]{"--failures-only=false", "--agent", "--changed"});

        assertTrue(args.agent());
        assertFalse(args.failuresOnly());
        assertTrue(args.omitRedundancy());
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

        assertTrue(enabled.failuresOnly());
        assertFalse(disabled.failuresOnly());
    }

    @Test
    void failuresOnlyFlagRejectsInvalidAssignments() {
        assertThrows(IllegalArgumentException.class,
                () -> CliArgumentsParser.parse(new String[]{"--failures-only=yes", "--changed"}));
        assertThrows(IllegalArgumentException.class,
                () -> CliArgumentsParser.parse(new String[]{"--failures-only=", "--changed"}));
        assertThrows(IllegalArgumentException.class,
                () -> CliArgumentsParser.parse(new String[]{"--failures-only=TRUE", "--changed"}));
        assertThrows(IllegalArgumentException.class,
                () -> CliArgumentsParser.parse(new String[]{"--failures-only=False", "--changed"}));
        assertThrows(IllegalArgumentException.class,
                () -> CliArgumentsParser.parse(new String[]{"--failures-only=FALSE", "--changed"}));
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

        assertTrue(enabled.omitRedundancy());
        assertFalse(disabled.omitRedundancy());
    }

    @Test
    void omitRedundancyFlagRejectsInvalidAssignments() {
        assertThrows(IllegalArgumentException.class,
                () -> CliArgumentsParser.parse(new String[]{"--omit-redundancy=yes", "--changed"}));
        assertThrows(IllegalArgumentException.class,
                () -> CliArgumentsParser.parse(new String[]{"--omit-redundancy=", "--changed"}));
        assertThrows(IllegalArgumentException.class,
                () -> CliArgumentsParser.parse(new String[]{"--omit-redundancy=TRUE", "--changed"}));
        assertThrows(IllegalArgumentException.class,
                () -> CliArgumentsParser.parse(new String[]{"--omit-redundancy=False", "--changed"}));
        assertThrows(IllegalArgumentException.class,
                () -> CliArgumentsParser.parse(new String[]{"--omit-redundancy=FALSE", "--changed"}));
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
    void noneReportFormatIsParsed() {
        CliArguments args = CliArgumentsParser.parse(new String[]{"--format", "none", "--changed"});

        assertEquals(ReportFormat.NONE, args.reportFormat());
    }

    @Test
    void reportFormatRequiresValue() {
        assertParseError("--format requires one of: toon, json, text, junit, none", "--format");
        assertParseError("--format requires one of: toon, json, text, junit, none", "--format=");
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
        assertParseError("--threshold requires a finite number greater than 0", "--threshold");
        assertParseError("--threshold requires a finite number greater than 0", "--threshold=");
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
        assertParseError("--output requires a path", "--output");
        assertParseError("--output requires a path", "--output=");
    }

    @Test
    void outputCanOnlyBeProvidedOnce() {
        assertThrows(IllegalArgumentException.class,
                () -> CliArgumentsParser.parse(new String[]{"--output", "one.json", "--output", "two.json"}));
    }

    @Test
    void junitReportRequiresValue() {
        assertParseError("--junit-report requires a path", "--junit-report");
        assertParseError("--junit-report requires a path", "--junit-report=");
    }

    @Test
    void junitReportCanOnlyBeProvidedOnce() {
        assertThrows(IllegalArgumentException.class,
                () -> CliArgumentsParser.parse(new String[]{"--junit-report", "one.xml", "--junit-report", "two.xml"}));
    }

    @Test
    void sourceExclusionOptionsRequireValues() {
        assertThrows(IllegalArgumentException.class,
                () -> CliArgumentsParser.parse(new String[]{"--exclude"}));
        assertThrows(IllegalArgumentException.class,
                () -> CliArgumentsParser.parse(new String[]{"--exclude-class"}));
        assertThrows(IllegalArgumentException.class,
                () -> CliArgumentsParser.parse(new String[]{"--exclude-annotation"}));
        assertThrows(IllegalArgumentException.class,
                () -> CliArgumentsParser.parse(new String[]{"--source-root"}));
        assertThrows(IllegalArgumentException.class,
                () -> CliArgumentsParser.parse(new String[]{"--use-default-exclusions=maybe"}));
        assertThrows(IllegalArgumentException.class,
                () -> CliArgumentsParser.parse(new String[]{"--exclude="}));
        assertThrows(IllegalArgumentException.class,
                () -> CliArgumentsParser.parse(new String[]{"--exclude-class="}));
        assertThrows(IllegalArgumentException.class,
                () -> CliArgumentsParser.parse(new String[]{"--exclude-annotation="}));
        assertThrows(IllegalArgumentException.class,
                () -> CliArgumentsParser.parse(new String[]{"--source-root="}));
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
        assertThrows(IllegalArgumentException.class,
                () -> CliArgumentsParser.parse(new String[]{"--build-tool="}));
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

    private static void assertParseError(String expectedMessage, String... args) {
        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> CliArgumentsParser.parse(args)
        );
        assertEquals(expectedMessage, error.getMessage());
    }
}

