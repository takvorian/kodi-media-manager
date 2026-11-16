package de.tiktak.kmm.plugin;

import java.util.List;

import de.tiktak.kmm.plugin.api.MediaType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * A new order of the scrapers of one kind.
 *
 * @param inherit only for a source: follow the order from the settings again; {@code entries} are then ignored
 */
public record ScraperPriorityChange(@NotNull ScraperKind kind, @NotNull MediaType mediaType, boolean inherit,
        @NotNull @Size(max = MAX_SCRAPERS) List<@Valid @NotNull Entry> entries) {

    static final int MAX_SCRAPERS = 50;

    /** @param scraperId ID of the scraper; unknown IDs are kept, in case the plugin comes back */
    public record Entry(@NotNull @Pattern(regexp = "[a-z0-9][a-z0-9_.-]{0,39}") String scraperId, boolean enabled) {
    }

    ScraperOrder order() {
        return new ScraperOrder(entries.stream()
                .map(entry -> new ScraperOrder.Entry(entry.scraperId(), entry.enabled()))
                .toList());
    }
}
