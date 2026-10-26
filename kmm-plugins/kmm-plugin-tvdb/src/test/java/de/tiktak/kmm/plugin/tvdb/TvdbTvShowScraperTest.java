package de.tiktak.kmm.plugin.tvdb;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import de.tiktak.kmm.plugin.api.CastMember;
import de.tiktak.kmm.plugin.api.EpisodeResult;
import de.tiktak.kmm.plugin.api.MediaType;
import de.tiktak.kmm.plugin.api.ScrapeRequest;
import de.tiktak.kmm.plugin.api.ScraperException;
import de.tiktak.kmm.plugin.api.SearchQuery;
import de.tiktak.kmm.plugin.api.SeasonResult;
import de.tiktak.kmm.plugin.api.TvShowResult;

class TvdbTvShowScraperTest {

    private static final String API_KEY = "tvdb-test-key";
    private static final String SERIES = "/v4/series/73739";
    private static final Instant NOW = Instant.parse("2026-10-26T12:00:00Z");

    private FakeTvdbServer tvdb;
    private TvdbTvShowScraper scraper;

    @BeforeEach
    void setUp() {
        tvdb = new FakeTvdbServer()
                .respond("/v4/search", "search-lost.json")
                .respond(SERIES + "/extended", "series-73739.json")
                .respond(SERIES + "/translations/deu", "series-73739-translation.json")
                .respond(SERIES + "/episodes/official", "episodes-73739-page0.json")
                .respond(SERIES + "/episodes/official?page=1", "episodes-73739-page1.json")
                .respond(SERIES + "/episodes/official/deu", "episodes-73739-deu.json")
                .respond("/v4/search/remoteid/tt0411008", "remoteid-tt0411008.json")
                .respond("/v4/search/remoteid/4607", "remoteid-empty.json");
        scraper = initialized(context(), Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @AfterEach
    void tearDown() {
        tvdb.close();
    }

    @Test
    void describesItselfWithAttribution() {
        assertThat(scraper.id()).isEqualTo("tvdb");
        assertThat(scraper.displayName()).isEqualTo("TheTVDB");
        assertThat(scraper.supportedMediaTypes()).containsExactly(MediaType.TV_SHOW);
        assertThat(scraper.missingConfiguration()).isEmpty();
        assertThat(scraper.attribution()).hasValueSatisfying(attribution -> {
            assertThat(attribution.text()).startsWith("Metadata provided by TheTVDB.");
            assertThat(attribution.link()).isEqualTo(URI.create("https://www.thetvdb.com"));
        });
    }

    @Test
    void logsInOnceAndSearchesSeriesWithTranslatedTitles() {
        var results = scraper.search(new SearchQuery(MediaType.TV_SHOW, "Lost", 2004));
        scraper.search(new SearchQuery(MediaType.TV_SHOW, "Lost", null));

        assertThat(results).hasSize(3);
        assertThat(results.getFirst()).satisfies(lost -> {
            assertThat(lost.providerId()).isEqualTo("73739");
            assertThat(lost.title()).isEqualTo("Lost");
            assertThat(lost.year()).isEqualTo(2004);
            assertThat(lost.overview()).isEqualTo("Überlebende eines Flugzeugabsturzes.");
            assertThat(lost.posterUrl()).hasToString("https://artworks.thetvdb.com/banners/posters/73739-1.jpg");
        });
        assertThat(results.get(1).title()).startsWith("Lost in Space – Verschollen");
        assertThat(results.get(2)).satisfies(unknown -> {
            assertThat(unknown.providerId()).isEqualTo("series-1");
            assertThat(unknown.year()).isNull();
            assertThat(unknown.posterUrl()).isNull();
        });
        assertThat(tvdb.requestsTo("/v4/login")).isEqualTo(1);
        assertThat(tvdb.requests().getFirst().body()).isEqualTo("{\"apikey\":\"tvdb-test-key\"}");
        assertThat(tvdb.requests().get(1).authorization()).isEqualTo("Bearer " + FakeTvdbServer.TOKEN);
        assertThat(tvdb.requests().get(1).query()).contains("query=Lost", "type=series", "year=2004");
    }

    @Test
    void sendsThePinAndRenewsExpiredTokens() {
        Clock later = Clock.offset(Clock.fixed(NOW, ZoneOffset.UTC), TvdbTokens.LIFETIME.plus(Duration.ofDays(1)));
        TvdbTvShowScraper withPin = initialized(context().with("pin", "1234"), Clock.fixed(NOW, ZoneOffset.UTC));
        withPin.search(new SearchQuery(MediaType.TV_SHOW, "Lost", null));
        initialized(context().with("pin", "1234"), later).search(new SearchQuery(MediaType.TV_SHOW, "Lost", null));

        assertThat(tvdb.requestsTo("/v4/login")).isEqualTo(2);
        assertThat(tvdb.requests().getFirst().body()).isEqualTo("{\"apikey\":\"tvdb-test-key\",\"pin\":\"1234\"}");
    }

    @Test
    void fetchesTheSeriesWithTranslationAllPagesAndSeasons() {
        TvShowResult lost = scraper.fetch("73739").orElseThrow();

        assertThat(lost.title()).isEqualTo("Lost – Verschollen");
        assertThat(lost.originalTitle()).isEqualTo("Lost");
        assertThat(lost.plot()).isEqualTo("Überlebende eines Flugzeugabsturzes.");
        assertThat(lost.premiered()).isEqualTo(LocalDate.of(2004, 9, 22));
        assertThat(lost.year()).isEqualTo(2004);
        assertThat(lost.runtime()).isEqualTo(45);
        assertThat(lost.status()).isEqualTo("Ended");
        assertThat(lost.certification()).isEqualTo("FSK 16");
        assertThat(lost.genres()).containsExactly("Drama", "Science Fiction");
        assertThat(lost.studios()).containsExactly("ABC");
        assertThat(lost.ratings()).isEmpty();
        assertThat(lost.uniqueIds()).containsExactlyInAnyOrderEntriesOf(
                Map.of("tvdb", "73739", "imdb", "tt0411008", "tmdb", "4607"));
        assertThat(lost.actors()).containsExactly(
                new CastMember("Matthew Fox", "Jack Shephard", URI.create("https://artworks.thetvdb.com/fox.jpg")),
                new CastMember("Evangeline Lilly", "Kate Austen", URI.create("https://artworks.thetvdb.com/kate.jpg")));
        assertThat(lost.seasons()).containsExactly(new SeasonResult(0, null, null, null),
                new SeasonResult(1, "Die Insel", null, null));
        assertThat(lost.episodes()).extracting(EpisodeResult::season, EpisodeResult::episode, EpisodeResult::title)
                .containsExactly(tuple(1, 1, "Gestrandet (1)"), tuple(1, 2, "Pilot (2)"), tuple(0, 1, "The Journey"));
    }

    @Test
    void mapsEpisodeDetails() {
        TvShowResult lost = scraper.fetch("73739").orElseThrow();

        EpisodeResult pilot = lost.episodes().getFirst();
        assertThat(pilot.plot()).isEqualTo("Der Absturz.");
        assertThat(pilot.aired()).isEqualTo(LocalDate.of(2004, 9, 22));
        assertThat(pilot.runtime()).isEqualTo(42);
        assertThat(pilot.uniqueIds()).containsExactlyEntriesOf(Map.of("tvdb", "127131"));
        assertThat(pilot.thumb()).hasToString("https://artworks.thetvdb.com/banners/episodes/73739/127131.jpg");
        EpisodeResult second = lost.episodes().get(1);
        assertThat(second.plot()).isNull();
        assertThat(second.aired()).isNull();
        assertThat(second.runtime()).isNull();
        assertThat(lost.episodes().get(2).thumb()).as("relative Pfade sind keine Bild-URL").isNull();
    }

    @Test
    void usesTheConfiguredEpisodeOrdering() {
        tvdb.respond(SERIES + "/episodes/dvd", "episodes-73739-page1.json");
        TvdbTvShowScraper dvd = initialized(context().with("episode-ordering", "DVD"), Clock.systemUTC());

        TvShowResult lost = dvd.fetch("73739").orElseThrow();

        assertThat(lost.seasons()).extracting(SeasonResult::number).containsExactly(1);
        assertThat(tvdb.requests()).extracting(FakeTvdbServer.Request::path).contains(SERIES + "/episodes/dvd");
    }

    @Test
    void keepsOriginalTextsWithoutTranslation() {
        tvdb.respondWithStatus(SERIES + "/translations/deu", 404);
        tvdb.respondWithStatus(SERIES + "/episodes/official/deu", 404);

        TvShowResult lost = scraper.fetch("73739").orElseThrow();

        assertThat(lost.title()).isEqualTo("Lost");
        assertThat(lost.plot()).isEqualTo("Stranded on an island.");
        assertThat(lost.episodes().getFirst().title()).isEqualTo("Pilot (1)");
    }

    @Test
    void returnsNothingForUnknownOrInvalidIds() {
        assertThat(scraper.fetch("1")).isEmpty();
        assertThat(scraper.fetch("../login")).isEmpty();
    }

    @Test
    void scrapesByKnownIdsOrTheUniqueSearchMatch() {
        assertThat(scraper.scrape(request("egal", null, Map.of("tvdb", "73739")))).isPresent();
        assertThat(scraper.scrape(request("egal", null, Map.of("imdb", "tt0411008")))).isPresent();
        assertThat(scraper.scrape(request("Lost", 2004, Map.of("tmdb", "4607")))).as("Suche als Rückfall").isPresent();
        assertThat(scraper.scrape(request("Lost", 1999, Map.of()))).isEmpty();
        assertThat(scraper.scrape(new ScrapeRequest(MediaType.MOVIE, "Lost", null, Map.of()))).isEmpty();
        assertThat(scraper.search(new SearchQuery(MediaType.MOVIE, "Lost", null))).isEmpty();
    }

    @Test
    void reportsMissingApiKeyAndUseBeforeInitialization() {
        TvdbTvShowScraper withoutKey = initialized(new TestPluginContext(), Clock.systemUTC());

        assertThat(withoutKey.missingConfiguration()).contains(TvdbTvShowScraper.MISSING_API_KEY);
        assertThatThrownBy(() -> withoutKey.fetch("73739")).isInstanceOf(ScraperException.class);
        assertThat(new TvdbTvShowScraper().missingConfiguration()).isPresent();
        assertThatIllegalStateException().isThrownBy(() -> new TvdbTvShowScraper().fetch("73739"));
    }

    @Test
    void reportsRejectedCredentials() {
        tvdb.respondWithStatus("/v4/login", 401);

        assertThatThrownBy(() -> scraper.fetch("73739"))
                .isInstanceOf(ScraperException.class)
                .hasMessageContaining("API-Key oder die PIN");
    }

    @Test
    void logsInAgainWhenTheTokenIsRejected() {
        scraper.search(new SearchQuery(MediaType.TV_SHOW, "Lost", null));
        tvdb.respondWithStatus("/v4/search", 401);

        assertThatThrownBy(() -> scraper.search(new SearchQuery(MediaType.TV_SHOW, "Lost", null)))
                .isInstanceOf(ScraperException.class)
                .hasMessageContaining("API-Key oder die PIN");
        assertThat(tvdb.requestsTo("/v4/login")).isEqualTo(2);
    }

    @ParameterizedTest(name = "HTTP {0}")
    @CsvSource(delimiter = '|', value = {
            "429 | Anfragelimit",
            "500 | Fehler 500"})
    void translatesErrorResponses(int status, String expectedMessage) {
        tvdb.respondWithStatus(SERIES + "/extended", status);

        assertThatThrownBy(() -> scraper.fetch("73739"))
                .isInstanceOf(ScraperException.class)
                .hasMessageContaining(expectedMessage);
    }

    @Test
    void reportsFailedLoginAndUnexpectedResponses() {
        tvdb.respondWithStatus("/v4/login", 503);
        assertThatThrownBy(() -> scraper.fetch("73739")).hasMessageContaining("Anmeldung bei TVDB");

        tvdb.respond("/v4/login", "remoteid-empty.json");
        assertThatThrownBy(() -> scraper.fetch("73739")).hasMessageContaining("kein Token");

        tvdb.respond("/v4/login", "invalid.json");
        assertThatThrownBy(() -> scraper.fetch("73739")).hasMessageContaining("unerwartete Antwort");
    }

    @Test
    void reportsUnreachableServer() {
        URI unreachable = tvdb.baseUrl();
        tvdb.close();
        TvdbTvShowScraper offline = initialized(context().with("base-url", unreachable.toString()), Clock.systemUTC());

        assertThatThrownBy(() -> offline.fetch("73739"))
                .isInstanceOf(ScraperException.class)
                .hasMessageContaining("nicht erreichbar");
    }

    private TestPluginContext context() {
        return new TestPluginContext().with("api-key", API_KEY).with("base-url", tvdb.baseUrl().toString())
                .with("tv-language", "de-DE");
    }

    private static TvdbTvShowScraper initialized(TestPluginContext context, Clock clock) {
        TvdbTvShowScraper tvdbScraper = new TvdbTvShowScraper(clock);
        tvdbScraper.initialize(context);
        return tvdbScraper;
    }

    private static ScrapeRequest request(String title, Integer year, Map<String, String> ids) {
        return new ScrapeRequest(MediaType.TV_SHOW, title, year, ids);
    }
}
