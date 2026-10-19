package dev.procrun;

import dev.procrun.cli.RunCommand;
import dev.procrun.config.AutowireResolver;
import dev.procrun.input.JobFileReader;
import dev.procrun.input.TsvJobFileReader;
import java.util.Map;
import picocli.CommandLine;

/** Boots the CLI; operational work belongs to the command and its collaborators. */
public class ProcRunApplication {
    static void main(String[] args) {
        var resolver = new AutowireResolver(
                Map.of(JobFileReader.class, TsvJobFileReader.class));
        var command = resolver.resolve(RunCommand.class);
        System.exit(new CommandLine(command).execute(args));
    }
}
