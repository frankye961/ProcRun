package dev.procrun.cli;

import dev.procrun.config.AutowireComponent;
import dev.procrun.execution.ProcessRunner;
import dev.procrun.input.JobFileReader;
import dev.procrun.model.JobSpec;
import dev.procrun.model.JobStatus;
import dev.procrun.model.RunnerOptions;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.Callable;
import picocli.CommandLine.ParameterException;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Spec;
import picocli.CommandLine.Model.CommandSpec;

/** Coordinates input, batch execution, and reporting; each batch owns its execution resources. */
@Command(name = "procrun", mixinStandardHelpOptions = true, version = "ProcRun 0.1.0",
        description = "Run commands from a TSV file with bounded concurrency.",
        showDefaultValues = true)
public class RunCommand implements Callable<Integer> {
    private final JobFileReader jobFileReader;
    private final ProcessRunner processRunner;

    @AutowireComponent
    public RunCommand(JobFileReader jobFileReader, ProcessRunner processRunner) {
        this.jobFileReader = jobFileReader;
        this.processRunner = processRunner;
    }

    @Spec
    private CommandSpec commandSpec;

    @Option(names = "--parallel", defaultValue = "3", description = "Maximum concurrent processes.")
    private int parallel;

    @Option(names = "--queue-capacity", defaultValue = "20", description = "Waiting-queue capacity.")
    private int queueCapacity;

    @Option(names = "--timeout-seconds", defaultValue = "10", description = "Timeout per process in seconds.")
    private long timeoutSeconds;

    @Option(names = "--grace-seconds", defaultValue = "2", description = "Termination grace period in seconds.")
    private long graceSeconds;

    @Option(names = "--output-dir", defaultValue = "output", description = "Directory for process output.")
    private Path outputDirectory;

    @Parameters(index = "0", paramLabel = "JOBS_TSV", description = "TSV job definitions.")
    private Path jobFile;

    /** Passes parsed runtime options to the injected runner for this batch. */
    @Override
    public Integer call() throws Exception {
        var options = toRunnerOptions();
        List<JobSpec> jobs;
        try {
            jobs = jobFileReader.read(jobFile);
        } catch (IOException e) {
            commandSpec.commandLine().getErr().println("Cannot read jobs from " + jobFile + ": " + e.getMessage());
            return 1;
        }

        // The resolver creates collaborators; parsed options configure each invocation.
        var results = processRunner.execute(jobs, options);
        int exitStatus = 0;
        for (var result : results) {
            commandSpec.commandLine().getOut().printf(
                    "%s\t%s\texit=%s\tduration=%dms\toutput=%s%s%n",
                    result.jobId(), result.status(),
                    result.exitCode().isPresent() ? Integer.toString(result.exitCode().getAsInt()) : "-",
                    result.duration().toMillis(),
                    result.outputFile().map(Path::toString).orElse("-"),
                    result.errorMessage().map(message -> "\terror=" + message).orElse(""));
            if (result.status() != JobStatus.SUCCESS) {
                exitStatus = 1;
            }
        }
        return exitStatus;
    }

    /** Converts parsed CLI values without allocating execution resources. */
    RunnerOptions toRunnerOptions() {
        if (parallel <= 0) {
            throw new ParameterException(commandSpec.commandLine(), "--parallel must be greater than zero");
        }
        if (queueCapacity <= 0) {
            throw new ParameterException(commandSpec.commandLine(), "--queue-capacity must be greater than zero");
        }
        if (timeoutSeconds <= 0) {
            throw new ParameterException(commandSpec.commandLine(), "--timeout-seconds must be greater than zero");
        }
        if (graceSeconds < 0) {
            throw new ParameterException(commandSpec.commandLine(), "--grace-seconds must be zero or greater");
        }
        return new RunnerOptions(parallel, queueCapacity, Duration.ofSeconds(timeoutSeconds),
                Duration.ofSeconds(graceSeconds), outputDirectory);
    }

}
