package de.tiktak.kmm.nfo;

import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Übersetzt Codec-Namen, wie MediaInfo sie liefert und Ember sie teils unverändert gespeichert hat, in die Namen, die
 * Kodi verwendet (z. B. {@code avc1} → {@code h264}, {@code mp4a-40-2} → {@code aac}). Enthält Embers
 * Standardzuordnungen ({@code AudioFormatConverts}, {@code VideoFormatConverts}); unbekannte Namen bleiben.
 */
public final class CodecNames {

    private static final Map<String, String> VIDEO = Map.ofEntries(
            Map.entry("avc", "h264"),
            Map.entry("avc1", "h264"),
            Map.entry("x264", "h264"),
            Map.entry("v_mpeg4/iso/avc", "h264"),
            Map.entry("hvc1", "hevc"),
            Map.entry("hev1", "hevc"),
            Map.entry("v_mpegh/iso/hevc", "hevc"),
            Map.entry("av01", "av1"),
            Map.entry("v_mpeg2", "mpeg2video"),
            Map.entry("v_mpeg4/iso/asp", "mpeg4"),
            Map.entry("v_mpeg4/iso/sp", "mpeg4"),
            Map.entry("v_ms/vfw/fourcc / dx50", "dx50"),
            Map.entry("v_ms/vfw/fourcc / xvid", "xvid"),
            Map.entry("v_vp7", "vp7"),
            Map.entry("v_vp8", "vp8"),
            Map.entry("v_vp9", "vp9"));

    private static final Map<String, String> AUDIO = Map.ofEntries(
            Map.entry("dts", "dca"),
            Map.entry("mp4a-40-2", "aac"),
            Map.entry("mp4a-40-5", "aac"),
            Map.entry("mp4a-40-29", "aac"),
            Map.entry("mp4a-69", "mp3"),
            Map.entry("mp4a-6b", "mp3"),
            Map.entry("mp4a-a5", "ac3"),
            Map.entry("mp4a-a6", "eac3"),
            Map.entry("mp4a-a9", "dca"),
            Map.entry("00002000-0000-0010-8000-00aa00389b71", "ac3"),
            Map.entry("00002001-0000-0010-8000-00aa00389b71", "dca"),
            Map.entry("a_aac", "aac"),
            Map.entry("a_aac-2", "aac"),
            Map.entry("a_ac3", "ac3"),
            Map.entry("a_dts", "dca"),
            Map.entry("a_eac3", "eac3"),
            Map.entry("a_flac", "flac"),
            Map.entry("a_mpeg/l3", "mp3"),
            Map.entry("a_truehd", "truehd"),
            Map.entry("a_vorbis", "vorbis"),
            Map.entry("ac-3", "ac3"),
            Map.entry("atmos / truehd / ac-3", "truehd_atmos"),
            Map.entry("atmos / truehd", "truehd_atmos"),
            Map.entry("e-ac-3", "eac3"),
            Map.entry("e-ac-3+atmos", "eac3_atmos"),
            Map.entry("hra / core", "dtshd_hra"),
            Map.entry("ma / core", "dtshd_ma"),
            Map.entry("truehd / ac-3", "truehd"),
            Map.entry("truehd+atmos / truehd / ac-3", "truehd_atmos"),
            Map.entry("x / hra / core", "dtshd_hra_x"),
            Map.entry("x / ma / core", "dtshd_ma_x"));

    private CodecNames() {
    }

    public static String video(String codec) {
        return translate(VIDEO, codec);
    }

    public static String audio(String codec) {
        return translate(AUDIO, codec);
    }

    /** @return die Angaben mit Kodis Codec-Namen; dasselbe Objekt, wenn nichts zu übersetzen war */
    public static StreamDetails normalize(StreamDetails details) {
        if (details == null) {
            return null;
        }
        StreamDetails normalized = new StreamDetails(
                details.videos().stream().map(CodecNames::normalize).toList(),
                details.audios().stream().map(audio -> new StreamDetails.Audio(audio(audio.codec()), audio.language(),
                        audio.channels(), audio.bitrate())).toList(),
                details.subtitles());
        return normalized.equals(details) ? details : normalized;
    }

    private static StreamDetails.Video normalize(StreamDetails.Video video) {
        return new StreamDetails.Video(video(video.codec()), video.aspect(), video.width(), video.height(),
                video.durationSeconds(), video.bitrate(), video.scanType(), video.stereoMode(), video.language(),
                video.hdrType(), video.multiViewCount(), video.multiViewLayout(), video.fileSize());
    }

    private static String translate(Map<String, String> names, String codec) {
        if (codec == null) {
            return null;
        }
        String translated = names.get(codec.strip().toLowerCase(Locale.ROOT));
        return Objects.requireNonNullElse(translated, codec);
    }
}
