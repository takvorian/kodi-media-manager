package de.tiktak.kmm.plugin.api;

import java.util.Objects;

/**
 * Angaben zu einem Filmset (einer Sammlung) beim Anbieter.
 *
 * @param providerId ID der Sammlung beim Anbieter
 * @param overview   Beschreibung oder {@code null}
 */
public record MovieSetResult(String providerId, String title, String overview) {

    public MovieSetResult {
        Objects.requireNonNull(providerId, "providerId must not be null");
        Objects.requireNonNull(title, "title must not be null");
    }
}
