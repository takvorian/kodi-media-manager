package de.tiktak.kmm.tvshow;

import java.util.List;
import java.util.Objects;

/**
 * Cleans texts from forms, NFOs and third-party databases before they are saved.
 */
final class TvTexts {

    static final int MAX_TITLE_LENGTH = 512;
    static final int MAX_PLOT_LENGTH = 20_000;
    static final int MAX_SHORT_LENGTH = 64;
    static final int MAX_LABEL_LENGTH = 255;
    static final int MAX_GENRE_LENGTH = 128;
    static final int MAX_NAME_LENGTH = 512;
    static final int MAX_THUMB_LENGTH = 1024;
    static final int MAX_ENTRIES = 50;
    static final int MAX_ACTORS = 500;

    private TvTexts() {
    }

    /** Trimmed, truncated to {@code maxLength}; empty becomes {@code null}. */
    static String clean(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        String stripped = value.strip();
        if (stripped.isEmpty()) {
            return null;
        }
        return stripped.length() > maxLength ? stripped.substring(0, maxLength).strip() : stripped;
    }

    /** Cleaned, unique entries in their order, at most {@code maxEntries}. */
    static List<String> cleanAll(List<String> values, int maxLength, int maxEntries) {
        if (values == null) {
            return List.of();
        }
        return values.stream()
                .map(value -> clean(value, maxLength))
                .filter(Objects::nonNull)
                .distinct()
                .limit(maxEntries)
                .toList();
    }

    /** The ID if it matches the pattern, otherwise {@code null}. */
    static String id(String value, String pattern) {
        String cleaned = clean(value, MAX_SHORT_LENGTH);
        return cleaned != null && cleaned.matches(pattern) ? cleaned : null;
    }
}
