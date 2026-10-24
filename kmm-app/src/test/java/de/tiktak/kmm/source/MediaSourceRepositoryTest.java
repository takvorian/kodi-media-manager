package de.tiktak.kmm.source;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;

import de.tiktak.kmm.plugin.api.MediaType;

@DataJpaTest
class MediaSourceRepositoryTest {

    @Autowired
    private MediaSourceRepository repository;

    @Test
    void persistsMediaSourceWithGeneratedIdAndCreationTime() {
        MediaSource saved = repository.saveAndFlush(new MediaSource("Filme", "/media/filme", MediaType.MOVIE));

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(repository.findByName("Filme")).hasValueSatisfying(source -> {
            assertThat(source.getPath()).isEqualTo("/media/filme");
            assertThat(source.getMediaType()).isEqualTo(MediaType.MOVIE);
        });
    }

    @Test
    void findsSourcesByMediaTypeSortedByName() {
        repository.save(new MediaSource("Serien", "/media/serien", MediaType.TV_SHOW));
        repository.save(new MediaSource("Kinderfilme", "/media/kinder", MediaType.MOVIE));
        repository.save(new MediaSource("Filme", "/media/filme", MediaType.MOVIE));

        assertThat(repository.findByMediaTypeOrderByNameAsc(MediaType.MOVIE))
                .extracting(MediaSource::getName)
                .containsExactly("Filme", "Kinderfilme");
    }

    @Test
    void rejectsDuplicateName() {
        repository.saveAndFlush(new MediaSource("Filme", "/media/filme", MediaType.MOVIE));

        assertThatExceptionOfType(DataIntegrityViolationException.class).isThrownBy(
                () -> repository.saveAndFlush(new MediaSource("Filme", "/media/andere", MediaType.MOVIE)));
    }
}
