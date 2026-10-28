package de.tiktak.kmm.tag;

/**
 * Ein Tag mit der Anzahl seiner Filme und Serien.
 */
public record TagSummary(String name, long movies, long shows) {
}
