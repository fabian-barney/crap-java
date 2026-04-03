package media.barney.crapjava.core;

import java.util.ArrayList;
import java.util.List;

final class CliArgumentsParser {

    private CliArgumentsParser() {
    }

    static CliArguments parse(String[] args) {
        if (args.length == 0) {
            return new CliArguments(CliMode.ALL_SRC, BuildToolSelection.AUTO, List.of());
        }

        ParseState state = parseState(args);
        if (state.help) {
            return new CliArguments(CliMode.HELP, state.buildToolSelection, List.of());
        }
        boolean changed = state.changed;
        List<String> values = state.fileArgs;
        ensureChangedIsNotCombined(changed, values);
        if (changed) {
            return new CliArguments(CliMode.CHANGED_SRC, state.buildToolSelection, List.of());
        }
        if (values.isEmpty()) {
            return new CliArguments(CliMode.ALL_SRC, state.buildToolSelection, List.of());
        }
        return new CliArguments(CliMode.EXPLICIT_FILES, state.buildToolSelection, List.copyOf(values));
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
        if (!arg.startsWith("--")) {
            state.values.add(arg);
        }
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

    private static void ensureChangedIsNotCombined(boolean changed, List<String> values) {
        if (changed && !values.isEmpty()) {
            throw new IllegalArgumentException("--changed cannot be combined with file arguments");
        }
    }

    private record ParseState(boolean help,
                              boolean changed,
                              BuildToolSelection buildToolSelection,
                              List<String> fileArgs) {
    }

    private static final class ParseStateBuilder {
        private boolean help;
        private boolean changed;
        private BuildToolSelection buildToolSelection = BuildToolSelection.AUTO;
        private boolean buildToolSeen;
        private final List<String> values = new ArrayList<>();

        private ParseState build() {
            return new ParseState(help, changed, buildToolSelection, values);
        }
    }
}
