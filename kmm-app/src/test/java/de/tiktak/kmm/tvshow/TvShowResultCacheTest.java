package de.tiktak.kmm.tvshow;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

import de.tiktak.kmm.plugin.api.TvShowResult;

class TvShowResultCacheTest {

    private static final Instant NOW = Instant.parse("2026-10-26T12:00:00Z");
    private static final TvShowResult LOST = TvShowResult.builder().title("Lost").build();

    private final AtomicReference<Instant> now = new AtomicReference<>(NOW);
    private final TvShowResultCache cache = new TvShowResultCache(new AdjustableClock());

    @Test
    void keepsResultsPerScraperAndIdUntilTheyExpire() {
        cache.put("tvdb", "73739", LOST);

        assertThat(cache.get("tvdb", "73739")).contains(LOST);
        assertThat(cache.get("tmdb", "73739")).isEmpty();
        now.set(NOW.plus(TvShowResultCache.TIME_TO_LIVE));
        assertThat(cache.get("tvdb", "73739")).isEmpty();
    }

    @Test
    void dropsTheOldestEntryWhenFull() {
        for (int index = 0; index < TvShowResultCache.MAX_ENTRIES; index++) {
            now.set(NOW.plusSeconds(index));
            cache.put("tvdb", String.valueOf(index), LOST);
        }
        now.set(NOW.plusSeconds(TvShowResultCache.MAX_ENTRIES));

        cache.put("tvdb", "neu", LOST);

        assertThat(cache.get("tvdb", "0")).isEmpty();
        assertThat(cache.get("tvdb", "1")).contains(LOST);
        assertThat(cache.get("tvdb", "neu")).contains(LOST);
    }

    /** Uhr, die der Test vorstellen kann. */
    private final class AdjustableClock extends Clock {

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now.get();
        }
    }
}
