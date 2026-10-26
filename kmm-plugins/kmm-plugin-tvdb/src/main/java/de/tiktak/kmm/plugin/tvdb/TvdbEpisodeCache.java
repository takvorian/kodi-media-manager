package de.tiktak.kmm.plugin.tvdb;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import tools.jackson.databind.JsonNode;

/**
 * Hält Episodendetails einen Tag lang vor, damit wiederholte Übernahmen TVDB nicht erneut je Episode
 * fragen. PF4J erzeugt die Erweiterung bei jeder Abfrage neu, deshalb ist der Speicher statisch; er
 * vergisst die ältesten Einträge, sobald er voll ist.
 */
final class TvdbEpisodeCache {

    static final Duration LIFETIME = Duration.ofDays(1);
    static final int MAX_ENTRIES = 20_000;
    private static final int INITIAL_CAPACITY = 256;
    private static final float LOAD_FACTOR = 0.75f;
    private static final boolean ACCESS_ORDER = true;

    private static final Map<String, CachedDetails> ENTRIES = new LinkedHashMap<>(INITIAL_CAPACITY, LOAD_FACTOR,
            ACCESS_ORDER) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, CachedDetails> eldest) {
            return size() > MAX_ENTRIES;
        }
    };

    private TvdbEpisodeCache() {
    }

    static synchronized Optional<JsonNode> get(String key, Clock clock) {
        CachedDetails entry = ENTRIES.get(key);
        return entry != null && entry.expiresAt().isAfter(clock.instant()) ? Optional.of(entry.details())
                : Optional.empty();
    }

    static synchronized void put(String key, JsonNode details, Clock clock) {
        ENTRIES.put(key, new CachedDetails(details, clock.instant().plus(LIFETIME)));
    }

    private record CachedDetails(JsonNode details, Instant expiresAt) {
    }
}
