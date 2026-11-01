package de.tiktak.kmm.tvshow;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

class TvShowFillFromNameTest {

    private static final String IMDB_ID = "tt0090390";

    @Test
    void fillsMissingYearAndImdbId() {
        TvShow show = show();

        show.fillMissingFromName(1986, IMDB_ID);

        assertThat(show.metadata().year()).isEqualTo(1986);
        assertThat(show.metadata().imdbId()).isEqualTo(IMDB_ID);
    }

    @Test
    void keepsExistingValues() {
        TvShow show = new TvShow(1L, "/serien/ALF", "ALF", 1987);
        show.fillMissingFromName(null, "tt0000001");

        show.fillMissingFromName(1986, IMDB_ID);

        assertThat(show.metadata().year()).isEqualTo(1987);
        assertThat(show.metadata().imdbId()).isEqualTo("tt0000001");
    }

    @Test
    void leavesAnIdentifiedShowAlone() {
        TvShow show = show();
        show.applyMetadata(identified("78020", null));

        show.fillMissingFromName(1986, IMDB_ID);

        assertThat(show.metadata().year()).isNull();
        assertThat(show.metadata().imdbId()).isNull();
    }

    @Test
    void leavesAShowIdentifiedAtTmdbAlone() {
        TvShow show = show();
        show.applyMetadata(identified(null, "2293"));

        show.fillMissingFromName(1986, IMDB_ID);

        assertThat(show.metadata().imdbId()).isNull();
    }

    private static TvShow show() {
        return new TvShow(1L, "/serien/ALF", "ALF", null);
    }

    private static TvShowMetadata identified(String tvdbId, String tmdbId) {
        return new TvShowMetadata("ALF", null, null, null, null, null, null, null, null, List.of(), List.of(),
                List.of(), tvdbId, tmdbId, null, null);
    }
}
