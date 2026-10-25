package de.tiktak.kmm;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import de.tiktak.kmm.plugin.api.MediaType;
import de.tiktak.kmm.source.MediaSource;

/**
 * Prüft die PostgreSQL-Migrationen gegen ein echtes PostgreSQL. Das Mapping aller Entities validiert
 * Hibernate beim Start ({@code ddl-auto=validate}). Wird ohne Docker übersprungen.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
class PostgreSqlSchemaTest {

    private static final String POSTGRES_IMAGE = "postgres:17";
    private static final String INSERT_MOVIE = """
            INSERT INTO movie (source_id, file_path, file_path_hash, title, disc_format, part_count, added_at)
            VALUES (?, '/media/filme/Heat.mkv', REPEAT('a', 64), 'Heat', 'FILE', 1, NOW())
            """;
    private static final String COUNT_MOVIES = "SELECT COUNT(*) FROM movie";

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(POSTGRES_IMAGE);

    @Autowired
    private TestEntityManager entityManager;

    @Test
    void persistsMediaSource() {
        MediaSource saved = entityManager.persistFlushFind(new MediaSource("Filme", "/media/filme", MediaType.MOVIE));

        assertThat(saved.getCreatedAt()).isNotNull();
    }

    @Test
    void deletesMoviesTogetherWithTheirSource() {
        MediaSource source = entityManager.persistAndFlush(new MediaSource("Filme", "/media/filme", MediaType.MOVIE));
        entityManager.getEntityManager().createNativeQuery(INSERT_MOVIE).setParameter(1, source.getId()).executeUpdate();
        assertThat(countMovies()).isEqualTo(1);

        entityManager.remove(source);
        entityManager.flush();

        assertThat(countMovies()).isZero();
    }

    private long countMovies() {
        return ((Number) entityManager.getEntityManager().createNativeQuery(COUNT_MOVIES).getSingleResult()).longValue();
    }
}
