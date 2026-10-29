package de.tiktak.kmm.plugin.youtube;

import java.io.IOException;
import java.time.Duration;
import java.util.List;

/**
 * Führt ein Programm aus; in Tests durch eine Attrappe ersetzbar.
 */
interface CommandRunner {

    /**
     * @return Ausgabe und Rückgabewert
     * @throws IOException wenn das Programm nicht startet oder die Zeit überschreitet
     */
    Result run(List<String> command, Duration timeout) throws IOException;

    /**
     * @param output Standardausgabe
     * @param errors Fehlerausgabe
     */
    record Result(int exitCode, String output, String errors) {

        boolean succeeded() {
            return exitCode == 0;
        }
    }
}
