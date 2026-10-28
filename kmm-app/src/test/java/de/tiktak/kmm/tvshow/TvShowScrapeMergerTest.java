package de.tiktak.kmm.tvshow;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.junit.jupiter.api.Test;

import de.tiktak.kmm.nfo.StreamDetails;
import de.tiktak.kmm.plugin.api.CastMember;
import de.tiktak.kmm.plugin.api.EpisodeResult;
import de.tiktak.kmm.plugin.api.ProviderRating;
import de.tiktak.kmm.plugin.api.SeasonResult;
import de.tiktak.kmm.plugin.api.TvShowResult;

class TvShowScrapeMergerTest {

    private static final TvShowMetadata CURRENT = new TvShowMetadata("Lost (Ordner)", "Lost", "Lost, The", 2003,
            null, "Eigene Handlung", 40, "FSK 12", null, List.of("Abenteuer"), List.of(), List.of("Insel"), null,
            "4607", null, 9);

    @Test
    void overlaysTheShowWithScrapedValuesButKeepsOwnFields() {
        TvShowResult scraped = TvShowResult.builder().title("Lost").premiered(LocalDate.of(2004, 9, 22)).year(2004)
                .status("Ended").studios(List.of("ABC")).uniqueIds(Map.of("tvdb", "73739", "imdb", "kaputt")).build();

        TvShowMetadata merged = TvShowScrapeMerger.mergeShow(CURRENT, scraped);

        assertThat(merged.title()).isEqualTo("Lost");
        assertThat(merged.sortTitle()).isEqualTo("Lost, The");
        assertThat(merged.year()).isEqualTo(2004);
        assertThat(merged.plot()).isEqualTo("Eigene Handlung");
        assertThat(merged.runtime()).isEqualTo(40);
        assertThat(merged.mpaa()).isEqualTo("FSK 12");
        assertThat(merged.status()).isEqualTo("Ended");
        assertThat(merged.genres()).containsExactly("Abenteuer");
        assertThat(merged.studios()).containsExactly("ABC");
        assertThat(merged.tags()).containsExactly("Insel");
        assertThat(merged.tvdbId()).isEqualTo("73739");
        assertThat(merged.tmdbId()).isEqualTo("4607");
        assertThat(merged.imdbId()).as("ungültige ID entfällt").isNull();
        assertThat(merged.userRating()).isEqualTo(9);
    }

    @Test
    void fillsShowExtrasWithoutReplacingKnownOnes() {
        TvShowExtras current = new TvShowExtras(List.of(new TvActor("Paul Fusco", "ALF", null)),
                List.of(new TvRating("imdb", 7.0, 10, 100, true)), new TreeMap<>(Map.of(1, "Ankunft")));
        TvShowResult scraped = TvShowResult.builder().title("ALF")
                .actors(List.of(new CastMember("Max Wright", "Willie", URI.create("https://example.org/max.jpg"))))
                .ratings(List.of(new ProviderRating("imdb", 8.0, 10, 5), new ProviderRating("tvdb", 7.5, 10, 50)))
                .seasons(List.of(new SeasonResult(1, "Staffel Eins", null, null),
                        new SeasonResult(2, "Zwei", null, null), new SeasonResult(3, null, null, null)))
                .build();

        TvShowExtras filled = TvShowScrapeMerger.fillShowExtras(current, scraped);

        assertThat(filled.actors()).containsExactly(new TvActor("Paul Fusco", "ALF", null));
        assertThat(filled.ratings()).containsExactly(new TvRating("imdb", 7.0, 10, 100, true),
                new TvRating("tvdb", 7.5, 10, 50, false));
        assertThat(filled.seasonNames()).containsExactlyEntriesOf(new TreeMap<>(Map.of(1, "Ankunft", 2, "Zwei")));
        TvShowExtras fromScratch = TvShowScrapeMerger.fillShowExtras(TvShowExtras.EMPTY, scraped);
        assertThat(fromScratch.actors())
                .containsExactly(new TvActor("Max Wright", "Willie", "https://example.org/max.jpg"));
        assertThat(fromScratch.ratings().getFirst().defaultRating())
                .as("die erste Bewertung ohne vorhandene ist die Standardbewertung").isTrue();
        assertThat(fromScratch.ratings().get(1).defaultRating()).isFalse();
    }

    @Test
    void fillsOnlyEmptyEpisodeFields() {
        EpisodeMetadata metadata = new EpisodeMetadata("Eigener Titel", null, "Eigene Handlung", null, null, null,
                null, null, 8, null);
        EpisodeExtras extras = new EpisodeExtras(List.of("Regie"), List.of(), List.of(), List.of(), 2, null,
                StreamDetails.EMPTY);
        EpisodeResult scraped = EpisodeResult.builder().season(1).episode(1).title("Pilot").plot("Absturz")
                .aired(LocalDate.of(2004, 9, 22)).runtime(42).directors(List.of("J. J. Abrams"))
                .writers(List.of("Damon Lindelof")).uniqueIds(Map.of("tvdb", "127131", "tmdb", "64465")).build();

        var fill = TvShowScrapeMerger.fillEpisode(metadata, extras, scraped, false).orElseThrow();

        assertThat(fill.metadata().title()).isEqualTo("Eigener Titel");
        assertThat(fill.metadata().plot()).isEqualTo("Eigene Handlung");
        assertThat(fill.metadata().aired()).isEqualTo(LocalDate.of(2004, 9, 22));
        assertThat(fill.metadata().runtime()).isEqualTo(42);
        assertThat(fill.metadata().tvdbId()).isEqualTo("127131");
        assertThat(fill.metadata().tmdbId()).isEqualTo("64465");
        assertThat(fill.metadata().userRating()).isEqualTo(8);
        assertThat(fill.extras().directors()).containsExactly("Regie");
        assertThat(fill.extras().writers()).containsExactly("Damon Lindelof");
        assertThat(fill.extras().playCount()).isEqualTo(2);
        assertThat(TvShowScrapeMerger.fillEpisode(metadata, extras, scraped, true).orElseThrow().metadata().title())
                .as("Titel aus dem Dateinamen wird ersetzt").isEqualTo("Pilot");
    }

    @Test
    void reportsUnchangedEpisodes() {
        EpisodeMetadata metadata = new EpisodeMetadata("Pilot", null, "Absturz", null, null, null, null, null, null, null);
        EpisodeResult scraped = EpisodeResult.builder().season(1).episode(1).build();

        assertThat(TvShowScrapeMerger.fillEpisode(metadata, EpisodeExtras.EMPTY, scraped, true)).isEmpty();
    }
}
