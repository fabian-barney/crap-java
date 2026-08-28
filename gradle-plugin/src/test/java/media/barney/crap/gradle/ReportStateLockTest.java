package media.barney.crap.gradle;

import org.gradle.api.Project;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class ReportStateLockTest {

    @TempDir
    Path tempDir;

    @Test
    void sameJvmCallersSerializeAndKeepDistinctReportsConsistent() throws Exception {
        Project project = ProjectBuilder.builder().withProjectDir(tempDir.toFile()).build();
        CrapJavaCheckTask firstTask = project.getTasks().register(
                "first-crap-java-check",
                CrapJavaCheckTask.class
        ).get();
        CrapJavaCheckTask secondTask = project.getTasks().register(
                "second-crap-java-check",
                CrapJavaCheckTask.class
        ).get();
        Path lockPath = stateLockPath(firstTask);
        assertEquals(lockPath, stateLockPath(secondTask));
        Path firstReport = tempDir.resolve("reports/first.json").toAbsolutePath().normalize();
        Path firstJunit = tempDir.resolve("reports/first-junit.xml").toAbsolutePath().normalize();
        Path secondReport = tempDir.resolve("reports/second.json").toAbsolutePath().normalize();
        Path secondJunit = tempDir.resolve("reports/second-junit.xml").toAbsolutePath().normalize();
        firstTask.getOutput().fileValue(firstReport.toFile());
        firstTask.getJunitReport().fileValue(firstJunit.toFile());
        secondTask.getOutput().fileValue(secondReport.toFile());
        secondTask.getJunitReport().fileValue(secondJunit.toFile());
        CountDownLatch firstEntered = new CountDownLatch(1);
        CountDownLatch secondStarted = new CountDownLatch(1);
        CountDownLatch secondEntered = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        AtomicReference<Thread> secondThread = new AtomicReference<>();
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            Future<Void> first = executor.submit(() -> ReportStateLock.withLock(lockPath, () -> {
                firstEntered.countDown();
                assertTrue(releaseFirst.await(5, TimeUnit.SECONDS));
                Files.createDirectories(firstReport.getParent());
                Files.writeString(firstReport, "first");
                Files.writeString(firstJunit, "first-junit");
                return null;
            }));
            assertTrue(firstEntered.await(5, TimeUnit.SECONDS));

            Future<Void> second = executor.submit(() -> {
                secondThread.set(Thread.currentThread());
                secondStarted.countDown();
                return ReportStateLock.withLock(lockPath, () -> {
                    secondEntered.countDown();
                    Files.createDirectories(secondReport.getParent());
                    Files.writeString(secondReport, "second");
                    Files.writeString(secondJunit, "second-junit");
                    return null;
                });
            });
            assertTrue(secondStarted.await(5, TimeUnit.SECONDS));
            awaitThreadWaiting(secondThread);
            assertFalse(secondEntered.await(100, TimeUnit.MILLISECONDS));
            assertFalse(second.isDone());

            releaseFirst.countDown();
            first.get(5, TimeUnit.SECONDS);
            second.get(5, TimeUnit.SECONDS);
            assertEquals("first", Files.readString(firstReport));
            assertEquals("first-junit", Files.readString(firstJunit));
            assertEquals("second", Files.readString(secondReport));
            assertEquals("second-junit", Files.readString(secondJunit));
        } finally {
            releaseFirst.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void interruptedInProcessWaitReleasesTheLocalLock() throws Exception {
        Path lockPath = tempDir.resolve(".gradle/crap-java/state.lock");
        CountDownLatch firstEntered = new CountDownLatch(1);
        CountDownLatch secondStarted = new CountDownLatch(1);
        CountDownLatch secondEntered = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        AtomicReference<Thread> secondThread = new AtomicReference<>();
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            Future<Void> first = executor.submit(() -> ReportStateLock.withLock(lockPath, () -> {
                firstEntered.countDown();
                assertTrue(releaseFirst.await(5, TimeUnit.SECONDS));
                return null;
            }));
            assertTrue(firstEntered.await(5, TimeUnit.SECONDS));

            Future<Void> second = executor.submit(() -> {
                secondThread.set(Thread.currentThread());
                secondStarted.countDown();
                return ReportStateLock.withLock(lockPath, () -> {
                    secondEntered.countDown();
                    return null;
                });
            });
            assertTrue(secondStarted.await(5, TimeUnit.SECONDS));
            awaitThreadWaiting(secondThread);
            second.cancel(true);
            assertTrue(second.isCancelled());

            releaseFirst.countDown();
            first.get(5, TimeUnit.SECONDS);
            assertFalse(secondEntered.await(1, TimeUnit.SECONDS));
            ReportStateLock.withLock(lockPath, () -> null);
        } finally {
            releaseFirst.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void waitsForAFileLockHeldByAnotherJvm() throws Exception {
        Path lockPath = tempDir.resolve(".gradle/crap-java/state.lock");
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Process holder = startLockHolder(lockPath);

        try {
            awaitLockHolder(holder);
            CountDownLatch entered = new CountDownLatch(1);
            Future<Void> blocked = executor.submit(() -> ReportStateLock.withLock(lockPath, () -> {
                entered.countDown();
                return null;
            }));

            assertFalse(entered.await(250, TimeUnit.MILLISECONDS));
            assertFalse(blocked.isDone());
            releaseLockHolder(holder);
            blocked.get(5, TimeUnit.SECONDS);
        } finally {
            stopLockHolder(holder);
            executor.shutdownNow();
        }
    }

    @Test
    void interruptedFileLockAcquisitionDoesNotPoisonLaterTasks() throws Exception {
        Path lockPath = tempDir.resolve(".gradle/crap-java/state.lock");
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Process holder = startLockHolder(lockPath);

        try {
            awaitLockHolder(holder);
            CountDownLatch started = new CountDownLatch(1);
            Future<Void> blocked = executor.submit(() -> {
                started.countDown();
                return ReportStateLock.withLock(lockPath, () -> null);
            });
            assertTrue(started.await(5, TimeUnit.SECONDS));
            assertFalse(blocked.isDone());
            blocked.cancel(true);
            assertTrue(blocked.isCancelled());
            releaseLockHolder(holder);
            ReportStateLock.withLock(lockPath, () -> null);
        } finally {
            stopLockHolder(holder);
            executor.shutdownNow();
        }
    }

    private Process startLockHolder(Path lockPath) throws Exception {
        Path testClasses = Path.of(CrossProcessFileLockHolder.class
                .getProtectionDomain()
                .getCodeSource()
                .getLocation()
                .toURI());
        Path java = Path.of(System.getProperty("java.home"), "bin",
                System.getProperty("os.name", "").toLowerCase().contains("win") ? "java.exe" : "java");
        return new ProcessBuilder(
                java.toString(),
                "-cp",
                testClasses.toString(),
                CrossProcessFileLockHolder.class.getName(),
                lockPath.toString()
        ).redirectErrorStream(true).start();
    }

    private void awaitLockHolder(Process holder) throws IOException {
        try (BufferedReader output = new BufferedReader(
                new InputStreamReader(holder.getInputStream(), StandardCharsets.UTF_8))) {
            assertEquals("LOCKED", output.readLine());
        }
    }

    private void awaitThreadWaiting(AtomicReference<Thread> threadReference) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < deadline) {
            Thread thread = threadReference.get();
            if (thread != null && (thread.getState() == Thread.State.WAITING
                    || thread.getState() == Thread.State.TIMED_WAITING)) {
                return;
            }
            Thread.sleep(10);
        }
        fail("Expected worker thread to wait for the report-state lock");
    }

    private void releaseLockHolder(Process holder) throws IOException, InterruptedException {
        try (OutputStream input = holder.getOutputStream()) {
            input.write(1);
            input.flush();
        }
        assertTrue(holder.waitFor(5, TimeUnit.SECONDS));
    }

    private void stopLockHolder(Process holder) throws InterruptedException {
        if (holder.isAlive()) {
            holder.destroyForcibly();
            assertTrue(holder.waitFor(5, TimeUnit.SECONDS));
        }
    }

    private Path stateLockPath(CrapJavaCheckTask task) throws Exception {
        Method stateLockPath = CrapJavaCheckTask.class.getDeclaredMethod("stateLockPath");
        stateLockPath.setAccessible(true);
        return ((Path) stateLockPath.invoke(task)).toAbsolutePath().normalize();
    }
}
