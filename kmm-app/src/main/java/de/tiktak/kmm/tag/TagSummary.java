package de.tiktak.kmm.tag;

/**
 * A tag with the number of its movies and TV shows.
 */
public record TagSummary(String name, long movies, long shows) {
}
