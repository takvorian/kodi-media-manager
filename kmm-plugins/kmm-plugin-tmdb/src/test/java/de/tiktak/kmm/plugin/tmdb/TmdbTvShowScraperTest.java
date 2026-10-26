package de.tiktak.kmm.plugin.tmdb;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.net.URI;
import java.time.LocalDate;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import de.tiktak.kmm.plugin.api.CastMember;
import de.tiktak.kmm.plugin.api.EpisodeResult;
import de.tiktak.kmm.plugin.api.MediaType;
import de.tiktak.kmm.plugin.api.ProviderRating;
import de.tiktak.kmm.plugin.api.ScrapeRequest;
import de.tiktak.kmm.plugin.api.SearchQuery;
import de.tiktak.kmm.plugin.api.SeasonResult;
import de.tiktak.kmm.plugin.api.TvShowResult;

class TmdbTvShowScraperTest {

    private static final String API_KEY = "0123456789abcdef0123456789abcdef";

    private FakeTmdbServer tmdb;
    private TmdbTvShowScraper scraper;

    @BeforeEach
    void setUp() {
        tmdb = new FakeTmdbServer()
                .respond("/3/search/tv", "search-lost.json")
                .respond("/3/tv/4607", "tv-4607.json")
                .respond("/3/tv/4607/season/0", "tv-4607-season-0.json")
                .respond("/3/tv/4607/season/1", "tv-4607-season-1.json")
                .respond("/3/tv/4607/season/2", "tv-4607-season-2.json")
                .respond("/3/find/73739", "find-tvdb-73739.json")
                .respond("/3/find/tt0411008", "find-tt0411008.json");
        scraper = new TmdbTvShowScraper();
        scraper.initialize(new TestPluginContext().with("api-key", API_KEY)
                .with("base-url", tmdb.baseUrl().toString())
                .with("language", "en-US")
                .with("tv-language", "de-DE"));
    }

    @AfterEach
    void tearDown() {
        tmdb.close();
    }

    @Test
    void describesItselfAsTvScraper() {
        assertThat(scraper.id()).isEqualTo("tmdb");
        assertThat(scraper.supportedMediaTypes()).containsExactly(MediaType.TV_SHOW);
        assertThat(scraper.attribution()).isEmpty();
    }

    @Test
    void searchesShowsInTheTvLanguage() {
        var results = scraper.search(new SearchQuery(MediaType.TV_SHOW, "Lost", 2004));

        assertThat(results).hasSize(3);
        assertThat(results.getFirst()).satisfies(lost -> {
            assertThat(lost.providerId()).isEqualTo("4607");
            assertThat(lost.year()).isEqualTo(2004);
            assertThat(lost.posterUrl()).isEqualTo(URI.create("https://image.tmdb.org/t/p/w185/lost.jpg"));
        });
        assertThat(results.get(1).overview()).isNull();
        assertThat(results.get(2).year()).isNull();
        assertThat(tmdb.requests().getFirst().query())
                .contains("query=Lost", "first_air_date_year=2004", "language=de-DE");
    }

    @Test
    void searchesNothingForMovies() {
        assertThat(scraper.search(new SearchQuery(MediaType.MOVIE, "Lost", null))).isEmpty();
        assertThat(scraper.scrape(new ScrapeRequest(MediaType.MOVIE, "Lost", null, Map.of()))).isEmpty();
        assertThat(tmdb.requests()).isEmpty();
    }

    @Test
    void fetchesTheShowWithAllSeasonsAndEpisodes() {
        TvShowResult lost = scraper.fetch("4607").orElseThrow();

        assertThat(lost.title()).isEqualTo("Lost");
        assertThat(lost.premiered()).isEqualTo(LocalDate.of(2004, 9, 22));
        assertThat(lost.year()).isEqualTo(2004);
        assertThat(lost.runtime()).isEqualTo(45);
        assertThat(lost.status()).isEqualTo("Ended");
        assertThat(lost.certification()).isEqualTo("FSK 16");
        assertThat(lost.genres()).containsExactly("Drama", "Mystery");
        assertThat(lost.studios()).containsExactly("ABC");
        assertThat(lost.ratings()).containsExactly(new ProviderRating("themoviedb", 7.9, 10, 3000));
        assertThat(lost.uniqueIds()).containsExactlyInAnyOrderEntriesOf(
                Map.of("tmdb", "4607", "tvdb", "73739", "imdb", "tt0411008"));
        assertThat(lost.actors()).containsExactly(
                new CastMember("Matthew Fox", "Jack Shephard", URI.create("https://image.tmdb.org/t/p/w185/fox.jpg")),
                new CastMember("Evangeline Lilly", null, null));
        assertThat(lost.seasons()).containsExactly(
                new SeasonResult(0, null, null, LocalDate.of(2005, 4, 27)),
                new SeasonResult(1, "Die Insel", "Die erste Staffel.", LocalDate.of(2004, 9, 22)),
                new SeasonResult(2, null, null, null));
        assertThat(lost.episodes()).extracting(EpisodeResult::season, EpisodeResult::episode)
                .containsExactly(tuple(0, 1), tuple(1, 1),
                        tuple(1, 2));
        assertThat(tmdb.requests()).anySatisfy(request -> assertThat(request.query())
                .contains("append_to_response=external_ids%2Ccontent_ratings%2Ccredits", "language=de-DE"));
    }

    @Test
    void mapsAllDetailsOfAnEpisode() {
        TvShowResult lost = scraper.fetch("4607").orElseThrow();

        EpisodeResult pilot = lost.episodes().get(1);
        assertThat(pilot.title()).isEqualTo("Gestrandet (1)");
        assertThat(pilot.plot()).isEqualTo("Der Absturz.");
        assertThat(pilot.aired()).isEqualTo(LocalDate.of(2004, 9, 22));
        assertThat(pilot.runtime()).isEqualTo(42);
        assertThat(pilot.directors()).containsExactly("J. J. Abrams");
        assertThat(pilot.writers()).containsExactly("Damon Lindelof", "J. J. Abrams");
        assertThat(pilot.guestStars()).containsExactly(
                new CastMember("Greg Grunberg", "Pilot", URI.create("https://image.tmdb.org/t/p/w185/grunberg.jpg")));
        assertThat(pilot.ratings()).containsExactly(new ProviderRating("themoviedb", 8.1, 10, 120));
        assertThat(pilot.uniqueIds()).containsExactlyEntriesOf(Map.of("tmdb", "64465"));
        assertThat(pilot.thumb()).isEqualTo(URI.create("https://image.tmdb.org/t/p/original/pilot.jpg"));

        EpisodeResult unknown = lost.episodes().get(2);
        assertThat(unknown.plot()).isNull();
        assertThat(unknown.aired()).isNull();
        assertThat(unknown.runtime()).isNull();
        assertThat(unknown.ratings()).isEmpty();
        assertThat(unknown.thumb()).isNull();
    }

    @Test
    void mapsReturningSeriesToContinuing() {
        tmdb.respond("/3/tv/4607", "tv-4607-returning.json");

        assertThat(scraper.fetch("4607").orElseThrow().status()).isEqualTo("Continuing");
    }

    @Test
    void returnsNothingForUnknownOrInvalidIds() {
        assertThat(scraper.fetch("1")).isEmpty();
        assertThat(scraper.fetch("../movie/949")).isEmpty();
    }

    @Test
    void scrapesByKnownTmdbTvdbOrImdbId() {
        assertThat(scraper.scrape(new ScrapeRequest(MediaType.TV_SHOW, "egal", null, Map.of("tmdb", "4607"))))
                .isPresent();
        assertThat(scraper.scrape(new ScrapeRequest(MediaType.TV_SHOW, "egal", null, Map.of("tvdb", "73739"))))
                .isPresent();
        assertThat(scraper.scrape(new ScrapeRequest(MediaType.TV_SHOW, "egal", null, Map.of("imdb", "tt0411008"))))
                .isPresent();
        assertThat(tmdb.requests()).extracting(FakeTmdbServer.HttpExchangeRecord::path)
                .contains("/3/find/73739", "/3/find/tt0411008");
    }

    @Test
    void scrapesByTheUniqueSearchMatch() {
        assertThat(scraper.scrape(new ScrapeRequest(MediaType.TV_SHOW, "lost", 2004, Map.of("tvdb", "kaputt"))))
                .map(TvShowResult::title).contains("Lost");
        assertThat(scraper.scrape(new ScrapeRequest(MediaType.TV_SHOW, "Lost", 1999, Map.of()))).isEmpty();
    }
}
