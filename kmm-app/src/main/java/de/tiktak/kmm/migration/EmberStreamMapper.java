package de.tiktak.kmm.migration;

import java.nio.file.Path;
import java.util.regex.Pattern;

import de.tiktak.kmm.migration.ember.library.EmberStreams;
import de.tiktak.kmm.nfo.StreamDetails;

/**
 * Übersetzt Embers Spuren in KMMs Stream-Details. Externe Untertitel liegen neben der Videodatei; ihr
 * Pfad wird deshalb neben die Videodatei im Zielsystem gelegt statt über die Pfadzuordnung übersetzt.
 */
final class EmberStreamMapper {

    /** Kodi erwartet ISO-639-2-Codes; Ember speichert für unbekannte Sprachen z. B. „Unbekannt“. */
    private static final Pattern LANGUAGE_CODE = Pattern.compile("[a-z]{3}");

    private EmberStreamMapper() {
    }

    /**
     * @param filePath Pfad der Videodatei im Zielsystem
     */
    static StreamDetails toStreamDetails(EmberStreams streams, String filePath) {
        return new StreamDetails(
                streams.videos().stream().map(EmberStreamMapper::video).toList(),
                streams.audios().stream()
                        .map(audio -> new StreamDetails.Audio(audio.codec(), language(audio.language()), audio.channels(),
                                audio.bitrate()))
                        .toList(),
                streams.subtitles().stream()
                        .map(subtitle -> new StreamDetails.Subtitle(language(subtitle.language()), subtitle.forced(),
                                subtitle.external() ? externalPath(subtitle.path(), filePath) : null))
                        .toList());
    }

    private static StreamDetails.Video video(EmberStreams.Video video) {
        return new StreamDetails.Video(video.codec(), aspect(video.aspect()), video.width(), video.height(),
                video.durationSeconds(), video.bitrate(), video.scanType(), video.stereoMode(), language(video.language()),
                null, video.multiViewCount(), video.multiViewLayout(), video.fileSize());
    }

    /** @return der Pfad neben der Videodatei; leer, wenn Ember keinen Dateinamen kennt */
    private static String externalPath(String emberPath, String filePath) {
        if (emberPath == null || filePath == null) {
            return "";
        }
        String fileName = emberPath.substring(Math.max(emberPath.lastIndexOf('\\'), emberPath.lastIndexOf('/')) + 1);
        Path directory = Path.of(filePath).getParent();
        return fileName.isEmpty() || directory == null ? "" : directory.resolve(fileName).toString();
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
