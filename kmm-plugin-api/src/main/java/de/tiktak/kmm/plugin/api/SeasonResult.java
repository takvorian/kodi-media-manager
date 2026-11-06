package de.tiktak.kmm.plugin.api;

import java.time.LocalDate;

/**
 * A season of a TV show at the provider; season 0 holds the specials.
 *
 * @param title season title or {@code null} if the provider only knows the number
 */
public record SeasonResult(int number, String title, String plot, LocalDate aired) {
}
