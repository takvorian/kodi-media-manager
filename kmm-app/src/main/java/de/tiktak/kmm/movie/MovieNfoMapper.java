package de.tiktak.kmm.movie;

import java.util.LinkedHashMap;
import java.util.Map;

import de.tiktak.kmm.nfo.MovieNfo;

/**
 * Übersetzt zwischen NFO-Inhalt und Film-Metadaten.
 */
final class MovieNfoMapper {

    private MovieNfoMapper() {
    }

    /**
     * Übernimmt die NFO; fehlende Werte werden mit den Werten aus dem Scan ergänzt.
     *
     * @param fallbackTitle aus dem Dateinamen abgeleiteter Titel, falls die NFO keinen enthält
     * @param fallbackYear  aus dem Dateinamen abgeleitetes Jahr, falls die NFO keines enthält
     */
    static MovieMetadata toMetadata(MovieNfo nfo, String fallbackTitle, Integer fallbackYear) {
        Integer year = nfo.effectiveYear();
        return new MovieMetadata(
                nfo.title() == null ? fallbackTitle : nfo.title(),
                nfo.originalTitle(),
                nfo.sortTitle(),
                year == null ? fallbackYear : year,
                nfo.premiered(),
                nfo.plot(),
                nfo.outline(),
                nfo.tagline(),
                nfo.runtime(),
                nfo.mpaa(),
                nfo.genres(),
                nfo.uniqueId(MovieNfo.IMDB).orElse(null),
                nfo.uniqueId(MovieNfo.TMDB).orElse(null));
    }

    /**
     * Erzeugt den NFO-Inhalt. IDs weiterer Anbieter aus der vorhandenen NFO (z. B. {@code tvdb})
     * bleiben erhalten; IMDb und TMDB kommen ausschließlich aus den Metadaten.
     */
    static MovieNfo toNfo(MovieMetadata metadata, Map<String, String> existingUniqueIds) {
        Map<String, String> uniqueIds = new LinkedHashMap<>();
        putIfPresent(uniqueIds, MovieNfo.IMDB, metadata.imdbId());
        putIfPresent(uniqueIds, MovieNfo.TMDB, metadata.tmdbId());
        existingUniqueIds.forEach((type, value) -> {
            if (!MovieNfo.IMDB.equals(type) && !MovieNfo.TMDB.equals(type)) {
                uniqueIds.putIfAbsent(type, value);
            }
        });
        return new MovieNfo(
                metadata.title(),
                metadata.originalTitle(),
                metadata.sortTitle(),
                metadata.year(),
                metadata.premiered(),
                metadata.plot(),
                metadata.outline(),
                metadata.tagline(),
                metadata.runtime(),
                metadata.mpaa(),
                metadata.genres(),
                uniqueIds);
    }

    private static void putIfPresent(Map<String, String> uniqueIds, String type, String value) {
        if (value != null) {
            uniqueIds.put(type, value);
        }
    }
}
