package media.barney.crap.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.jspecify.annotations.Nullable;

final class CliArgumentsParser {

    private CliArgumentsParser() {
    }

    static CliArguments parse(String[] args) {
        if (args.length == 0) {
            return new CliArguments(
                    CliMode.ALL_SRC,
                    BuildToolSelection.AUTO,
                    ReportFormat.TOON,
                    Thresholds.DEFAULT,
                    false,
                    false,
                    false,
                    null,
                    null,
                    List.of()
            );
        }

        ParseState state = parseState(args);
        if (state.help) {
            return new CliArguments(
                    CliMode.HELP,
                    state.buildToolSelection,
                    state.reportFormat,
                    state.threshold,
                    state.agent,
                    state.failuresOnly,
                    state.omitRedundancy,
                    state.outputPath,
                    state.junitReportPath,
                    List.of()
            );
        }
        boolean changed = state.changed;
        List<String> values = state.fileArgs;
        ensureChangedIsNotCombined(changed, values);
        if (changed) {
            return new CliArguments(
                    CliMode.CHANGED_SRC,
                    state.buildToolSelection,
                    state.reportFormat,
                    state.threshold,
                    state.agent,
                    state.failuresOnly,
                    state.omitRedundancy,
                    state.outputPath,
                    state.junitReportPath,
                    List.of()
            );
        }
        if (values.isEmpty()) {
            return new CliArguments(
                    CliMode.ALL_SRC,
                    state.buildToolSelection,
                    state.reportFormat,
                    state.threshold,
                    state.agent,
                    state.failuresOnly,
                    state.omitRedundancy,
                    state.outputPath,
                    state.junitReportPath,
                    List.of()
            );
        }
        return new CliArguments(
                CliMode.EXPLICIT_FILES,
                state.buildToolSelection,
                state.reportFormat,
                state.threshold,
                state.agent,
                state.failuresOnly,
                state.omitRedundancy,
                state.outputPath,
                state.junitReportPath,
                List.copyOf(values)
        );
    }

    private static ParseState parseState(String[] args) {
        ParseStateBuilder state = new ParseStateBuilder();
        for (int index = 0; index < args.length; index++) {
            index = parseArg(args, index, state);
        }
        return state.build();
    }

    private static int parseArg(String[] args, int index, ParseStateBuilder state) {
        String arg = args[index];
        if (!arg.startsWith("--")) {
            state.values.add(arg);
            return index;
        }
        return parseOption(args, index, state, arg);
    }

    private static int parseOption(String[] args, int index, ParseStateBuilder state, String arg) {
        if ("--help".equals(arg)) {
            state.help = true;
            return index;
        }
        if ("--changed".equals(arg)) {
            state.changed = true;
            return index;
        }
        if ("--agent".equals(arg)) {
            state.agent = parseAgent(state.agentSeen);
            state.agentSeen = true;
            return index;
        }
        if (isBooleanOption(arg, "--failures-only")) {
            state.failuresOnly = parseBooleanOption(arg, "--failures-only", state.failuresOnlySeen);
            state.failuresOnlySeen = true;
            return index;
        }
        if (isBooleanOption(arg, "--omit-redundancy")) {
            state.omitRedundancy = parseBooleanOption(arg, "--omit-redundancy", state.omitRedundancySeen);
            state.omitRedundancySeen = true;
            return index;
        }
        return parseValuedOption(args, index, state, arg);
    }

    private static int parseValuedOption(String[] args, int index, ParseStateBuilder state, String arg) {
        if ("--build-tool".equals(arg)) {
            state.buildToolSelection = parseBuildTool(args, index, state.buildToolSeen);
            state.buildToolSeen = true;
            return index + 1;
        }
        if ("--format".equals(arg)) {
            state.reportFormat = parseReportFormat(args, index, state.reportFormatSeen);
            state.reportFormatSeen = true;
            return index + 1;
        }
        if ("--output".equals(arg)) {
            state.outputPath = parsePathOption(args, index, state.outputPathSeen, "--output");
            state.outputPathSeen = true;
            return index + 1;
        }
        if ("--junit-report".equals(arg)) {
            state.junitReportPath = parsePathOption(args, index, state.junitReportPathSeen, "--junit-report");
            state.junitReportPathSeen = true;
            return index + 1;
        }
        if ("--threshold".equals(arg)) {
            state.threshold = parseThreshold(args, index, state.thresholdSeen);
            state.thresholdSeen = true;
            return index + 1;
        }
        throw new IllegalArgumentException("Unknown option: " + arg);
    }

    private static boolean parseAgent(boolean agentSeen) {
        if (agentSeen) {
            throw new IllegalArgumentException("--agent can only be provided once");
        }
        return true;
    }

    private static boolean isBooleanOption(String arg, String option) {
        return arg.equals(option) || arg.startsWith(option + "=");
    }

    private static boolean parseBooleanOption(String arg, String option, boolean seen) {
        if (seen) {
            throw new IllegalArgumentException(option + " can only be provided once");
        }
        if (arg.equals(option)) {
            return true;
        }
        String value = arg.substring(option.length() + 1).toLowerCase(Locale.ROOT);
        if ("true".equals(value)) {
            return true;
        }
        if ("false".equals(value)) {
            return false;
        }
        throw new IllegalArgumentException(option + " requires true or false when assigned");
    }

    private static BuildToolSelection parseBuildTool(String[] args, int index, boolean buildToolSeen) {
        if (buildToolSeen) {
            throw new IllegalArgumentException("--build-tool can only be provided once");
        }
        if (index + 1 >= args.length) {
            throw new IllegalArgumentException("--build-tool requires one of: auto, maven, gradle");
        }
        return BuildToolSelection.parse(args[index + 1]);
    }

    private static ReportFormat parseReportFormat(String[] args, int index, boolean reportFormatSeen) {
        if (reportFormatSeen) {
            throw new IllegalArgumentException("--format can only be provided once");
        }
        if (index + 1 >= args.length) {
            throw new IllegalArgumentException("--format requires one of: toon, json, text, junit");
        }
        return ReportFormat.parse(args[index + 1]);
    }

    private static String parsePathOption(String[] args, int index, boolean seen, String option) {
        if (seen) {
            throw new IllegalArgumentException(option + " can only be provided once");
        }
        if (index + 1 >= args.length) {
            throw new IllegalArgumentException(option + " requires a path");
        }
        return args[index + 1];
    }

    private static double parseThreshold(String[] args, int index, boolean thresholdSeen) {
        if (thresholdSeen) {
            throw new IllegalArgumentException("--threshold can only be provided once");
        }
        if (index + 1 >= args.length) {
            throw new IllegalArgumentException("--threshold requires a finite number greater than 0");
        }
        try {
            return Thresholds.parse(args[index + 1]);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("--threshold requires a finite number greater than 0", ex);
        }
    }

    private static void ensureChangedIsNotCombined(boolean changed, List<String> values) {
        if (changed && !values.isEmpty()) {
            throw new IllegalArgumentException("--changed cannot be combined with file arguments");
        }
    }

    private static void ensureAgentFormatIsSupported(boolean agent, ReportFormat reportFormat) {
        if (agent && reportFormat == ReportFormat.JUNIT) {
            throw new IllegalArgumentException("--agent cannot be combined with --format junit");
        }
    }

    private static void ensureFailuresOnlyDoesNotConflictWithAgent(boolean agent,
                                                                   boolean failuresOnly,
                                                                   boolean failuresOnlySeen) {
        if (agent && failuresOnlySeen && !failuresOnly) {
            throw new IllegalArgumentException("--agent cannot be combined with --failures-only=false");
        }
    }

    private record ParseState(boolean help,
                              boolean changed,
                              BuildToolSelection buildToolSelection,
                              ReportFormat reportFormat,
                              double threshold,
                              boolean agent,
                              boolean failuresOnly,
                              boolean omitRedundancy,
                              @Nullable String outputPath,
                              @Nullable String junitReportPath,
                              List<String> fileArgs) {
    }

    private static final class ParseStateBuilder {
        private boolean help;
        private boolean changed;
        private BuildToolSelection buildToolSelection = BuildToolSelection.AUTO;
        private boolean buildToolSeen;
        private ReportFormat reportFormat = ReportFormat.TOON;
        private boolean reportFormatSeen;
        private double threshold = Thresholds.DEFAULT;
        private boolean thresholdSeen;
        private boolean agent;
        private boolean agentSeen;
        private boolean failuresOnly;
        private boolean failuresOnlySeen;
        private boolean omitRedundancy;
        private boolean omitRedundancySeen;
        private @Nullable String outputPath;
        private boolean outputPathSeen;
        private @Nullable String junitReportPath;
        private boolean junitReportPathSeen;
        private final List<String> values = new ArrayList<>();

        private ParseState build() {
            ensureAgentFormatIsSupported(agent, reportFormat);
            ensureFailuresOnlyDoesNotConflictWithAgent(agent, failuresOnly, failuresOnlySeen);
            return new ParseState(help, changed, buildToolSelection, reportFormat, threshold, agent, failuresOnly, omitRedundancy, outputPath, junitReportPath, values);
        }
    }
}

