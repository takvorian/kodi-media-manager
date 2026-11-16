package de.tiktak.kmm.plugin.api;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.pf4j.ExtensionPoint;

import de.tiktak.kmm.i18n.LocalizedText;

/**
 * Properties shared by all scraper extensions.
 */
public interface Scraper extends ExtensionPoint {

    /**
     * Unique, stable identifier of the scraper, e.g. {@code tmdb}.
     */
    String id();

    String displayName();

    Set<MediaType> supportedMediaTypes();

    default boolean supports(MediaType mediaType) {
        return supportedMediaTypes().contains(mediaType);
    }

    /**
     * Called by KMM once after creation, before the scraper is used.
     */
    default void initialize(PluginContext context) {
    }

    /**
     * @return note for the user on what is missing for use (e.g. an API key), or empty if the scraper is ready
     */
    default Optional<LocalizedText> missingConfiguration() {
        return Optional.empty();
    }

    /**
     * Settings of the plugin the user can change in KMM, e.g. its API key. All scrapers of a plugin share the settings,
     * so it is enough if one of them declares them.
     */
    default List<SettingDefinition> settingDefinitions() {
        return List.of();
    }

    /**
     * @return attribution of the data source required by the provider, or empty
     */
    default Optional<Attribution> attribution() {
        return Optional.empty();
    }
}
