package de.tiktak.kmm.nfo;

import java.util.List;

/**
 * Technical details of a video file ({@code <fileinfo><streamdetails>} in Kodi NFOs): video, audio and subtitle
 * streams.
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
     * @param aspect          aspect ratio, e.g. 1.78
     * @param durationSeconds runtime in seconds
     * @param bitrate         bit rate in kbit/s
     * @param scanType        e.g. {@code Progressive}
     * @param stereoMode      3D mode, e.g. {@code left_right}
     * @param hdrType         HDR type as in Kodi 20+: {@code hdr10}, {@code dolbyvision} or {@code hlg}
     * @param multiViewCount  number of views for 3D (MVC), written by Ember
     * @param multiViewLayout arrangement of the views for 3D (MVC), written by Ember
     * @param fileSize        size of the file(s) in bytes, written by Ember
     */
    public record Video(String codec, Double aspect, Integer width, Integer height, Integer durationSeconds,
            Integer bitrate, String scanType, String stereoMode, String language, String hdrType,
            String multiViewCount, String multiViewLayout, Long fileSize) {

        /** Video stream with the details Kodi itself knows, without HDR. */
        public Video(String codec, Double aspect, Integer width, Integer height, Integer durationSeconds,
                Integer bitrate, String scanType, String stereoMode, String language) {
            this(codec, aspect, width, height, durationSeconds, bitrate, scanType, stereoMode, language, null, null,
                    null, null);
        }
    }

    /**
     * @param language language as ISO 639-2 code, e.g. {@code deu}
     * @param channels number of channels, e.g. 6 for 5.1
     * @param bitrate  bit rate in kbit/s
     */
    public record Audio(String codec, String language, Integer channels, Integer bitrate) {
    }

    /**
     * @param forced whether these are forced subtitles
     * @param path   path of an external subtitle file; {@code null} for subtitles in the video file
     */
    public record Subtitle(String language, boolean forced, String path) {

        public Subtitle(String language, boolean forced) {
            this(language, forced, null);
        }

        public boolean isExternal() {
            return path != null;
        }
    }
}
