package de.tiktak.kmm.nfo;

import java.util.List;
import java.util.Optional;

/**
 * Short labels of a video stream, e.g. for file names: resolution tier as in Kodi and 3D as in Ember.
 */
public final class StreamLabels {

    private static final String THREE_D = "3d";
    private static final String MULTIPLE_VIEWS = "[2-9]|\\d{2,}";

    /**
     * Resolution tiers as in Kodi ({@code CStreamDetails::COMMON_RESOLUTIONS}): the first one the picture fits into
     * in both directions.
     */
    private static final List<Resolution> RESOLUTIONS = List.of(
            new Resolution(854, 480, "480p", 480),
            new Resolution(960, 544, "540p", 540),
            new Resolution(1024, 576, "576p", 576),
            new Resolution(1280, 962, "720p", 720),
            new Resolution(1920, 1440, "1080p", 1080),
            new Resolution(4096, 3072, "4K", 2160),
            new Resolution(8192, 6144, "8K", 4320));

    private StreamLabels() {
    }

    /** @return e.g. {@code 1080p}, or empty without dimensions and for pictures above 8K */
    public static Optional<String> resolution(StreamDetails.Video video) {
        return level(video).map(Resolution::label);
    }

    /** @return number of lines of the resolution tier, e.g. {@code 1080} or {@code 2160} for 4K, for comparisons */
    public static Optional<Integer> resolutionLines(StreamDetails.Video video) {
        return level(video).map(Resolution::lines);
    }

    private static Optional<Resolution> level(StreamDetails.Video video) {
        if (video.width() == null || video.height() == null || video.width() == 0 || video.height() == 0) {
            return Optional.empty();
        }
        return RESOLUTIONS.stream()
                .filter(resolution -> video.width() <= resolution.maxWidth() && video.height() <= resolution.maxHeight())
                .findFirst();
    }

    /** Like Ember: {@code 3d} for a stereo mode or more than one view. */
    public static Optional<String> threeD(StreamDetails.Video video) {
        boolean stereo = video.stereoMode() != null && !video.stereoMode().isBlank();
        boolean views = video.multiViewCount() != null && video.multiViewCount().strip().matches(MULTIPLE_VIEWS);
        return stereo || views ? Optional.of(THREE_D) : Optional.empty();
    }

    private record Resolution(int maxWidth, int maxHeight, String label, int lines) {
    }
}
