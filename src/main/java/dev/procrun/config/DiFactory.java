package dev.procrun.config;

import dev.procrun.cli.RunCommand;
import dev.procrun.input.JobFileReader;
import dev.procrun.input.TsvJobFileReader;

import java.util.Map;

/** Assembles fresh command instances using the application's explicit dependency bindings. */
public final class DiFactory {

    private static final DiFactory INSTANCE = new DiFactory();

    private DiFactory() {}

    public RunCommand runCommand() {
        var resolver = new AutowireResolver(
                Map.of(JobFileReader.class, TsvJobFileReader.class));
        return resolver.resolve(RunCommand.class);
    }

    public static DiFactory getInstance() {
        return INSTANCE;
    }
}
