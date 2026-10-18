package dev.procrun.model;

import java.util.List;

/** Command arguments include the executable first; no shell interpretation is intended. */
public record JobSpec(String jobId, List<String> commandArguments) {

    public JobSpec(String jobId, List<String> commandArguments) {
        if (jobId == null) throw new IllegalArgumentException("jobId cannot be null");
        if (jobId.isBlank()) throw new IllegalArgumentException("jobId cannot be blank");
        if (commandArguments == null || commandArguments.isEmpty())
            throw new IllegalArgumentException("commandArguments cannot be null or empty");
        if (commandArguments.getFirst() == null || commandArguments.getFirst().isBlank())
            throw new IllegalArgumentException("executable cannot be blank");

        this.jobId = jobId;
        this.commandArguments = commandArguments;
    }
}
