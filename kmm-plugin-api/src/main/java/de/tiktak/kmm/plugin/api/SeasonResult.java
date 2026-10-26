package de.tiktak.kmm.plugin.api;

import java.time.LocalDate;

/**
 * Eine Staffel einer Serie beim Anbieter; Staffel 0 enthält die Specials.
 *
 * @param title Staffeltitel oder {@code null}, wenn der Anbieter nur die Nummer kennt
 */
public record SeasonResult(int number, String title, String plot, LocalDate aired) {
}
