package de.tiktak.kmm.plugin.youtube;

import de.tiktak.kmm.plugin.api.PluginContext;

/**
 * Einstellungen des Plugins ({@code kmm.plugins.settings.youtube.*}).
 *
 * @param ytDlp          Programm yt-dlp, ohne Pfad aus dem Suchpfad
 * @param ffmpegLocation Ordner oder Programm ffmpeg für das Umwandeln in MP3; leer, wenn ffmpeg im Suchpfad liegt
 * @param maxResults     Anzahl der Suchtreffer
 */
record YouTubeSettings(String ytDlp, String ffmpegLocation, int maxResults) {

    static final String YT_DLP = "yt-dlp";
    static final String FFMPEG_LOCATION = "ffmpeg-location";
    static final String MAX_RESULTS = "max-results";
    static final int DEFAULT_MAX_RESULTS = 6;
    private static final int MAX_ALLOWED_RESULTS = 20;

    static YouTubeSettings from(PluginContext context) {
        return new YouTubeSettings(
                text(context, YT_DLP).isEmpty() ? YT_DLP : text(context, YT_DLP),
                text(context, FFMPEG_LOCATION),
                maxResults(text(context, MAX_RESULTS)));
    }

    boolean hasFfmpegLocation() {
        return !ffmpegLocation.isEmpty();
    }

    private static int maxResults(String value) {
        try {
            return value.isEmpty() ? DEFAULT_MAX_RESULTS
                    : Math.clamp(Integer.parseInt(value), 1, MAX_ALLOWED_RESULTS);
        } catch (NumberFormatException e) {
            return DEFAULT_MAX_RESULTS;
        }
    }

    private static String text(PluginContext context, String name) {
        return context.setting(name).map(String::strip).orElse("");
    }
}
