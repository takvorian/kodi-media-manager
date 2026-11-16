package de.tiktak.kmm.plugin;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * The chosen order of the scrapers of one kind, with scrapers switched off. Stored as text such as
 * {@code tmdb,-tvdb}, where a leading {@code -} switches a scraper off.
 */
public record ScraperOrder(List<Entry> entries) {

    private static final String SEPARATOR = ",";
    private static final String OFF = "-";

    public ScraperOrder {
        entries = List.copyOf(entries);
    }

    /** One scraper in the order. */
    public record Entry(String scraperId, boolean enabled) {
    }

    static ScraperOrder parse(String text) {
        return new ScraperOrder(Arrays.stream(text.split(SEPARATOR))
                .map(String::strip)
                .filter(id -> !id.isEmpty() && !id.equals(OFF))
                .map(id -> id.startsWith(OFF) ? new Entry(id.substring(OFF.length()), false) : new Entry(id, true))
                .toList());
    }

    String format() {
        return entries.stream()
                .map(entry -> entry.enabled() ? entry.scraperId() : OFF + entry.scraperId())
                .collect(Collectors.joining(SEPARATOR));
    }
}
