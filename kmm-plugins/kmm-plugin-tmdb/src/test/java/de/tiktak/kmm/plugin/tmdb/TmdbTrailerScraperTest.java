package de.tiktak.kmm.plugin.tmdb;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import de.tiktak.kmm.plugin.api.MediaType;
import de.tiktak.kmm.plugin.api.ScrapeRequest;
import de.tiktak.kmm.plugin.api.TrailerCandidate;

class TmdbTrailerScraperTest {

    private static final String API_KEY = "0123456789abcdef0123456789abcdef";

    private FakeTmdbServer tmdb;
    private TmdbTrailerScraper scraper;

    @BeforeEach
    void setUp() {
        tmdb = new FakeTmdbServer()
                .respond("/3/movie/949/videos", "videos-949.json")
                .respond("/3/find/tt0113277", "find-tt0113277.json")
                .respond("/3/tv/4607/videos", "videos-tv-4607.json")
                .respond("/3/find/73739", "find-tvdb-73739.json");
        scraper = new TmdbTrailerScraper();
        scraper.initialize(new TestPluginContext().with("api-key", API_KEY).with("base-url", tmdb.baseUrl().toString()));
    }

    @AfterEach
    void tearDown() {
        tmdb.close();
    }

    @Test
    void findsYouTubeTrailersAsKodiLinks() {
        var trailers = scraper.findTrailers(request(Map.of("tmdb", "949")));

        assertThat(trailers).hasSize(2);
        assertThat(trailers.getFirst()).isEqualTo(new TrailerCandidate("HEAT | Trailer | Deutsch",
                "plugin://plugin.video.youtube/?action=play_video&videoid=2GfZl4kuVNI",
                URI.create("https://www.youtube.com/watch?v=2GfZl4kuVNI"),
                URI.create("https://img.youtube.com/vi/2GfZl4kuVNI/mqdefault.jpg"), "de", "Trailer", true, 1080,
                "2015-06-01"));
        assertThat(trailers.get(1)).satisfies(teaser -> {
            assertThat(teaser.type()).isEqualTo("Teaser");
            assertThat(teaser.official()).isFalse();
            assertThat(teaser.published()).isNull();
        });
        assertThat(tmdb.requests().getFirst().query()).contains("include_video_language=de%2Cen%2Cnull");
    }

    @Test
    void findsTheMovieByImdbIdAndReturnsNothingForOtherMedia() {
        assertThat(scraper.findTrailers(request(Map.of("imdb", "tt0113277")))).hasSize(2);
        assertThat(scraper.findTrailers(request(Map.of()))).isEmpty();
        assertThat(scraper.findTrailers(new ScrapeRequest(MediaType.TV_EPISODE, "Pilot", null,
                Map.of("tmdb", "4607")))).isEmpty();
        assertThat(scraper.supportedMediaTypes()).containsExactlyInAnyOrder(MediaType.MOVIE, MediaType.TV_SHOW);
    }

    @Test
    void findsTvShowTrailersInTheTvLanguage() {
        var trailers = scraper.findTrailers(tvRequest(Map.of("tmdb", "4607")));

        assertThat(trailers).extracting(TrailerCandidate::kodiUrl)
                .containsExactly("plugin://plugin.video.youtube/?action=play_video&videoid=KTu8iDynwNc");
        assertThat(trailers.getFirst().language()).isEqualTo("en");
        assertThat(tmdb.requests().getFirst().query())
                .contains("include_video_language=de%2Cen%2Cnull")
                .contains("language=de-DE");
    }

    @Test
    void findsTheTvShowByTvdbId() {
        assertThat(scraper.findTrailers(tvRequest(Map.of("tvdb", "73739")))).hasSize(1);
        assertThat(tmdb.requests()).extracting(FakeTmdbServer.HttpExchangeRecord::path)
                .containsExactly("/3/find/73739", "/3/tv/4607/videos");
    }

    private static ScrapeRequest tvRequest(Map<String, String> ids) {
        return new ScrapeRequest(MediaType.TV_SHOW, "Lost", 2004, ids);
    }

    private static ScrapeRequest request(Map<String, String> ids) {
        return new ScrapeRequest(MediaType.MOVIE, "Heat", 1995, ids);
    }
}
