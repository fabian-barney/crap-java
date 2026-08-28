package media.barney.crap.gradle;

import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Objects;

public final class CrossProcessFileLockHolder {

    private CrossProcessFileLockHolder() {
    }

    public static void main(String[] arguments) throws Exception {
        Path lockPath = Path.of(arguments[0]).toAbsolutePath().normalize();
        Files.createDirectories(Objects.requireNonNull(lockPath.getParent(), "lockPath parent"));
        try (FileChannel channel = FileChannel.open(lockPath, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
             FileLock ignored = channel.lock()) {
            System.out.println("LOCKED");
            System.out.flush();
            System.in.read();
        }
    }
}
