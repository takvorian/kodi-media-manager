package de.tiktak.kmm.plugin.tmdb;

import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import tools.jackson.databind.JsonNode;

/**
 * Hilfen für den Umgang mit JSON-Antworten.
 */
final class JsonNodes {

    private JsonNodes() {
    }

    /** Elemente eines JSON-Arrays; leer, wenn der Knoten fehlt oder kein Array ist. */
    static Stream<JsonNode> elements(JsonNode array) {
        return StreamSupport.stream(array.spliterator(), false);
    }
}
