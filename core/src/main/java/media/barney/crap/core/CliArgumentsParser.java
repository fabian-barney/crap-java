package media.barney.crap.core;

import java.util.ArrayList;
import java.util.List;
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
                    state.sourceRoots,
                    List.of(),
                    state.exclusionOptions
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
                    state.sourceRoots,
                    List.of(),
                    state.exclusionOptions
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
                    state.sourceRoots,
                    List.of(),
                    state.exclusionOptions
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
                state.sourceRoots,
                List.copyOf(values),
                state.exclusionOptions
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
        if (parseFlagOption(state, arg)) {
            return index;
        }
        return parseBooleanOrValuedOption(args, index, state, arg);
    }

    private static boolean parseFlagOption(ParseStateBuilder state, String arg) {
        if ("--help".equals(arg)) {
            state.help = true;
            return true;
        }
        if ("--changed".equals(arg)) {
            state.changed = true;
            return true;
        }
        if ("--agent".equals(arg)) {
            state.agent = parseAgent(state.agentSeen);
            state.agentSeen = true;
            return true;
        }
        return false;
    }

    private static int parseBooleanOrValuedOption(String[] args,
                                                   int index,
                                                   ParseStateBuilder state,
                                                   String arg) {
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
        if (isBooleanOption(arg, "--use-default-exclusions")) {
            state.useDefaultExclusions = parseBooleanOption(
                    arg,
                    "--use-default-exclusions",
                    state.useDefaultExclusionsSeen
            );
            state.useDefaultExclusionsSeen = true;
            return index;
        }
        return parseValuedOption(args, index, state, arg);
    }

    private static int parseValuedOption(String[] args, int index, ParseStateBuilder state, String arg) {
        AssignedOption option = AssignedOption.parse(arg);
        if (parseGeneralValuedOption(args, index, state, option)
                || parseThresholdOption(args, index, state, option)
                || parseExclusionOption(args, index, state, option)) {
            return option.hasInlineValue() ? index : index + 1;
        }
        throw new IllegalArgumentException("Unknown option: " + arg);
    }

    private static boolean parseGeneralValuedOption(String[] args,
                                                    int index,
                                                    ParseStateBuilder state,
                                                    AssignedOption option) {
        return parseBuildToolOption(args, index, state, option)
                || parseReportFormatOption(args, index, state, option)
                || parseOutputOption(args, index, state, option)
                || parseJunitReportOption(args, index, state, option)
                || parseSourceRootOption(args, index, state, option);
    }

    private static boolean parseBuildToolOption(String[] args,
                                                int index,
                                                ParseStateBuilder state,
                                                AssignedOption option) {
        if ("--build-tool".equals(option.name())) {
            state.buildToolSelection = parseBuildTool(args, index, option, state.buildToolSeen);
            state.buildToolSeen = true;
            return true;
        }
        return false;
    }

    private static boolean parseReportFormatOption(String[] args,
                                                   int index,
                                                   ParseStateBuilder state,
                                                   AssignedOption option) {
        if ("--format".equals(option.name())) {
            state.reportFormat = parseReportFormat(args, index, option, state.reportFormatSeen);
            state.reportFormatSeen = true;
            return true;
        }
        return false;
    }

    private static boolean parseOutputOption(String[] args,
                                             int index,
                                             ParseStateBuilder state,
                                             AssignedOption option) {
        if ("--output".equals(option.name())) {
            state.outputPath = parsePathOption(args, index, option, state.outputPathSeen);
            state.outputPathSeen = true;
            return true;
        }
        return false;
    }

    private static boolean parseJunitReportOption(String[] args,
                                                  int index,
                                                  ParseStateBuilder state,
                                                  AssignedOption option) {
        if ("--junit-report".equals(option.name())) {
            state.junitReportPath = parsePathOption(args, index, option, state.junitReportPathSeen);
            state.junitReportPathSeen = true;
            return true;
        }
        return false;
    }

    private static boolean parseThresholdOption(String[] args,
                                                int index,
                                                ParseStateBuilder state,
                                                AssignedOption option) {
        if ("--threshold".equals(option.name())) {
            state.threshold = parseThreshold(args, index, option, state.thresholdSeen);
            state.thresholdSeen = true;
            return true;
        }
        return false;
    }

    private static boolean parseSourceRootOption(String[] args,
                                                 int index,
                                                 ParseStateBuilder state,
                                                 AssignedOption option) {
        if ("--source-root".equals(option.name())) {
            state.sourceRoots.add(parseListOption(args, index, option, "a source root path"));
            return true;
        }
        return false;
    }

    private static boolean parseExclusionOption(String[] args,
                                                int index,
                                                ParseStateBuilder state,
                                                AssignedOption option) {
        if ("--exclude".equals(option.name())) {
            state.excludes.add(parseListOption(args, index, option, "a glob"));
            return true;
        }
        if ("--exclude-class".equals(option.name())) {
            state.excludeClasses.add(parseListOption(args, index, option, "a regex"));
            return true;
        }
        if ("--exclude-annotation".equals(option.name())) {
            state.excludeAnnotations.add(parseListOption(
                    args,
                    index,
                    option,
                    "an annotation name"
            ));
            return true;
        }
        return false;
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
        String value = arg.substring(option.length() + 1);
        if ("true".equals(value)) {
            return true;
        }
        if ("false".equals(value)) {
            return false;
        }
        throw new IllegalArgumentException(option + " requires true or false when assigned");
    }

    private static BuildToolSelection parseBuildTool(String[] args,
                                                     int index,
                                                     AssignedOption option,
                                                     boolean buildToolSeen) {
        if (buildToolSeen) {
            throw new IllegalArgumentException("--build-tool can only be provided once");
        }
        return BuildToolSelection.parse(optionValue(
                args,
                index,
                option,
                "one of: auto, maven, gradle"
        ));
    }

    private static ReportFormat parseReportFormat(String[] args,
                                                  int index,
                                                  AssignedOption option,
                                                  boolean reportFormatSeen) {
        if (reportFormatSeen) {
            throw new IllegalArgumentException("--format can only be provided once");
        }
        return ReportFormat.parse(optionValue(args, index, option, "one of: toon, json, text, junit, none"));
    }

    private static String parsePathOption(String[] args,
                                          int index,
                                          AssignedOption assignedOption,
                                          boolean seen) {
        if (seen) {
            throw new IllegalArgumentException(assignedOption.name() + " can only be provided once");
        }
        return optionValue(args, index, assignedOption, "a path");
    }

    private static double parseThreshold(String[] args,
                                         int index,
                                         AssignedOption option,
                                         boolean thresholdSeen) {
        if (thresholdSeen) {
            throw new IllegalArgumentException("--threshold can only be provided once");
        }
        try {
            return Thresholds.parse(optionValue(
                    args,
                    index,
                    option,
                    "a finite number greater than 0"
            ));
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("--threshold requires a finite number greater than 0", ex);
        }
    }

    private static String parseListOption(String[] args,
                                          int index,
                                          AssignedOption assignedOption,
                                          String valueDescription) {
        String value = optionValue(args, index, assignedOption, valueDescription).trim();
        if (value.isEmpty()) {
            throw new IllegalArgumentException(assignedOption.name() + " requires " + valueDescription);
        }
        return value;
    }

    private static String optionValue(String[] args,
                                      int index,
                                      AssignedOption option,
                                      String valueDescription) {
        @Nullable String inlineValue = option.inlineValue();
        if (inlineValue != null) {
            if (inlineValue.isEmpty()) {
                throw new IllegalArgumentException(option.name() + " requires " + valueDescription);
            }
            return inlineValue;
        }
        if (index + 1 >= args.length) {
            throw new IllegalArgumentException(option.name() + " requires " + valueDescription);
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
                              double threshold,
                              boolean agent,
                              boolean failuresOnly,
                              boolean omitRedundancy,
                              @Nullable String outputPath,
                              @Nullable String junitReportPath,
                              SourceExclusionOptions exclusionOptions,
                              List<String> sourceRoots,
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
        private boolean useDefaultExclusions = true;
        private boolean useDefaultExclusionsSeen;
        private final List<String> excludes = new ArrayList<>();
        private final List<String> excludeClasses = new ArrayList<>();
        private final List<String> excludeAnnotations = new ArrayList<>();
        private @Nullable String outputPath;
        private boolean outputPathSeen;
        private @Nullable String junitReportPath;
        private boolean junitReportPathSeen;
        private final List<String> sourceRoots = new ArrayList<>();
        private final List<String> values = new ArrayList<>();

        private ParseState build() {
            boolean effectiveFailuresOnly = (agent && !failuresOnlySeen) || failuresOnly;
            boolean effectiveOmitRedundancy = (agent && !omitRedundancySeen) || omitRedundancy;
            return new ParseState(
                    help,
                    changed,
                    buildToolSelection,
                    reportFormat,
                    threshold,
                    agent,
                    effectiveFailuresOnly,
                    effectiveOmitRedundancy,
                    outputPath,
                    junitReportPath,
                    new SourceExclusionOptions(
                            excludes,
                            excludeClasses,
                            excludeAnnotations,
                            useDefaultExclusions
                    ),
                    List.copyOf(sourceRoots),
                    values
            );
        }
    }

    private record AssignedOption(String name, @Nullable String inlineValue) {
        private static AssignedOption parse(String arg) {
            int equalsIndex = arg.indexOf('=');
            if (equalsIndex < 0) {
                return new AssignedOption(arg, null);
            }
            return new AssignedOption(arg.substring(0, equalsIndex), arg.substring(equalsIndex + 1));
        }

        private boolean hasInlineValue() {
            return inlineValue != null;
        }
    }
}

