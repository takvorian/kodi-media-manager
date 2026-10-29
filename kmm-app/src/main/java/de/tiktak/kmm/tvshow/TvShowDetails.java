package de.tiktak.kmm.tvshow;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * Alle Angaben zu einer Serie für die Detailansicht, mit Staffeln und Episoden.
 *
 * @param seasons  Staffeln mit Titel und Anzahl der Episoden, aufsteigend
 * @param episodes Episoden nach Staffel und Nummer
 * @param trailer  Trailer-Link oder {@code null}
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
        String trailer) {

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
                show.getTrailer());
    }

    /**
     * @param number       Staffelnummer, 0 für Specials
     * @param title        Staffeltitel oder {@code null}
     * @param episodeCount Anzahl der Episoden mit Datei
     */
    public record Season(int number, String title, long episodeCount) {
    }
}
