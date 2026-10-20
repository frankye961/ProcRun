package dev.procrun.execution;

import dev.procrun.model.JobResult;
import dev.procrun.model.JobSpec;
import dev.procrun.model.JobStatus;
import dev.procrun.model.RunnerOptions;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Owns each batch's bounded executor, output capture, and direct child processes.
 * Has no constructor configuration; runtime options are supplied to execute().
 * execute() releases batch resources before returning; external shutdown is still unimplemented.
 */
public class ProcessRunner {

    /** A started direct child and its output file; lifecycle ownership stays with the runner. */
    record StartedJob(Process process, Path outputFile) {}

    /** Uses per-batch options and bounded admission; cleans up before propagating interruption. */
    public List<JobResult> execute(List<JobSpec> jobs, RunnerOptions options) throws InterruptedException {
        ThreadPoolExecutor executor = getExecutor(options);
        List<Future<JobResult>> futureJobs = new ArrayList<>();
        try {
            var results = new ArrayList<JobResult>();
            for (JobSpec job : jobs) {
                futureJobs.add(submitBlocking(executor, job, options));
            }
            // Workers finish independently; future order preserves TSV order in the report.
            for (Future<JobResult> futureJob : futureJobs) {
                results.add(futureJob.get());

            }
            return results;
        } catch (ExecutionException e) {
            throw new IllegalStateException("Job execution failed unexpectedly", e.getCause());
        } finally {
            // Cancellation finishes a Future before its worker has necessarily cleaned up.
            cancelUnfinishedJobs(futureJobs);
            executor.shutdown();
            awaitTermination(executor);
        }
    }

    private JobResult runJob(JobSpec job, RunnerOptions options) throws InterruptedException {

        long started = System.nanoTime();
        StartedJob startedJob;
        try {
            startedJob = startJob(job, options);
        } catch (IOException e) {
            return new JobResult(job.jobId(), OptionalLong.empty(), JobStatus.FAILED,
                    OptionalInt.empty(), Duration.ofNanos(System.nanoTime() - started),
                    Optional.empty(), Optional.of(e.toString()));
        }
        Process process = startedJob.process();
        try {
            boolean isFinished = isProcessFinished(process, options);
            if (!isFinished) {
                process.destroy();
                // A timeout remains a timeout even if the child exits during its grace period.
                if (!process.waitFor(options.terminationGracePeriod())) {
                    process.destroyForcibly();
                    process.waitFor();
                }
            }
            int exitCode = process.exitValue();
            JobStatus status = !isFinished ? JobStatus.TIMED_OUT
                    : exitCode == 0 ? JobStatus.SUCCESS : JobStatus.FAILED;
            return new JobResult(job.jobId(), OptionalLong.of(process.pid()),
                    status,
                    OptionalInt.of(exitCode), Duration.ofNanos(System.nanoTime() - started),
                    Optional.of(startedJob.outputFile()),
                    isFinished ? Optional.empty()
                            : Optional.of("Execution exceeded timeout of " + options.perProcessTimeout()));
        } finally {
            if (process.isAlive()) {
                process.destroyForcibly();
                awaitChildTermination(process);
            }
        }
    }

    private boolean isProcessFinished(Process process, RunnerOptions options) throws InterruptedException {
        return process.waitFor(options.perProcessTimeout());
    }

    private StartedJob startJob(JobSpec job, RunnerOptions options) throws IOException {
        Files.createDirectories(options.outputDirectory());
        var outputFile = createTempFile(options);
        ProcessBuilder processBuilder = new ProcessBuilder();
        processBuilder.command(job.commandArguments());
        processBuilder.redirectErrorStream(true);
        processBuilder.redirectOutput(outputFile.toFile());
        var process = processBuilder.start();
        try {
            process.getOutputStream().close();
        } catch (IOException e) {
            process.destroyForcibly();
            awaitChildTermination(process);
            throw e;
        }
        return new StartedJob(process, outputFile);
    }

    private Path createTempFile(RunnerOptions options) throws IOException {
        return Files.createTempFile(options.outputDirectory(), "job-", ".log");
    }

    /** Interrupts unfinished tasks; their workers own child cleanup. */
    private void cancelUnfinishedJobs(List<Future<JobResult>> futureJobs) {
        futureJobs.forEach(job -> {
            if(!job.isDone()) {
                job.cancel(true);
            }
        });
    }

    /** Will stop admission and clean up an active batch from another thread. */
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

    private Future<JobResult> submitBlocking(ThreadPoolExecutor executor, JobSpec job, RunnerOptions options)
            throws InterruptedException {
        // execute() does not check interruption when it can immediately accept a task.
        if (Thread.interrupted()) {
            throw new InterruptedException("Interrupted during job admission");
        }
        var task = new FutureTask<JobResult>(() -> runJob(job, options));
        try {
            executor.execute(task);
            return task;
        } catch (RejectedExecutionException e) {
            if (executor.isShutdown()) {
                throw e;
            }
        }
        // The fixed pool already has workers. Queue insertion must not run a job here.
        while (!executor.isShutdown()) {
            if (executor.getQueue().offer(task, 100, TimeUnit.MILLISECONDS)) {
                if (executor.isShutdown() && executor.getQueue().remove(task)) {
                    task.cancel(false);
                    throw new RejectedExecutionException("Executor shut down during admission");
                }
                return task;
            }
        }
        throw new RejectedExecutionException("Executor is shut down");
    }

    /** Finish cleanup even under repeated interruption, then restore the interruption signal. */
    private void awaitTermination(ThreadPoolExecutor executor) throws InterruptedException {
        boolean interrupted = false;
        try {
            while (!executor.isTerminated()) {
                try {
                    executor.awaitTermination(1, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    interrupted = true;
                    // Keep cancelling if cleanup itself was interrupted during a normal return.
                    executor.shutdownNow();
                }
            }
        } finally {
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
        }
        if (interrupted) {
            throw new InterruptedException("Interrupted while waiting for executor termination");
        }
    }

    /** A second interrupt must not abandon a child that is still being forcibly terminated. */
    private void awaitChildTermination(Process process) {
        boolean interrupted = false;
        try {
            while (true) {
                try {
                    process.waitFor();
                    return;
                } catch (InterruptedException e) {
                    interrupted = true;
                }
            }
        } finally {
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
