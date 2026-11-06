package de.tiktak.kmm.media;

import java.nio.file.Path;

import de.tiktak.kmm.i18n.LocalizedException;
import de.tiktak.kmm.i18n.LocalizedText;

/**
 * The technical details of a video file could not be read.
 */
public class MediaProbeException extends LocalizedException {

    private MediaProbeException(LocalizedText message, Throwable cause) {
        super(message, cause);
    }

    static MediaProbeException notStartable(String executable, Throwable cause) {
        return new MediaProbeException(LocalizedText.of("media.ffprobe.notStartable").with("executable", executable),
                cause);
    }

    /** @param detail the error output of ffprobe, shown as is */
    static MediaProbeException failed(String input, String detail) {
        return new MediaProbeException(
                LocalizedText.of("media.ffprobe.failed").with("input", input).with("detail", detail), null);
    }

    static MediaProbeException unintelligibleOutput(String input) {
        return new MediaProbeException(LocalizedText.of("media.ffprobe.unintelligibleOutput").with("input", input),
                null);
    }

    static MediaProbeException timedOut(String input) {
        return new MediaProbeException(LocalizedText.of("media.ffprobe.timedOut").with("input", input), null);
    }

    static MediaProbeException unreadable(Path path, Throwable cause) {
        return new MediaProbeException(LocalizedText.of("media.unreadable").with("path", path), cause);
    }

    static MediaProbeException noStreams(String input) {
        return new MediaProbeException(LocalizedText.of("media.noStreams").with("input", input), null);
    }
}
