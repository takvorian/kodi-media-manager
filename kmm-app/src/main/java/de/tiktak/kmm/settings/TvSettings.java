package de.tiktak.kmm.settings;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/**
 * Settings for TV shows, separate from those for movies as in Ember.
 *
 * @param metadataLanguage language of the TV show metadata when scraping, e.g. {@code de-DE}
 * @param artworkLanguage  preferred language of the TV show artwork (ISO 639-1)
 * @param episodeOrdering  order of the episodes for the scrapers
 */
public record TvSettings(
        @NotBlank @Pattern(regexp = KmmSettings.METADATA_LANGUAGE_PATTERN) String metadataLanguage,
        @NotBlank @Pattern(regexp = KmmSettings.ARTWORK_LANGUAGE_PATTERN) String artworkLanguage,
        @NotNull EpisodeOrdering episodeOrdering) {

    public TvSettings {
        metadataLanguage = metadataLanguage == null ? null : metadataLanguage.strip();
        artworkLanguage = artworkLanguage == null ? null : artworkLanguage.strip();
    }

    /** Default: the same languages as for movies, episodes in airing order. */
    static TvSettings like(String metadataLanguage, String artworkLanguage) {
        return new TvSettings(metadataLanguage, artworkLanguage, EpisodeOrdering.AIRED);
    }
}
