package de.tiktak.kmm.plugin.tvdb;

import java.util.Optional;
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

    /** Text eines Feldes ohne Leerraum; leer, wenn das Feld fehlt, {@code null} oder leer ist. */
    static Optional<String> text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (value.isMissingNode() || value.isNull()) {
            return Optional.empty();
        }
        String text = value.asString().strip();
        return text.isEmpty() ? Optional.empty() : Optional.of(text);
    }
}
