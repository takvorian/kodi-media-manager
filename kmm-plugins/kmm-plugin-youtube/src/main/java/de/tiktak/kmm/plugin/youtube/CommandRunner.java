package de.tiktak.kmm.plugin.youtube;

import java.io.IOException;
import java.time.Duration;
import java.util.List;

/**
 * Runs a program; can be replaced by a stub in tests.
 */
interface CommandRunner {

    /**
     * @return output and exit code
     * @throws IOException if the program does not start or exceeds the time
     */
    Result run(List<String> command, Duration timeout) throws IOException;

    /**
     * @param output standard output
     * @param errors error output
     */
    record Result(int exitCode, String output, String errors) {

        boolean succeeded() {
            return exitCode == 0;
        }
    }
}
