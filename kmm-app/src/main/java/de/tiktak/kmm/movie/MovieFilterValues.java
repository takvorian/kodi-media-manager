package de.tiktak.kmm.movie;

import java.util.Collection;
import java.util.List;

/**
 * Vorhandene Werte je Filterfeld für Auswahllisten, sortiert.
 *
 * @param resolutions Zeilen der Auflösungsstufen, z. B. 720, 1080, 2160
 */
public record MovieFilterValues(
        List<String> genres,
        List<String> tags,
        List<String> countries,
        List<String> studios,
        List<String> sets,
        List<String> mpaa,
        List<String> editions,
        List<String> videoSources,
        List<Integer> resolutions,
        List<String> videoCodecs,
        List<String> hdrTypes,
        List<String> audioCodecs,
        List<String> audioLanguages,
        List<String> subtitleLanguages) {

    static List<String> sorted(Collection<String> values) {
        return values.stream().distinct().sorted(String.CASE_INSENSITIVE_ORDER).toList();
    }
}
