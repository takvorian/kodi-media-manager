package de.tiktak.kmm.nfo;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * Immutable copies for the NFO records; {@code null} becomes an empty collection.
 */
final class NfoCollections {

    private NfoCollections() {
    }

    static <T> List<T> list(List<T> values) {
        return values == null ? List.of() : List.copyOf(values);
    }

    /** The order is kept: the first ID becomes the default ID ({@code default}) when writing. */
    static Map<String, String> orderedIds(Map<String, String> uniqueIds) {
        if (uniqueIds == null) {
            return Map.of();
        }
        Map<String, String> normalized = new LinkedHashMap<>();
        uniqueIds.forEach((type, value) -> normalized.put(type.toLowerCase(Locale.ROOT), value));
        return Collections.unmodifiableMap(normalized);
    }

    static <V> SortedMap<Integer, V> sorted(Map<Integer, V> values) {
        return Collections.unmodifiableSortedMap(values == null ? new TreeMap<>() : new TreeMap<>(values));
    }
}
