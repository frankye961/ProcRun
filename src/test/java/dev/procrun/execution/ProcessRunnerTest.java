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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Timeout(15)
class ProcessRunnerTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void reportsSuccessAndEveryNonzeroExitCodeInInputOrder() throws Exception {
        List<JobResult> results = runner(outputDirectory()).execute(List.of(
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

        List<JobResult> results = runner(outputDirectory()).execute(
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
        JobResult result = runner(outputDirectory()).execute(
                List.of(fixture("output", "output", "262144"))).getFirst();

        assertEquals(JobStatus.SUCCESS, result.status());
        assertEquals("o".repeat(262144) + "e".repeat(262144),
                Files.readString(result.outputFile().orElseThrow()));
    }

    @Test
    void createsDistinctOutputFilesInsideConfiguredDirectory() throws Exception {
        Path output = outputDirectory();
        ProcessRunner runner = runner(output);
        assertFalse(Files.exists(output), "Constructor must not create output resources");

        List<JobResult> results = runner.execute(List.of(
                fixture("../../outside", "exit", "0"), fixture("another", "exit", "0")));

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
        List<JobResult> results = runner(outputDirectory()).execute(List.of(
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
        JobResult result = runner(outputDirectory()).execute(
                List.of(fixture("sleep", "sleep", "200"))).getFirst();

        assertEquals(JobStatus.SUCCESS, result.status());
        assertTrue(result.duration().compareTo(Duration.ofMillis(200)) >= 0);
    }

    @Test
    void returnsEmptyResultsForEmptyBatch() throws Exception {
        assertEquals(List.of(), runner(outputDirectory()).execute(List.of()));
    }

    @Test
    void reportsOutputDirectoryFailureAsJobFailure() throws Exception {
        Path file = Files.writeString(temporaryDirectory.resolve("file"), "not a directory");

        JobResult result = runner(file).execute(List.of(fixture("job", "exit", "0"))).getFirst();

        assertEquals(JobStatus.FAILED, result.status());
        assertTrue(result.pid().isEmpty());
        assertTrue(result.exitCode().isEmpty());
        assertTrue(result.errorMessage().isPresent());
    }

    private Path outputDirectory() {
        return temporaryDirectory.resolve("nested/output");
    }

    private ProcessRunner runner(Path output) {
        return new ProcessRunner(new RunnerOptions(3, 20, Duration.ofSeconds(10),
                Duration.ofSeconds(2), output));
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
