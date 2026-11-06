package de.tiktak.kmm.plugin.tvdb;

import java.util.Optional;
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

    /** Text of a field without surrounding whitespace; empty if the field is missing, {@code null} or empty. */
    static Optional<String> text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (value.isMissingNode() || value.isNull()) {
            return Optional.empty();
        }
        String text = value.asString().strip();
        return text.isEmpty() ? Optional.empty() : Optional.of(text);
    }
}
