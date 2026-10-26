package de.tiktak.kmm.scanner;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class TvShowFilesTest {

    private static final Path SHOW = Path.of("serien", "ALF (1986)").toAbsolutePath();
    private static final Path EPISODE = SHOW.resolve("Staffel 1").resolve("ALF - S01E02 - Der Fremde.avi");

    @Test
    void namesShowFiles() {
        assertThat(TvShowFiles.tvShowNfo(SHOW)).isEqualTo(SHOW.resolve("tvshow.nfo"));
        assertThat(TvShowFiles.tvShowArtworkCandidates(SHOW, "poster"))
                .containsExactly(SHOW.resolve("poster.jpg"), SHOW.resolve("poster.jpeg"), SHOW.resolve("poster.png"));
    }

    @Test
    void namesSeasonArtwork() {
        assertThat(TvShowFiles.seasonArtworkCandidates(SHOW, 1, "poster")).first()
                .isEqualTo(SHOW.resolve("season01-poster.jpg"));
        assertThat(TvShowFiles.seasonArtworkCandidates(SHOW, 12, "fanart")).first()
                .isEqualTo(SHOW.resolve("season12-fanart.jpg"));
        assertThat(TvShowFiles.seasonArtworkCandidates(SHOW, 0, "poster")).first()
                .isEqualTo(SHOW.resolve("season-specials-poster.jpg"));
        assertThat(TvShowFiles.seasonArtworkCandidates(SHOW, null, "banner")).first()
                .isEqualTo(SHOW.resolve("season-all-banner.jpg"));
    }

    @Test
    void namesEpisodeFilesAfterTheVideoFile() {
        assertThat(TvShowFiles.episodeNfo(EPISODE)).isEqualTo(EPISODE.resolveSibling("ALF - S01E02 - Der Fremde.nfo"));
        assertThat(TvShowFiles.episodeThumbCandidates(EPISODE)).first()
                .isEqualTo(EPISODE.resolveSibling("ALF - S01E02 - Der Fremde-thumb.jpg"));
        assertThat(TvShowFiles.episodeNfo(SHOW.resolve("S01E01"))).isEqualTo(SHOW.resolve("S01E01.nfo"));
    }
}
