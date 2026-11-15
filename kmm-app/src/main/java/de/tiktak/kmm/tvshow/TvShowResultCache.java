package de.tiktak.kmm.tvshow;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

import de.tiktak.kmm.plugin.ScrapeLanguage;
import de.tiktak.kmm.plugin.api.TvShowResult;
import lombok.RequiredArgsConstructor;

/**
 * Keeps recently loaded TV shows for a while. A TV show with all episodes costs several requests
 * at the provider; proposal, applying and missing episodes thus use the same response.
 */
@Component
@RequiredArgsConstructor
class TvShowResultCache {

    static final Duration TIME_TO_LIVE = Duration.ofMinutes(15);
    static final int MAX_ENTRIES = 20;

    private final Map<String, Entry> entries = new ConcurrentHashMap<>();
    private final Clock clock;

    /** @param episodeOrder the provider's key of the episode order, {@code null} for the one from the settings */
    Optional<TvShowResult> get(String scraperId, String providerId, String episodeOrder) {
        Entry entry = entries.get(key(scraperId, providerId, episodeOrder));
        return entry != null && entry.storedAt().plus(TIME_TO_LIVE).isAfter(clock.instant())
                ? Optional.of(entry.result()) : Optional.empty();
    }

    void put(String scraperId, String providerId, String episodeOrder, TvShowResult result) {
        if (entries.size() >= MAX_ENTRIES) {
            entries.entrySet().stream()
                    .min(Comparator.comparing(entry -> entry.getValue().storedAt()))
                    .ifPresent(oldest -> entries.remove(oldest.getKey()));
        }
        entries.put(key(scraperId, providerId, episodeOrder), new Entry(result, clock.instant()));
    }

    /** After a change of language or episode order the TV show must be fetched again. */
    private static String key(String scraperId, String providerId, String episodeOrder) {
        return scraperId + ":" + providerId + ":" + ScrapeLanguage.cacheKey() + ":" + episodeOrder;
    }

    private record Entry(TvShowResult result, Instant storedAt) {
    }
}
