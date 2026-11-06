package de.tiktak.kmm.filter;

import tools.jackson.databind.JsonNode;

/**
 * A saved filter for the UI.
 */
public record SavedFilterView(long id, FilterMediaType mediaType, String name, JsonNode definition) {
}
