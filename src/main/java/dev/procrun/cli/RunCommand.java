package dev.procrun.cli;

import dev.procrun.config.AutowireComponent;
import dev.procrun.input.JobFileReader;
import dev.procrun.model.RunnerOptions;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.Callable;
import picocli.CommandLine.ParameterException;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Spec;
import picocli.CommandLine.Model.CommandSpec;

/** Will coordinate input, execution, monitoring, and reporting; owns the runner lifecycle. */
@Command(name = "procrun", mixinStandardHelpOptions = true, version = "ProcRun 0.1.0",
        description = "Run commands from a TSV file (execution is not implemented yet).",
        showDefaultValues = true)
public class RunCommand implements Callable<Integer> {
    private final JobFileReader jobFileReader;

    @AutowireComponent
    public RunCommand(JobFileReader jobFileReader) {
        this.jobFileReader = jobFileReader;
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

    /** Validates options and loads input; batch execution and reporting remain unimplemented. */
    @Override
    public Integer call() throws Exception {
        toRunnerOptions();
        var jobs = jobFileReader.read(jobFile);
        throw new UnsupportedOperationException("Not implemented yet");
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

    private void run(){
        var runnerOption = toRunnerOptions();

    }
}
