package de.tiktak.kmm.plugin.api;

import java.util.Objects;

/**
 * Details of a movie set (a collection) at the provider.
 *
 * @param providerId ID of the collection at the provider
 * @param overview   description or {@code null}
 */
public record MovieSetResult(String providerId, String title, String overview) {

    public MovieSetResult {
        Objects.requireNonNull(providerId, "providerId must not be null");
        Objects.requireNonNull(title, "title must not be null");
    }
}
