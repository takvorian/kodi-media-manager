package de.tiktak.kmm.plugin.api;

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
     * @return attribution of the data source required by the provider, or empty
     */
    default Optional<Attribution> attribution() {
        return Optional.empty();
    }
}
