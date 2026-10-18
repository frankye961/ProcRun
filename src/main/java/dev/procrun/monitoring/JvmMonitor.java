package dev.procrun.monitoring;

/** Will observe this runner's JVM via java.lang.management without owning execution resources. */
public class JvmMonitor {
    /** Will capture heap, live platform-thread, and cumulative garbage-collection observations. */
    public JvmSnapshot snapshot() {
        throw new UnsupportedOperationException("Not implemented yet");
    }
}
