package de.tiktak.kmm.plugin.fanarttv;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import java.net.URI;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import de.tiktak.kmm.i18n.LocalizedText;
import de.tiktak.kmm.plugin.api.ImageCandidate;
import de.tiktak.kmm.plugin.api.ImageType;
import de.tiktak.kmm.plugin.api.MediaType;
import de.tiktak.kmm.plugin.api.ScrapeRequest;
import de.tiktak.kmm.plugin.api.ScraperException;

class FanartTvImageScraperTest {

    private static final String API_KEY = "fanart-project-key";

    private FakeFanartTvServer fanart;
    private FanartTvImageScraper scraper;

    @BeforeEach
    void setUp() {
        fanart = new FakeFanartTvServer().respond("/v3/movies/949", "movie-949.json")
                .respond("/v3/movies/tt0113277", "movie-949.json");
        scraper = initialized(new TestPluginContext().with("api-key", API_KEY));
    }

    @AfterEach
    void tearDown() {
        fanart.close();
    }

    @Test
    void describesItself() {
        assertThat(scraper.id()).isEqualTo("fanarttv");
        assertThat(scraper.displayName()).isEqualTo("fanart.tv");
        assertThat(scraper.supportedMediaTypes()).containsExactlyInAnyOrder(MediaType.MOVIE, MediaType.MOVIE_SET,
                MediaType.TV_SHOW);
        assertThat(scraper.missingConfiguration()).isEmpty();
    }

    @Test
    void mapsAllImageKindsToKodiTypes() {
        var images = scraper.findImages(request(Map.of("tmdb", "949")));

        assertThat(images)
                .extracting(ImageCandidate::type, ImageCandidate::language, ImageCandidate::rating)
                .containsExactly(
                        tuple(ImageType.POSTER, "de", 3.0),
                        tuple(ImageType.FANART, null, 4.0),
                        tuple(ImageType.CLEARLOGO, "en", 7.0),
                        tuple(ImageType.CLEARLOGO, "de", 2.0),
                        tuple(ImageType.CLEARLOGO, "en", 1.0),
                        tuple(ImageType.CLEARART, "en", 0.0),
                        tuple(ImageType.DISCART, "en", null),
                        tuple(ImageType.BANNER, "en", 1.0),
                        tuple(ImageType.LANDSCAPE, "de", 2.0));
    }

    @Test
    void derivesPreviewFromImageUrl() {
        var logo = scraper.findImages(request(Map.of("tmdb", "949"))).get(2);

        assertThat(logo.url()).isEqualTo(URI.create("https://assets.fanart.tv/fanart/movies/949/hdmovielogo/heat-1.png"));
        assertThat(logo.previewUrl())
                .isEqualTo(URI.create("https://assets.fanart.tv/preview/movies/949/hdmovielogo/heat-1.png"));
    }

    @Test
    void sendsApiKeyAndOptionalClientKey() {
        scraper = initialized(new TestPluginContext().with("api-key", API_KEY).with("client-key", "persoenlich"));

        scraper.findImages(request(Map.of("tmdb", "949")));

        assertThat(fanart.requests().getFirst().query()).isEqualTo("api_key=" + API_KEY + "&client_key=persoenlich");
        assertThat(fanart.requests().getFirst().userAgent()).isEqualTo(TestPluginContext.USER_AGENT);
    }

    @Test
    void prefersTmdbIdAndFallsBackToImdbId() {
        scraper.findImages(request(Map.of("tmdb", "949", "imdb", "tt0113277")));
        scraper.findImages(request(Map.of("imdb", "tt0113277")));

        assertThat(fanart.requests()).extracting(FakeFanartTvServer.HttpExchangeRecord::path)
                .containsExactly("/v3/movies/949", "/v3/movies/tt0113277");
    }

    @Test
    void returnsNothingWithoutUsableIdOrForUnknownMovie() {
        assertThat(scraper.findImages(request(Map.of()))).isEmpty();
        assertThat(scraper.findImages(request(Map.of("tmdb", "../x")))).isEmpty();
        assertThat(scraper.findImages(request(Map.of("tmdb", "404")))).isEmpty();
        assertThat(fanart.requests()).hasSize(1);
    }

    @Test
    void findsMovieSetImagesByTheTmdbIdOfTheCollection() {
        assertThat(scraper.findImages(new ScrapeRequest(MediaType.MOVIE_SET, "Heat", null, Map.of("tmdb", "949"))))
                .isNotEmpty();
        assertThat(scraper.findImages(new ScrapeRequest(MediaType.MOVIE_SET, "Heat", null, Map.of("imdb", "tt0113277"))))
                .isEmpty();
    }

    @Test
    void returnsNothingForUnsupportedMediaType() {
        assertThat(scraper.findImages(new ScrapeRequest(MediaType.TV_EPISODE, "Pilot", null, Map.of("tvdb", "1"))))
                .isEmpty();
        assertThat(fanart.requests()).isEmpty();
    }

    @Test
    void findsShowAndSeasonImagesByTvdbId() {
        fanart.respond("/v3/tv/78020", "tv-78020.json");

        var images = scraper.findImages(new ScrapeRequest(MediaType.TV_SHOW, "ALF", null, Map.of("tvdb", "78020")));

        assertThat(images)
                .extracting(ImageCandidate::type, ImageCandidate::season, ImageCandidate::language)
                .containsExactly(
                        tuple(ImageType.POSTER, null, "en"),
                        tuple(ImageType.FANART, null, null),
                        tuple(ImageType.CLEARLOGO, null, "de"),
                        tuple(ImageType.LANDSCAPE, null, "en"),
                        tuple(ImageType.CHARACTERART, null, "en"),
                        tuple(ImageType.POSTER, 1, "en"),
                        tuple(ImageType.LANDSCAPE, 0, "en"));
        assertThat(images.get(4).url()).as("spaces are encoded")
                .hasToString("https://assets.fanart.tv/fanart/A_78020%20(0).png");
        assertThat(images.get(4).previewUrl()).hasToString("https://assets.fanart.tv/preview/A_78020%20(0).png");
        assertThat(scraper.findImages(new ScrapeRequest(MediaType.TV_SHOW, "ALF", null, Map.of("tmdb", "4658"))))
                .as("TV shows only via the TVDB ID").isEmpty();
        assertThat(scraper.findImages(new ScrapeRequest(MediaType.TV_SHOW, "ALF", null, Map.of("tvdb", "x"))))
                .isEmpty();
    }

    @Test
    void reportsMissingApiKey() {
        scraper = initialized(new TestPluginContext().with("api-key", " "));

        assertThat(scraper.missingConfiguration()).contains(FanartTvImageScraper.MISSING_API_KEY);
        assertThatThrownBy(() -> scraper.findImages(request(Map.of("tmdb", "949"))))
                .isInstanceOfSatisfying(ScraperException.class, e -> assertThat(e.localizedText())
                        .isEqualTo(LocalizedText.of("scraper.apiKeyMissing").with("provider", "fanart.tv")));
    }

    @Test
    void rejectsUseBeforeInitialization() {
        var uninitialized = new FanartTvImageScraper();

        assertThat(uninitialized.missingConfiguration()).isPresent();
        assertThatIllegalStateException().isThrownBy(() -> uninitialized.findImages(request(Map.of("tmdb", "949"))));
    }

    @ParameterizedTest(name = "HTTP {0}")
    @CsvSource(delimiter = '|', value = {"401 | scraper.apiKeyRejected", "403 | scraper.apiKeyRejected",
            "500 | scraper.httpError"})
    void translatesErrorResponses(int status, String expectedKey) {
        fanart.respondWithStatus("/v3/movies/949", status);

        assertThatThrownBy(() -> scraper.findImages(request(Map.of("tmdb", "949"))))
                .isInstanceOfSatisfying(ScraperException.class, e -> {
                    assertThat(e.localizedText().key()).isEqualTo(expectedKey);
                    assertThat(e.localizedText().args()).containsEntry("provider", "fanart.tv");
                });
    }

    @Test
    void reportsUnexpectedResponse() {
        fanart.respond("/v3/movies/949", "invalid.json");

        assertThatThrownBy(() -> scraper.findImages(request(Map.of("tmdb", "949"))))
                .isInstanceOfSatisfying(ScraperException.class, e -> assertThat(e.localizedText())
                        .isEqualTo(LocalizedText.of("scraper.unexpectedResponse").with("provider", "fanart.tv")));
    }

    @Test
    void reportsUnreachableServer() {
        URI unreachable = fanart.baseUrl();
        fanart.close();
        scraper = initialized(new TestPluginContext().with("api-key", API_KEY).with("base-url", unreachable.toString()));

        assertThatThrownBy(() -> scraper.findImages(request(Map.of("tmdb", "949"))))
                .isInstanceOfSatisfying(ScraperException.class, e ->
                        assertThat(e.localizedText().key()).isEqualTo("scraper.unreachable"))
                .hasMessageContaining("fanart.tv cannot be reached");
    }

    private FanartTvImageScraper initialized(TestPluginContext context) {
        if (context.setting("base-url").isEmpty()) {
            context.with("base-url", fanart.baseUrl().toString());
        }
        var fanartScraper = new FanartTvImageScraper();
        fanartScraper.initialize(context);
        return fanartScraper;
    }

    private static ScrapeRequest request(Map<String, String> uniqueIds) {
        return new ScrapeRequest(MediaType.MOVIE, "Heat", 1995, uniqueIds);
    }
}
