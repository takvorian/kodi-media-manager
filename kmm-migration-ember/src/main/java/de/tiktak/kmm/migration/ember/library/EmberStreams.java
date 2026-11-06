package de.tiktak.kmm.migration.ember.library;

import java.util.List;

/**
 * Video, audio and subtitle streams of a movie or an episode from Ember's stream tables.
 */
public record EmberStreams(List<Video> videos, List<Audio> audios, List<Subtitle> subtitles) {

    public static final EmberStreams NONE = new EmberStreams(List.of(), List.of(), List.of());

    public EmberStreams {
        videos = videos == null ? List.of() : List.copyOf(videos);
        audios = audios == null ? List.of() : List.copyOf(audios);
        subtitles = subtitles == null ? List.of() : List.copyOf(subtitles);
    }

    /**
     * @param aspect          aspect ratio as text, e.g. {@code 1.778}
     * @param durationSeconds runtime in seconds or {@code null}
     * @param bitrate         bit rate in kbit/s or {@code null}
     * @param fileSize        size in bytes or {@code null}
     */
    public record Video(String codec, Integer width, Integer height, String aspect, Integer durationSeconds,
            Integer bitrate, String scanType, String stereoMode, String language, String multiViewCount,
            String multiViewLayout, Long fileSize) {
    }

    /**
     * @param channels number of channels or {@code null}
     * @param bitrate  bit rate in kbit/s or {@code null}
     */
    public record Audio(String codec, String language, Integer channels, Integer bitrate) {
    }

    /**
     * @param external whether it is a separate subtitle file
     * @param path     path of the subtitle file in Ember or {@code null}
     */
    public record Subtitle(String language, boolean forced, boolean external, String path) {
    }
}
