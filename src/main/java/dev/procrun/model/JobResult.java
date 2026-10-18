package dev.procrun.model;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.OptionalLong;

/** Outcome data; absent values represent facts or artifacts unavailable for this job. */
public record JobResult(
        String jobId,
        OptionalLong pid,
        JobStatus status,
        OptionalInt exitCode,
        Duration duration,
        Optional<Path> outputFile,
        Optional<String> errorMessage) {
}
