package dev.procrun;

import dev.procrun.config.DiFactory;
import picocli.CommandLine;

/** Boots the CLI; operational work belongs to the command and its collaborators. */
public class ProcRunApplication {
    public static void main(String[] args) {
        DiFactory diFactory = DiFactory.getInstance();
        var command = diFactory.runCommand();
        System.exit(new CommandLine(command).execute(args));
    }
}
