package dev.procrun.execution;

import java.io.OutputStream;
import java.io.IOException;
import java.util.Arrays;

/** A sequential child program used by tests; never packaged in the application JAR. */
public final class ChildProcessFixture {
    public static void main(String[] args) throws Exception {
        switch (args[0]) {
            case "exit" -> System.exit(Integer.parseInt(args[1]));
            case "sleep" -> Thread.sleep(Long.parseLong(args[1]));
            case "stdin" -> System.out.println(System.in.read());
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
