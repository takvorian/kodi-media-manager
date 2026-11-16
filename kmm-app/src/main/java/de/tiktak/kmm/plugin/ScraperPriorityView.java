package de.tiktak.kmm.plugin;

import java.util.List;

import de.tiktak.kmm.i18n.LocalizedText;
import de.tiktak.kmm.plugin.api.MediaType;
import de.tiktak.kmm.plugin.api.Scraper;

/**
 * The order of the scrapers of one kind for movies or TV shows.
 *
 * @param inherited whether a source follows the order from the settings for this kind
 * @param entries   all scrapers of the kind in their order, including those switched off
 */
public record ScraperPriorityView(ScraperKind kind, MediaType mediaType, boolean inherited, List<Entry> entries) {

    public ScraperPriorityView {
        entries = List.copyOf(entries);
    }

    /**
     * @param missingConfiguration what the scraper still needs (e.g. a key), or {@code null} if it is ready
     */
    public record Entry(String scraperId, String displayName, boolean enabled, LocalizedText missingConfiguration) {

        static Entry of(Scraper scraper, boolean enabled) {
            return new Entry(scraper.id(), scraper.displayName(), enabled,
                    scraper.missingConfiguration().orElse(null));
        }
    }
}
