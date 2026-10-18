package dev.procrun.cli;

import dev.procrun.model.RunnerOptions;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Path;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import picocli.CommandLine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RunCommandTest {
    @Test
    void convertsDefaultsIntoRunnerOptions() {
        RunCommand command = new RunCommand();
        new CommandLine(command).parseArgs("examples/jobs.tsv");

        assertEquals(new RunnerOptions(3, 20, Duration.ofSeconds(10), Duration.ofSeconds(2),
                Path.of("output")), command.toRunnerOptions());
    }

    @Test
    void convertsCustomOptionsAndAllowsZeroGracePeriod() {
        RunCommand command = new RunCommand();
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
        CommandLine cli = new CommandLine(new RunCommand());
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
        CommandLine cli = new CommandLine(new RunCommand());
        StringWriter errors = new StringWriter();
        cli.setErr(new PrintWriter(errors));

        assertEquals(2, cli.execute(option, "jobs.tsv"));
        assertTrue(errors.toString().contains(option.substring(0, option.indexOf('='))));
    }

    @Test
    void helpWorksWithoutAJobFile() {
        CommandLine cli = new CommandLine(new RunCommand());
        StringWriter output = new StringWriter();
        cli.setOut(new PrintWriter(output));

        assertEquals(0, cli.execute("--help"));
        assertTrue(output.toString().contains("Usage: procrun"));
        assertTrue(output.toString().contains("--timeout-seconds"));
    }

    @Test
    void versionWorksWithoutAJobFile() {
        CommandLine cli = new CommandLine(new RunCommand());
        StringWriter output = new StringWriter();
        cli.setOut(new PrintWriter(output));

        assertEquals(0, cli.execute("--version"));
        assertEquals("ProcRun 0.1.0", output.toString().strip());
    }

    @Test
    void validOptionsStillFailExplicitlyForUnimplementedExecution() {
        RunCommand command = new RunCommand();
        new CommandLine(command).parseArgs("jobs.tsv");

        UnsupportedOperationException error = assertThrows(UnsupportedOperationException.class,
                command::call);

        assertEquals("Not implemented yet", error.getMessage());
    }
}
