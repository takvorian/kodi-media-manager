package de.tiktak.kmm.source;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mariadb.MariaDBContainer;

import de.tiktak.kmm.plugin.api.MediaType;

/**
 * Prüft die MariaDB-Migrationen und das Mapping gegen eine echte MariaDB. Wird ohne Docker übersprungen.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
class MariaDbMigrationTest {

    private static final String MARIADB_IMAGE = "mariadb:11.8";

    @Container
    @ServiceConnection
    static final MariaDBContainer MARIADB = new MariaDBContainer(MARIADB_IMAGE);

    @Autowired
    private MediaSourceRepository repository;

    @Test
    void migratesSchemaAndPersistsMediaSource() {
        MediaSource saved = repository.saveAndFlush(new MediaSource("Filme", "/media/filme", MediaType.MOVIE));

        assertThat(repository.findById(saved.getId())).hasValueSatisfying(
                source -> assertThat(source.getCreatedAt()).isNotNull());
    }
}
