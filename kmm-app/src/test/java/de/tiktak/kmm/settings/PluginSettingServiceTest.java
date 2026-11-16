package de.tiktak.kmm.settings;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

import de.tiktak.kmm.i18n.LocalizedText;
import de.tiktak.kmm.plugin.api.SettingDefinition;

@DataJpaTest
@Import(PluginSettingService.class)
class PluginSettingServiceTest {

    private static final SettingDefinition API_KEY = SettingDefinition.secret("api-key", "API key");
    private static final SettingDefinition PIN = SettingDefinition.secret("pin", "PIN");
    private static final SettingDefinition COUNTRY = SettingDefinition.text("certification-country", "Country", "DE");
    private static final SettingDefinition DELAY = SettingDefinition.number("request-delay-ms", "Pause", 1000);
    private static final List<SettingDefinition> TVDB = List.of(API_KEY, PIN, COUNTRY);

    @Autowired
    private PluginSettingService service;

    @Test
    void seedsKeysFromTheConfigurationOnlyOnce() {
        assertThat(service.seedIfMissing("tmdb", "api-key", " 0123456789abcdef ")).isTrue();
        assertThat(service.seedIfMissing("tmdb", "api-key", "anderer-key")).isFalse();
        assertThat(service.seedIfMissing("tvdb", "api-key", " ")).isFalse();
        assertThat(service.seedIfMissing("tvdb", "pin", null)).isFalse();

        assertThat(service.overlay("tmdb", Map.of("api-key", "aus-der-umgebung", "language", "de-DE")))
                .containsEntry("api-key", "0123456789abcdef").containsEntry("language", "de-DE");
        assertThat(service.overlay("tvdb", Map.of())).isEmpty();
    }

    @Test
    void showsOnlyWhetherAKeyIsSetAndItsLastCharacters() {
        service.update("tvdb", TVDB, Map.of("api-key", "tvdb-key-1234", "pin", "1234"));

        assertThat(service.view("tvdb", TVDB, Map.of("certification-country", "AT"))).satisfiesExactly(
                key -> {
                    assertThat(key.set()).isTrue();
                    assertThat(key.hint()).isEqualTo("…1234");
                    assertThat(key.value()).isNull();
                    assertThat(key.label()).isEqualTo(LocalizedText.of("scraper.setting.tvdb.apiKey"));
                },
                pin -> {
                    assertThat(pin.set()).isTrue();
                    assertThat(pin.hint()).isNull();
                },
                country -> {
                    assertThat(country.set()).isFalse();
                    assertThat(country.value()).isNull();
                    assertThat(country.defaultValue()).isEqualTo("AT");
                });
    }

    @Test
    void keepsADeletedKeyDeleted() {
        service.update("tmdb", List.of(API_KEY), Map.of("api-key", "alt-0123456789"));
        Map<String, String> changes = new HashMap<>();
        changes.put("api-key", "");
        changes.put("pin", null);

        service.update("tmdb", List.of(API_KEY, PIN), changes);

        assertThat(service.seedIfMissing("tmdb", "api-key", "aus-der-umgebung")).isFalse();
        assertThat(service.overlay("tmdb", Map.of("api-key", "aus-der-umgebung", "pin", "konfiguriert")))
                .doesNotContainKey("api-key").containsEntry("pin", "konfiguriert");
    }

    @Test
    void returnsOtherSettingsToTheConfigurationWhenEmptied() {
        service.update("tvdb", TVDB, Map.of("certification-country", " US "));
        assertThat(service.overlay("tvdb", Map.of("certification-country", "DE")))
                .containsEntry("certification-country", "US");
        assertThat(service.view("tvdb", TVDB, Map.of()).get(2).value()).isEqualTo("US");

        service.update("tvdb", TVDB, Map.of("certification-country", ""));

        assertThat(service.overlay("tvdb", Map.of("certification-country", "DE")))
                .containsEntry("certification-country", "DE");
        assertThat(service.view("tvdb", TVDB, Map.of()).get(2).defaultValue()).isEqualTo("DE");
    }

    @Test
    void keepsTheSettingsOfThePluginsApart() {
        service.update("tmdb", List.of(API_KEY), Map.of("api-key", "tmdb-key"));

        assertThat(service.overlay("fanarttv", Map.of("api-key", "fanart"))).containsEntry("api-key", "fanart");
    }

    @Test
    void rejectsUnknownSettingsAndInvalidNumbers() {
        assertThatThrownBy(() -> service.update("ofdb", List.of(DELAY), Map.of("base-url", "http://x")))
                .isInstanceOf(InvalidSettingsException.class);
        assertThatThrownBy(() -> service.update("ofdb", List.of(DELAY), Map.of("request-delay-ms", "-5")))
                .isInstanceOf(InvalidSettingsException.class);
        assertThatThrownBy(() -> service.update("tmdb", List.of(API_KEY), Map.of("api-key", "x".repeat(513))))
                .isInstanceOf(InvalidSettingsException.class);

        service.update("ofdb", List.of(DELAY), Map.of("request-delay-ms", "2500"));
        assertThat(service.overlay("ofdb", Map.of())).containsEntry("request-delay-ms", "2500");
    }

    @Test
    void namesTheSettingsOfOtherPluginsWithTheirOwnLabel() {
        assertThat(service.view("fremd", List.of(SettingDefinition.text("token", "Access token", null)), Map.of()))
                .singleElement()
                .satisfies(view -> assertThat(view.label()).isEqualTo(LocalizedText.literal("Access token")));
    }
}
