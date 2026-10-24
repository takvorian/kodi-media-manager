package de.tiktak.kmm;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

class ApplicationProfilesTest {

    private final YamlPropertySourceLoader loader = new YamlPropertySourceLoader();

    @Test
    void usesStandaloneProfileByDefault() throws IOException {
        assertThat(property("application.yml", "spring.profiles.default")).isEqualTo("standalone");
    }

    @Test
    void standaloneProfileUsesEmbeddedH2WithoutAuthentication() throws IOException {
        assertThat(property("application-standalone.yml", "spring.datasource.url")).asString().startsWith("jdbc:h2:file:");
        assertThat(property("application-standalone.yml", "kmm.security.enabled")).isEqualTo(false);
        assertThat(property("application-standalone.yml", "server.address")).asString().contains("127.0.0.1");
    }

    @Test
    void dockerProfileUsesMariaDbWithAuthentication() throws IOException {
        assertThat(property("application-docker.yml", "spring.datasource.url")).asString().startsWith("jdbc:mariadb://");
        assertThat(property("application-docker.yml", "kmm.security.enabled")).isEqualTo(true);
        assertThat(property("application-docker.yml", "kmm.home")).isEqualTo("/config");
    }

    @Test
    void readsDatabaseMigrationsPerVendor() throws IOException {
        assertThat(property("application.yml", "spring.flyway.locations")).isEqualTo("classpath:db/migration/{vendor}");
        assertThat(new ClassPathResource("db/migration/h2/V1__create_media_source.sql").exists()).isTrue();
        assertThat(new ClassPathResource("db/migration/mariadb/V1__create_media_source.sql").exists()).isTrue();
        assertThat(new ClassPathResource("db/migration/postgresql/V1__create_media_source.sql").exists()).isTrue();
    }

    private Object property(String file, String name) throws IOException {
        List<PropertySource<?>> sources = loader.load(file, new ClassPathResource(file));
        return sources.getFirst().getProperty(name);
    }
}
