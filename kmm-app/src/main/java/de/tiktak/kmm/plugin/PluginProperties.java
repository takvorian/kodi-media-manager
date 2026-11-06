package de.tiktak.kmm.plugin;

import java.nio.file.Path;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotNull;

/**
 * @param directory      directory from which PF4J loads the plugin JARs
 * @param installBundled copy bundled plugins into the directory on startup
 * @param settings       settings per plugin ID, e.g. {@code tmdb -> {api-key -> …}}
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
