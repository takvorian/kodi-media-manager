package de.tiktak.kmm.media;

import java.nio.file.Path;

/**
 * Die technischen Angaben einer Videodatei konnten nicht gelesen werden.
 */
public class MediaProbeException extends RuntimeException {

    private MediaProbeException(String message, Throwable cause) {
        super(message, cause);
    }

    static MediaProbeException notStartable(String executable, Throwable cause) {
        return new MediaProbeException(
                "ffprobe kann nicht gestartet werden ('%s'). Ist es installiert und der Pfad richtig eingestellt?"
                        .formatted(executable), cause);
    }

    static MediaProbeException failed(String input, String detail) {
        return new MediaProbeException("ffprobe kann '%s' nicht lesen: %s".formatted(input, detail), null);
    }

    static MediaProbeException timedOut(String input) {
        return new MediaProbeException("ffprobe hat für '%s' zu lange gebraucht.".formatted(input), null);
    }

    static MediaProbeException unreadable(Path path, Throwable cause) {
        return new MediaProbeException("'%s' kann nicht gelesen werden.".formatted(path), cause);
    }

    static MediaProbeException noStreams(String input) {
        return new MediaProbeException("'%s' enthält keine erkennbaren Spuren.".formatted(input), null);
    }
}
