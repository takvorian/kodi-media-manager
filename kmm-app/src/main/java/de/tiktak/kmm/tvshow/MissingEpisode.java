package de.tiktak.kmm.tvshow;

import java.time.LocalDate;

/**
 * An episode that the provider knows but for which there is no file. Only displayed, not
 * stored.
 *
 * @param aired first air date or {@code null} if unknown
 */
public record MissingEpisode(int season, int episode, String title, LocalDate aired) {
}
