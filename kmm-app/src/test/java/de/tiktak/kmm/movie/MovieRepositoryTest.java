package de.tiktak.kmm.movie;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import de.tiktak.kmm.plugin.api.MediaType;
import de.tiktak.kmm.scanner.DiscFormat;
import de.tiktak.kmm.source.MediaSource;

@DataJpaTest
class MovieRepositoryTest {

    @Autowired
    private MovieRepository repository;

    @Autowired
    private TestEntityManager entityManager;

    private long sourceId;

    @BeforeEach
    void setUp() {
        sourceId = entityManager.persistAndFlush(new MediaSource("Filme", "/media/filme", MediaType.MOVIE)).getId();
    }

    @Test
    void persistsMovieWithAddedTime() {
        Movie saved = repository.saveAndFlush(movie("/media/filme/Heat.mkv", "Heat"));
        entityManager.clear();

        assertThat(repository.findById(saved.getId())).hasValueSatisfying(movie -> {
            assertThat(movie.getAddedAt()).isNotNull();
            assertThat(movie.getDiscFormat()).isEqualTo(DiscFormat.FILE);
            assertThat(movie.getFilePathHash()).hasSize(64);
        });
    }

    @Test
    void findsMoviesOfSource() {
        long otherSourceId = entityManager.persistAndFlush(
                new MediaSource("Andere", "/media/andere", MediaType.MOVIE)).getId();
        repository.save(movie("/media/filme/Heat.mkv", "Heat"));
        repository.save(new Movie(otherSourceId, "/media/andere/Ronin.mkv", "Ronin", null, DiscFormat.FILE, 1));

        assertThat(repository.findBySourceId(sourceId)).extracting(Movie::getTitle).containsExactly("Heat");
    }

    @Test
    void searchesTitleCaseInsensitivelyWithPaging() {
        repository.save(movie("/media/filme/Heat.mkv", "Heat"));
        repository.save(movie("/media/filme/Heathers.mkv", "Heathers"));
        repository.save(movie("/media/filme/Ronin.mkv", "Ronin"));

        var page = repository.findByTitleContainingIgnoreCase("HEAT", PageRequest.of(0, 1, Sort.by("title")));

        assertThat(page.getTotalElements()).isEqualTo(2);
        assertThat(page.getContent()).extracting(Movie::getTitle).containsExactly("Heat");
    }

    @Test
    void rejectsSameFileTwice() {
        repository.saveAndFlush(movie("/media/filme/Heat.mkv", "Heat"));

        assertThatExceptionOfType(DataIntegrityViolationException.class)
                .isThrownBy(() -> repository.saveAndFlush(movie("/media/filme/Heat.mkv", "Heat 2")));
    }

    @Test
    void deletesMoviesTogetherWithTheirSource() {
        repository.saveAndFlush(movie("/media/filme/Heat.mkv", "Heat"));
        entityManager.clear();

        entityManager.remove(entityManager.find(MediaSource.class, sourceId));
        entityManager.flush();

        assertThat(repository.count()).isZero();
    }

    private Movie movie(String path, String title) {
        return new Movie(sourceId, path, title, null, DiscFormat.FILE, 1);
    }
}
