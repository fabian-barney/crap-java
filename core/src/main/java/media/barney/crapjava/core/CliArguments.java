package media.barney.crapjava.core;

import java.util.List;

record CliArguments(CliMode mode, BuildToolSelection buildToolSelection, List<String> fileArgs) {
}
