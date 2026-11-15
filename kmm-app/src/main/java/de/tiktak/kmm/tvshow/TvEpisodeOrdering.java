package de.tiktak.kmm.tvshow;

import java.util.Objects;
import java.util.Optional;

/**
 * The order in which a TV show's seasons and episodes are counted, as one provider knows it, e.g. TVDB's
 * {@code alternate} order named {@code Netflix Order}. The local files must be numbered in this order, since KMM
 * matches the provider's episodes by season and episode number.
 *
 * @param scraperId the TV show scraper that knows the order, e.g. {@code tvdb}
 * @param orderId   the provider's key of the order
 * @param name      the name as the provider shows it
 */
public record TvEpisodeOrdering(String scraperId, String orderId, String name) {

    private static final String SEPARATOR = ":";

    public TvEpisodeOrdering {
        Objects.requireNonNull(scraperId, "scraperId must not be null");
        Objects.requireNonNull(orderId, "orderId must not be null");
        Objects.requireNonNull(name, "name must not be null");
    }

    /**
     * @param stored the value as stored, {@code <scraperId>:<orderId>}, or {@code null}
     * @return empty without an own order or for an unreadable value
     */
    static Optional<TvEpisodeOrdering> parse(String stored, String name) {
        if (stored == null) {
            return Optional.empty();
        }
        int separator = stored.indexOf(SEPARATOR);
        if (separator <= 0 || separator == stored.length() - 1) {
            return Optional.empty();
        }
        String orderId = stored.substring(separator + 1);
        return Optional.of(new TvEpisodeOrdering(stored.substring(0, separator), orderId,
                name == null ? orderId : name));
    }

    /** The value as stored. */
    String stored() {
        return scraperId + SEPARATOR + orderId;
    }

    /** @return the provider's key if the order belongs to this scraper, otherwise {@code null} */
    String orderFor(String scraper) {
        return scraperId.equals(scraper) ? orderId : null;
    }
}
