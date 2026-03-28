package media.barney.crap4java.core;

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
        boolean help = false;
        boolean changed = false;
        BuildToolSelection buildToolSelection = BuildToolSelection.AUTO;
        boolean buildToolSeen = false;
        List<String> values = new ArrayList<>();
        for (int index = 0; index < args.length; index++) {
            String arg = args[index];
            switch (arg) {
                case "--help" -> help = true;
                case "--changed" -> changed = true;
                case "--build-tool" -> {
                    if (buildToolSeen) {
                        throw new IllegalArgumentException("--build-tool can only be provided once");
                    }
                    if (index + 1 >= args.length) {
                        throw new IllegalArgumentException("--build-tool requires one of: auto, maven, gradle");
                    }
                    buildToolSelection = BuildToolSelection.parse(args[++index]);
                    buildToolSeen = true;
                }
                default -> {
                    if (!arg.startsWith("--")) {
                        values.add(arg);
                    }
                }
            }
        }
        return new ParseState(help, changed, buildToolSelection, values);
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
}
