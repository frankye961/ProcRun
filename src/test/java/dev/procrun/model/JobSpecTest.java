package dev.procrun.model;

import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JobSpecTest {
    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t", "\n"})
    void rejectsNullOrBlankIds(String id) {
        assertThrows(IllegalArgumentException.class,
                () -> new JobSpec(id, List.of("echo")));
    }

    @Test
    void rejectsNullCommandList() {
        assertThrows(IllegalArgumentException.class, () -> new JobSpec("job", null));
    }

    @Test
    void rejectsEmptyCommandList() {
        assertThrows(IllegalArgumentException.class, () -> new JobSpec("job", List.of()));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t", "\n"})
    void rejectsNullOrBlankExecutables(String executable) {
        assertThrows(IllegalArgumentException.class,
                () -> new JobSpec("job", Collections.singletonList(executable)));
    }

    @Test
    void preservesIdAndAllowsEmptyOrWhitespaceArguments() {
        JobSpec job = new JobSpec(" job ", List.of("echo", "", "  "));

        assertEquals(" job ", job.jobId());
        assertEquals(List.of("echo", "", "  "), job.commandArguments());
    }
}
