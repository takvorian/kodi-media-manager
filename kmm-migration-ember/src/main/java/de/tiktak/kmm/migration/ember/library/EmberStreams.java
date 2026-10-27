package de.tiktak.kmm.migration.ember.library;

import java.util.List;

/**
 * Video-, Ton- und Untertitelspuren eines Films oder einer Episode aus Embers Stream-Tabellen.
 */
public record EmberStreams(List<Video> videos, List<Audio> audios, List<Subtitle> subtitles) {

    public static final EmberStreams NONE = new EmberStreams(List.of(), List.of(), List.of());

    public EmberStreams {
        videos = videos == null ? List.of() : List.copyOf(videos);
        audios = audios == null ? List.of() : List.copyOf(audios);
        subtitles = subtitles == null ? List.of() : List.copyOf(subtitles);
    }

    /**
     * @param aspect          Seitenverhältnis als Text, z. B. {@code 1.778}
     * @param durationSeconds Laufzeit in Sekunden oder {@code null}
     * @param bitrate         Bitrate in kbit/s oder {@code null}
     * @param fileSize        Größe in Byte oder {@code null}
     */
    public record Video(String codec, Integer width, Integer height, String aspect, Integer durationSeconds,
            Integer bitrate, String scanType, String stereoMode, String language, String multiViewCount,
            String multiViewLayout, Long fileSize) {
    }

    /**
     * @param channels Anzahl der Kanäle oder {@code null}
     * @param bitrate  Bitrate in kbit/s oder {@code null}
     */
    public record Audio(String codec, String language, Integer channels, Integer bitrate) {
    }

    /**
     * @param external ob es eine separate Untertiteldatei ist
     * @param path     Pfad der Untertiteldatei in Ember oder {@code null}
     */
    public record Subtitle(String language, boolean forced, boolean external, String path) {
    }
}
