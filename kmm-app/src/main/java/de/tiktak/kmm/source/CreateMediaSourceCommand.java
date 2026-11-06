package de.tiktak.kmm.source;

import de.tiktak.kmm.plugin.api.MediaType;
import de.tiktak.kmm.settings.KmmSettings;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Data for creating a source.
 */
public record CreateMediaSourceCommand(
        @NotBlank @Size(max = MAX_NAME_LENGTH) String name,
        @NotBlank @Size(max = MAX_PATH_LENGTH) String path,
        @NotNull MediaType mediaType,
        @Pattern(regexp = KmmSettings.METADATA_LANGUAGE_PATTERN) String metadataLanguage) {

    static final int MAX_NAME_LENGTH = 255;
    static final int MAX_PATH_LENGTH = 1024;

    /** A source for which the general language applies. */
    public CreateMediaSourceCommand(String name, String path, MediaType mediaType) {
        this(name, path, mediaType, null);
    }
}
