package de.tiktak.kmm.nfo;

import java.util.List;
import java.util.Optional;

/**
 * Kurze Bezeichnungen einer Videospur, etwa für Dateinamen: Auflösungsstufe wie Kodi und 3D wie Ember.
 */
public final class StreamLabels {

    private static final String THREE_D = "3d";
    private static final String MULTIPLE_VIEWS = "[2-9]|\\d{2,}";

    /**
     * Auflösungsstufen wie Kodi ({@code CStreamDetails::COMMON_RESOLUTIONS}): die erste, in die das Bild
     * in beiden Richtungen passt.
     */
    private static final List<Resolution> RESOLUTIONS = List.of(
            new Resolution(854, 480, "480p"),
            new Resolution(960, 544, "540p"),
            new Resolution(1024, 576, "576p"),
            new Resolution(1280, 962, "720p"),
            new Resolution(1920, 1440, "1080p"),
            new Resolution(4096, 3072, "4K"),
            new Resolution(8192, 6144, "8K"));

    private StreamLabels() {
    }

    /** @return z. B. {@code 1080p} oder leer ohne Abmessungen und bei Bildern über 8K */
    public static Optional<String> resolution(StreamDetails.Video video) {
        if (video.width() == null || video.height() == null || video.width() == 0 || video.height() == 0) {
            return Optional.empty();
        }
        return RESOLUTIONS.stream()
                .filter(resolution -> video.width() <= resolution.maxWidth() && video.height() <= resolution.maxHeight())
                .map(Resolution::label)
                .findFirst();
    }

    /** Wie Ember: {@code 3d} bei Stereo-Modus oder mehr als einer Ansicht. */
    public static Optional<String> threeD(StreamDetails.Video video) {
        boolean stereo = video.stereoMode() != null && !video.stereoMode().isBlank();
        boolean views = video.multiViewCount() != null && video.multiViewCount().strip().matches(MULTIPLE_VIEWS);
        return stereo || views ? Optional.of(THREE_D) : Optional.empty();
    }

    private record Resolution(int maxWidth, int maxHeight, String label) {
    }
}
