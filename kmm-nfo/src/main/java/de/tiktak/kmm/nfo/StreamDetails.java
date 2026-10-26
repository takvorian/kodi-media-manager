package de.tiktak.kmm.nfo;

import java.util.List;

/**
 * Technische Angaben einer Videodatei ({@code <fileinfo><streamdetails>} in Kodi-NFOs): Video-,
 * Ton- und Untertitelspuren.
 */
public record StreamDetails(List<Video> videos, List<Audio> audios, List<Subtitle> subtitles) {

    public static final StreamDetails EMPTY = new StreamDetails(List.of(), List.of(), List.of());

    public StreamDetails {
        videos = NfoCollections.list(videos);
        audios = NfoCollections.list(audios);
        subtitles = NfoCollections.list(subtitles);
    }

    public boolean isEmpty() {
        return videos.isEmpty() && audios.isEmpty() && subtitles.isEmpty();
    }

    /**
     * @param aspect          Seitenverhältnis, z. B. 1.78
     * @param durationSeconds Laufzeit in Sekunden
     * @param bitrate         Bitrate in kbit/s
     * @param scanType        z. B. {@code Progressive}
     * @param stereoMode      3D-Modus, z. B. {@code left_right}
     */
    public record Video(String codec, Double aspect, Integer width, Integer height, Integer durationSeconds,
            Integer bitrate, String scanType, String stereoMode, String language) {
    }

    /**
     * @param language Sprache als ISO-639-2-Code, z. B. {@code deu}
     * @param channels Anzahl der Kanäle, z. B. 6 für 5.1
     * @param bitrate  Bitrate in kbit/s
     */
    public record Audio(String codec, String language, Integer channels, Integer bitrate) {
    }

    /**
     * @param forced ob es sich um erzwungene Untertitel handelt
     */
    public record Subtitle(String language, boolean forced) {
    }
}
