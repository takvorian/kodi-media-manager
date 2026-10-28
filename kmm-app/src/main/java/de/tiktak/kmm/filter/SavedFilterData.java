package de.tiktak.kmm.filter;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import tools.jackson.databind.JsonNode;

/**
 * Anlegen oder Ändern eines gespeicherten Filters.
 *
 * @param definition die Bedingungen, wie die Liste sie abfragt, z. B. {@code {"matchAll": true, "conditions": [...]}}
 */
public record SavedFilterData(
        @NotNull FilterMediaType mediaType,
        @NotBlank @Size(max = SavedFilterData.MAX_NAME_LENGTH) String name,
        @NotNull JsonNode definition) {

    static final int MAX_NAME_LENGTH = 255;

    public SavedFilterData {
        name = name == null ? null : name.strip();
    }
}
