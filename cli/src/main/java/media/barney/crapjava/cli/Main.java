package media.barney.crapjava.cli;

import java.io.PrintStream;
import java.nio.file.Path;

public final class Main {

    private Main() {
    }

    public static void main(String[] args) throws Exception {
        System.exit(run(args, Path.of(".").toAbsolutePath().normalize(), System.out, System.err));
    }

    static int run(String[] args, Path projectRoot, PrintStream out, PrintStream err) throws Exception {
        return media.barney.crapjava.core.Main.run(args, projectRoot, out, err);
    }
}
