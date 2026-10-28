package de.tiktak.kmm.persistence;

import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import de.tiktak.kmm.nfo.StreamDetails;
import de.tiktak.kmm.nfo.StreamLabels;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Abfragbare Kurzform der technischen Angaben für Filter: Auflösungsstufe, Codec und HDR der ersten Videospur sowie
 * alle Audio-Codecs, Audio- und Untertitelsprachen. Listen stehen als {@code " ac3 dts "} in einer Spalte, damit
 * ein Filter mit {@code LIKE '% dts %'} genau einen Eintrag trifft.
 */
@Embeddable
@Getter
@EqualsAndHashCode
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StreamIndex {

    public static final String SEPARATOR = " ";
    /** Escape-Zeichen für {@code LIKE}; kein Backslash, weil MariaDB ihn auch in Zeichenketten auswertet. */
    public static final char LIKE_ESCAPE = '!';
    static final int MAX_LIST_LENGTH = 512;
    private static final int MAX_LABEL_LENGTH = 32;

    @Column(name = "video_resolution")
    private Integer videoResolution;

    @Column(name = "video_codec", length = MAX_LABEL_LENGTH)
    private String videoCodec;

    @Column(name = "hdr_type", length = MAX_LABEL_LENGTH)
    private String hdrType;

    /** Ist nie {@code null}, sobald technische Angaben bekannt sind; so erkennt KMM noch nicht indizierte Filme. */
    @Column(name = "audio_codecs", length = MAX_LIST_LENGTH)
    private String audioCodecs;

    @Column(name = "audio_languages", length = MAX_LIST_LENGTH)
    private String audioLanguages;

    @Column(name = "subtitle_languages", length = MAX_LIST_LENGTH)
    private String subtitleLanguages;

    /** @return die Kurzform oder {@code null} ohne technische Angaben */
    public static StreamIndex of(StreamDetails details) {
        if (details == null || details.isEmpty()) {
            return null;
        }
        StreamIndex index = new StreamIndex();
        details.videos().stream().findFirst().ifPresent(video -> {
            index.videoResolution = StreamLabels.resolutionLines(video).orElse(null);
            index.videoCodec = label(video.codec());
            index.hdrType = label(video.hdrType());
        });
        index.audioCodecs = tokens(details.audios().stream().map(StreamDetails.Audio::codec).toList());
        index.audioLanguages = tokens(details.audios().stream().map(StreamDetails.Audio::language).toList());
        index.subtitleLanguages = tokens(details.subtitles().stream().map(StreamDetails.Subtitle::language).toList());
        return index;
    }

    /** Muster für {@code LIKE} mit {@link #LIKE_ESCAPE}, das genau einen Eintrag einer Liste trifft. */
    public static String containsToken(String value) {
        return "%" + SEPARATOR + escapeLike(normalize(value)) + SEPARATOR + "%";
    }

    /** Maskiert die Platzhalter von {@code LIKE} mit {@link #LIKE_ESCAPE}. */
    public static String escapeLike(String value) {
        String escape = String.valueOf(LIKE_ESCAPE);
        return value.replace(escape, escape + escape).replace("%", escape + "%").replace("_", escape + "_");
    }

    /** @return die Einträge einer Liste, z. B. für Auswahllisten */
    public static Set<String> tokensOf(String list) {
        return list == null ? Set.of() : Arrays.stream(list.split(SEPARATOR))
                .filter(token -> !token.isEmpty())
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private static String tokens(Collection<String> values) {
        StringBuilder list = new StringBuilder(SEPARATOR);
        values.stream()
                .filter(Objects::nonNull)
                .map(StreamIndex::normalize)
                .filter(token -> !token.isEmpty())
                .distinct()
                .forEach(token -> {
                    if (list.length() + token.length() + SEPARATOR.length() <= MAX_LIST_LENGTH) {
                        list.append(token).append(SEPARATOR);
                    }
                });
        return list.toString();
    }

    private static String label(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = normalize(value);
        return normalized.length() > MAX_LABEL_LENGTH ? normalized.substring(0, MAX_LABEL_LENGTH) : normalized;
    }

    private static String normalize(String value) {
        return value.strip().toLowerCase(Locale.ROOT).replace(SEPARATOR, "_");
    }
}
