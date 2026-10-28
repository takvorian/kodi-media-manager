package de.tiktak.kmm.filter;

import tools.jackson.databind.JsonNode;

/**
 * Ein gespeicherter Filter für die Oberfläche.
 */
public record SavedFilterView(long id, FilterMediaType mediaType, String name, JsonNode definition) {
}
