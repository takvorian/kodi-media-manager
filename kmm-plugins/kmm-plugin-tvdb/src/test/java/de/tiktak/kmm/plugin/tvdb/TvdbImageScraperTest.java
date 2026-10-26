package de.tiktak.kmm.plugin.tvdb;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.time.Clock;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import de.tiktak.kmm.plugin.api.ImageCandidate;
import de.tiktak.kmm.plugin.api.ImageType;
import de.tiktak.kmm.plugin.api.MediaType;
import de.tiktak.kmm.plugin.api.ScrapeRequest;

class TvdbImageScraperTest {

    private FakeTvdbServer tvdb;
    private TvdbImageScraper scraper;

    @BeforeEach
    void setUp() {
        tvdb = new FakeTvdbServer()
                .respond("/v4/series/73739/extended", "series-73739.json")
                .respond("/v4/search/remoteid/tt0411008", "remoteid-tt0411008.json");
        scraper = new TvdbImageScraper(Clock.systemUTC());
        scraper.initialize(new TestPluginContext().with("api-key", "tvdb-test-key")
                .with("base-url", tvdb.baseUrl().toString()));
    }

    @AfterEach
    void tearDown() {
        tvdb.close();
    }

    @Test
    void describesItselfWithAttribution() {
        assertThat(scraper.id()).isEqualTo("tvdb");
        assertThat(scraper.supportedMediaTypes()).containsExactly(MediaType.TV_SHOW);
        assertThat(scraper.attribution()).isPresent();
    }

    @Test
    void findsShowAndSeasonImagesWithIsoLanguages() {
        var images = scraper.findImages(request(Map.of("tvdb", "73739")));

        assertThat(images)
                .extracting(ImageCandidate::type, ImageCandidate::season, ImageCandidate::language)
                .containsExactly(
                        tuple(ImageType.POSTER, null, "en"),
                        tuple(ImageType.FANART, null, null),
                        tuple(ImageType.POSTER, 1, "de"));
        assertThat(images.getFirst()).satisfies(poster -> {
            assertThat(poster.previewUrl()).hasToString("https://artworks.thetvdb.com/banners/posters/73739-1_t.jpg");
            assertThat(poster.width()).isEqualTo(680);
            assertThat(poster.rating()).isEqualTo(100006.0);
        });
        assertThat(images.get(1).previewUrl()).as("ohne Vorschau das Bild selbst").isEqualTo(images.get(1).url());
    }

    @Test
    void resolvesTheSeriesThroughOtherIds() {
        assertThat(scraper.findImages(request(Map.of("imdb", "tt0411008")))).hasSize(3);
        assertThat(scraper.findImages(request(Map.of()))).isEmpty();
        assertThat(scraper.findImages(new ScrapeRequest(MediaType.MOVIE, "Heat", null, Map.of("tvdb", "73739"))))
                .isEmpty();
    }

    private static ScrapeRequest request(Map<String, String> ids) {
        return new ScrapeRequest(MediaType.TV_SHOW, "Lost", null, ids);
    }
}
