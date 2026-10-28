package de.tiktak.kmm.plugin.tmdb;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import java.net.URI;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import de.tiktak.kmm.plugin.api.ImageCandidate;
import de.tiktak.kmm.plugin.api.ImageType;
import de.tiktak.kmm.plugin.api.MediaType;
import de.tiktak.kmm.plugin.api.ScrapeRequest;
import de.tiktak.kmm.plugin.api.ScraperException;

class TmdbImageScraperTest {

    private static final String API_KEY = "0123456789abcdef0123456789abcdef";

    private FakeTmdbServer tmdb;
    private TmdbImageScraper scraper;

    @BeforeEach
    void setUp() {
        tmdb = new FakeTmdbServer()
                .respond("/3/movie/949/images", "images-949.json")
                .respond("/3/find/tt0113277", "find-tt0113277.json");
        scraper = new TmdbImageScraper();
        scraper.initialize(new TestPluginContext().with("api-key", API_KEY).with("base-url", tmdb.baseUrl().toString()));
    }

    @AfterEach
    void tearDown() {
        tmdb.close();
    }

    @Test
    void findsPostersFanartAndLogosByTmdbId() {
        var images = scraper.findImages(request(Map.of("tmdb", "949")));

        assertThat(images)
                .extracting(ImageCandidate::type, ImageCandidate::language, ImageCandidate::rating)
                .containsExactly(
                        tuple(ImageType.POSTER, "de", 5.4),
                        tuple(ImageType.POSTER, "en", 5.9),
                        tuple(ImageType.FANART, null, 6.1),
                        tuple(ImageType.CLEARLOGO, "en", 0.0));
        assertThat(images.getFirst()).satisfies(poster -> {
            assertThat(poster.url()).isEqualTo(URI.create("https://image.tmdb.org/t/p/original/poster-de.jpg"));
            assertThat(poster.previewUrl()).isEqualTo(URI.create("https://image.tmdb.org/t/p/w342/poster-de.jpg"));
            assertThat(poster.width()).isEqualTo(1000);
            assertThat(poster.height()).isEqualTo(1500);
        });
        assertThat(images.get(2).previewUrl()).hasToString("https://image.tmdb.org/t/p/w780/backdrop.jpg");
        assertThat(tmdb.requests().getFirst().query()).contains("include_image_language=de%2Cen%2Cnull");
    }

    @Test
    void resolvesTmdbIdThroughImdbId() {
        assertThat(scraper.findImages(request(Map.of("imdb", "tt0113277")))).hasSize(4);
        assertThat(tmdb.requests()).extracting(FakeTmdbServer.HttpExchangeRecord::path)
                .containsExactly("/3/find/tt0113277", "/3/movie/949/images");
    }

    @Test
    void returnsNothingWithoutUsableId() {
        assertThat(scraper.findImages(request(Map.of()))).isEmpty();
        assertThat(scraper.findImages(request(Map.of("tmdb", "../x")))).isEmpty();
        assertThat(scraper.findImages(request(Map.of("tmdb", "404")))).isEmpty();
    }

    @Test
    void findsTheImagesOfACollection() {
        assertThat(scraper.supportedMediaTypes()).containsExactlyInAnyOrder(MediaType.MOVIE, MediaType.MOVIE_SET,
                MediaType.TV_SHOW);
        tmdb.respond("/3/collection/2344/images", "images-collection-2344.json");

        assertThat(scraper.findImages(new ScrapeRequest(MediaType.MOVIE_SET, "Matrix", null, Map.of("tmdb", "2344"))))
                .extracting(ImageCandidate::type).containsExactly(ImageType.POSTER, ImageType.FANART);
        assertThat(scraper.findImages(new ScrapeRequest(MediaType.MOVIE_SET, "Matrix", null, Map.of()))).isEmpty();
    }

    @Test
    void returnsNothingForUnsupportedMediaType() {
        assertThat(scraper.findImages(new ScrapeRequest(MediaType.TV_EPISODE, "Pilot", null, Map.of("tmdb", "1"))))
                .isEmpty();
        assertThat(tmdb.requests()).isEmpty();
    }

    @Test
    void findsShowImagesAndSeasonPosters() {
        tmdb.respond("/3/find/73739", "find-tvdb-73739.json")
                .respond("/3/tv/4607/images", "images-tv-4607.json")
                .respond("/3/tv/4607", "tv-4607.json")
                .respond("/3/tv/4607/season/1/images", "images-tv-4607-season-1.json");

        var images = scraper.findImages(new ScrapeRequest(MediaType.TV_SHOW, "Lost", null, Map.of("tvdb", "73739")));

        assertThat(images)
                .extracting(ImageCandidate::type, ImageCandidate::season, ImageCandidate::language)
                .containsExactly(
                        tuple(ImageType.POSTER, null, "de"),
                        tuple(ImageType.FANART, null, null),
                        tuple(ImageType.POSTER, 1, "en"));
        assertThat(images.get(2).url()).hasToString("https://image.tmdb.org/t/p/original/lost-s1.jpg");
        assertThat(tmdb.requests()).extracting(FakeTmdbServer.HttpExchangeRecord::path)
                .contains("/3/tv/4607/season/0/images", "/3/tv/4607/season/2/images");
    }

    @Test
    void requiresApiKey() {
        var unconfigured = new TmdbImageScraper();
        unconfigured.initialize(new TestPluginContext());

        assertThatThrownBy(() -> unconfigured.findImages(request(Map.of("tmdb", "949"))))
                .isInstanceOf(ScraperException.class);
    }

    @Test
    void usesImageLanguageWithoutRegion() {
        scraper.initialize(new TestPluginContext().with("api-key", API_KEY).with("language", "fr-CA")
                .with("base-url", tmdb.baseUrl().toString()));

        scraper.findImages(request(Map.of("tmdb", "949")));

        assertThat(tmdb.requests().getFirst().query()).contains("include_image_language=fr%2Cen%2Cnull");
    }

    private static ScrapeRequest request(Map<String, String> uniqueIds) {
        return new ScrapeRequest(MediaType.MOVIE, "Heat", 1995, uniqueIds);
    }
}
