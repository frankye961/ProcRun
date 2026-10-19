package dev.procrun.cli;

import dev.procrun.input.TsvJobFileReader;
import dev.procrun.config.DiFactory;
import dev.procrun.execution.ChildProcessFixture;
import dev.procrun.execution.ProcessRunner;
import dev.procrun.model.JobSpec;
import dev.procrun.model.JobResult;
import dev.procrun.model.RunnerOptions;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import picocli.CommandLine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Timeout(15)
class RunCommandTest {
    @Test
    void convertsDefaultsIntoRunnerOptions() {
        RunCommand command = new RunCommand(new TsvJobFileReader(), new ProcessRunner());
        new CommandLine(command).parseArgs("examples/jobs.tsv");

        assertEquals(new RunnerOptions(3, 20, Duration.ofSeconds(10), Duration.ofSeconds(2),
                Path.of("output")), command.toRunnerOptions());
    }

    @Test
    void convertsCustomOptionsAndAllowsZeroGracePeriod() {
        RunCommand command = new RunCommand(new TsvJobFileReader(), new ProcessRunner());
        new CommandLine(command).parseArgs("--parallel", "1", "--queue-capacity", "1",
                "--timeout-seconds", "30", "--grace-seconds", "0",
                "--output-dir", "custom output", "jobs.tsv");

        assertEquals(new RunnerOptions(1, 1, Duration.ofSeconds(30), Duration.ZERO,
                Path.of("custom output")), command.toRunnerOptions());
    }

    @ParameterizedTest
    @ValueSource(strings = {"--parallel=0", "--parallel=-1", "--queue-capacity=0",
            "--queue-capacity=-1", "--timeout-seconds=0", "--timeout-seconds=-1",
            "--grace-seconds=-1"})
    void rejectsInvalidLimitsWithReadableUsageError(String option) {
        CommandLine cli = new CommandLine(new RunCommand(new TsvJobFileReader(), new ProcessRunner()));
        StringWriter errors = new StringWriter();
        cli.setErr(new PrintWriter(errors));

        assertEquals(2, cli.execute(option, "jobs.tsv"));
        assertTrue(errors.toString().contains(option.substring(0, option.indexOf('=')) + " must be"));
        assertFalse(errors.toString().contains("UnsupportedOperationException"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"--parallel=abc", "--queue-capacity=2147483648",
            "--timeout-seconds=1.5", "--grace-seconds=9223372036854775808"})
    void rejectsMalformedOrOverflowingNumericOptions(String option) {
        CommandLine cli = new CommandLine(new RunCommand(new TsvJobFileReader(), new ProcessRunner()));
        StringWriter errors = new StringWriter();
        cli.setErr(new PrintWriter(errors));

        assertEquals(2, cli.execute(option, "jobs.tsv"));
        assertTrue(errors.toString().contains(option.substring(0, option.indexOf('='))));
    }

    @Test
    void helpWorksWithoutAJobFile() {
        CommandLine cli = new CommandLine(new RunCommand(new TsvJobFileReader(), new ProcessRunner()));
        StringWriter output = new StringWriter();
        cli.setOut(new PrintWriter(output));

        assertEquals(0, cli.execute("--help"));
        assertTrue(output.toString().contains("Usage: procrun"));
        assertTrue(output.toString().contains("--timeout-seconds"));
    }

    @Test
    void versionWorksWithoutAJobFile() {
        CommandLine cli = new CommandLine(new RunCommand(new TsvJobFileReader(), new ProcessRunner()));
        StringWriter output = new StringWriter();
        cli.setOut(new PrintWriter(output));

        assertEquals(0, cli.execute("--version"));
        assertEquals("ProcRun 0.1.0", output.toString().strip());
    }

    @Test
    void executesJobsAndReportsSuccessInInputOrder(@TempDir Path directory) throws Exception {
        Invocation invocation = executeJobs(directory,
                fixture("second", "exit", "0"), fixture("first", "arguments", "Hello, Java", "$HOME"));

        assertEquals(0, invocation.exitCode());
        assertEquals("", invocation.errors());
        var rows = invocation.output().lines().toList();
        assertEquals(2, rows.size());
        assertTrue(rows.getFirst().startsWith("second\tSUCCESS\texit=0\tduration="));
        assertTrue(rows.getLast().startsWith("first\tSUCCESS\texit=0\tduration="));
        assertTrue(rows.getFirst().contains("ms\toutput=" + directory.resolve("output")));
        Path capturedOutput = Path.of(rows.getLast().split("\toutput=", 2)[1]);
        assertEquals(List.of("Hello, Java", "$HOME"), Files.readAllLines(capturedOutput));
    }

    @Test
    void usesInjectedReaderAndReportsItsInputError() {
        Path input = Path.of("injected.tsv");
        IOException failure = new IOException("Injected reader failure");
        RunCommand command = new RunCommand(path -> {
            assertEquals(input, path);
            throw failure;
        }, new ProcessRunner());
        CommandLine cli = new CommandLine(command);
        StringWriter errors = new StringWriter();
        cli.setErr(new PrintWriter(errors));

        assertEquals(1, cli.execute(input.toString()));
        assertTrue(errors.toString().contains("Cannot read jobs from injected.tsv: Injected reader failure"));
        assertFalse(errors.toString().contains("at dev.procrun"));
    }

    @Test
    void helpAndVersionDoNotReadJobFiles() {
        RunCommand command = new RunCommand(path -> {
            throw new AssertionError("Help and version must not read input");
        }, new ProcessRunner());
        CommandLine cli = new CommandLine(command);
        cli.setOut(new PrintWriter(new StringWriter()));

        assertEquals(0, cli.execute("--help"));
        assertEquals(0, cli.execute("--version"));
    }

    @Test
    void returnsFailureForNonzeroExitAndStillReportsFollowingJobs(@TempDir Path directory) throws Exception {
        Invocation invocation = executeJobs(directory,
                fixture("failed", "exit", "7"), fixture("next", "exit", "0"));

        assertEquals(1, invocation.exitCode());
        var rows = invocation.output().lines().toList();
        assertEquals(2, rows.size());
        assertTrue(rows.getFirst().startsWith("failed\tFAILED\texit=7\t"));
        assertTrue(rows.getLast().startsWith("next\tSUCCESS\texit=0\t"));
    }

    @Test
    void reportsStartupErrorsAndContinues(@TempDir Path directory) throws Exception {
        JobSpec missing = new JobSpec("missing", List.of(directory.resolve("missing-executable").toString()));

        Invocation invocation = executeJobs(directory, missing, fixture("next", "exit", "0"));

        assertEquals(1, invocation.exitCode());
        var rows = invocation.output().lines().toList();
        assertEquals(2, rows.size());
        assertTrue(rows.getFirst().startsWith("missing\tFAILED\texit=-\t"));
        assertTrue(rows.getFirst().contains("\terror="));
        assertTrue(rows.getFirst().contains("missing-executable"));
        assertTrue(rows.getLast().startsWith("next\tSUCCESS\t"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"job\n", "same\techo\nsame\tpwd\n"})
    void reportsMalformedInputWithoutStartingJobs(String content, @TempDir Path directory) throws IOException {
        Path input = Files.writeString(directory.resolve("invalid.tsv"), content);
        Path output = directory.resolve("output");
        CommandLine cli = new CommandLine(DiFactory.getInstance().runCommand());
        StringWriter errors = new StringWriter();
        cli.setErr(new PrintWriter(errors));

        assertEquals(1, cli.execute("--output-dir", output.toString(), input.toString()));
        assertTrue(errors.toString().contains("Cannot read jobs from " + input));
        assertTrue(errors.toString().contains("Line "));
        assertFalse(Files.exists(output));
    }

    @Test
    void reportsMissingInputFile(@TempDir Path directory) {
        Path input = directory.resolve("missing.tsv");
        CommandLine cli = new CommandLine(DiFactory.getInstance().runCommand());
        StringWriter errors = new StringWriter();
        cli.setErr(new PrintWriter(errors));

        assertEquals(1, cli.execute(input.toString()));
        assertTrue(errors.toString().contains("Cannot read jobs from " + input));
    }

    @Test
    void acceptsAnEmptyInputFile(@TempDir Path directory) throws IOException {
        Invocation invocation = executeJobs(directory);

        assertEquals(0, invocation.exitCode());
        assertEquals("", invocation.output());
        assertEquals("", invocation.errors());
    }

    @Test
    void passesParsedOptionsAndLoadedJobsToInjectedRunner(@TempDir Path directory) throws Exception {
        JobSpec job = fixture("injected-runner", "exit", "0");
        Path input = Files.writeString(directory.resolve("jobs.tsv"),
                job.jobId() + "\t" + String.join("\t", job.commandArguments()) + "\n");
        Path output = directory.resolve("captured");
        AtomicReference<RunnerOptions> observedOptions = new AtomicReference<>();
        AtomicReference<List<JobSpec>> observedJobs = new AtomicReference<>();
        ProcessRunner runner = new ProcessRunner() {
            @Override
            public List<JobResult> execute(List<JobSpec> jobs, RunnerOptions options) throws InterruptedException {
                observedOptions.set(options);
                observedJobs.set(jobs);
                return super.execute(jobs, options);
            }
        };
        CommandLine cli = new CommandLine(new RunCommand(new TsvJobFileReader(), runner));
        cli.setOut(new PrintWriter(new StringWriter()));

        assertEquals(0, cli.execute("--parallel", "2", "--queue-capacity", "1",
                "--timeout-seconds", "30", "--grace-seconds", "0",
                "--output-dir", output.toString(), input.toString()));
        assertEquals(new RunnerOptions(2, 1, Duration.ofSeconds(30), Duration.ZERO, output),
                observedOptions.get());
        assertEquals(List.of(job), observedJobs.get());
    }

    private Invocation executeJobs(Path directory, JobSpec... jobs) throws IOException {
        var content = new StringBuilder();
        for (JobSpec job : jobs) {
            content.append(job.jobId()).append('\t')
                    .append(String.join("\t", job.commandArguments())).append('\n');
        }
        Path input = Files.writeString(directory.resolve("jobs.tsv"), content);
        CommandLine cli = new CommandLine(DiFactory.getInstance().runCommand());
        StringWriter output = new StringWriter();
        StringWriter errors = new StringWriter();
        cli.setOut(new PrintWriter(output));
        cli.setErr(new PrintWriter(errors));
        int exitCode = cli.execute("--parallel", "1", "--queue-capacity", "1",
                "--output-dir", directory.resolve("output").toString(), input.toString());
        return new Invocation(exitCode, output.toString(), errors.toString());
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

    private record Invocation(int exitCode, String output, String errors) {}
}
