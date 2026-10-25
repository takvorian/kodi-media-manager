package de.tiktak.kmm.movie;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.time.Instant;
import java.util.List;

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
    void storesAndReturnsSupplement() {
        var movie = new Movie(1L, PATH, "Heat", 1995, DiscFormat.FILE, 1);
        var set = new MovieSet("Mann Collection");
        var supplement = new MovieSupplement(List.of(new MovieSupplement.Actor("Pacino", "Hanna", null)),
                List.of("Michael Mann"), List.of("Mann", "Scott"), List.of("Warner"), List.of("USA"), List.of("Heist"),
                List.of(new MovieSupplement.Rating("imdb", 8.3, 10, 650000, true)),
                new MovieSupplement.SetReference("Mann Collection", null), 9, 2, Instant.parse("2024-05-01T18:15:00Z"));

        movie.applySupplement(supplement, set);

        assertThat(movie.supplement()).isEqualTo(supplement);
        assertThat(movie.isSupplementKnown()).isTrue();
        assertThat(movie.getMovieSet()).isSameAs(set);
    }

    @Test
    void keepsSetOrderWhileTheSetStaysTheSame() {
        var movie = new Movie(1L, PATH, "Heat", 1995, DiscFormat.FILE, 1);
        var set = new MovieSet("Mann Collection");
        movie.assignSet(set, 3);

        movie.applySupplement(MovieSupplement.EMPTY, set);
        assertThat(movie.getSetOrder()).isEqualTo(3);

        movie.applySupplement(MovieSupplement.EMPTY, null);
        assertThat(movie.getMovieSet()).isNull();
        assertThat(movie.getSetOrder()).isNull();
    }

    @Test
    void keepsExplicitAddedTimeWhenPersisted() {
        var movie = new Movie(1L, PATH, "Heat", 1995, DiscFormat.FILE, 1);
        movie.changeAddedAt(Instant.parse("2016-11-30T00:20:15Z"));

        movie.onCreate();

        assertThat(movie.getAddedAt()).isEqualTo(Instant.parse("2016-11-30T00:20:15Z"));
        assertThat(movie.isSupplementKnown()).isFalse();
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
