package de.tiktak.kmm.plugin.api;

import java.util.Objects;

/**
 * The collection (movie set) a movie belongs to at the provider.
 *
 * @param providerId ID of the collection at the provider, e.g. the TMDB collection ID
 */
public record CollectionReference(String providerId, String name) {

    public CollectionReference {
        Objects.requireNonNull(providerId, "providerId must not be null");
        Objects.requireNonNull(name, "name must not be null");
    }
}
