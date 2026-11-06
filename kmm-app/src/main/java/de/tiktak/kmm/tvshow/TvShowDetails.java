package de.tiktak.kmm.tvshow;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * All details of a TV show for the detail view, with seasons and episodes.
 *
 * @param seasons  seasons with title and number of episodes, ascending
 * @param episodes episodes by season and number
 * @param trailer  trailer link or {@code null}
 * @param language own language or {@code null}, in which case that of the source or the settings applies
 */
public record TvShowDetails(
        long id,
        long sourceId,
        String directoryPath,
        Instant addedAt,
        TvShowMetadata metadata,
        TvShowExtras extras,
        List<Season> seasons,
        List<EpisodeSummary> episodes,
        TvNfoInfo nfo,
        String trailer,
        String language) {

    static TvShowDetails from(TvShow show, List<Episode> episodes, TvNfoInfo nfo) {
        TvShowExtras extras = show.extras();
        Map<Integer, Long> episodeCounts = episodes.stream()
                .collect(Collectors.groupingBy(Episode::getSeason, TreeMap::new, Collectors.counting()));
        TreeMap<Integer, String> titles = new TreeMap<>(extras.seasonNames());
        episodeCounts.keySet().forEach(season -> titles.putIfAbsent(season, null));
        List<Season> seasons = titles.entrySet().stream()
                .map(entry -> new Season(entry.getKey(), entry.getValue(), episodeCounts.getOrDefault(entry.getKey(), 0L)))
                .toList();
        return new TvShowDetails(show.getId(), show.getSourceId(), show.getDirectoryPath(), show.getAddedAt(),
                show.metadata(), extras, seasons, episodes.stream().map(EpisodeSummary::from).toList(), nfo,
                show.getTrailer(), show.getMetadataLanguage());
    }

    /**
     * @param number       season number, 0 for specials
     * @param title        season title or {@code null}
     * @param episodeCount number of episodes with a file
     */
    public record Season(int number, String title, long episodeCount) {
    }
}
