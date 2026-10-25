package de.tiktak.kmm.movie;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import org.junit.jupiter.api.Test;

import de.tiktak.kmm.scanner.DiscFormat;

class MovieTest {

    private static final String PATH = "/media/filme/Heat.mkv";

    @Test
    void derivesPathHashFromFilePath() {
        var movie = new Movie(1L, PATH, "Heat", 1995, DiscFormat.FILE, 1);

        assertThat(movie.getFilePathHash()).isEqualTo(FilePathHash.of(PATH));
        assertThat(movie.getTitle()).isEqualTo("Heat");
        assertThat(movie.getReleaseYear()).isEqualTo(1995);
    }

    @Test
    void rejectsMissingFilePath() {
        assertThatNullPointerException()
                .isThrownBy(() -> new Movie(1L, null, "Heat", null, DiscFormat.FILE, 1))
                .withMessageContaining("filePath");
    }

    @Test
    void rejectsMissingTitle() {
        assertThatNullPointerException()
                .isThrownBy(() -> new Movie(1L, PATH, null, null, DiscFormat.FILE, 1))
                .withMessageContaining("title");
    }

    @Test
    void rejectsMissingDiscFormat() {
        assertThatNullPointerException()
                .isThrownBy(() -> new Movie(1L, PATH, "Heat", null, null, 1))
                .withMessageContaining("discFormat");
    }
}
