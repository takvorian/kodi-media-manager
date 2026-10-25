package de.tiktak.kmm.source;

import de.tiktak.kmm.plugin.api.MediaType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Daten zum Anlegen einer Quelle.
 */
public record CreateMediaSourceCommand(
        @NotBlank @Size(max = MAX_NAME_LENGTH) String name,
        @NotBlank @Size(max = MAX_PATH_LENGTH) String path,
        @NotNull MediaType mediaType) {

    static final int MAX_NAME_LENGTH = 255;
    static final int MAX_PATH_LENGTH = 1024;
}
