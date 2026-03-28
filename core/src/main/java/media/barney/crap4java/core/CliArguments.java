package media.barney.crap4java.core;

import java.util.List;

record CliArguments(CliMode mode, BuildToolSelection buildToolSelection, List<String> fileArgs) {
}
