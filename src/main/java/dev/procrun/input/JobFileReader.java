package dev.procrun.input;

import dev.procrun.model.JobSpec;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/** Loads job definitions; implementations own and close any input resources they open. */
public interface JobFileReader {
    /** Loads definitions in file order; I/O and malformed input may be reported as IOException. */
    List<JobSpec> read(Path path) throws IOException;
}
