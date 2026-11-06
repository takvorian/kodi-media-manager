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
 * Queryable short form of the technical details for filters: resolution tier, codec and HDR of the first video track,
 * plus all audio codecs, audio and subtitle languages. Lists are stored as {@code " ac3 dts "} in one column so that
 * a filter with {@code LIKE '% dts %'} matches exactly one entry.
 */
@Embeddable
@Getter
@EqualsAndHashCode
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StreamIndex {

    public static final String SEPARATOR = " ";
    /** Escape character for {@code LIKE}; not a backslash because MariaDB also interprets it in strings. */
    public static final char LIKE_ESCAPE = '!';
    static final int MAX_LIST_LENGTH = 512;
    private static final int MAX_LABEL_LENGTH = 32;

    @Column(name = "video_resolution")
    private Integer videoResolution;

    @Column(name = "video_codec", length = MAX_LABEL_LENGTH)
    private String videoCodec;

    @Column(name = "hdr_type", length = MAX_LABEL_LENGTH)
    private String hdrType;

    /** Never {@code null} once technical details are known; this is how KMM detects movies not yet indexed. */
    @Column(name = "audio_codecs", length = MAX_LIST_LENGTH)
    private String audioCodecs;

    @Column(name = "audio_languages", length = MAX_LIST_LENGTH)
    private String audioLanguages;

    @Column(name = "subtitle_languages", length = MAX_LIST_LENGTH)
    private String subtitleLanguages;

    /** @return the short form or {@code null} without technical details */
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

    /** Pattern for {@code LIKE} with {@link #LIKE_ESCAPE} that matches exactly one entry of a list. */
    public static String containsToken(String value) {
        return "%" + SEPARATOR + escapeLike(normalize(value)) + SEPARATOR + "%";
    }

    /** Escapes the wildcards of {@code LIKE} with {@link #LIKE_ESCAPE}. */
    public static String escapeLike(String value) {
        String escape = String.valueOf(LIKE_ESCAPE);
        return value.replace(escape, escape + escape).replace("%", escape + "%").replace("_", escape + "_");
    }

    /** @return the entries of a list, e.g. for selection lists */
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
