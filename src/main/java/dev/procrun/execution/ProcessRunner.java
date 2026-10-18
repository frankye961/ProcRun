package dev.procrun.execution;

import dev.procrun.model.JobResult;
import dev.procrun.model.JobSpec;
import dev.procrun.model.JobStatus;
import dev.procrun.model.RunnerOptions;
import lombok.RequiredArgsConstructor;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.concurrent.*;

/**
 * Will own bounded execution resources, output capture, and direct child processes.
 * The caller owns this runner's lifecycle and must invoke shutdown when finished.
 */
@RequiredArgsConstructor
public class ProcessRunner {

    private final RunnerOptions options;

    /** A started direct child and its output file; lifecycle ownership stays with the runner. */
    record StartedJob(Process process, Path outputFile) {}

    /** Executes sequentially in input order; interruption propagates after cleaning up the active child. */
    public List<JobResult> execute(List<JobSpec> jobs) {
       List<Future<JobResult>> futureJobs = new ArrayList<>();
       ThreadPoolExecutor executor = getExecutor(options);
        var results = new ArrayList<JobResult>();
        for (JobSpec job : jobs) {
            futureJobs.add(executor.submit(() -> runJob(job)));
        }
        for (Future<JobResult> futureJob : futureJobs) {
            try {
                results.add(futureJob.get());
            } catch (InterruptedException | ExecutionException e) {
                throw new RuntimeException(e);
            }
        }
        return results;
    }

    private JobResult runJob(JobSpec job) throws IOException, InterruptedException {

        long started = System.nanoTime();
        StartedJob startedJob;
        try {
            startedJob = startJob(job);
        } catch (IOException e) {
            return new JobResult(job.jobId(), OptionalLong.empty(), JobStatus.FAILED,
                    OptionalInt.empty(), Duration.ofNanos(System.nanoTime() - started),
                    Optional.empty(), Optional.of(e.toString()));
        }
        Process process = startedJob.process();
        try {
            int exitCode = process.waitFor();
            return new JobResult(job.jobId(), OptionalLong.of(process.pid()),
                    exitCode == 0 ? JobStatus.SUCCESS : JobStatus.FAILED,
                    OptionalInt.of(exitCode), Duration.ofNanos(System.nanoTime() - started),
                    Optional.of(startedJob.outputFile()), Optional.empty());
        } finally {
            if (process.isAlive()) {
                process.destroyForcibly();
                process.waitFor();
            }
        }
    }

    private StartedJob startJob(JobSpec job) throws IOException {
        Files.createDirectories(options.outputDirectory());
        var outputFile = createTempFile();
        ProcessBuilder processBuilder = new ProcessBuilder();
        processBuilder.command(job.commandArguments());
        processBuilder.redirectErrorStream(true);
        processBuilder.redirectOutput(outputFile.toFile());
        var process = processBuilder.start();
        try {
            process.getOutputStream().close();
        } catch (IOException e) {
            process.destroyForcibly();
            throw e;
        }
        return new StartedJob(process, outputFile);
    }

    private Path createTempFile() throws IOException {
        return Files.createTempFile(options.outputDirectory(), "job-", ".log");
    }

    /** Will stop admission, clean up direct children, and release owned execution resources. */
    public void shutdown() throws InterruptedException {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    private static ThreadPoolExecutor getExecutor(RunnerOptions options) {
        return new ThreadPoolExecutor(
                options.maxParallelProcesses(),
                options.maxParallelProcesses(),
                0L,
                TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(options.waitingQueueCapacity())
        );
    }
}
