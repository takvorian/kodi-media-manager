package de.tiktak.kmm.plugin.api;

import java.util.Optional;
import java.util.Set;

/**
 * Ergänzt Filme und Serien über bekannte IDs ({@link ScrapeRequest#uniqueIds()}) um Bewertungen und, falls der Anbieter
 * es kann, um Angaben für leere Felder, etwa Trakt oder OMDb. Ein Abruf liefert alles, weil manche Anbieter nur wenige
 * Abfragen am Tag erlauben.
 */
public interface EnrichmentScraper extends Scraper {

    /** Kennungen der Bewertungen in der NFO, z. B. {@code trakt} oder {@code imdb}. */
    Set<String> ratingSources();

    /** Ob der Anbieter auch Angaben für leere Felder liefert. */
    default boolean providesDetails() {
        return false;
    }

    /**
     * @return die Ergänzungen oder leer, wenn der Anbieter den Eintrag nicht kennt
     * @throws ScraperException wenn der Anbieter nicht erreichbar ist oder die Anfrage ablehnt
     */
    Optional<Enrichment> findEnrichment(ScrapeRequest request);
}
