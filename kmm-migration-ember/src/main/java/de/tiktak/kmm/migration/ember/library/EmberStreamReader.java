package de.tiktak.kmm.migration.ember.library;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Reads Ember's stream tables ({@code MoviesVStreams} … or {@code TVVStreams} …) and assigns the streams to movies
 * or episodes.
 */
final class EmberStreamReader {

    private static final String EXTERNAL = "External";

    private EmberStreamReader() {
    }

    static Map<Long, EmberStreams> movies(Connection connection) throws SQLException {
        return read(connection, "MoviesVStreams", "MoviesAStreams", "MoviesSubs", "MovieID");
    }

    static Map<Long, EmberStreams> episodes(Connection connection) throws SQLException {
        return read(connection, "TVVStreams", "TVAStreams", "TVSubs", "TVEpID");
    }

    private static Map<Long, EmberStreams> read(Connection connection, String videoTable, String audioTable,
            String subtitleTable, String idColumn) throws SQLException {
        Map<Long, List<EmberStreams.Video>> videos = EmberQueries.grouped(connection,
                "SELECT * FROM %s ORDER BY %s, StreamID".formatted(videoTable, idColumn), idColumn,
                EmberStreamReader::video);
        Map<Long, List<EmberStreams.Audio>> audios = EmberQueries.grouped(connection,
                "SELECT * FROM %s ORDER BY %s, StreamID".formatted(audioTable, idColumn), idColumn,
                EmberStreamReader::audio);
        Map<Long, List<EmberStreams.Subtitle>> subtitles = EmberQueries.grouped(connection,
                "SELECT * FROM %s ORDER BY %s, StreamID".formatted(subtitleTable, idColumn), idColumn,
                EmberStreamReader::subtitle);
        Set<Long> ids = new HashSet<>(videos.keySet());
        ids.addAll(audios.keySet());
        ids.addAll(subtitles.keySet());
        Map<Long, EmberStreams> streams = new HashMap<>();
        for (Long id : ids) {
            streams.put(id, new EmberStreams(videos.get(id), audios.get(id), subtitles.get(id)));
        }
        return streams;
    }

    private static EmberStreams.Video video(ResultSet row) throws SQLException {
        return new EmberStreams.Video(
                EmberValues.text(row, "Video_Codec"),
                EmberValues.positiveInteger(row, "Video_Width"),
                EmberValues.positiveInteger(row, "Video_Height"),
                EmberValues.text(row, "Video_AspectDisplayRatio"),
                EmberValues.positiveInteger(row, "Video_Duration"),
                EmberValues.positiveInteger(row, "Video_Bitrate"),
                EmberValues.text(row, "Video_ScanType"),
                EmberValues.text(row, "Video_StereoMode"),
                EmberValues.text(row, "Video_Language"),
                EmberValues.text(row, "Video_MultiViewCount"),
                EmberValues.text(row, "Video_MultiViewLayout"),
                fileSize(EmberValues.text(row, "Video_FileSize")));
    }

    private static EmberStreams.Audio audio(ResultSet row) throws SQLException {
        return new EmberStreams.Audio(
                EmberValues.text(row, "Audio_Codec"),
                EmberValues.text(row, "Audio_Language"),
                EmberValues.positiveInteger(row, "Audio_Channel"),
                EmberValues.positiveInteger(row, "Audio_Bitrate"));
    }

    private static EmberStreams.Subtitle subtitle(ResultSet row) throws SQLException {
        return new EmberStreams.Subtitle(
                EmberValues.text(row, "Subs_Language"),
                Integer.valueOf(1).equals(EmberValues.positiveInteger(row, "Subs_Forced")),
                EXTERNAL.equalsIgnoreCase(EmberValues.text(row, "Subs_Type")),
                EmberValues.text(row, "Subs_Path"));
    }

    /** Ember stores the size as the text of a floating point number. */
    private static Long fileSize(String text) {
        if (text == null) {
            return null;
        }
        try {
            double size = Double.parseDouble(text.replace(',', '.'));
            return size > 0 ? Math.round(size) : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
