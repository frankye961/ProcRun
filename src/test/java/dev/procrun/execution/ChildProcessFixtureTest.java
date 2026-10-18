package dev.procrun.execution;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChildProcessFixtureTest {
    @TempDir
    Path temporaryDirectory;

    @ParameterizedTest
    @ValueSource(ints = {0, 7, 23})
    void exitsWithRequestedCode(int exitCode) throws Exception {
        Process child = startFixture("exit", Integer.toString(exitCode));
        try {
            assertTrue(child.waitFor(10, TimeUnit.SECONDS), "Child did not exit within deadline");
            assertEquals(exitCode, child.exitValue());
        } finally {
            cleanUp(child);
        }
    }

    @Test
    void sleepsForRequestedDurationThenExitsNormally() throws Exception {
        long started = System.nanoTime();
        Process child = startFixture("sleep", "200");
        try {
            assertTrue(child.waitFor(10, TimeUnit.SECONDS), "Sleeping child exceeded deadline");
            assertEquals(0, child.exitValue());
            assertTrue(System.nanoTime() - started >= Duration.ofMillis(200).toNanos(),
                    "Child finished before the requested sleep duration");
        } finally {
            cleanUp(child);
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 10000, 262144})
    void writesExactByteCountsToBothOutputStreams(int count) throws Exception {
        Process child = startFixture("output", Integer.toString(count));
        try {
            assertTrue(child.waitFor(10, TimeUnit.SECONDS), "Output child exceeded deadline");
            assertEquals(0, child.exitValue());
            byte[] expected = new byte[count];
            Arrays.fill(expected, (byte) 'o');
            assertArrayEquals(expected, Files.readAllBytes(temporaryDirectory.resolve("stdout.txt")));
            Arrays.fill(expected, (byte) 'e');
            assertArrayEquals(expected, Files.readAllBytes(temporaryDirectory.resolve("stderr.txt")));
        } finally {
            cleanUp(child);
        }
    }

    private Process startFixture(String mode, String value) throws Exception {
        Path javaExecutable = Path.of(System.getProperty("java.home"), "bin", "java");
        Path fixtureClasspath = Path.of(ChildProcessFixture.class.getProtectionDomain()
                .getCodeSource().getLocation().toURI());
        return new ProcessBuilder(javaExecutable.toString(), "-cp", fixtureClasspath.toString(),
                ChildProcessFixture.class.getName(), mode, value)
                .redirectOutput(temporaryDirectory.resolve("stdout.txt").toFile())
                .redirectError(temporaryDirectory.resolve("stderr.txt").toFile())
                .start();
    }

    private void cleanUp(Process child) throws InterruptedException {
        if (child.isAlive()) {
            child.destroyForcibly();
        }
        assertTrue(child.waitFor(5, TimeUnit.SECONDS), "Child survived forced cleanup");
    }
}
