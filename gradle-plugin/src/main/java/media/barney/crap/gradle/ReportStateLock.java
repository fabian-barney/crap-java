package media.barney.crap.gradle;

import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Coordinates report-state access within one JVM and across Gradle processes.
 */
final class ReportStateLock {

    private static final ConcurrentMap<Path, ReentrantLock> IN_PROCESS_LOCKS = new ConcurrentHashMap<>();

    private ReportStateLock() {
    }

    static <T> T withLock(Path lockPath, LockedAction<T> action) throws Exception {
        Path normalizedLockPath = Objects.requireNonNull(lockPath, "lockPath").toAbsolutePath().normalize();
        LockedAction<T> nonNullAction = Objects.requireNonNull(action, "action");
        Files.createDirectories(Objects.requireNonNull(normalizedLockPath.getParent(), "lockPath parent"));
        ReentrantLock inProcessLock = IN_PROCESS_LOCKS.computeIfAbsent(
                normalizedLockPath,
                ignored -> new ReentrantLock()
        );
        inProcessLock.lockInterruptibly();
        try {
            try (FileChannel channel = FileChannel.open(
                    normalizedLockPath,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.WRITE
            );
                 FileLock ignored = channel.lock()) {
                return nonNullAction.run();
            }
        } finally {
            inProcessLock.unlock();
        }
    }

    @FunctionalInterface
    interface LockedAction<T> {
        T run() throws Exception;
    }
}
