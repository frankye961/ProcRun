package dev.procrun;

import dev.procrun.cli.RunCommand;
import picocli.CommandLine;

/** Boots the CLI; operational work belongs to the command and its collaborators. */
public class ProcRunApplication {
    public static void main(String[] args) {
        System.exit(new CommandLine(new RunCommand()).execute(args));
    }
}
