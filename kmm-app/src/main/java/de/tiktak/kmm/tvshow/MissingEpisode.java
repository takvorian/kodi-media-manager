package de.tiktak.kmm.tvshow;

import java.time.LocalDate;

/**
 * Eine Episode, die der Anbieter kennt, für die es aber keine Datei gibt. Wird nur angezeigt, nicht
 * gespeichert.
 *
 * @param aired Erstausstrahlung oder {@code null}, wenn unbekannt
 */
public record MissingEpisode(int season, int episode, String title, LocalDate aired) {
}
