package de.tiktak.kmm.plugin.api;

import java.util.List;
import java.util.Optional;

/**
 * Liefert Metadaten zu Serien samt Staffeln und Episoden. Sprache und Episodenreihenfolge kommen aus
 * den Einstellungen ({@link PluginContext#setting(String)}: {@link #TV_LANGUAGE_SETTING},
 * {@link #EPISODE_ORDERING_SETTING}).
 */
public interface TvShowScraper extends Scraper {

    /** Sprache der Serien-Metadaten, z. B. {@code de-DE}. */
    String TV_LANGUAGE_SETTING = "tv-language";

    /** Episodenreihenfolge: {@code AIRED}, {@code DVD} oder {@code ABSOLUTE}. */
    String EPISODE_ORDERING_SETTING = "episode-ordering";

    /**
     * Sucht passende Serien; der Nutzer wählt daraus einen Treffer aus.
     *
     * @throws ScraperException wenn der Anbieter nicht erreichbar ist oder die Anfrage ablehnt
     */
    List<SearchResult> search(SearchQuery query);

    /**
     * Lädt die Serie zu einem Treffer aus {@link #search(SearchQuery)} mit allen Episoden.
     *
     * @return leer, wenn der Anbieter die ID nicht kennt
     * @throws ScraperException wenn der Anbieter nicht erreichbar ist oder die Anfrage ablehnt
     */
    Optional<TvShowResult> fetch(String providerId);

    /**
     * Ermittelt die Serie selbstständig, bevorzugt über bekannte IDs, sonst über den eindeutig
     * passenden Suchtreffer.
     *
     * @return leer, wenn kein Treffer eindeutig genug ist
     */
    Optional<TvShowResult> scrape(ScrapeRequest request);

    /**
     * Ergänzt Angaben, die der Anbieter nur je Episode liefert (bei TVDB Regie, Drehbuch und Gaststars).
     * KMM fragt nur Episoden an, denen diese Angaben fehlen, weil jede Anfrage den Anbieter belastet.
     *
     * @param episode eine Episode aus {@link #fetch(String)} bzw. {@link #scrape(ScrapeRequest)}
     * @return die ergänzte Episode; ohne weitere Angaben die übergebene
     * @throws ScraperException wenn der Anbieter nicht erreichbar ist oder die Anfrage ablehnt
     */
    default EpisodeResult episodeDetails(EpisodeResult episode) {
        return episode;
    }
}
