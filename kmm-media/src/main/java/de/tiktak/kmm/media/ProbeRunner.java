package de.tiktak.kmm.media;

/**
 * Calls ffprobe for an input and returns its JSON output.
 */
@FunctionalInterface
public interface ProbeRunner {

    /**
     * @param input path of a file or an ffmpeg input such as {@code concat:a.vob|b.vob}
     * @throws MediaProbeException if ffprobe is missing, fails or takes too long
     */
    String run(String input);
}
