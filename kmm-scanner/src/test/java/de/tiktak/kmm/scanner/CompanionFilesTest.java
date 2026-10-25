package de.tiktak.kmm.scanner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class CompanionFilesTest {

    private static final Path MOVIE_DIRECTORY = Path.of("library", "Heat (1995)").toAbsolutePath();

    @Test
    void usesFileNameForSingleFile() {
        var files = CompanionFiles.of(MOVIE_DIRECTORY.resolve("Heat (1995).mkv"), DiscFormat.FILE, 1);

        assertThat(files.nfoFile()).isEqualTo(MOVIE_DIRECTORY.resolve("Heat (1995).nfo"));
        assertThat(files.nfoCandidates()).containsExactly(
                MOVIE_DIRECTORY.resolve("Heat (1995).nfo"), MOVIE_DIRECTORY.resolve("movie.nfo"));
    }

    @Test
    void stripsPartMarkerForStackedFiles() {
        var files = CompanionFiles.of(MOVIE_DIRECTORY.resolve("Heat (1995)-cd1.avi"), DiscFormat.FILE, 2);

        assertThat(files.nfoFile()).isEqualTo(MOVIE_DIRECTORY.resolve("Heat (1995).nfo"));
    }

    @Test
    void keepsPartLikeTitleForSingleFile() {
        var files = CompanionFiles.of(MOVIE_DIRECTORY.resolve("Kill Bill Part 2.mkv"), DiscFormat.FILE, 1);

        assertThat(files.nfoFile().getFileName()).hasToString("Kill Bill Part 2.nfo");
    }

    @Test
    void placesDvdNfoInsideVideoTsAndFolderNfoInMovieDirectory() {
        var files = CompanionFiles.of(MOVIE_DIRECTORY.resolve("VIDEO_TS").resolve("VIDEO_TS.IFO"), DiscFormat.DVD, 1);

        assertThat(files.nfoCandidates()).containsExactly(
                MOVIE_DIRECTORY.resolve("VIDEO_TS").resolve("VIDEO_TS.nfo"), MOVIE_DIRECTORY.resolve("movie.nfo"));
    }

    @Test
    void placesBluRayNfoInsideBdmv() {
        var files = CompanionFiles.of(MOVIE_DIRECTORY.resolve("BDMV").resolve("index.bdmv"), DiscFormat.BLURAY, 1);

        assertThat(files.nfoFile()).isEqualTo(MOVIE_DIRECTORY.resolve("BDMV").resolve("index.nfo"));
    }

    @Test
    void handlesFileWithoutExtension() {
        var files = CompanionFiles.of(MOVIE_DIRECTORY.resolve("Heat"), DiscFormat.FILE, 2);

        assertThat(files.nfoFile().getFileName()).hasToString("Heat.nfo");
    }

    @Test
    void namesArtworkAfterTheMovieFile() {
        var files = CompanionFiles.of(MOVIE_DIRECTORY.resolve("Heat (1995)-cd1.avi"), DiscFormat.FILE, 2);

        assertThat(files.artworkFile("poster", "jpg")).isEqualTo(MOVIE_DIRECTORY.resolve("Heat (1995)-poster.jpg"));
        assertThat(files.artworkCandidates("clearlogo")).containsExactly(
                MOVIE_DIRECTORY.resolve("Heat (1995)-clearlogo.jpg"),
                MOVIE_DIRECTORY.resolve("Heat (1995)-clearlogo.jpeg"),
                MOVIE_DIRECTORY.resolve("Heat (1995)-clearlogo.png"));
    }

    @Test
    void placesDiscArtworkInTheMovieDirectory() {
        var dvd = CompanionFiles.of(MOVIE_DIRECTORY.resolve("VIDEO_TS").resolve("VIDEO_TS.IFO"), DiscFormat.DVD, 1);
        var bluray = CompanionFiles.of(MOVIE_DIRECTORY.resolve("BDMV").resolve("index.bdmv"), DiscFormat.BLURAY, 1);

        assertThat(dvd.artworkFile("poster", "jpg")).isEqualTo(MOVIE_DIRECTORY.resolve("poster.jpg"));
        assertThat(bluray.artworkFile("fanart", "png")).isEqualTo(MOVIE_DIRECTORY.resolve("fanart.png"));
    }

    @Test
    void comparesByValue() {
        Path file = MOVIE_DIRECTORY.resolve("Heat.mkv");

        assertThat(CompanionFiles.of(file, DiscFormat.FILE, 1))
                .isEqualTo(CompanionFiles.of(file, DiscFormat.FILE, 1))
                .hasSameHashCodeAs(CompanionFiles.of(file, DiscFormat.FILE, 1))
                .isNotEqualTo(CompanionFiles.of(MOVIE_DIRECTORY.resolve("Ronin.mkv"), DiscFormat.FILE, 1));
    }

    @Test
    void rejectsMissingArguments() {
        assertThatNullPointerException().isThrownBy(() -> CompanionFiles.of(null, DiscFormat.FILE, 1));
        assertThatNullPointerException().isThrownBy(() -> CompanionFiles.of(MOVIE_DIRECTORY, null, 1));
    }

    @Test
    void keepsSpaceBeforeTheExtensionOfTheMovieFile() {
        CompanionFiles files = CompanionFiles.of(Path.of("Filme", "Film .mkv"), DiscFormat.FILE, 1);

        assertThat(files.nfoFile().getFileName().toString()).isEqualTo("Film .nfo");
        assertThat(files.artworkFile("poster", "jpg").getFileName().toString()).isEqualTo("Film -poster.jpg");
    }
}
