package de.tiktak.kmm.source;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;

import de.tiktak.kmm.plugin.api.MediaType;

@DataJpaTest
class MediaSourceRepositoryTest {

    @Autowired
    private MediaSourceRepository repository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    void persistsMediaSourceWithGeneratedIdAndCreationTime() {
        MediaSource saved = repository.saveAndFlush(new MediaSource("Filme", "/media/filme", MediaType.MOVIE));

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getLastScannedAt()).isNull();
        entityManager.clear();
        assertThat(repository.findById(saved.getId())).hasValueSatisfying(source -> {
            assertThat(source.getPath()).isEqualTo("/media/filme");
            assertThat(source.getMediaType()).isEqualTo(MediaType.MOVIE);
        });
    }

    @Test
    void persistsScanTime() {
        MediaSource source = repository.saveAndFlush(new MediaSource("Filme", "/media/filme", MediaType.MOVIE));
        Instant scannedAt = Instant.parse("2026-10-25T10:15:30Z");

        source.markScanned(scannedAt);
        repository.flush();
        entityManager.clear();

        assertThat(repository.findById(source.getId()).orElseThrow().getLastScannedAt()).isEqualTo(scannedAt);
    }

    @Test
    void checksNameExistenceIgnoringCase() {
        repository.save(new MediaSource("Filme", "/media/filme", MediaType.MOVIE));

        assertThat(repository.existsByNameIgnoreCase("FILME")).isTrue();
        assertThat(repository.existsByNameIgnoreCase("Serien")).isFalse();
    }

    @Test
    void rejectsDuplicateName() {
        repository.saveAndFlush(new MediaSource("Filme", "/media/filme", MediaType.MOVIE));

        assertThatExceptionOfType(DataIntegrityViolationException.class).isThrownBy(
                () -> repository.saveAndFlush(new MediaSource("Filme", "/media/andere", MediaType.MOVIE)));
    }
}
