package de.tiktak.kmm.plugin.api;

import java.util.Objects;

/**
 * A rating at the provider.
 *
 * @param source identifier for the NFO, e.g. {@code themoviedb} or {@code tvdb}
 * @param max    maximum of the scale
 * @param votes  number of votes or {@code null}
 */
public record ProviderRating(String source, double value, int max, Integer votes) {

    public ProviderRating {
        Objects.requireNonNull(source, "source must not be null");
    }
}
