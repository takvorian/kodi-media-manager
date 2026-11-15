package de.tiktak.kmm.plugin.api;

import java.util.Objects;

/**
 * A way the provider counts the seasons and episodes of a TV show, such as the order of first broadcast or the order
 * of a streaming service.
 *
 * @param id   the provider's key of the order, passed to {@link TvShowScraper#fetch(String, String)}
 * @param name the name as the provider shows it, e.g. {@code Netflix Order}
 */
public record EpisodeOrder(String id, String name) {

    public EpisodeOrder {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(name, "name must not be null");
    }
}
