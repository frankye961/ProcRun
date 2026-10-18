package dev.procrun.input;

import dev.procrun.model.JobSpec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TsvJobFileReaderTest {
    @TempDir
    Path temporaryDirectory;

    private final TsvJobFileReader reader = new TsvJobFileReader();

    @Test
    void readsAllJobsInInputOrderWithoutAHeader() throws IOException {
        Path input = writeInput("second\t/bin/echo\tHello\n"
                + "first\t/usr/bin/uname\t-s\n"
                + "third\t/bin/pwd");

        assertEquals(List.of(
                new JobSpec("second", List.of("/bin/echo", "Hello")),
                new JobSpec("first", List.of("/usr/bin/uname", "-s")),
                new JobSpec("third", List.of("/bin/pwd"))), reader.read(input));
    }

    @Test
    void skipsEmptyAndWhitespaceOnlyLines() throws IOException {
        Path input = writeInput("\n \t \nfirst\techo\tone\n\n\t\nsecond\tpwd\n  \n");

        assertEquals(List.of(
                new JobSpec("first", List.of("echo", "one")),
                new JobSpec("second", List.of("pwd"))), reader.read(input));
    }

    @Test
    void readsAnExecutableWithoutArguments() throws IOException {
        Path input = writeInput("location\t/bin/pwd\n");

        assertEquals(List.of(new JobSpec("location", List.of("/bin/pwd"))),
                reader.read(input));
    }

    @Test
    void preservesLiteralArgumentsAndUtf8Characters() throws IOException {
        Path input = writeInput("literal\techo\t  Hello, Java  \t\"quoted words\""
                + "\t$HOME\t|\t*.txt\tZażółć gęślą jaźń\n");

        assertEquals(List.of(new JobSpec("literal", List.of(
                "echo", "  Hello, Java  ", "\"quoted words\"", "$HOME", "|",
                "*.txt", "Zażółć gęślą jaźń"))), reader.read(input));
    }

    @Test
    void preservesEmptyArgumentsIncludingTrailingFields() throws IOException {
        Path input = writeInput("empty-arguments\techo\t\ttext\t\t\n");

        assertEquals(List.of(new JobSpec("empty-arguments",
                List.of("echo", "", "text", "", ""))), reader.read(input));
    }

    @Test
    void returnsNoJobsForAnEmptyFile() throws IOException {
        assertEquals(List.of(), reader.read(writeInput("")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"\techo", "   \techo"})
    void rejectsBlankIdsWithPhysicalLineNumber(String row) throws IOException {
        Path input = writeInput("valid\tpwd\n\n \t \n" + row + "\n");

        IOException error = assertThrows(IOException.class, () -> reader.read(input));

        assertEquals("Line 4: jobId cannot be blank", error.getMessage());
        assertInstanceOf(IllegalArgumentException.class, error.getCause());
    }

    @Test
    void rejectsMissingExecutable() throws IOException {
        Path input = writeInput("missing-executable\n");

        IOException error = assertThrows(IOException.class, () -> reader.read(input));

        assertEquals("Line 1: commandArguments cannot be null or empty", error.getMessage());
        assertInstanceOf(IllegalArgumentException.class, error.getCause());
    }

    @ParameterizedTest
    @ValueSource(strings = {"job\t", "job\t   ", "job\t\targument"})
    void rejectsBlankExecutables(String row) throws IOException {
        Path input = writeInput(row + "\n");

        IOException error = assertThrows(IOException.class, () -> reader.read(input));

        assertEquals("Line 1: executable cannot be blank", error.getMessage());
        assertInstanceOf(IllegalArgumentException.class, error.getCause());
    }

    @ParameterizedTest
    @ValueSource(strings = {"echo\tfirst", "pwd"})
    void rejectsDuplicateIdsRegardlessOfCommand(String secondCommand) throws IOException {
        Path input = writeInput("same\techo\tfirst\n\n  \nsame\t" + secondCommand + "\n");

        IOException error = assertThrows(IOException.class, () -> reader.read(input));

        assertEquals("Line 4: duplicate job ID 'same'", error.getMessage());
    }

    @Test
    void comparesIdsExactlyAndCaseSensitively() throws IOException {
        Path input = writeInput("Job\tpwd\njob\tpwd\n job \tpwd\n");

        assertEquals(List.of(
                new JobSpec("Job", List.of("pwd")),
                new JobSpec("job", List.of("pwd")),
                new JobSpec(" job ", List.of("pwd"))), reader.read(input));
    }

    @Test
    void propagatesMissingFileError() {
        Path missing = temporaryDirectory.resolve("missing.tsv");

        NoSuchFileException error = assertThrows(NoSuchFileException.class,
                () -> reader.read(missing));

        assertEquals(missing.toString(), error.getFile());
    }

    private Path writeInput(String content) throws IOException {
        return Files.writeString(temporaryDirectory.resolve("jobs.tsv"), content,
                StandardCharsets.UTF_8);
    }
}
