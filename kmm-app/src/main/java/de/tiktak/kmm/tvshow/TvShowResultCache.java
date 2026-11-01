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
 * Hält zuletzt geladene Serien eine Weile vor. Eine Serie mit allen Episoden kostet beim Anbieter
 * mehrere Anfragen; Vorschlag, Übernahme und fehlende Episoden nutzen so dieselbe Antwort.
 */
@Component
@RequiredArgsConstructor
class TvShowResultCache {

    static final Duration TIME_TO_LIVE = Duration.ofMinutes(15);
    static final int MAX_ENTRIES = 20;

    private final Map<String, Entry> entries = new ConcurrentHashMap<>();
    private final Clock clock;

    Optional<TvShowResult> get(String scraperId, String providerId) {
        Entry entry = entries.get(key(scraperId, providerId));
        return entry != null && entry.storedAt().plus(TIME_TO_LIVE).isAfter(clock.instant())
                ? Optional.of(entry.result()) : Optional.empty();
    }

    void put(String scraperId, String providerId, TvShowResult result) {
        if (entries.size() >= MAX_ENTRIES) {
            entries.entrySet().stream()
                    .min(Comparator.comparing(entry -> entry.getValue().storedAt()))
                    .ifPresent(oldest -> entries.remove(oldest.getKey()));
        }
        entries.put(key(scraperId, providerId), new Entry(result, clock.instant()));
    }

    /** Nach einem Sprachwechsel muss die Serie neu geholt werden. */
    private static String key(String scraperId, String providerId) {
        return scraperId + ":" + providerId + ":" + ScrapeLanguage.cacheKey();
    }

    private record Entry(TvShowResult result, Instant storedAt) {
    }
}
