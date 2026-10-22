package dev.procrun.execution;

import java.io.OutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

/** A sequential child program used by tests; never packaged in the application JAR. */
public final class ChildProcessFixture {
    public static void main(String[] args) throws Exception {
        switch (args[0]) {
            case "exit" -> System.exit(Integer.parseInt(args[1]));
            case "sleep" -> Thread.sleep(Long.parseLong(args[1]));
            case "stdin" -> System.out.println(System.in.read());
            case "shutdown-delay" -> {
                Path started = Path.of(args[1]);
                Path finished = Path.of(args[2]);
                long delay = Long.parseLong(args[3]);
                Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                    try {
                        Files.writeString(started, Long.toString(ProcessHandle.current().pid()));
                        Thread.sleep(delay);
                        Files.writeString(finished, "shutdown finished");
                    } catch (IOException e) {
                        throw new IllegalStateException(e);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }));
                Thread.sleep(30000);
            }
            case "gate" -> {
                Path ready = Path.of(args[1]);
                Path temporary = ready.resolveSibling(ready.getFileName() + ".tmp");
                Files.writeString(temporary, Long.toString(ProcessHandle.current().pid()));
                Files.move(temporary, ready);
                while (!Files.exists(Path.of(args[2]))) {
                    Thread.sleep(10);
                }
            }
            case "arguments" -> {
                for (int index = 1; index < args.length; index++) {
                    System.out.println(args[index]);
                }
            }
            case "output" -> {
                int count = Integer.parseInt(args[1]);
                writeBytes(System.out, count, (byte) 'o');
                writeBytes(System.err, count, (byte) 'e');
            }
            default -> throw new IllegalArgumentException("Unknown fixture mode: " + args[0]);
        }
    }

    private static void writeBytes(OutputStream output, int count, byte value) throws IOException {
        byte[] buffer = new byte[8192];
        Arrays.fill(buffer, value);
        int remaining = count;
        while (remaining > 0) {
            int length = Math.min(remaining, buffer.length);
            output.write(buffer, 0, length);
            remaining -= length;
        }
        output.flush();
    }
}
