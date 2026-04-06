package media.barney.crap.core;

import java.util.List;

record CliArguments(CliMode mode, BuildToolSelection buildToolSelection, List<String> fileArgs) {
}

