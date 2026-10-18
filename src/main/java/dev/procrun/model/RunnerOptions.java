package dev.procrun.model;

import java.nio.file.Path;
import java.time.Duration;

/** Execution limits and output location; contains no operational resources. */
public record RunnerOptions(
        int maxParallelProcesses,
        int waitingQueueCapacity,
        Duration perProcessTimeout,
        Duration terminationGracePeriod,
        Path outputDirectory) {
}
