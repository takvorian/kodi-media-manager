package de.tiktak.kmm.plugin.api;

import java.util.Objects;

/**
 * Eine Bewertung beim Anbieter.
 *
 * @param source Kennung für die NFO, z. B. {@code themoviedb} oder {@code tvdb}
 * @param max    Höchstwert der Skala
 * @param votes  Anzahl der Stimmen oder {@code null}
 */
public record ProviderRating(String source, double value, int max, Integer votes) {

    public ProviderRating {
        Objects.requireNonNull(source, "source must not be null");
    }
}
