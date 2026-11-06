package de.tiktak.kmm.plugin.tmdb;

import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import tools.jackson.databind.JsonNode;

/**
 * Helpers for handling JSON responses.
 */
final class JsonNodes {

    private JsonNodes() {
    }

    /** Elements of a JSON array; empty if the node is missing or not an array. */
    static Stream<JsonNode> elements(JsonNode array) {
        return StreamSupport.stream(array.spliterator(), false);
    }
}
