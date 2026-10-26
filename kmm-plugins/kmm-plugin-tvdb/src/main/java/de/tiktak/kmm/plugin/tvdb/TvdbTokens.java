package de.tiktak.kmm.plugin.tvdb;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Merkt sich TVDB-Tokens über die Lebensdauer einzelner Scraper hinaus, denn PF4J erzeugt die
 * Erweiterung bei jeder Abfrage neu. Ein Token gilt einen Monat; es wird vorher erneuert.
 */
final class TvdbTokens {

    static final Duration LIFETIME = Duration.ofDays(25);
    private static final Map<String, Token> TOKENS = new ConcurrentHashMap<>();

    private TvdbTokens() {
    }

    static Optional<String> valid(String key, Clock clock) {
        Token token = TOKENS.get(key);
        return token != null && token.expiresAt().isAfter(clock.instant()) ? Optional.of(token.value())
                : Optional.empty();
    }

    static void remember(String key, String token, Clock clock) {
        TOKENS.put(key, new Token(token, clock.instant().plus(LIFETIME)));
    }

    static void forget(String key) {
        TOKENS.remove(key);
    }

    private record Token(String value, Instant expiresAt) {
    }
}
