package media.barney.crap.core;

import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;

final class CliArgumentsParser {

    private CliArgumentsParser() {
    }

    static CliArguments parse(String[] args) {
        if (args.length == 0) {
            return new CliArguments(CliMode.ALL_SRC, BuildToolSelection.AUTO, ReportFormat.TOON, null, null, List.of());
        }

        ParseState state = parseState(args);
        if (state.help) {
            return new CliArguments(
                    CliMode.HELP,
                    state.buildToolSelection,
                    state.reportFormat,
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
                    state.outputPath,
                    state.junitReportPath,
                    List.of()
            );
        }
        return new CliArguments(
                CliMode.EXPLICIT_FILES,
                state.buildToolSelection,
                state.reportFormat,
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
        if ("--help".equals(arg)) {
            state.help = true;
            return index;
        }
        if ("--changed".equals(arg)) {
            state.changed = true;
            return index;
        }
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
        if (arg.startsWith("--")) {
            throw new IllegalArgumentException("Unknown option: " + arg);
        }
        state.values.add(arg);
        return index;
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

    private static void ensureChangedIsNotCombined(boolean changed, List<String> values) {
        if (changed && !values.isEmpty()) {
            throw new IllegalArgumentException("--changed cannot be combined with file arguments");
        }
    }

    private record ParseState(boolean help,
                              boolean changed,
                              BuildToolSelection buildToolSelection,
                              ReportFormat reportFormat,
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
        private @Nullable String outputPath;
        private boolean outputPathSeen;
        private @Nullable String junitReportPath;
        private boolean junitReportPathSeen;
        private final List<String> values = new ArrayList<>();

        private ParseState build() {
            return new ParseState(help, changed, buildToolSelection, reportFormat, outputPath, junitReportPath, values);
        }
    }
}

