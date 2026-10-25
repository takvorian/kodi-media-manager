package de.tiktak.kmm.plugin;

import java.nio.file.Path;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotNull;

/**
 * @param directory      Verzeichnis, aus dem PF4J die Plugin-JARs lädt
 * @param installBundled mitgelieferte Plugins beim Start in das Verzeichnis kopieren
 * @param settings       Einstellungen je Plugin-ID, z. B. {@code tmdb -> {api-key -> …}}
 */
@Validated
@ConfigurationProperties("kmm.plugins")
public record PluginProperties(
        @NotNull Path directory,
        @DefaultValue("true") boolean installBundled,
        Map<String, Map<String, String>> settings) {

    public PluginProperties {
        settings = settings == null ? Map.of() : Map.copyOf(settings);
    }

    Map<String, String> settingsOf(String pluginId) {
        return settings.getOrDefault(pluginId, Map.of());
    }
}
