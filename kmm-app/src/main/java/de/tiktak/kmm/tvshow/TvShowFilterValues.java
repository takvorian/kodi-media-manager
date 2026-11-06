package de.tiktak.kmm.tvshow;

import java.util.Collection;
import java.util.List;

/**
 * Existing values per filter field of the TV show list for selection lists, sorted; codecs and languages come from
 * the episodes.
 *
 * @param resolutions rows of the episodes' resolution levels, e.g. 720, 1080
 */
public record TvShowFilterValues(
        List<String> genres,
        List<String> tags,
        List<String> studios,
        List<String> statuses,
        List<String> mpaa,
        List<String> videoSources,
        List<Integer> resolutions,
        List<String> videoCodecs,
        List<String> audioCodecs,
        List<String> audioLanguages,
        List<String> subtitleLanguages) {

    static List<String> sorted(Collection<String> values) {
        return values.stream().distinct().sorted(String.CASE_INSENSITIVE_ORDER).toList();
    }
}
