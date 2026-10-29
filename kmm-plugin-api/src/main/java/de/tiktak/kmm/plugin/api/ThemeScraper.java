package de.tiktak.kmm.plugin.api;

import java.nio.file.Path;
import java.util.List;

/**
 * Sucht Titelmusik zu einem Film oder einer Serie und lädt sie als MP3, die KMM als {@code theme.mp3} ablegt. Gesucht
 * wird über den Titel ({@link ScrapeRequest#title()}).
 */
public interface ThemeScraper extends Scraper {

    /**
     * @return gefundene Titelmusik in der Reihenfolge des Anbieters; leer, wenn nichts passt
     * @throws ScraperException wenn der Anbieter nicht erreichbar ist oder die Anfrage ablehnt
     */
    List<ThemeCandidate> findThemes(ScrapeRequest request);

    /**
     * Lädt die Musik eines eigenen Vorschlags als MP3 nach {@code target}; eine vorhandene Datei wird ersetzt.
     *
     * @throws ScraperException wenn der Vorschlag nicht von diesem Scraper stammt oder nicht geladen werden kann
     */
    void download(ThemeCandidate candidate, Path target);
}
