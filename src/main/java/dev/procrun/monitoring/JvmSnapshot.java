package dev.procrun.monitoring;

/**
 * Observations of the runner JVM, not its child processes. Heap values are bytes;
 * GC count and time are cumulative across collectors, with time in milliseconds.
 * Use -1 for an unavailable metric, including an undefined heap maximum.
 * If any collector lacks a count or time, the corresponding aggregate is unavailable.
 * Thread count covers live platform threads, excluding virtual threads.
 */
public record JvmSnapshot(
        long heapUsedBytes,
        long heapCommittedBytes,
        long heapMaxBytes,
        int livePlatformThreadCount,
        long garbageCollectionCount,
        long garbageCollectionTimeMillis) {
}
