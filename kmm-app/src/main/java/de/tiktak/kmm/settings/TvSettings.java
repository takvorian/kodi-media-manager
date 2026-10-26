package de.tiktak.kmm.settings;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/**
 * Einstellungen für Serien, getrennt von denen für Filme wie in Ember.
 *
 * @param metadataLanguage Sprache der Serien-Metadaten beim Scrapen, z. B. {@code de-DE}
 * @param artworkLanguage  bevorzugte Sprache der Serienbilder (ISO 639-1)
 * @param episodeOrdering  Reihenfolge der Episoden für die Scraper
 */
public record TvSettings(
        @NotBlank @Pattern(regexp = KmmSettings.METADATA_LANGUAGE_PATTERN) String metadataLanguage,
        @NotBlank @Pattern(regexp = KmmSettings.ARTWORK_LANGUAGE_PATTERN) String artworkLanguage,
        @NotNull EpisodeOrdering episodeOrdering) {

    public TvSettings {
        metadataLanguage = metadataLanguage == null ? null : metadataLanguage.strip();
        artworkLanguage = artworkLanguage == null ? null : artworkLanguage.strip();
    }

    /** Vorgabe: dieselben Sprachen wie bei Filmen, Episoden in Ausstrahlungsreihenfolge. */
    static TvSettings like(String metadataLanguage, String artworkLanguage) {
        return new TvSettings(metadataLanguage, artworkLanguage, EpisodeOrdering.AIRED);
    }
}
