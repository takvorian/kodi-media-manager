package de.tiktak.kmm.migration;

import java.util.regex.Pattern;

import de.tiktak.kmm.migration.ember.library.EmberStreams;
import de.tiktak.kmm.nfo.StreamDetails;

/**
 * Übersetzt Embers Spuren in KMMs Stream-Details. Externe Untertitel sind eigene Dateien und gehören
 * nicht zu den Spuren der Videodatei.
 */
final class EmberStreamMapper {

    /** Kodi erwartet ISO-639-2-Codes; Ember speichert für unbekannte Sprachen z. B. „Unbekannt“. */
    private static final Pattern LANGUAGE_CODE = Pattern.compile("[a-z]{3}");

    private EmberStreamMapper() {
    }

    static StreamDetails toStreamDetails(EmberStreams streams) {
        return new StreamDetails(
                streams.videos().stream().map(EmberStreamMapper::video).toList(),
                streams.audios().stream()
                        .map(audio -> new StreamDetails.Audio(audio.codec(), language(audio.language()), audio.channels(),
                                audio.bitrate()))
                        .toList(),
                streams.subtitles().stream()
                        .filter(subtitle -> !subtitle.external())
                        .map(subtitle -> new StreamDetails.Subtitle(language(subtitle.language()), subtitle.forced()))
                        .toList());
    }

    private static StreamDetails.Video video(EmberStreams.Video video) {
        return new StreamDetails.Video(video.codec(), aspect(video.aspect()), video.width(), video.height(),
                video.durationSeconds(), video.bitrate(), video.scanType(), video.stereoMode(), language(video.language()));
    }

    private static String language(String language) {
        return language != null && LANGUAGE_CODE.matcher(language).matches() ? language : null;
    }

    private static Double aspect(String aspect) {
        if (aspect == null) {
            return null;
        }
        try {
            double value = Double.parseDouble(aspect.replace(',', '.'));
            return value > 0 ? value : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
