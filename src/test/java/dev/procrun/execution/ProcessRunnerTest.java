package dev.procrun.execution;

import dev.procrun.model.JobResult;
import dev.procrun.model.JobSpec;
import dev.procrun.model.JobStatus;
import dev.procrun.model.RunnerOptions;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

@Timeout(15)
class ProcessRunnerTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void reportsSuccessAndEveryNonzeroExitCodeInInputOrder() throws Exception {
        List<JobResult> results = execute(outputDirectory(), List.of(
                fixture("second", "exit", "0"), fixture("first", "exit", "7")));

        assertEquals(List.of("second", "first"), results.stream().map(JobResult::jobId).toList());
        assertEquals(JobStatus.SUCCESS, results.getFirst().status());
        assertEquals(0, results.getFirst().exitCode().orElseThrow());
        assertEquals(JobStatus.FAILED, results.getLast().status());
        assertEquals(7, results.getLast().exitCode().orElseThrow());
        for (JobResult result : results) {
            assertTrue(result.pid().orElseThrow() > 0);
            assertFalse(result.duration().isNegative());
            assertTrue(result.errorMessage().isEmpty());
            assertTrue(Files.isRegularFile(result.outputFile().orElseThrow()));
        }
    }

    @Test
    void reportsStartupFailureAndContinuesWithNextJob() throws Exception {
        JobSpec missing = new JobSpec("missing",
                List.of(temporaryDirectory.resolve("missing-executable").toString()));

        List<JobResult> results = execute(outputDirectory(),
                List.of(missing, fixture("next", "exit", "0")));

        assertEquals(2, results.size());
        JobResult failed = results.getFirst();
        assertEquals("missing", failed.jobId());
        assertEquals(JobStatus.FAILED, failed.status());
        assertTrue(failed.pid().isEmpty());
        assertTrue(failed.exitCode().isEmpty());
        assertTrue(failed.errorMessage().orElseThrow().contains("missing-executable"));
        assertEquals(JobStatus.SUCCESS, results.getLast().status());
    }

    @Test
    void capturesLargeMergedOutputWithoutBlocking() throws Exception {
        JobResult result = execute(outputDirectory(),
                List.of(fixture("output", "output", "262144"))).getFirst();

        assertEquals(JobStatus.SUCCESS, result.status());
        assertEquals("o".repeat(262144) + "e".repeat(262144),
                Files.readString(result.outputFile().orElseThrow()));
    }

    @Test
    void createsDistinctOutputFilesInsideConfiguredDirectory() throws Exception {
        Path output = outputDirectory();
        ProcessRunner runner = new ProcessRunner();
        assertFalse(Files.exists(output), "Constructor must not create output resources");

        List<JobResult> results = runner.execute(List.of(
                fixture("../../outside", "exit", "0"), fixture("another", "exit", "0")), options(output));

        Path first = results.getFirst().outputFile().orElseThrow();
        Path second = results.getLast().outputFile().orElseThrow();
        assertNotEquals(first, second);
        assertEquals(output, first.getParent());
        assertEquals(output, second.getParent());
        assertTrue(Files.isRegularFile(first));
        assertTrue(Files.isRegularFile(second));
    }

    @Test
    void closesChildStdinAndPassesArgumentsLiterally() throws Exception {
        List<JobResult> results = execute(outputDirectory(), List.of(
                fixture("stdin", "stdin"),
                fixture("literal", "arguments", "Hello, Java", "$HOME", "", "\"quoted\"")));

        assertEquals(JobStatus.SUCCESS, results.getFirst().status());
        assertEquals("-1", Files.readString(results.getFirst().outputFile().orElseThrow()).strip());
        assertEquals(JobStatus.SUCCESS, results.getLast().status());
        assertEquals(List.of("Hello, Java", "$HOME", "", "\"quoted\""),
                Files.readAllLines(results.getLast().outputFile().orElseThrow()));
    }

    @Test
    void measuresDurationThroughProcessCompletion() throws Exception {
        JobResult result = execute(outputDirectory(),
                List.of(fixture("sleep", "sleep", "200"))).getFirst();

        assertEquals(JobStatus.SUCCESS, result.status());
        assertTrue(result.duration().compareTo(Duration.ofMillis(200)) >= 0);
    }

    @Test
    void returnsEmptyResultsForEmptyBatch() throws Exception {
        assertEquals(List.of(), execute(outputDirectory(), List.of()));
    }

    @Test
    void reportsOutputDirectoryFailureAsJobFailure() throws Exception {
        Path file = Files.writeString(temporaryDirectory.resolve("file"), "not a directory");

        JobResult result = execute(file, List.of(fixture("job", "exit", "0"))).getFirst();

        assertEquals(JobStatus.FAILED, result.status());
        assertTrue(result.pid().isEmpty());
        assertTrue(result.exitCode().isEmpty());
        assertTrue(result.errorMessage().isPresent());
    }

    @Test
    void boundsParallelismAndAdmitsMoreJobsThanQueueCapacity() throws Exception {
        Path release = temporaryDirectory.resolve("release");
        var jobs = new ArrayList<JobSpec>();
        for (int index = 0; index < 6; index++) {
            jobs.add(fixture("job-" + index, "gate",
                    temporaryDirectory.resolve("ready-" + index).toString(), release.toString()));
        }
        ProcessRunner runner = new ProcessRunner();
        RunnerOptions options = new RunnerOptions(2, 1,
                Duration.ofSeconds(10), Duration.ofSeconds(2), outputDirectory());
        AtomicReference<List<JobResult>> results = new AtomicReference<>();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        CountDownLatch completed = new CountDownLatch(1);
        Thread caller = Thread.ofPlatform().name("bounded-batch-test").start(() -> {
            try {
                results.set(runner.execute(jobs, options));
            } catch (Throwable e) {
                failure.set(e);
            } finally {
                completed.countDown();
            }
        });
        try {
            waitUntil(() -> Files.exists(temporaryDirectory.resolve("ready-0"))
                    && Files.exists(temporaryDirectory.resolve("ready-1")),
                    "Two workers should start children concurrently before either is released");
            // Both workers are gated, so saturation cannot resolve until the test releases them.
            assertFalse(completed.await(200, TimeUnit.MILLISECONDS));
            for (int index = 2; index < 6; index++) {
                assertFalse(Files.exists(temporaryDirectory.resolve("ready-" + index)),
                        "Admission must not start an extra child on the caller thread");
            }
            Files.writeString(release, "go");
            assertTrue(completed.await(5, TimeUnit.SECONDS));
            assertNull(failure.get());
            assertEquals(jobs.stream().map(JobSpec::jobId).toList(),
                    results.get().stream().map(JobResult::jobId).toList());
            assertTrue(results.get().stream().allMatch(result -> result.status() == JobStatus.SUCCESS));
            for (JobResult result : results.get()) {
                assertFalse(ProcessHandle.of(result.pid().orElseThrow())
                        .map(ProcessHandle::isAlive).orElse(false));
            }
        } finally {
            Files.writeString(release, "go");
            caller.interrupt();
            caller.join(5000);
            assertFalse(caller.isAlive(), "Batch caller leaked after cleanup");
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 3})
    void interruptionDuringCollectionOrAdmissionWaitsForChildCleanup(int count) throws Exception {
        Path release = temporaryDirectory.resolve("release");
        Path ready = temporaryDirectory.resolve("ready");
        var jobs = new ArrayList<JobSpec>();
        jobs.add(fixture("active", "gate", ready.toString(), release.toString()));
        for (int index = 1; index < count; index++) {
            jobs.add(fixture("queued-" + index, "gate",
                    temporaryDirectory.resolve("queued-ready-" + index).toString(), release.toString()));
        }
        ProcessRunner runner = new ProcessRunner();
        RunnerOptions options = new RunnerOptions(1, 1,
                Duration.ofSeconds(10), Duration.ofSeconds(2), outputDirectory());
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread caller = Thread.ofPlatform().name("interrupted-batch-test").start(() -> {
            try {
                runner.execute(jobs, options);
            } catch (Throwable e) {
                failure.set(e);
            }
        });
        try {
            waitUntil(() -> Files.exists(ready), "Active child never reached its gate");
            long pid = Long.parseLong(Files.readString(ready));
            caller.interrupt();
            caller.join(5000);

            assertFalse(caller.isAlive(), "Interrupted caller did not finish cleanup");
            assertInstanceOf(InterruptedException.class, failure.get());
            assertFalse(ProcessHandle.of(pid).map(ProcessHandle::isAlive).orElse(false),
                    "execute() must wait for active child termination before propagating interruption");
        } finally {
            Files.writeString(release, "go");
            caller.interrupt();
            caller.join(5000);
            assertFalse(caller.isAlive(), "Batch caller leaked after cleanup");
        }
    }

    @Test
    void rejectsAnAlreadyInterruptedCallerBeforeStartingChildren() throws Exception {
        ProcessRunner runner = new ProcessRunner();
        JobSpec job = fixture("job", "exit", "0");
        Thread.currentThread().interrupt();
        try {
            assertThrows(InterruptedException.class,
                    () -> runner.execute(List.of(job), options(outputDirectory())));
            assertFalse(Files.exists(outputDirectory()));
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    void reusesInjectedRunnerWithDifferentOptionsPerBatch() throws Exception {
        ProcessRunner runner = new ProcessRunner();
        Path firstOutput = temporaryDirectory.resolve("first");
        Path secondOutput = temporaryDirectory.resolve("second");
        JobSpec job = fixture("job", "exit", "0");

        JobResult first = runner.execute(List.of(job), options(firstOutput)).getFirst();
        assertFalse(Files.exists(secondOutput));
        JobResult second = runner.execute(List.of(job), options(secondOutput)).getFirst();

        assertEquals(JobStatus.SUCCESS, first.status());
        assertEquals(JobStatus.SUCCESS, second.status());
        assertEquals(firstOutput, first.outputFile().orElseThrow().getParent());
        assertEquals(secondOutput, second.outputFile().orElseThrow().getParent());
    }

    @Test
    void timesOutAChildAndWaitsForItsTermination() throws Exception {
        RunnerOptions options = new RunnerOptions(1, 1, Duration.ofMillis(300),
                Duration.ZERO, outputDirectory());

        JobResult result = new ProcessRunner().execute(
                List.of(fixture("overdue", "sleep", "30000")), options).getFirst();

        assertEquals(JobStatus.TIMED_OUT, result.status());
        assertTrue(result.exitCode().isPresent());
        assertTrue(result.duration().compareTo(options.perProcessTimeout()) >= 0);
        assertTrue(result.errorMessage().orElseThrow().contains("timeout"));
        assertTrue(Files.isRegularFile(result.outputFile().orElseThrow()));
        assertFalse(ProcessHandle.of(result.pid().orElseThrow()).map(ProcessHandle::isAlive).orElse(false));
    }

    @ParameterizedTest
    @ValueSource(longs = {2, Long.MAX_VALUE})
    @EnabledOnOs(OS.LINUX)
    void allowsGracefulTerminationAndKeepsTimedOutStatus(long graceSeconds) throws Exception {
        Path started = temporaryDirectory.resolve("shutdown-started");
        Path finished = temporaryDirectory.resolve("shutdown-finished");
        RunnerOptions options = new RunnerOptions(1, 1, Duration.ofSeconds(1),
                Duration.ofSeconds(graceSeconds), outputDirectory());

        JobResult result = new ProcessRunner().execute(List.of(fixture("graceful", "shutdown-delay",
                started.toString(), finished.toString(), "200")), options).getFirst();

        assertEquals(JobStatus.TIMED_OUT, result.status());
        assertTrue(Files.exists(started), "Termination should run the child's shutdown hook");
        assertTrue(Files.exists(finished), "Grace period should allow the child's hook to finish");
        assertTrue(result.duration().compareTo(Duration.ofMillis(1200)) >= 0);
        assertFalse(ProcessHandle.of(result.pid().orElseThrow()).map(ProcessHandle::isAlive).orElse(false));
    }

    @Test
    @EnabledOnOs(OS.LINUX)
    void forcesTerminationAfterGracePeriodExpires() throws Exception {
        Path started = temporaryDirectory.resolve("shutdown-started");
        Path finished = temporaryDirectory.resolve("shutdown-finished");
        RunnerOptions options = new RunnerOptions(1, 1, Duration.ofSeconds(1),
                Duration.ofMillis(300), outputDirectory());

        JobResult result = new ProcessRunner().execute(List.of(fixture("stubborn", "shutdown-delay",
                started.toString(), finished.toString(), "30000")), options).getFirst();

        assertEquals(JobStatus.TIMED_OUT, result.status());
        assertTrue(Files.exists(started), "Graceful termination must be requested first");
        assertFalse(Files.exists(finished), "Force should stop the child before its hook finishes");
        assertTrue(result.duration().compareTo(Duration.ofMillis(1300)) >= 0);
        assertFalse(ProcessHandle.of(result.pid().orElseThrow()).map(ProcessHandle::isAlive).orElse(false));
    }

    @Test
    void excludesQueueWaitingFromTimeoutAndContinuesAfterTimedOutJobs() throws Exception {
        RunnerOptions options = new RunnerOptions(1, 1, Duration.ofSeconds(2),
                Duration.ZERO, outputDirectory());

        List<JobResult> results = new ProcessRunner().execute(List.of(
                fixture("first", "sleep", "1200"), fixture("second", "sleep", "1200"),
                fixture("overdue", "sleep", "30000"), fixture("next", "exit", "0")), options);

        assertEquals(List.of(JobStatus.SUCCESS, JobStatus.SUCCESS, JobStatus.TIMED_OUT, JobStatus.SUCCESS),
                results.stream().map(JobResult::status).toList());
        assertEquals(List.of("first", "second", "overdue", "next"),
                results.stream().map(JobResult::jobId).toList());
    }

    @Test
    void supportsTimeoutDurationsTooLargeForNanosecondConversion() throws Exception {
        RunnerOptions options = new RunnerOptions(1, 1, Duration.ofSeconds(Long.MAX_VALUE),
                Duration.ZERO, outputDirectory());

        JobResult result = new ProcessRunner().execute(
                List.of(fixture("quick", "exit", "0")), options).getFirst();

        assertEquals(JobStatus.SUCCESS, result.status());
        assertEquals(0, result.exitCode().orElseThrow());
    }

    private void waitUntil(BooleanSupplier condition, String message) throws InterruptedException {
        long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
        while (!condition.getAsBoolean() && System.nanoTime() < deadline) {
            Thread.sleep(10);
        }
        assertTrue(condition.getAsBoolean(), message);
    }

    private Path outputDirectory() {
        return temporaryDirectory.resolve("nested/output");
    }

    private List<JobResult> execute(Path output, List<JobSpec> jobs) throws InterruptedException {
        return new ProcessRunner().execute(jobs, options(output));
    }

    private RunnerOptions options(Path output) {
        return new RunnerOptions(3, 20, Duration.ofSeconds(10), Duration.ofSeconds(2), output);
    }

    private JobSpec fixture(String id, String... arguments) throws Exception {
        Path classpath = Path.of(ChildProcessFixture.class.getProtectionDomain()
                .getCodeSource().getLocation().toURI());
        var command = new ArrayList<>(List.of(
                Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-cp", classpath.toString(), ChildProcessFixture.class.getName()));
        command.addAll(List.of(arguments));
        return new JobSpec(id, command);
    }
}
