package de.tiktak.kmm.media;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import de.tiktak.kmm.nfo.CodecNames;
import de.tiktak.kmm.nfo.IsoLanguages;
import de.tiktak.kmm.nfo.StreamDetails;

/**
 * Übersetzt die Spuren von ffprobe in Kodis Stream-Details mit den Namen, die Kodi und Ember verwenden
 * ({@code dca} statt {@code dts}, {@code deu} statt {@code ger}).
 */
final class KodiStreams {

    private static final Set<String> INTERLACED = Set.of("tt", "bb", "tb", "bt");
    private static final Map<String, String> HDR_TRANSFERS = Map.of("smpte2084", "hdr10", "arib-std-b67", "hlg");
    /** MPEG-4-Part-2-Videos nennt Kodi nach ihrem FourCC, etwa {@code xvid} oder {@code divx}. */
    private static final Pattern FOUR_CC = Pattern.compile("[A-Za-z0-9]{4}");
    private static final String MPEG4 = "mpeg4";
    private static final String PROGRESSIVE = "progressive";
    private static final String MONO = "mono";
    private static final String FORCED = "forced";
    private static final String DOLBY_VISION = "dolbyvision";
    private static final double KILO = 1000.0;
    private static final double ASPECT_PRECISION = 1000.0;

    private KodiStreams() {
    }

    /**
     * @param durationSeconds gesamte Laufzeit, bei mehrteiligen Filmen aller Teile
     * @param fileSize        gesamte Größe in Byte
     */
    static StreamDetails toStreamDetails(ProbedMedia media, Double durationSeconds, Long fileSize) {
        List<ProbedMedia.Stream> audios = media.streams().stream().filter(stream -> stream.is(ProbedMedia.Stream.AUDIO))
                .toList();
        long audioBitRate = audios.stream().mapToLong(stream -> stream.bitRate() == null ? 0 : stream.bitRate()).sum();
        return new StreamDetails(
                media.streams().stream()
                        .filter(stream -> stream.is(ProbedMedia.Stream.VIDEO) && !stream.attachedPicture())
                        .map(stream -> video(stream, media, audioBitRate, durationSeconds, fileSize))
                        .toList(),
                audios.stream().map(KodiStreams::audio).toList(),
                media.streams().stream().filter(stream -> stream.is(ProbedMedia.Stream.SUBTITLE))
                        .map(KodiStreams::subtitle).toList());
    }

    private static StreamDetails.Video video(ProbedMedia.Stream stream, ProbedMedia media, long audioBitRate,
                                             Double durationSeconds, Long fileSize) {
        return new StreamDetails.Video(videoCodec(stream), aspect(stream), stream.width(), stream.height(),
                durationSeconds == null ? null : (int) Math.round(durationSeconds),
                kilobits(videoBitRate(stream, media, audioBitRate)), scanType(stream.fieldOrder()),
                stereoMode(stream.stereoMode()), IsoLanguages.toTerminology(stream.language()), hdrType(stream), null,
                null, fileSize);
    }

    private static StreamDetails.Audio audio(ProbedMedia.Stream stream) {
        return new StreamDetails.Audio(audioCodec(stream), IsoLanguages.toTerminology(stream.language()),
                stream.channels(), kilobits(stream.bitRate()));
    }

    private static StreamDetails.Subtitle subtitle(ProbedMedia.Stream stream) {
        boolean forced = stream.forced()
                || stream.title() != null && stream.title().toLowerCase(Locale.ROOT).contains(FORCED);
        return new StreamDetails.Subtitle(IsoLanguages.toTerminology(stream.language()), forced);
    }

    private static String videoCodec(ProbedMedia.Stream stream) {
        if (MPEG4.equals(stream.codec()) && stream.codecTag() != null && FOUR_CC.matcher(stream.codecTag()).matches()) {
            return stream.codecTag().toLowerCase(Locale.ROOT);
        }
        return stream.codec();
    }

    /** Kodi unterscheidet DTS-HD Master Audio und High Resolution Audio. */
    private static String audioCodec(ProbedMedia.Stream stream) {
        String profile = stream.profile() == null ? "" : stream.profile();
        if ("dts".equals(stream.codec()) && profile.contains("DTS-HD MA")) {
            return "dtshd_ma";
        }
        if ("dts".equals(stream.codec()) && profile.contains("DTS-HD HRA")) {
            return "dtshd_hra";
        }
        return CodecNames.audio(stream.codec());
    }

    /** Matroska-Dateien enthalten die Videobitrate oft nicht; dann bleibt die Gesamtbitrate ohne Ton. */
    private static Long videoBitRate(ProbedMedia.Stream stream, ProbedMedia media, long audioBitRate) {
        if (stream.bitRate() != null) {
            return stream.bitRate();
        }
        if (media.bitRate() == null || media.bitRate() <= audioBitRate) {
            return null;
        }
        return media.bitRate() - audioBitRate;
    }

    private static Double aspect(ProbedMedia.Stream stream) {
        Double display = ratio(stream.displayAspectRatio());
        if (display != null) {
            return rounded(display);
        }
        if (stream.width() == null || stream.height() == null || stream.height() == 0) {
            return null;
        }
        Double sample = ratio(stream.sampleAspectRatio());
        return rounded((double) stream.width() / stream.height() * (sample == null ? 1 : sample));
    }

    /** @return der Wert eines Verhältnisses wie {@code 16:9} oder {@code null} für {@code 0:1} und Unbekanntes */
    private static Double ratio(String ratio) {
        if (ratio == null) {
            return null;
        }
        String[] parts = ratio.split(":");
        try {
            double numerator = Double.parseDouble(parts[0]);
            double denominator = parts.length == 2 ? Double.parseDouble(parts[1]) : 0;
            return numerator > 0 && denominator > 0 ? numerator / denominator : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static double rounded(double aspect) {
        return Math.round(aspect * ASPECT_PRECISION) / ASPECT_PRECISION;
    }

    private static String scanType(String fieldOrder) {
        if (PROGRESSIVE.equals(fieldOrder)) {
            return "Progressive";
        }
        return fieldOrder != null && INTERLACED.contains(fieldOrder) ? "Interlaced" : null;
    }

    private static String stereoMode(String stereoMode) {
        return stereoMode == null || MONO.equals(stereoMode) ? null : stereoMode;
    }

    private static String hdrType(ProbedMedia.Stream stream) {
        if (stream.dolbyVision()) {
            return DOLBY_VISION;
        }
        return stream.colorTransfer() == null ? null : HDR_TRANSFERS.get(stream.colorTransfer());
    }

    private static Integer kilobits(Long bitsPerSecond) {
        return bitsPerSecond == null ? null : (int) Math.round(bitsPerSecond / KILO);
    }
}
