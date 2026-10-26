package de.tiktak.kmm.settings;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

@DataJpaTest
@Import(ApiKeyService.class)
class ApiKeyServiceTest {

    @Autowired
    private ApiKeyService service;

    @Test
    void seedsKeysFromTheConfigurationOnlyOnce() {
        assertThat(service.seedIfMissing(ApiKey.TMDB_API_KEY, " 0123456789abcdef ")).isTrue();
        assertThat(service.seedIfMissing(ApiKey.TMDB_API_KEY, "anderer-key")).isFalse();
        assertThat(service.seedIfMissing(ApiKey.TVDB_API_KEY, " ")).isFalse();
        assertThat(service.seedIfMissing(ApiKey.TVDB_PIN, null)).isFalse();

        assertThat(service.overlay("tmdb", Map.of("api-key", "aus-der-umgebung", "language", "de-DE")))
                .containsEntry("api-key", "0123456789abcdef").containsEntry("language", "de-DE");
    }

    @Test
    void showsOnlyWhetherAKeyIsSetAndItsLastCharacters() {
        service.update(Map.of(ApiKey.FANARTTV_API_KEY, "fanart-key-1234", ApiKey.TVDB_PIN, "1234"));

        assertThat(service.view()).hasSize(ApiKey.values().length).anySatisfy(view -> {
            assertThat(view.key()).isEqualTo(ApiKey.FANARTTV_API_KEY);
            assertThat(view.set()).isTrue();
            assertThat(view.hint()).isEqualTo("…1234");
        }).anySatisfy(view -> {
            assertThat(view.key()).isEqualTo(ApiKey.TVDB_PIN);
            assertThat(view.hint()).isNull();
        }).anySatisfy(view -> {
            assertThat(view.key()).isEqualTo(ApiKey.TMDB_API_KEY);
            assertThat(view.set()).isFalse();
        });
    }

    @Test
    void keepsADeletedKeyDeleted() {
        service.update(Map.of(ApiKey.TMDB_API_KEY, "alt-0123456789"));
        Map<ApiKey, String> changes = new HashMap<>();
        changes.put(ApiKey.TMDB_API_KEY, "");
        changes.put(ApiKey.FANARTTV_API_KEY, null);

        service.update(changes);

        assertThat(service.seedIfMissing(ApiKey.TMDB_API_KEY, "aus-der-umgebung")).isFalse();
        assertThat(service.overlay("tmdb", Map.of("api-key", "aus-der-umgebung"))).doesNotContainKey("api-key");
        assertThat(service.overlay("fanarttv", Map.of("api-key", "konfiguriert")))
                .containsEntry("api-key", "konfiguriert");
    }
}
