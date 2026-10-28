package de.tiktak.kmm.plugin.api;

import java.util.Objects;

/**
 * Die Sammlung (das Filmset), zu der ein Film beim Anbieter gehört.
 *
 * @param providerId ID der Sammlung beim Anbieter, z. B. die TMDB-Collection-ID
 */
public record CollectionReference(String providerId, String name) {

    public CollectionReference {
        Objects.requireNonNull(providerId, "providerId must not be null");
        Objects.requireNonNull(name, "name must not be null");
    }
}
