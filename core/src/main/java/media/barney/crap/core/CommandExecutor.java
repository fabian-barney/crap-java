package media.barney.crap.core;

import java.nio.file.Path;
import java.util.List;

interface CommandExecutor {
    int run(List<String> command, Path directory) throws Exception;
}

