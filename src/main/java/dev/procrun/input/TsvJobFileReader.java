package dev.procrun.input;

import dev.procrun.model.JobSpec;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;


/**
 * Will read UTF-8 TSV fields as literal arguments without shell or quote parsing.
 */
public class TsvJobFileReader implements JobFileReader {
    @Override
    public List<JobSpec> read(Path path) throws IOException {
        var jobSpecs = new ArrayList<JobSpec>();
        var jobs = new HashSet<String>();
        int lineNumber = 0;
        try (BufferedReader bufferedFile = Files.newBufferedReader(path)) {
            String line;
            while ((line = bufferedFile.readLine()) != null) {
                lineNumber++;
                if (line.isBlank()) continue;
                executeReader(line, jobs, jobSpecs, lineNumber);
            }
        }
        return jobSpecs;
    }

    private void executeReader(String line, HashSet<String> jobs, List<JobSpec> jobSpecs, int lineNumber) throws IOException {
        var splitLines = line.split("\t", -1);
        var jobId = splitLines[0];
        var len = splitLines.length;
        var cmd = List.of(Arrays.copyOfRange(splitLines, 1, len));
        checkForDuplicates(jobId, jobs, lineNumber);
        try {
            jobSpecs.add(mapJobSpec(jobId, cmd));
        } catch (IllegalArgumentException e) {
            throw new IOException("Line " + lineNumber + ": " + e.getMessage(), e);
        }
    }

    private JobSpec mapJobSpec(String jobId, List<String> arguments) {
        return new JobSpec(jobId, arguments);
    }

    private void checkForDuplicates(String ids, HashSet<String> jobs, int lineNumber) throws IOException {
        if (!jobs.add(ids)) {
            throw new IOException(
                    "Line " + lineNumber + ": duplicate job ID '" + ids + "'");
        }
    }
}
